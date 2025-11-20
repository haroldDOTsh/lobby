package sh.harold.fulcrum.lobby.feature;

import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import sh.harold.fulcrum.api.rank.Rank;
import sh.harold.fulcrum.api.rank.RankUtils;
import sh.harold.fulcrum.common.cooldown.CooldownAcquisition;
import sh.harold.fulcrum.common.cooldown.CooldownKey;
import sh.harold.fulcrum.common.cooldown.CooldownKeys;
import sh.harold.fulcrum.common.cooldown.CooldownRegistry;
import sh.harold.fulcrum.common.cooldown.CooldownSpec;
import sh.harold.fulcrum.lifecycle.ServiceLocatorImpl;
import sh.harold.fulcrum.lobby.system.LobbyFeature;
import sh.harold.fulcrum.lobby.system.LobbyFeatureContext;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Allows top donators to launch staff members into the sky with a left-click punch.
 */
public final class StaffPunchFeature implements LobbyFeature, Listener {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final double MIN_VERTICAL_VELOCITY = 1.35D;
    private static final double MAX_VERTICAL_VELOCITY = 1.75D;
    private static final int BUILD_UP_DURATION_TICKS = 40;
    private static final int BUILD_UP_INTERVAL_TICKS = 4;
    private static final int TRAIL_DURATION_TICKS = 30;
    private static final int TRAIL_INTERVAL_TICKS = 2;
    private static final Duration STAFF_PUNCH_COOLDOWN = Duration.ofSeconds(5L);
    private static final Particle.DustOptions WHITE_DUST = new Particle.DustOptions(Color.fromRGB(255, 255, 255), 1.35F);
    private static final Component PLAYER_ONLY_MESSAGE = Component.text("Only players can use this command.", NamedTextColor.RED);
    private static final Component STAFF_ONLY_MESSAGE = Component.text("Only staff can turn off staff punches.", NamedTextColor.RED);
    private static final Component OPT_OUT_ENABLED_MESSAGE = Component.text("Staff punches disabled. Donators cannot launch you.", NamedTextColor.GREEN);
    private static final Component OPT_OUT_DISABLED_MESSAGE = Component.text("Staff punches enabled. Donators can launch you again.", NamedTextColor.YELLOW);
    private static final Component PUNCHES_DISABLED_NOTICE = Component.text("That staff member has staff punches disabled.", NamedTextColor.RED);

    private JavaPlugin plugin;
    private CooldownRegistry cooldownRegistry;
    private final Set<UUID> activeVictims = ConcurrentHashMap.newKeySet();
    private final Set<UUID> punchOptOuts = ConcurrentHashMap.newKeySet();

    @Override
    public String id() {
        return "staff-punch";
    }

    @Override
    public int priority() {
        return 120;
    }

    @Override
    public void initialize(LobbyFeatureContext context) {
        this.plugin = context.plugin();
        ServiceLocatorImpl locator = ServiceLocatorImpl.getInstance();
        if (locator != null) {
            cooldownRegistry = locator.findService(CooldownRegistry.class).orElse(null);
        }
        PluginManager pluginManager = plugin.getServer().getPluginManager();
        pluginManager.registerEvents(this, plugin);
        registerCommandHandler();
        context.logger().info("Staff punch feature initialised.");
    }

    @Override
    public void shutdown(LobbyFeatureContext context) {
        HandlerList.unregisterAll(this);
        activeVictims.clear();
        punchOptOuts.clear();
        cooldownRegistry = null;
        this.plugin = null;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        activeVictims.remove(playerId);
        punchOptOuts.remove(playerId);
    }

    @EventHandler(ignoreCancelled = true)
    public void onStaffPunch(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        DamageCause cause = event.getCause();
        if (cause != DamageCause.ENTITY_ATTACK && cause != DamageCause.ENTITY_SWEEP_ATTACK) {
            return;
        }

        Rank attackerRank = Optional.ofNullable(RankUtils.getEffectiveRank(attacker)).orElse(Rank.DEFAULT);
        if (attackerRank != Rank.DONATOR_4) {
            return;
        }

        if (!RankUtils.isStaff(victim)) {
            return;
        }

        if (punchOptOuts.contains(victim.getUniqueId())) {
            event.setCancelled(true);
            attacker.sendMessage(PUNCHES_DISABLED_NOTICE);
            return;
        }

        if (!acquireStaffPunchTicket(attacker)) {
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);
        beginPunchSequence(attacker, victim, attackerRank);
    }

