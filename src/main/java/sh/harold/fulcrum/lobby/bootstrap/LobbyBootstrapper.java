package sh.harold.fulcrum.lobby.bootstrap;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.bootstrap.PluginProviderContext;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.fulcrum.api.module.BootstrapContextHolder;
import sh.harold.fulcrum.api.module.FulcrumEnvironment;
import sh.harold.fulcrum.lobby.LobbyPlugin;

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
