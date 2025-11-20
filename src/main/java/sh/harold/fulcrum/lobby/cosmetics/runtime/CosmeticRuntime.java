package sh.harold.fulcrum.lobby.cosmetics.runtime;

import com.destroystokyo.paper.ParticleBuilder;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import sh.harold.fulcrum.lobby.cosmetics.CloakCosmetic;
import sh.harold.fulcrum.lobby.cosmetics.ClickEffectCosmetic;
import sh.harold.fulcrum.lobby.cosmetics.Cosmetic;
import sh.harold.fulcrum.lobby.cosmetics.CosmeticKeys;
import sh.harold.fulcrum.lobby.cosmetics.CosmeticSlot;
import sh.harold.fulcrum.lobby.cosmetics.ParticleTrailCosmetic;
import sh.harold.fulcrum.lobby.cosmetics.SuitSet;
import sh.harold.fulcrum.lobby.cosmetics.SuitSlot;
import sh.harold.fulcrum.lobby.cosmetics.loadout.CosmeticLoadout;
import sh.harold.fulcrum.lobby.cosmetics.loadout.LoadoutService;
import sh.harold.fulcrum.lobby.cosmetics.registry.CosmeticRegistry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Coordinates cosmetic lifecycles and async ticking.
 */
public final class CosmeticRuntime implements Listener, AutoCloseable {
    private static final long HEARTBEAT_DELAY_TICKS = 20L;
    private static final long HEARTBEAT_PERIOD_TICKS = 1L;
    private static final double MOVEMENT_EPSILON = 0.0025D;
    private static final long IDLE_DWELL_MILLIS = 1_500L;

    private final JavaPlugin plugin;
    private final CosmeticRegistry registry;
    private final LoadoutService loadoutService;
    private final Logger logger;
    private final Map<UUID, ActivePlayerState> activePlayers = new ConcurrentHashMap<>();
    private final ExecutorService executor;
    private BukkitTask heartbeatTask;