    private void registerCommandHandler() {
        if (plugin == null) {
            return;
        }
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands commands = event.registrar();
            commands.register(
                    Commands.literal("dontpunchme")
                            .executes(context -> handleDontPunchMeCommand(context.getSource()))
                            .build(),
                    "Toggle staff punch immunity"
            );
        });
    }

    private int handleDontPunchMeCommand(CommandSourceStack sourceStack) {
        if (!(sourceStack.getSender() instanceof Player player)) {
            sourceStack.getSender().sendMessage(PLAYER_ONLY_MESSAGE);
            return Command.SINGLE_SUCCESS;
        }
        if (!RankUtils.isStaff(player)) {
            player.sendMessage(STAFF_ONLY_MESSAGE);
            return Command.SINGLE_SUCCESS;
        }

        UUID playerId = player.getUniqueId();
        boolean optingOut = punchOptOuts.add(playerId);
        if (!optingOut) {
            punchOptOuts.remove(playerId);
            player.sendMessage(OPT_OUT_DISABLED_MESSAGE);
        } else {
            player.sendMessage(OPT_OUT_ENABLED_MESSAGE);
        }
        return Command.SINGLE_SUCCESS;
    }

    private void beginPunchSequence(Player attacker, Player victim, Rank attackerRank) {
        if (plugin == null) {
            return;
        }

        UUID victimId = victim.getUniqueId();
        if (!activeVictims.add(victimId)) {
            return;
        }

        Location startLocation = victim.getLocation();
        victim.getWorld().playSound(startLocation, Sound.ENTITY_CREEPER_PRIMED, SoundCategory.PLAYERS, 1.1F, 1.0F);

        final int[] elapsed = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (!victim.isOnline()) {
                task.cancel();
                activeVictims.remove(victimId);
                return;
            }

            spawnBuildUpParticles(victim);
            elapsed[0] += BUILD_UP_INTERVAL_TICKS;

            if (elapsed[0] >= BUILD_UP_DURATION_TICKS) {
                task.cancel();
                triggerKaboom(attacker, victim, attackerRank);
            }
        }, 0L, BUILD_UP_INTERVAL_TICKS);
    }

    private void spawnBuildUpParticles(Player victim) {
        Location base = victim.getLocation().clone().subtract(0, 0.2, 0);
        victim.getWorld().spawnParticle(
                Particle.EXPLOSION,
                base.getX(),
                base.getY(),
                base.getZ(),
                6,
                0.3,
                0.05,
                0.3,
                0.02
        );
    }

    private void triggerKaboom(Player attacker, Player victim, Rank attackerRank) {
        try {
            if (!victim.isOnline()) {
                return;
            }

            Location kaboomLocation = victim.getLocation();
            victim.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, kaboomLocation, 1);
            victim.getWorld().playSound(kaboomLocation, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1.3F, 1.1F);

            launchVictim(attacker, victim, attackerRank);
            spawnTrail(victim);
        } finally {
            activeVictims.remove(victim.getUniqueId());
        }
    }

    private void spawnTrail(Player victim) {
        if (plugin == null) {
            return;
        }

        final int[] elapsed = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, scheduledTask -> {
            if (!victim.isOnline()) {
                scheduledTask.cancel();
                return;
            }

            Location trailLocation = victim.getLocation().clone().subtract(0, 0.35, 0);
            victim.getWorld().spawnParticle(
                    Particle.DUST,
                    trailLocation.getX(),
                    trailLocation.getY(),
                    trailLocation.getZ(),
                    4,
                    0.15,
                    0.05,
                    0.15,
                    0.0,
                    WHITE_DUST
            );

            elapsed[0] += TRAIL_INTERVAL_TICKS;
            if (elapsed[0] >= TRAIL_DURATION_TICKS) {
                scheduledTask.cancel();
            }
        }, 0L, TRAIL_INTERVAL_TICKS);
    }

    private void launchVictim(Player attacker, Player victim, Rank attackerRank) {
        double verticalVelocity = ThreadLocalRandom.current().nextDouble(MIN_VERTICAL_VELOCITY, MAX_VERTICAL_VELOCITY);
        Vector launchVector = new Vector(0, verticalVelocity, 0);
        victim.setVelocity(launchVector);
        victim.setFallDistance(0);

        Rank victimRank = Optional.ofNullable(RankUtils.getEffectiveRank(victim)).orElse(Rank.DEFAULT);
        Component message = Component.empty()
                .append(formatPlayer(attacker.getName(), attackerRank))
                .append(Component.text(" punched ", NamedTextColor.GRAY))
                .append(formatPlayer(victim.getName(), victimRank))
                .append(Component.text(" into the sky!", NamedTextColor.GRAY));

        if (plugin != null) {
            plugin.getServer().sendMessage(message);
        } else {
            Bukkit.getServer().sendMessage(message);
        }
    }

    private Component formatPlayer(String playerName, Rank rank) {
        String fullPrefix = rank.getFullPrefix();
        Component prefix = Component.empty();
        if (fullPrefix != null && !fullPrefix.isBlank()) {
            prefix = LEGACY.deserialize(fullPrefix).append(Component.space());
        }
        NamedTextColor nameColor = Optional.ofNullable(rank.getNameColor()).orElse(NamedTextColor.WHITE);
        return prefix.append(Component.text(playerName, nameColor));
    }

    private boolean acquireStaffPunchTicket(Player attacker) {
        if (attacker == null) {
            return false;
        }
        if (cooldownRegistry == null) {
            return true;
        }
        CooldownKey key = CooldownKeys.of("lobby", "staff-punch", attacker.getUniqueId(), null);
        CooldownAcquisition acquisition;
        try {
            acquisition = cooldownRegistry.acquire(key, CooldownSpec.rejecting(STAFF_PUNCH_COOLDOWN))
                    .toCompletableFuture()
                    .join();
        } catch (Exception exception) {
            JavaPlugin owningPlugin = this.plugin;
            if (owningPlugin != null) {
                owningPlugin.getLogger().log(Level.WARNING, "Failed to acquire staff punch cooldown for "
                        + attacker.getName(), exception);
            }
            return true;
        }
        if (acquisition instanceof CooldownAcquisition.Accepted) {
            return true;
        }
        Duration remaining = acquisition instanceof CooldownAcquisition.Rejected rejected
                ? rejected.remaining()
                : STAFF_PUNCH_COOLDOWN;
        long seconds = Math.max(1L, remaining.toSeconds());
        attacker.sendMessage(Component.text(
                "Whoa! Give staff a moment before launching them again (" + seconds + "s).",
                NamedTextColor.RED));
        return false;
    }
}
