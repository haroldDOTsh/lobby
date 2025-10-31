package sh.harold.fulcrum.lobby;

import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.fulcrum.api.module.FulcrumModule;
import sh.harold.fulcrum.api.module.ModuleInfo;
import sh.harold.fulcrum.lobby.bootstrap.LobbyFeatureRegistry;
import sh.harold.fulcrum.lobby.feature.LobbyFeatureContext;
import sh.harold.fulcrum.lobby.feature.LobbyFeatureManager;

@ModuleInfo(name = LobbyPlugin.MODULE_ID, description = LobbyPlugin.MODULE_DESCRIPTION)
public final class LobbyPlugin extends JavaPlugin implements FulcrumModule {
    public static final String MODULE_ID = "lobby";
    public static final String MODULE_DESCRIPTION = "Fulcrum lobby services";

    private LobbyFeatureManager featureManager;
    private LobbyFeatureContext featureContext;
    private boolean enabled;

    @Override
    public void onEnable() {
        featureManager = LobbyFeatureRegistry.claim();
        featureContext = new LobbyFeatureContext(this);
        featureManager.initializeAll(featureContext);
        enabled = true;

        getLogger().info("Lobby plugin enabled with " + featureManager.registeredFeatures().size() + " features");
    }

    @Override
    public void onDisable() {
        if (featureManager != null && featureContext != null) {
            featureManager.shutdownAll(featureContext);
        }
        LobbyFeatureRegistry.clear();
        featureManager = null;
        featureContext = null;
        enabled = false;

        getLogger().info("Lobby plugin disabled");
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
