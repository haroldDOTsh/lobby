package sh.harold.fulcrum.lobby.feature;

import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.fulcrum.lobby.system.LobbyFeature;
import sh.harold.fulcrum.lobby.system.LobbyFeatureContext;

/**
 * Locks every lobby world at daytime by disabling the daylight cycle.
 */
public final class LobbyDaylightFeature implements LobbyFeature, Listener {
    private static final long DAYTIME_TICKS = 1000L;

    private JavaPlugin plugin;

    @Override
    public String id() {
        return "lobby-daylight";
    }

    @Override
    public int priority() {
        return 83;
    }

    @Override
    public void initialize(LobbyFeatureContext context) {
        this.plugin = context.plugin();

        PluginManager pluginManager = plugin.getServer().getPluginManager();
        pluginManager.registerEvents(this, plugin);

        plugin.getServer().getWorlds().forEach(this::lockDaytime);
        context.logger().info("Lobby daylight feature initialised.");
    }

    @Override
    public void shutdown(LobbyFeatureContext context) {
        if (plugin != null) {
            plugin.getServer().getWorlds().forEach(world -> world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, true));
        }
        HandlerList.unregisterAll(this);
        plugin = null;
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        lockDaytime(event.getWorld());
    }

    private void lockDaytime(World world) {
        if (world == null) {
            return;
        }
        world.setTime(DAYTIME_TICKS);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
    }
}
