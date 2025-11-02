package sh.harold.fulcrum.lobby.feature;

/**
 * Placeholder queue bridge integration.
 */
public final class QueueBridgeFeature implements LobbyFeature {
    @Override
    public String id() {
        return "queue-bridge";
    }

    @Override
    public int priority() {
        return 50;
    }

    @Override
    public void initialize(LobbyFeatureContext context) {
        context.logger().info("QueueBridgeFeature initialization stub");
    }

    @Override
    public void shutdown(LobbyFeatureContext context) {
        context.logger().info("QueueBridgeFeature shutdown stub");
    }
}
