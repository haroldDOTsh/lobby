package sh.harold.fulcrum.lobby.feature;

import org.bukkit.event.HandlerList;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.fulcrum.common.settings.PlayerSettingsService;
import sh.harold.fulcrum.lifecycle.ServiceLocatorImpl;
import sh.harold.fulcrum.lobby.cosmetics.loadout.LoadoutService;
import sh.harold.fulcrum.lobby.cosmetics.loadout.PlayerSettingsLoadoutService;
import sh.harold.fulcrum.lobby.cosmetics.registry.CosmeticRegistry;
import sh.harold.fulcrum.lobby.cosmetics.runtime.CosmeticRuntime;
import sh.harold.fulcrum.lobby.system.LobbyFeature;
import sh.harold.fulcrum.lobby.system.LobbyFeatureContext;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Boots the cosmetics runtime and exposes the loadout service for other features (menus, unlock flows).
 */
public final class LobbyCosmeticsFeature implements LobbyFeature {
    private static final String GAME_SCOPE = "lobby";

    private CosmeticRuntime runtime;
    private LoadoutService loadoutService;
    private CosmeticRegistry registry;
    private Logger logger;
    private JavaPlugin plugin;

    @Override
    public String id() {
        return "cosmetics";
    }

    @Override
    public int priority() {
        return 60;
    }

    @Override
    public void initialize(LobbyFeatureContext context) {
        this.plugin = context.plugin();
        this.logger = context.logger();

        PlayerSettingsService.GameSettingsScope scope = resolveSettingsScope();
        if (scope == null) {
            if (logger != null) {
                logger.warning("PlayerSettingsService unavailable; cosmetics runtime disabled.");
            }
            return;
        }

        this.loadoutService = new PlayerSettingsLoadoutService(scope);
        this.registry = new CosmeticRegistry(plugin, logger);
        this.runtime = new CosmeticRuntime(plugin, registry, loadoutService, logger);

        PluginManager pluginManager = plugin.getServer().getPluginManager();
        pluginManager.registerEvents(runtime, plugin);
        runtime.start();

        context.register(LoadoutService.class, loadoutService);
        context.register(CosmeticRegistry.class, registry);
    }

    @Override
    public void shutdown(LobbyFeatureContext context) {
        context.register(LoadoutService.class, null);
        context.register(CosmeticRegistry.class, null);
        if (runtime != null) {
            HandlerList.unregisterAll(runtime);
            runtime.close();
        }
        runtime = null;
        registry = null;
        loadoutService = null;
        plugin = null;
        logger = null;
    }

    private PlayerSettingsService.GameSettingsScope resolveSettingsScope() {
        ServiceLocatorImpl locator = ServiceLocatorImpl.getInstance();
        if (locator == null) {
            if (logger != null) {
                logger.warning("Service locator unavailable; cannot boot cosmetics.");
            }
            return null;
        }
        PlayerSettingsService settingsService = locator.findService(PlayerSettingsService.class).orElse(null);
        if (settingsService == null) {
            if (logger != null) {
                logger.warning("PlayerSettingsService missing; cosmetics disabled.");
            }
            return null;
        }
        try {
            return settingsService.forGame(GAME_SCOPE);
        } catch (Exception exception) {
            if (logger != null) {
                logger.log(Level.SEVERE, "Unable to resolve lobby settings scope", exception);
            }
            return null;
        }
    }
}
