package sh.harold.fulcrum.lobby.feature;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.fulcrum.lobby.system.LobbyFeature;
import sh.harold.fulcrum.lobby.system.LobbyFeatureContext;

/**
 * Resets player health to 20 when they enter the lobby.
 */
public final class LobbyHealthFeature implements LobbyFeature, Listener {
    private static final double TARGET_HEALTH = 20.0D;

    private JavaPlugin plugin;

    @Override
    public String id() {
        return "lobby-health";
    }

    @Override
    public int priority() {
        return 81;
    }

    @Override
    public void initialize(LobbyFeatureContext context) {
        this.plugin = context.plugin();

        PluginManager pluginManager = plugin.getServer().getPluginManager();
        pluginManager.registerEvents(this, plugin);

        plugin.getServer().getOnlinePlayers().forEach(this::normalizeHealth);
        context.logger().info("Lobby health feature initialised.");
    }

    @Override
    public void shutdown(LobbyFeatureContext context) {
        HandlerList.unregisterAll(this);
        plugin = null;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        normalizeHealth(event.getPlayer());
    }

    private void normalizeHealth(Player player) {
        if (player == null) {
            return;
        }

        AttributeInstance maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        double ceiling = maxHealth != null ? maxHealth.getValue() : TARGET_HEALTH;
        if (ceiling <= 0.0D) {
            ceiling = TARGET_HEALTH;
        }

        double resolvedHealth = Math.min(TARGET_HEALTH, ceiling);
        player.setHealth(resolvedHealth);
    }
}
