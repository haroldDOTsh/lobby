package sh.harold.fulcrum.lobby.bootstrap;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.bootstrap.PluginProviderContext;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.fulcrum.api.module.BootstrapContextHolder;
import sh.harold.fulcrum.api.module.FulcrumEnvironment;
import sh.harold.fulcrum.lobby.LobbyPlugin;
import sh.harold.fulcrum.lobby.feature.ConfigLoaderFeature;
import sh.harold.fulcrum.lobby.feature.LobbyFeatureManager;
import sh.harold.fulcrum.lobby.feature.LobbySlotProvisionFeature;
import sh.harold.fulcrum.lobby.feature.QueueBridgeFeature;
import sh.harold.fulcrum.lobby.feature.SelectorUiFeature;

public final class LobbyBootstrapper implements PluginBootstrap {
    private boolean shouldLoad;

    @Override
    public void bootstrap(BootstrapContext context) {
        try {
            BootstrapContextHolder.setContext(LobbyPlugin.MODULE_ID);

            if (!FulcrumEnvironment.isThisModuleEnabled()) {
                context.getLogger().info("Lobby module disabled via Fulcrum configuration; skipping load");
                shouldLoad = false;
                return;
            }

            LobbyFeatureManager manager = LobbyFeatureRegistry.prepare();
            manager.register(new ConfigLoaderFeature());
            manager.register(new LobbySlotProvisionFeature());
            manager.register(new SelectorUiFeature());
            manager.register(new QueueBridgeFeature());
            context.getLogger().info("Registered lobby features: " + manager.registeredFeatures().size());
            shouldLoad = true;
        } finally {
            BootstrapContextHolder.clearContext();
        }
    }

    @Override
    public JavaPlugin createPlugin(PluginProviderContext context) {
        if (!shouldLoad) {
            throw new IllegalStateException("Aborting load: Lobby module disabled via Fulcrum configuration.");
        }
        return PluginBootstrap.super.createPlugin(context);
    }
}
