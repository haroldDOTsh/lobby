package sh.harold.fulcrum.lobby.config;

import java.util.concurrent.atomic.AtomicReference;

public final class LobbyConfigurationRegistry {
    private static final AtomicReference<LobbyConfiguration> CURRENT = new AtomicReference<>(LobbyConfiguration.defaults());

    private LobbyConfigurationRegistry() {
    }

    public static LobbyConfiguration current() {
        return CURRENT.get();
    }

    public static void update(LobbyConfiguration configuration) {
        if (configuration == null) {
            return;
        }
        CURRENT.set(configuration);
    }

    public static void reset() {
        CURRENT.set(LobbyConfiguration.defaults());
    }
}
