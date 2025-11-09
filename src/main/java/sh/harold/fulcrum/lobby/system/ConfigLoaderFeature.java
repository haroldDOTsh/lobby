package sh.harold.fulcrum.lobby.system;

import sh.harold.fulcrum.api.environment.directory.EnvironmentDescriptorView;
import sh.harold.fulcrum.api.environment.directory.EnvironmentDirectoryService;
import sh.harold.fulcrum.api.module.FulcrumEnvironment;
import sh.harold.fulcrum.fundamentals.slot.SimpleSlotOrchestrator;
import sh.harold.fulcrum.fundamentals.slot.discovery.SlotFamilyService;
import sh.harold.fulcrum.lifecycle.ServiceLocatorImpl;
import sh.harold.fulcrum.lobby.config.LobbyConfiguration;
import sh.harold.fulcrum.lobby.config.LobbyConfigurationRegistry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

public final class ConfigLoaderFeature implements LobbyFeature {
    private static final String FAMILY_ID_KEY = "lobby.familyId";
    private static final String MAP_ID_KEY = "lobby.mapId";
    private static final String METADATA_KEY = "lobby.metadata";
    private static final String JOIN_DEFAULT_KEY = "lobby.joinMessages.default";
    private static final String JOIN_DONATOR_KEY = "lobby.joinMessages.donator";
    private static final String JOIN_DONATOR_RANKS_KEY = "lobby.joinMessages.donatorRanks";
    private static final String JOIN_TOP_DONATOR_KEY = "lobby.joinMessages.topDonator";

    @Override
    public String id() {
        return "config-loader";
    }

    @Override
    public int priority() {
        return 10;
    }

    @Override
    public void initialize(LobbyFeatureContext context) {
        Logger logger = context.logger();
        LobbyConfiguration configuration = resolveConfiguration(logger);
        LobbyConfigurationRegistry.update(configuration);
        context.register(LobbyConfiguration.class, configuration);
        logger.info(() -> "Lobby configuration resolved (family=" + configuration.familyId()
                + ", map=" + configuration.mapId() + ")");

        ServiceLocatorImpl locator = ServiceLocatorImpl.getInstance();
        if (locator != null) {
            locator.findService(SlotFamilyService.class).ifPresent(SlotFamilyService::refreshDescriptors);
            locator.findService(SimpleSlotOrchestrator.class).ifPresent(SimpleSlotOrchestrator::advertiseFamilies);
        }
    }

    @Override
    public void shutdown(LobbyFeatureContext context) {
        context.register(LobbyConfiguration.class, null);
        LobbyConfigurationRegistry.reset();
    }

    private LobbyConfiguration resolveConfiguration(Logger logger) {
        LobbyConfiguration.Builder builder = LobbyConfiguration.builder();
        EnvironmentDescriptorView descriptor = resolveDescriptor(logger);

        if (descriptor == null) {
            if (logger != null) {
                logger.warning("Environment descriptor unavailable; using default lobby configuration.");
            }
            return builder.build();
        }

        Map<String, Object> settings = descriptor.settings();
        if (settings == null) {
            settings = Map.of();
        }

        String familyId = EnvironmentSettings.getString(settings, FAMILY_ID_KEY)
                .orElse(LobbyConfiguration.DEFAULT_FAMILY_ID);
        String mapId = EnvironmentSettings.getString(settings, MAP_ID_KEY)
                .orElse(LobbyConfiguration.DEFAULT_MAP_ID);
        int minPlayers = normalizeMinPlayers(descriptor.minPlayers());
        int maxPlayers = normalizeMaxPlayers(minPlayers, descriptor.maxPlayers());
        int playerFactor = normalizePlayerFactor(descriptor.playerFactor());

        builder.familyId(familyId)
                .mapId(mapId)
                .minPlayers(minPlayers)
                .maxPlayers(maxPlayers)
                .playerEquivalentFactor(playerFactor)
                .addAllMetadata(resolveMetadata(mapId, settings));

        EnvironmentSettings.getString(settings, JOIN_DEFAULT_KEY).ifPresent(builder::joinDefaultMessage);
        EnvironmentSettings.getString(settings, JOIN_DONATOR_KEY).ifPresent(builder::joinDonatorMessage);
        List<String> donatorRanks = EnvironmentSettings.getStringList(settings, JOIN_DONATOR_RANKS_KEY);
        if (!donatorRanks.isEmpty()) {
            builder.joinDonatorRanks(donatorRanks);
        }
        EnvironmentSettings.getString(settings, JOIN_TOP_DONATOR_KEY).ifPresent(builder::joinTopDonatorMessage);
        return builder.build();
    }

    private EnvironmentDescriptorView resolveDescriptor(Logger logger) {
        ServiceLocatorImpl locator = ServiceLocatorImpl.getInstance();
        if (locator == null) {
            if (logger != null) {
                logger.warning("Service locator unavailable; cannot resolve environment descriptor.");
            }
            return null;
        }

        EnvironmentDirectoryService directoryService = locator.findService(EnvironmentDirectoryService.class)
                .orElse(null);
        if (directoryService == null) {
            if (logger != null) {
                logger.warning("EnvironmentDirectoryService unavailable; cannot load lobby settings.");
            }
            return null;
        }

        String environmentId = FulcrumEnvironment.getCurrent();
        if (environmentId == null || environmentId.isBlank()) {
            if (logger != null) {
                logger.warning("Active Fulcrum environment unknown; cannot resolve lobby descriptor.");
            }
            return null;
        }

        return directoryService.getEnvironment(environmentId).orElseGet(() -> {
            if (logger != null) {
                logger.warning("Environment '" + environmentId + "' missing from directory; using defaults.");
            }
            return null;
        });
    }

    private int normalizeMinPlayers(int rawMin) {
        return Math.max(0, rawMin);
    }

    private int normalizeMaxPlayers(int minPlayers, int rawMax) {
        int normalized = rawMax <= 0 ? minPlayers : rawMax;
        if (normalized < minPlayers) {
            normalized = minPlayers;
        }
        return Math.max(1, normalized);
    }

    private int normalizePlayerFactor(double rawFactor) {
        if (!Double.isFinite(rawFactor) || rawFactor <= 0.0D) {
            return LobbyConfiguration.DEFAULT_PLAYER_EQUIVALENT_FACTOR;
        }
        int rounded = (int) Math.round(rawFactor);
        return Math.max(LobbyConfiguration.DEFAULT_PLAYER_EQUIVALENT_FACTOR, rounded);
    }

    private Map<String, String> resolveMetadata(String mapId, Map<String, Object> settings) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("mapId", mapId);

        Map<String, Object> configured = EnvironmentSettings.getObjectMap(settings, METADATA_KEY);
        configured.forEach((key, value) -> {
            if (key == null || value == null) {
                return;
            }
            String normalized = Objects.toString(value, "").trim();
            if (!normalized.isEmpty()) {
                metadata.putIfAbsent(key, normalized);
            }
        });
        return metadata;
    }
}
