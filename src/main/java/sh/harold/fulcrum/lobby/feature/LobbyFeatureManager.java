package sh.harold.fulcrum.lobby.feature;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

/**
 * Orders and runs lobby features.
 */
public final class LobbyFeatureManager {
    private final List<LobbyFeature> registered = new CopyOnWriteArrayList<>();
    private final List<LobbyFeature> started = new ArrayList<>();

    public void register(LobbyFeature feature) {
        registered.add(Objects.requireNonNull(feature, "feature"));
        registered.sort(Comparator.comparingInt(LobbyFeature::priority).reversed());
    }

    public void initializeAll(LobbyFeatureContext context) {
        started.clear();
        for (LobbyFeature feature : registered) {
            logLifecycle(context.logger(), feature, "initialize");
            feature.initialize(context);
            started.add(feature);
        }
    }

    public void shutdownAll(LobbyFeatureContext context) {
        for (int i = started.size() - 1; i >= 0; i--) {
            LobbyFeature feature = started.get(i);
            logLifecycle(context.logger(), feature, "shutdown");
            feature.shutdown(context);
        }
        started.clear();
    }

    public List<LobbyFeature> registeredFeatures() {
        return List.copyOf(registered);
    }

    private void logLifecycle(Logger logger, LobbyFeature feature, String stage) {
        logger.fine(() -> "LobbyFeature[" + feature.id() + "] -> " + stage);
    }
}
