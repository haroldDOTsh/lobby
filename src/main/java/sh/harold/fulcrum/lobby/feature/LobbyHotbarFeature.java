package sh.harold.fulcrum.lobby.feature;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.fulcrum.api.rank.RankUtils;
import sh.harold.fulcrum.common.cooldown.CooldownAcquisition;
import sh.harold.fulcrum.common.cooldown.CooldownKey;
import sh.harold.fulcrum.common.cooldown.CooldownKeys;
import sh.harold.fulcrum.common.cooldown.CooldownRegistry;
import sh.harold.fulcrum.common.cooldown.CooldownSpec;
import sh.harold.fulcrum.common.settings.PlayerSettingsService;
import sh.harold.fulcrum.lifecycle.ServiceLocatorImpl;
import sh.harold.fulcrum.lobby.system.LobbyFeature;
import sh.harold.fulcrum.lobby.system.LobbyFeatureContext;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Ensures all lobby players receive a consistent hotbar layout.
 */
public final class LobbyHotbarFeature implements LobbyFeature, Listener {
    private static final String SETTINGS_SCOPE = "lobby";
    private static final String VISIBILITY_SETTING_KEY = "playerVisibilityEnabled";
    private static final boolean VISIBILITY_DEFAULT = true;
    private static final Duration VISIBILITY_COOLDOWN = Duration.ofSeconds(3L);
    private static final Component VISIBILITY_ENABLED_FEEDBACK = Component.text(
            "Player visibility enabled.", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false);
    private static final Component VISIBILITY_DISABLED_FEEDBACK = Component.text(
            "Player visibility disabled.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false);
    private final Map<HotbarItem, ItemStack> prototypes = new EnumMap<>(HotbarItem.class);
    private final Set<UUID> hiddenPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, AtomicBoolean> pendingVisibilityLoads = new ConcurrentHashMap<>();

    private JavaPlugin plugin;
    private NamespacedKey itemKey;
    private CooldownRegistry cooldownRegistry;
    private PlayerSettingsService.GameSettingsScope lobbySettingsScope;
    private Logger logger;

    @Override
    public String id() {
        return "lobby-hotbar";
    }

    @Override
    public int priority() {
        return 70;
    }

    @Override
    public void initialize(LobbyFeatureContext context) {
        this.plugin = context.plugin();
        this.itemKey = new NamespacedKey(plugin, "lobby_hotbar_item");
        this.logger = context.logger();

        ServiceLocatorImpl locator = ServiceLocatorImpl.getInstance();
        if (locator != null) {
            PlayerSettingsService settingsService = locator.findService(PlayerSettingsService.class).orElse(null);
            if (settingsService != null) {
                lobbySettingsScope = settingsService.forGame(SETTINGS_SCOPE);
            }
            cooldownRegistry = locator.findService(CooldownRegistry.class).orElse(null);
        }

        PluginManager pluginManager = plugin.getServer().getPluginManager();
        pluginManager.registerEvents(this, plugin);

        for (Player online : Bukkit.getOnlinePlayers()) {
            loadVisibilityPreference(online);
            applyLayout(online);
        }
        context.logger().info("Lobby hotbar feature initialised.");
    }

    @Override
    public void shutdown(LobbyFeatureContext context) {
        HandlerList.unregisterAll(this);
        prototypes.clear();
        plugin = null;
        itemKey = null;
        cooldownRegistry = null;
        lobbySettingsScope = null;
        logger = null;
        hiddenPlayers.clear();
        pendingVisibilityLoads.clear();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player != null) {
            AtomicBoolean pending = pendingVisibilityLoads.remove(player.getUniqueId());
            if (pending != null) {
                pending.set(false);
            }
        }
        loadVisibilityPreference(event.getPlayer());
        scheduleHotbarRefresh(event.getPlayer());
        applyHiddenViewersTo(event.getPlayer());
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        scheduleHotbarRefresh(event.getPlayer());
        applyHiddenViewersTo(event.getPlayer());
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        scheduleHotbarRefresh(event.getPlayer());
        applyHiddenViewersTo(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!isRightClick(event.getAction())) {
            return;
        }
        ItemStack stack = event.getItem();
        if (stack == null) {
            stack = player.getInventory().getItemInMainHand();
        }
        HotbarItem item = identifyManagedItem(stack);
        logRightClick(player, event.getAction(), stack, item);
        if (item == null) {
            return;
        }
        if (item == HotbarItem.PLAYER_VISIBILITY) {
            togglePlayerVisibility(player);
            event.setCancelled(true);
            scheduleHotbarRefresh(player);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (player != null) {
            UUID uuid = player.getUniqueId();
            hiddenPlayers.remove(uuid);
            AtomicBoolean pending = pendingVisibilityLoads.remove(uuid);
            if (pending != null) {
                pending.set(false);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() instanceof PlayerInventory
                && HotbarItem.fromSlot(event.getSlot()) != null) {
            enforceInventoryLock(player, event);
            return;
        }
        int hotbarButton = event.getHotbarButton();
        if (hotbarButton >= 0 && HotbarItem.fromSlot(hotbarButton) != null) {
            enforceInventoryLock(player, event);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!affectsManagedSlot(event)) {
            return;
        }
        enforceInventoryLock(player, event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemDrop(PlayerDropItemEvent event) {
        if (!isManagedItem(event.getItemDrop().getItemStack())) {
            return;
        }
        enforceInventoryLock(event.getPlayer(), event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (!isManagedItem(event.getMainHandItem()) && !isManagedItem(event.getOffHandItem())) {
            return;
        }
        enforceInventoryLock(event.getPlayer(), event);
    }

    private void enforceInventoryLock(Player player, Cancellable cancellable) {
        if (!shouldLockHotbar(player)) {
            return;
        }
        if (cancellable != null) {
            cancellable.setCancelled(true);
        }
        scheduleHotbarRefresh(player);
    }

    private boolean shouldLockHotbar(Player player) {
        // The lobby owns every hotbar slot, so we always refuse manual edits.
        return player != null;
    }

    private void applyLayout(Player player) {
        if (player == null) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        for (HotbarItem item : HotbarItem.values()) {
            ItemStack stack = copyOf(item);
            customizeItem(player, item, stack);
            inventory.setItem(item.slot(), stack);
        }
        player.updateInventory();
        enforceVisibilityPreference(player);
    }

    private void scheduleHotbarRefresh(Player player) {
        if (player == null || plugin == null) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> applyLayout(player));
    }

    private ItemStack copyOf(HotbarItem item) {
        ItemStack prototype = prototypes.computeIfAbsent(item, key -> key.createPrototype(itemKey));
        return prototype.clone();
    }

    private void customizeItem(Player player, HotbarItem item, ItemStack stack) {
        if (player == null || stack == null) {
            return;
        }
        switch (item) {
            case PROFILE -> applyProfileMeta(player, stack);
            case PLAYER_VISIBILITY -> applyVisibilityMeta(player, stack);
            default -> { }
        }
    }

    private void applyProfileMeta(Player player, ItemStack stack) {
        if (stack.getType() != Material.PLAYER_HEAD) {
            stack.setType(Material.PLAYER_HEAD);
        }
        ItemMeta meta = stack.getItemMeta();
        if (!(meta instanceof SkullMeta skullMeta)) {
            return;
        }
        skullMeta.setOwningPlayer(player);
        stack.setItemMeta(skullMeta);
    }

    private void applyVisibilityMeta(Player player, ItemStack stack) {
        boolean enabled = isPlayerVisibilityEnabled(player);
        stack.setType(enabled ? Material.LIME_DYE : Material.GRAY_DYE);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        Component status = Component.text(
                enabled ? "Players visible" : "Players hidden",
                enabled ? NamedTextColor.GREEN : NamedTextColor.GRAY
        ).decoration(TextDecoration.ITALIC, false);
        List<Component> baseLore = meta.lore();
        List<Component> updatedLore = new ArrayList<>(baseLore != null ? baseLore : List.of());
        updatedLore.add(status);
        meta.lore(updatedLore);
        stack.setItemMeta(meta);
    }

    private boolean isManagedItem(ItemStack stack) {
        return identifyManagedItem(stack) != null;
    }

    private HotbarItem identifyManagedItem(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta() || itemKey == null) {
            return null;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return null;
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        String identifier = container.get(itemKey, PersistentDataType.STRING);
        if (identifier == null) {
            return null;
        }
        return HotbarItem.fromDataId(identifier);
    }

    private void togglePlayerVisibility(Player player) {
        if (player == null || plugin == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        AtomicBoolean pending = pendingVisibilityLoads.remove(uuid);
        if (pending != null) {
            pending.set(false);
        }
        if (!acquireVisibilityToggleTicket(player)) {
            return;
        }
        boolean enabled = !isPlayerVisibilityEnabled(player);
        applyVisibilityState(player, enabled, true);
        player.sendMessage(enabled ? VISIBILITY_ENABLED_FEEDBACK : VISIBILITY_DISABLED_FEEDBACK);
        scheduleHotbarRefresh(player);
    }

    private void applyVisibilityState(Player player, boolean enabled, boolean persist) {
        if (player == null || plugin == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        boolean current = isPlayerVisibilityEnabled(player);
        if (enabled) {
            hiddenPlayers.remove(uuid);
        } else {
            hiddenPlayers.add(uuid);
        }
        if (current != enabled) {
            if (enabled) {
                showAllPlayers(player);
            } else {
                hideNonStaffPlayers(player);
            }
        } else if (!enabled) {
            // ensure the viewer still hides late joiners even if the stored state matched
            hideNonStaffPlayers(player);
        }
        if (persist) {
            saveVisibilityPreference(uuid, enabled);
        }
    }

    private boolean isPlayerVisibilityEnabled(Player player) {
        return player != null && !hiddenPlayers.contains(player.getUniqueId());
    }

    private void applyHiddenViewersTo(Player target) {
        if (target == null || plugin == null || isAlwaysVisible(target)) {
            return;
        }
        for (UUID viewerId : hiddenPlayers) {
            Player viewer = plugin.getServer().getPlayer(viewerId);
            if (viewer == null || !viewer.isOnline()) {
                continue;
            }
            viewer.hidePlayer(plugin, target);
        }
    }

    private void enforceVisibilityPreference(Player player) {
        if (player == null || plugin == null || isPlayerVisibilityEnabled(player)) {
            return;
        }
        hideNonStaffPlayers(player);
    }

    private void showAllPlayers(Player player) {
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.equals(player)) {
                continue;
            }
            player.showPlayer(plugin, target);
        }
    }

    private void hideNonStaffPlayers(Player player) {
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.equals(player) || isAlwaysVisible(target)) {
                continue;
            }
            player.hidePlayer(plugin, target);
        }
    }

    private boolean isAlwaysVisible(Player target) {
        return target != null && RankUtils.isStaff(target);
    }

    private void saveVisibilityPreference(UUID playerId, boolean enabled) {
        if (playerId == null || lobbySettingsScope == null) {
            return;
        }
        CompletionStage<Void> stage = lobbySettingsScope.set(playerId, VISIBILITY_SETTING_KEY, enabled);
        if (stage == null) {
            return;
        }
        stage.whenComplete((ignored, throwable) -> {
            if (throwable != null && logger != null) {
                logger.log(Level.FINE, "Failed to store lobby visibility setting for " + playerId, throwable);
            }
        });
    }

    private boolean acquireVisibilityToggleTicket(Player player) {
        if (player == null) {
            return false;
        }
        if (cooldownRegistry == null) {
            return true;
        }
        UUID playerId = player.getUniqueId();
        CooldownKey key = CooldownKeys.of("lobby", "visibility-toggle", playerId, null);
        CompletionStage<CooldownAcquisition> stage = cooldownRegistry.acquire(key, CooldownSpec.rejecting(VISIBILITY_COOLDOWN));
        CooldownAcquisition acquisition;
        try {
            acquisition = stage.toCompletableFuture().join();
        } catch (Exception exception) {
            if (logger != null) {
                logger.log(Level.WARNING, "Failed to acquire visibility toggle cooldown for " + player.getName(), exception);
            }
            return true;
        }
        if (acquisition instanceof CooldownAcquisition.Accepted accepted) {
            if (logger != null && logger.isLoggable(Level.FINE)) {
                logger.fine("Visibility toggle accepted for " + player.getName() + ", expires at " + accepted.ticket().expiresAt());
            }
            return true;
        }
        Duration remaining = acquisition instanceof CooldownAcquisition.Rejected rejected
                ? rejected.remaining()
                : VISIBILITY_COOLDOWN;
        if (logger != null && logger.isLoggable(Level.FINE)) {
            logger.fine("Visibility toggle rejected for " + player.getName() + ", remaining=" + remaining);
        }
        player.sendMessage(cooldownMessage(remaining));
        return false;
    }

    private Component cooldownMessage(Duration remaining) {
        long seconds = Math.max(1L, remaining.toSeconds());
        return Component.text("Please wait " + seconds + "s before toggling player visibility again.", NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false);
    }

    private void loadVisibilityPreference(Player player) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        if (lobbySettingsScope == null || plugin == null) {
            applyVisibilityState(player, VISIBILITY_DEFAULT, false);
            return;
        }
        CompletionStage<Optional<Boolean>> stage = lobbySettingsScope.get(playerId, VISIBILITY_SETTING_KEY, Boolean.class);
        if (stage == null) {
            applyVisibilityState(player, VISIBILITY_DEFAULT, false);
            return;
        }
        AtomicBoolean pending = new AtomicBoolean(true);
        pendingVisibilityLoads.put(playerId, pending);
        stage.whenComplete((result, throwable) -> {
            boolean enabled = result != null ? result.orElse(VISIBILITY_DEFAULT) : VISIBILITY_DEFAULT;
            if (throwable != null && logger != null) {
                logger.log(Level.FINE, "Failed to load lobby visibility setting for " + playerId, throwable);
            }
            boolean apply = pending.compareAndSet(true, false);
            pendingVisibilityLoads.remove(playerId, pending);
            if (!apply) {
                return;
            }
            JavaPlugin owningPlugin = this.plugin;
            if (owningPlugin == null) {
                return;
            }
            Bukkit.getScheduler().runTask(owningPlugin, () -> {
                Player online = owningPlugin.getServer().getPlayer(playerId);
                if (online == null) {
                    return;
                }
                applyVisibilityState(online, enabled, false);
                scheduleHotbarRefresh(online);
            });
        });
    }

    private static boolean isRightClick(Action action) {
        return action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
    }

    private void logRightClick(Player player, Action action, ItemStack stack, HotbarItem item) {
        if (logger == null) {
            return;
        }
        logger.fine(() -> "Hotbar right-click: player=" + player.getName()
                + ", action=" + action
                + ", stack=" + (stack != null ? stack.getType() : "null")
                + ", managedItem=" + (item != null ? item.name() : "none"));
    }

    private boolean affectsManagedSlot(InventoryDragEvent event) {
        InventoryView view = event.getView();
        int topSize = view.getTopInventory() != null ? view.getTopInventory().getSize() : 0;
        Set<Integer> rawSlots = event.getRawSlots();
        for (int rawSlot : rawSlots) {
            if (rawSlot < topSize) {
                continue;
            }
            int playerSlot = rawSlot - topSize;
            if (HotbarItem.fromSlot(playerSlot) != null) {
                return true;
            }
        }
        return false;
    }

    private enum HotbarItem {
        GAME_SELECTOR(
                "game-selector",
                0,
                Material.COMPASS,
                displayName("Game Selector", "Right Click"),
                List.of(
                        Component.text("Browse every featured Fulcrum game.", NamedTextColor.GRAY),
                        Component.text("Right-click to open the selector.", NamedTextColor.YELLOW)
                )
        ),
        PROFILE(
                "profile",
                1,
                Material.PLAYER_HEAD,
                displayName("Profile", "Right Click"),
                List.of(
                        Component.text("View stats, rewards, and account settings.", NamedTextColor.GRAY),
                        Component.text("Right-click to open your profile.", NamedTextColor.YELLOW)
                )
        ),
        COSMETICS(
                "cosmetics",
                4,
                Material.CHEST,
                displayName("Cosmetics", "Right Click"),
                List.of(
                        Component.text("Equip gadgets, trails, and more flair.", NamedTextColor.GRAY),
                        Component.text("Right-click to browse cosmetics.", NamedTextColor.YELLOW)
                )
        ),
        PLAYER_VISIBILITY(
                "player-visibility",
                7,
                Material.LIME_DYE,
                displayName("Player Visibility", "Right Click"),
                List.of(
                        Component.text("Toggle between showing or hiding players.", NamedTextColor.GRAY),
                        Component.text("Right-click to cycle modes.", NamedTextColor.YELLOW)
                )
        ),
        LOBBY_SELECTOR(
                "lobby-selector",
                8,
                Material.NETHER_STAR,
                displayName("Lobby Selector", "Right Click"),
                List.of(
                        Component.text("Jump to another lobby shard.", NamedTextColor.GRAY),
                        Component.text("Right-click to pick your destination.", NamedTextColor.YELLOW)
                )
        );

        private final String dataId;
        private final int slot;
        private final Material material;
        private final Component displayName;
        private final List<Component> lore;

        HotbarItem(String dataId,
                   int slot,
                   Material material,
                   Component displayName,
                   List<Component> lore) {
            this.dataId = Objects.requireNonNull(dataId, "dataId");
            this.slot = slot;
            this.material = Objects.requireNonNull(material, "material");
            this.displayName = Objects.requireNonNull(displayName, "displayName");
            this.lore = List.copyOf(Objects.requireNonNull(lore, "lore"));
        }

        int slot() {
            return slot;
        }

        ItemStack createPrototype(NamespacedKey itemKey) {
            ItemStack stack = new ItemStack(material);
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                meta.displayName(displayName);
                meta.lore(lore);
                meta.addItemFlags(
                        ItemFlag.HIDE_ATTRIBUTES,
                        ItemFlag.HIDE_ENCHANTS,
                        ItemFlag.HIDE_ADDITIONAL_TOOLTIP
                );
                if (itemKey != null) {
                    meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, dataId);
                }
                stack.setItemMeta(meta);
            }
            stack.setAmount(1);
            return stack;
        }

        static HotbarItem fromSlot(int slot) {
            for (HotbarItem item : values()) {
                if (item.slot == slot) {
                    return item;
                }
            }
            return null;
        }

        static HotbarItem fromDataId(String dataId) {
            for (HotbarItem item : values()) {
                if (item.dataId.equalsIgnoreCase(dataId)) {
                    return item;
                }
            }
            return null;
        }

        private static Component displayName(String name, String action) {
            Component label = Component.text()
                    .append(Component.text(name + " ", NamedTextColor.GREEN))
                    .append(Component.text("(" + action + ")", NamedTextColor.GRAY))
                    .build();
            return label.decoration(TextDecoration.ITALIC, false);
        }
    }
}
