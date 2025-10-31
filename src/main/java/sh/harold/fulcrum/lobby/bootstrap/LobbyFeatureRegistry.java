package sh.harold.fulcrum.lobby.bootstrap;

import sh.harold.fulcrum.lobby.feature.LobbyFeatureManager;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Simple holder bridging the bootstrap phase and runtime plugin instance.
 */
public final class LobbyFeatureRegistry {
    private static final AtomicReference<LobbyFeatureManager> MANAGER = new AtomicReference<>();

    private LobbyFeatureRegistry() {
    }

    public static LobbyFeatureManager prepare() {
        LobbyFeatureManager manager = new LobbyFeatureManager();
        if (!MANAGER.compareAndSet(null, manager)) {
            throw new IllegalStateException("LobbyFeatureManager already prepared");
        }
        return manager;
    }

    public static LobbyFeatureManager claim() {
        LobbyFeatureManager manager = MANAGER.get();
        if (manager == null) {
            throw new IllegalStateException("LobbyFeatureManager not prepared");
        }
        return manager;
    }

    public static void clear() {
        MANAGER.set(null);
    }
}
