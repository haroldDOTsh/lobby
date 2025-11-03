package sh.harold.fulcrum.lobby.feature;

import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.fulcrum.api.rank.Rank;
import sh.harold.fulcrum.api.rank.RankUtils;
import sh.harold.fulcrum.lobby.system.LobbyFeature;
import sh.harold.fulcrum.lobby.system.LobbyFeatureContext;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Grants lobby flight perks to all non-default ranks with an opt-out toggle.
 */
public final class RankFlightFeature implements LobbyFeature, Listener {
    private static final Component ENABLED_MESSAGE = Component.text("Lobby flight enabled.", NamedTextColor.GREEN);
    private static final Component DISABLED_MESSAGE = Component.text("Lobby flight disabled.", NamedTextColor.RED);
    private static final Component NO_ACCESS_MESSAGE = Component.text("You do not have access to lobby flight.", NamedTextColor.RED);
    private static final Component CONSOLE_DISALLOWED_MESSAGE = Component.text("Only players can use this command.", NamedTextColor.RED);

    private final Map<UUID, Boolean> flightToggles = new ConcurrentHashMap<>();
    private JavaPlugin plugin;

    @Override
    public String id() {
        return "rank-flight";
    }

    @Override
    public int priority() {
        return 90;
    }

    @Override
    public void initialize(LobbyFeatureContext context) {
        this.plugin = context.plugin();

        PluginManager pluginManager = plugin.getServer().getPluginManager();
        pluginManager.registerEvents(this, plugin);
        registerCommandHandler();

        plugin.getServer().getOnlinePlayers().forEach(player -> {
            if (hasFlightPerk(player)) {
                flightToggles.putIfAbsent(player.getUniqueId(), Boolean.TRUE);
                applyFlightState(player);
            } else {
                resetFlight(player);
            }
        });

        context.logger().info("Rank flight perk feature initialised.");
    }

    @Override
    public void shutdown(LobbyFeatureContext context) {
        if (plugin != null) {
            plugin.getServer().getOnlinePlayers().forEach(this::resetFlight);
        }
        HandlerList.unregisterAll(this);
        flightToggles.clear();
        plugin = null;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!hasFlightPerk(player)) {
            resetFlight(player);
            return;
        }

        flightToggles.putIfAbsent(player.getUniqueId(), Boolean.TRUE);
        applyFlightState(player);
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (!hasFlightPerk(player)) {
            resetFlight(player);
            return;
        }
        applyFlightState(player);
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (!hasFlightPerk(player)) {
            resetFlight(player);
            return;
        }
        applyFlightState(player);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        resetFlight(player);
        flightToggles.remove(player.getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (!event.isFlying() || hasCreativeFlight(player)) {
            return;
        }
        if (!hasFlightPerk(player)) {
            resetFlight(player);
            return;
        }
        boolean enabled = flightToggles.getOrDefault(player.getUniqueId(), Boolean.TRUE);
        if (!enabled) {
            // Prevent toggling when the perk is disabled.
            event.setCancelled(true);
            player.setFlying(false);
        }
    }

    private void registerCommandHandler() {
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands commands = event.registrar();
            commands.register(
                    Commands.literal("fly")
                            .requires(stack -> {
                                if (!(stack.getSender() instanceof Player player)) {
                                    return false;
                                }
                                return hasFlightPerk(player);
                            })
                            .executes(context -> handleFlyCommand(context.getSource()))
                            .build(),
                    "Toggle lobby flight perk"
            );
        });
    }

    private int handleFlyCommand(CommandSourceStack sourceStack) {
        if (!(sourceStack.getSender() instanceof Player player)) {
            sourceStack.getSender().sendMessage(CONSOLE_DISALLOWED_MESSAGE);
            return Command.SINGLE_SUCCESS;
        }

        if (!hasFlightPerk(player)) {
            player.sendMessage(NO_ACCESS_MESSAGE);
            return Command.SINGLE_SUCCESS;
        }

        UUID playerId = player.getUniqueId();
        boolean currentlyEnabled = flightToggles.getOrDefault(playerId, Boolean.TRUE);
        boolean newState = !currentlyEnabled;
        flightToggles.put(playerId, newState);
        applyFlightState(player);

        player.sendMessage(newState ? ENABLED_MESSAGE : DISABLED_MESSAGE);
        return Command.SINGLE_SUCCESS;
    }

    private void applyFlightState(Player player) {
        UUID playerId = player.getUniqueId();
        boolean enabled = flightToggles.getOrDefault(playerId, Boolean.TRUE);

        if (hasCreativeFlight(player)) {
            player.setAllowFlight(true);
            return;
        }

        if (enabled) {
            if (!player.getAllowFlight()) {
                player.setAllowFlight(true);
            }
        } else {
            if (player.isFlying()) {
                player.setFlying(false);
            }
            if (player.getAllowFlight()) {
                player.setAllowFlight(false);
            }
        }
    }

    private void resetFlight(Player player) {
        if (hasCreativeFlight(player)) {
            return;
        }
        if (player.isFlying()) {
            player.setFlying(false);
        }
        if (player.getAllowFlight()) {
            player.setAllowFlight(false);
        }
    }

    private boolean hasFlightPerk(Player player) {
        Rank rank = Optional.ofNullable(RankUtils.getEffectiveRank(player)).orElse(Rank.DEFAULT);
        return rank != Rank.DEFAULT;
    }

    private boolean hasCreativeFlight(Player player) {
        GameMode mode = player.getGameMode();
        return mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR;
    }
}