    public CosmeticRuntime(JavaPlugin plugin, CosmeticRegistry registry, LoadoutService loadoutService, Logger logger) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.loadoutService = Objects.requireNonNull(loadoutService, "loadoutService");
        this.logger = logger;
        int poolSize = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);
        this.executor = Executors.newFixedThreadPool(poolSize, new CosmeticThreadFactory());
    }

    public void start() {
        if (heartbeatTask != null) {
            return;
        }
        BukkitScheduler scheduler = plugin.getServer().getScheduler();
        heartbeatTask = scheduler.runTaskTimer(plugin, this::heartbeat, HEARTBEAT_DELAY_TICKS, HEARTBEAT_PERIOD_TICKS);
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            handleJoin(online);
        }
    }

    @Override
    public void close() {
        if (heartbeatTask != null) {
            heartbeatTask.cancel();
            heartbeatTask = null;
        }
        executor.shutdownNow();
        for (UUID uuid : new ArrayList<>(activePlayers.keySet())) {
            teardown(uuid);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        handleJoin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        refresh(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        refresh(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        refresh(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        teardown(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Player owner)) {
            return;
        }
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) {
            return;
        }
        handleClick(owner, event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player owner)) {
            return;
        }
        if (!(event.getDamager() instanceof Player clicker)) {
            return;
        }
        handleClick(owner, clicker);
    }

    private void handleJoin(Player player) {
        requestLoadout(player);
    }

    public void reloadPlayer(UUID playerId) {
        Player player = plugin.getServer().getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return;
        }
        requestLoadout(player);
    }

    private void requestLoadout(Player player) {
        UUID playerId = player.getUniqueId();
        loadoutService.loadout(playerId).whenComplete((loadout, throwable) -> {
            if (throwable != null) {
                if (logger != null) {
                    logger.log(Level.SEVERE, "Unable to load cosmetics for " + player.getName(), throwable);
                }
                return;
            }
            if (!plugin.isEnabled()) {
                return;
            }
            CosmeticLoadout resolved = loadout == null ? CosmeticLoadout.EMPTY : loadout;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                applyLoadout(player, resolved);
            });
        });
    }

    private void refresh(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            ActivePlayerState state = activePlayers.get(player.getUniqueId());
            if (state == null) {
                return;
            }
            applySuitPieces(player, state);
        });
    }

    private void handleClick(Player owner, Player clicker) {
        if (owner.getUniqueId().equals(clicker.getUniqueId())) {
            return;
        }
        ActivePlayerState state = activePlayers.get(owner.getUniqueId());
        if (state == null || state.clickEffect == null) {
            return;
        }
        state.clickEffect.onClick(owner, clicker);
    }

    private void heartbeat() {
        if (activePlayers.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        List<TickRequest> requests = new ArrayList<>();
        for (ActivePlayerState state : activePlayers.values()) {
            Player player = plugin.getServer().getPlayer(state.playerId);
            if (player == null || !player.isOnline()) {
                continue;
            }
            PlayerContext context = PlayerContext.fromPlayer(player, state.lastPosition);
            state.lastPosition = context.position();
            handleSuitStates(state, context);
            handleTrailTick(state, context, requests);
            handleCloakTick(player, state, context, now, requests);
        }
        if (requests.isEmpty()) {
            return;
        }
        CompletableFuture
                .supplyAsync(() -> runTicks(requests), executor)
                .thenAccept(instructions -> Bukkit.getScheduler()
                        .runTask(plugin, () -> flushInstructions(instructions)))
                .exceptionally(throwable -> {
                    if (logger != null) {
                        logger.log(Level.SEVERE, "Cosmetic tick failure", throwable);
                    }
                    return null;
                });
    }

    private void handleSuitStates(ActivePlayerState state, PlayerContext context) {
        if (state.suitPieces.isEmpty()) {
            return;
        }
        Map<String, Integer> counters = new HashMap<>();
        for (Map.Entry<SuitSlot, String> entry : state.suitPieces.entrySet()) {
            String flatKey = entry.getValue();
            CosmeticKeys.setIdFromPieceKey(flatKey).ifPresent(setId -> {
                counters.merge(setId, 1, Integer::sum);
            });
        }
        Set<String> currentlyFull = new HashSet<>();
        for (Map.Entry<String, Integer> entry : counters.entrySet()) {
            if (entry.getValue() == SuitSlot.values().length) {
                currentlyFull.add(entry.getKey());
            }
        }
        for (String setId : currentlyFull) {
            if (state.activeFullSets.add(setId)) {
                SuitSet suit = state.suitSets.get(setId);
                if (suit != null) {
                    safeInvoke(() -> suit.onFullSetStart(context));
                }
            }
        }
        for (String active : new HashSet<>(state.activeFullSets)) {
            if (!currentlyFull.contains(active)) {
                SuitSet suit = state.suitSets.get(active);
                if (suit != null) {
                    safeInvoke(() -> suit.onFullSetEnd(context));
                }
                state.activeFullSets.remove(active);
            }
        }
    }

    private void handleTrailTick(ActivePlayerState state, PlayerContext context, List<TickRequest> requests) {
        ParticleTrailCosmetic trail = state.trail;
        if (trail == null) {
            return;
        }
        requests.add(new TickRequest(trail, context));
    }

    private void handleCloakTick(Player player, ActivePlayerState state, PlayerContext context, long now,
                                 List<TickRequest> requests) {
        CloakCosmetic cloak = state.cloak;
        if (cloak == null) {
            return;
        }
        boolean creativeFlying = player.isFlying();
        boolean gliding = player.isGliding();
        boolean moving = context.velocity().lengthSquared() > MOVEMENT_EPSILON
                || gliding
                || (!context.onGround() && !creativeFlying);
        if (moving) {
            state.lastMovementAt = now;
            if (state.cloakActive) {
                state.cloakActive = false;
                safeInvoke(() -> cloak.onCancel(context));
            }
            return;
        }
        if (!state.cloakActive) {
            long dwell = now - state.lastMovementAt;
            if (dwell >= IDLE_DWELL_MILLIS) {
                state.cloakActive = true;
                safeInvoke(() -> cloak.onIdleStart(context));
            } else {
                return;
            }
        }
        if (state.cloakActive) {
            requests.add(new TickRequest(cloak, context));
        }
    }

    private List<ParticleInstruction> runTicks(List<TickRequest> requests) {
        List<ParticleInstruction> instructions = new ArrayList<>();
        for (TickRequest request : requests) {
            try {
                instructions.addAll(request.execute());
            } catch (Exception exception) {
                if (logger != null) {
                    logger.log(Level.WARNING, "Cosmetic tick failed for " + request.cosmetic().id(), exception);
                }
            }
        }
        return instructions;
    }

    private void flushInstructions(Collection<ParticleInstruction> instructions) {
        if (instructions.isEmpty()) {
            return;
        }
        for (ParticleInstruction instruction : instructions) {
            World world = plugin.getServer().getWorld(instruction.worldId());
            if (world == null) {
                continue;
            }
            Vector3d position = instruction.position();
            Vector3d offset = instruction.offset();
            ParticleBuilder builder = new ParticleBuilder(instruction.particle())
                    .count(instruction.count())
                    .extra(instruction.extra())
                    .force(instruction.force())
                    .location(world, position.x(), position.y(), position.z())
                    .offset(offset.x(), offset.y(), offset.z());
            Object data = instruction.data();
            if (data != null) {
                builder.data(data);
            }
            builder.spawn();
        }
    }

    private void applyLoadout(Player player, CosmeticLoadout loadout) {
        UUID uuid = player.getUniqueId();
        ActivePlayerState previous = activePlayers.remove(uuid);
        if (previous != null) {
            teardown(player, previous);
        }
        ActivePlayerState state = new ActivePlayerState(uuid, loadout);
        state.lastMovementAt = System.currentTimeMillis();
        state.trail = instantiate(validEquipped(loadout, CosmeticSlot.TRAIL), ParticleTrailCosmetic.class);
        state.cloak = instantiate(validEquipped(loadout, CosmeticSlot.CLOAK), CloakCosmetic.class);
        state.clickEffect = instantiate(validEquipped(loadout, CosmeticSlot.CLICK), ClickEffectCosmetic.class);
        state.suitPieces.putAll(resolveSuitPieces(loadout));
        state.suitSets.putAll(instantiateSuitSets(state.suitPieces.values()));
        activePlayers.put(uuid, state);
        applySuitPieces(player, state);
    }

    private EnumMap<SuitSlot, String> resolveSuitPieces(CosmeticLoadout loadout) {
        EnumMap<SuitSlot, String> pieces = new EnumMap<>(SuitSlot.class);
        for (SuitSlot slot : SuitSlot.values()) {
            loadout.equipped(slot.cosmeticSlot()).ifPresent(flatKey -> {
                if (loadout.isUnlocked(flatKey)) {
                    pieces.put(slot, flatKey);
                }
            });
        }
        return pieces;
    }

    private Map<String, SuitSet> instantiateSuitSets(Collection<String> flatKeys) {
        Map<String, SuitSet> suits = new HashMap<>();
        for (String flatKey : flatKeys) {
            CosmeticKeys.setIdFromPieceKey(flatKey).ifPresent(setId -> {
                suits.computeIfAbsent(setId, this::instantiateSuit);
            });
        }
        suits.values().removeIf(Objects::isNull);
        return suits;
    }

    private SuitSet instantiateSuit(String setId) {
        SuitSet suit = instantiate(Optional.of(setId), SuitSet.class);
        if (suit != null) {
            safeInvoke(suit::prepareAssets);
        }
        return suit;
    }

    private <T extends Cosmetic> T instantiate(Optional<String> id, Class<T> type) {
        if (id.isEmpty()) {
            return null;
        }
        String cosmeticId = id.get();
        Optional<Cosmetic> resolved = registry.instantiate(cosmeticId);
        if (resolved.isEmpty()) {
            resolved = registry.instantiateFromFlatKey(cosmeticId);
        }
        return resolved.filter(type::isInstance)
                .map(type::cast)
                .orElse(null);
    }

    private Optional<String> validEquipped(CosmeticLoadout loadout, CosmeticSlot slot) {
        return loadout.equipped(slot).filter(loadout::isUnlocked);
    }

    private void applySuitPieces(Player player, ActivePlayerState state) {
        PlayerInventory inventory = player.getInventory();
        for (SuitSlot slot : SuitSlot.values()) {
            String flatKey = state.suitPieces.get(slot);
            if (flatKey == null) {
                restoreSlot(inventory, state, slot);
                continue;
            }
            SuitSet suit = CosmeticKeys.setIdFromPieceKey(flatKey)
                    .map(state.suitSets::get)
                    .orElse(null);
            if (suit == null) {
                restoreSlot(inventory, state, slot);
                continue;
            }
            ItemStack item = buildPiece(suit, slot);
            if (item == null) {
                restoreSlot(inventory, state, slot);
                continue;
            }
            state.originalArmor.computeIfAbsent(slot, s -> getArmor(inventory, slot));
            setArmor(inventory, slot, item.clone());
        }
    }

    private ItemStack buildPiece(SuitSet suit, SuitSlot slot) {
        return switch (slot) {
            case HELMET -> suit.helmet();
            case CHEST -> suit.chestplate();
            case LEGGINGS -> suit.leggings();
            case BOOTS -> suit.boots();
        };
    }

    private void restoreSlot(PlayerInventory inventory, ActivePlayerState state, SuitSlot slot) {
        ItemStack original = state.originalArmor.remove(slot);
        if (original != null || hasArmor(inventory, slot)) {
            setArmor(inventory, slot, original);
        }
    }

    private ItemStack getArmor(PlayerInventory inventory, SuitSlot slot) {
        return switch (slot) {
            case HELMET -> inventory.getHelmet();
            case CHEST -> inventory.getChestplate();
            case LEGGINGS -> inventory.getLeggings();
            case BOOTS -> inventory.getBoots();
        };
    }

    private void setArmor(PlayerInventory inventory, SuitSlot slot, ItemStack stack) {
        switch (slot) {
            case HELMET -> inventory.setHelmet(stack);
            case CHEST -> inventory.setChestplate(stack);
            case LEGGINGS -> inventory.setLeggings(stack);
            case BOOTS -> inventory.setBoots(stack);
        }
    }

    private boolean hasArmor(PlayerInventory inventory, SuitSlot slot) {
        return getArmor(inventory, slot) != null;
    }

    private void teardown(UUID playerId) {
        Player player = plugin.getServer().getPlayer(playerId);
        ActivePlayerState state = activePlayers.remove(playerId);
        if (state == null) {
            return;
        }
        if (player != null) {
            teardown(player, state);
        }
    }

    private void teardown(Player player, ActivePlayerState state) {
        PlayerInventory inventory = player.getInventory();
        for (SuitSlot slot : SuitSlot.values()) {
            restoreSlot(inventory, state, slot);
        }
        PlayerContext context = PlayerContext.fromPlayer(player, state.lastPosition);
        if (state.cloakActive && state.cloak != null) {
            safeInvoke(() -> state.cloak.onCancel(context));
        }
        for (String active : state.activeFullSets) {
            SuitSet suit = state.suitSets.get(active);
            if (suit != null) {
                safeInvoke(() -> suit.onFullSetEnd(context));
            }
        }
        state.activeFullSets.clear();
    }

    private void safeInvoke(Runnable runnable) {
        try {
            runnable.run();
        } catch (Exception exception) {
            if (logger != null) {
                logger.log(Level.WARNING, "Cosmetic lifecycle threw an exception", exception);
            }
        }
    }

    private record TickRequest(Cosmetic cosmetic, PlayerContext context) {
        List<ParticleInstruction> execute() {
            if (cosmetic instanceof ParticleTrailCosmetic trail) {
                return trail.tick(context);
            }
            if (cosmetic instanceof CloakCosmetic cloak) {
                return cloak.tick(context);
            }
            return List.of();
        }
    }

    private static final class ActivePlayerState {
        private final UUID playerId;
        private final CosmeticLoadout loadout;
        private final Map<String, SuitSet> suitSets = new HashMap<>();
        private final EnumMap<SuitSlot, String> suitPieces = new EnumMap<>(SuitSlot.class);
        private final EnumMap<SuitSlot, ItemStack> originalArmor = new EnumMap<>(SuitSlot.class);
        private final Set<String> activeFullSets = new HashSet<>();
        private ParticleTrailCosmetic trail;
        private CloakCosmetic cloak;
        private ClickEffectCosmetic clickEffect;
        private Vector3d lastPosition;
        private long lastMovementAt;
        private boolean cloakActive;

        private ActivePlayerState(UUID playerId, CosmeticLoadout loadout) {
            this.playerId = playerId;
            this.loadout = loadout;
        }
    }

    private static final class CosmeticThreadFactory implements ThreadFactory {
        private int counter = 1;

        @Override
        public synchronized Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "lobby-cosmetics-" + counter++);
            thread.setDaemon(true);
            return thread;
        }
    }
}
