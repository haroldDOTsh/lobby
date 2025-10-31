package sh.harold.fulcrum.lobby.feature;

/**
 * Represents a lobby subsystem participating in the plugin lifecycle.
 */
public interface LobbyFeature {
    /**
     * Human-friendly identifier used for logging.
     */
    String id();

    /**
     * Higher values are loaded earlier.
     */
    int priority();

    /**
     * Invoked during plugin enablement.
     */
    void initialize(LobbyFeatureContext context);

    /**
     * Invoked during plugin shutdown in reverse order.
     */
    void shutdown(LobbyFeatureContext context);
}
