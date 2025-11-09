package sh.harold.fulcrum.lobby;

import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.fulcrum.api.module.FulcrumModule;
import sh.harold.fulcrum.api.module.ModuleInfo;
import sh.harold.fulcrum.api.slot.SlotFamilyDescriptor;
import sh.harold.fulcrum.api.slot.SlotFamilyProvider;
import sh.harold.fulcrum.lobby.system.LobbyFeatureContext;
import sh.harold.fulcrum.lobby.config.LobbyConfiguration;
import sh.harold.fulcrum.lobby.config.LobbyConfigurationRegistry;
import sh.harold.fulcrum.lobby.system.ConfigLoaderFeature;
import sh.harold.fulcrum.lobby.feature.LobbyActionFlagFeature;
import sh.harold.fulcrum.lobby.feature.LobbyJoinMessageFeature;
import sh.harold.fulcrum.lobby.system.LobbyFeatureManager;
import sh.harold.fulcrum.lobby.feature.LobbySlotProvisionFeature;
import sh.harold.fulcrum.lobby.feature.LobbyScoreboardFeature;
import sh.harold.fulcrum.lobby.feature.LobbyNametagFeature;
import sh.harold.fulcrum.lobby.feature.StaffPunchFeature;
import sh.harold.fulcrum.lobby.feature.RankFlightFeature;
import sh.harold.fulcrum.lobby.feature.LobbySpeedFeature;
import sh.harold.fulcrum.lobby.feature.LobbyHotbarFeature;
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
        featureManager = new LobbyFeatureManager();
        registerFeatures(featureManager);
        featureContext = new LobbyFeatureContext(this);
        featureManager.initializeAll(featureContext);

        getLogger().info("Lobby plugin enabled with " + featureManager.registeredFeatures().size() + " features");
    }

    @Override
    public void onDisable() {
        if (featureManager != null && featureContext != null) {
            featureManager.shutdownAll(featureContext);
        }
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

    private void registerFeatures(LobbyFeatureManager manager) {
        manager.register(new ConfigLoaderFeature());
        manager.register(new LobbyActionFlagFeature());
        manager.register(new LobbyJoinMessageFeature());
        manager.register(new LobbySlotProvisionFeature());
        manager.register(new LobbyHotbarFeature());
        manager.register(new LobbyScoreboardFeature());
        manager.register(new LobbyNametagFeature());
        manager.register(new LobbySpeedFeature());
        manager.register(new RankFlightFeature());
        manager.register(new StaffPunchFeature());
    }
}
