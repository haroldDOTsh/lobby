package sh.harold.fulcrum.lobby;

import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.fulcrum.api.module.FulcrumModule;
import sh.harold.fulcrum.api.module.ModuleInfo;
import sh.harold.fulcrum.api.slot.SlotFamilyDescriptor;
import sh.harold.fulcrum.api.slot.SlotFamilyProvider;
import sh.harold.fulcrum.lobby.bootstrap.LobbyFeatureRegistry;
import sh.harold.fulcrum.lobby.feature.LobbyFeatureContext;
import sh.harold.fulcrum.lobby.feature.LobbyFeatureManager;
import sh.harold.fulcrum.lobby.config.LobbyConfiguration;
import sh.harold.fulcrum.lobby.config.LobbyConfigurationRegistry;

import java.util.Collection;
import java.util.List;

@ModuleInfo(name = LobbyPlugin.MODULE_ID, description = LobbyPlugin.MODULE_DESCRIPTION)
public final class LobbyPlugin extends JavaPlugin implements FulcrumModule, SlotFamilyProvider {
    public static final String MODULE_ID = "lobby";
    public static final String MODULE_DESCRIPTION = "Fulcrum lobby services";

    private LobbyFeatureManager featureManager;
    private LobbyFeatureContext featureContext;

    @Override
    public void onEnable() {
        featureManager = LobbyFeatureRegistry.claim();
        featureContext = new LobbyFeatureContext(this);
        featureManager.initializeAll(featureContext);

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

        getLogger().info("Lobby plugin disabled");
    }

    @Override
    public Collection<SlotFamilyDescriptor> getSlotFamilies() {
        LobbyConfiguration configuration = LobbyConfigurationRegistry.current();
        SlotFamilyDescriptor descriptor = configuration.toDescriptor();
        return List.of(descriptor);
    }
}
