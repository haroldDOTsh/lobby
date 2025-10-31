package sh.harold.fulcrum.lobby.feature;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.logging.Logger;

/**
 * Lightweight context shared across lobby features.
 */
public final class LobbyFeatureContext {
    private final JavaPlugin plugin;
    private final Logger logger;

    public LobbyFeatureContext(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.logger = plugin.getLogger();
    }

    public JavaPlugin plugin() {
        return plugin;
    }

    public Logger logger() {
        return logger;
    }
}
