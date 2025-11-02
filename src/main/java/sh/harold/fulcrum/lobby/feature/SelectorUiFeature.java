package sh.harold.fulcrum.lobby.feature;

/**
 * Placeholder selector UI orchestration.
 */
public final class SelectorUiFeature implements LobbyFeature {
    @Override
    public String id() {
        return "selector-ui";
    }

    @Override
    public int priority() {
        return 40;
    }

    @Override
    public void initialize(LobbyFeatureContext context) {
        context.logger().info("SelectorUiFeature initialization stub");
    }

    @Override
    public void shutdown(LobbyFeatureContext context) {
        context.logger().info("SelectorUiFeature shutdown stub");
    }
}
