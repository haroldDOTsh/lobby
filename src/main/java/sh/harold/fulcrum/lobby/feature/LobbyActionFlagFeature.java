package sh.harold.fulcrum.lobby.feature;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.PluginManager;
import sh.harold.fulcrum.fundamentals.actionflag.ActionFlagContexts;
import sh.harold.fulcrum.fundamentals.actionflag.ActionFlagService;
import sh.harold.fulcrum.lifecycle.ServiceLocatorImpl;
import sh.harold.fulcrum.lobby.system.LobbyFeature;
import sh.harold.fulcrum.lobby.system.LobbyFeatureContext;

/**
 * Applies lobby action flag contexts to all players while they are on the lobby node.
 */
public final class LobbyActionFlagFeature implements LobbyFeature {
    private static final String CONTEXT_ID = ActionFlagContexts.LOBBY_DEFAULT;

    private ActionFlagService actionFlagService;
    private Listener listener;

    @Override
    public String id() {
        return "action-flags";
    }

    @Override
    public int priority() {
        return 20;
    }

    @Override
    public void initialize(LobbyFeatureContext context) {
        ServiceLocatorImpl locator = ServiceLocatorImpl.getInstance();
        actionFlagService = locator != null
                ? locator.findService(ActionFlagService.class).orElse(null)
                : null;

        if (actionFlagService == null) {
            context.logger().warning("ActionFlagService unavailable; lobby action flags will not be applied.");
            return;
        }

        // Ensure existing players receive the lobby context immediately.
        for (Player player : Bukkit.getOnlinePlayers()) {
            applyLobbyContext(player);
        }

        listener = new LobbyActionFlagListener();
        PluginManager pluginManager = context.plugin().getServer().getPluginManager();
        pluginManager.registerEvents(listener, context.plugin());
        context.logger().info("Lobby action flag feature initialized.");
    }

    @Override
    public void shutdown(LobbyFeatureContext context) {
        if (listener != null) {
            HandlerList.unregisterAll(listener);
            listener = null;
        }
        actionFlagService = null;
    }

    private void applyLobbyContext(Player player) {
        if (player == null || actionFlagService == null) {
            return;
        }
        actionFlagService.clear(player.getUniqueId());
        actionFlagService.applyContext(player.getUniqueId(), CONTEXT_ID);
    }

    private void clearFlags(Player player) {
        if (player == null || actionFlagService == null) {
            return;
        }
        actionFlagService.clear(player.getUniqueId());
    }

    private final class LobbyActionFlagListener implements Listener {
        @EventHandler
        public void onPlayerJoin(PlayerJoinEvent event) {
            applyLobbyContext(event.getPlayer());
        }

        @EventHandler
        public void onPlayerRespawn(PlayerRespawnEvent event) {
            applyLobbyContext(event.getPlayer());
        }

        @EventHandler
        public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
            applyLobbyContext(event.getPlayer());
        }

        @EventHandler
        public void onPlayerQuit(PlayerQuitEvent event) {
            clearFlags(event.getPlayer());
        }
    }
}
