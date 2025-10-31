package sh.harold.fulcrum.lobby.feature;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Lightweight context shared across lobby features.
 */
public final class LobbyFeatureContext {
    private final JavaPlugin plugin;
    private final Logger logger;
    private final Map<Class<?>, Object> registry = new ConcurrentHashMap<>();

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

    public <T> void register(Class<T> type, T instance) {
        Objects.requireNonNull(type, "type");
        if (instance == null) {
            registry.remove(type);
        } else {
            registry.put(type, instance);
        }
    }

    public <T> Optional<T> get(Class<T> type) {
        Objects.requireNonNull(type, "type");
        Object value = registry.get(type);
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(type.cast(value));
    }

    public <T> T getOrRegister(Class<T> type, T fallback) {
        Objects.requireNonNull(type, "type");
        if (fallback == null) {
            return type.cast(registry.get(type));
        }
        return type.cast(registry.computeIfAbsent(type, key -> fallback));
    }
}
