package sh.harold.fulcrum.lobby.feature;

import sh.harold.fulcrum.api.network.NetworkConfigService;
import sh.harold.fulcrum.api.network.NetworkProfileView;
import sh.harold.fulcrum.fundamentals.slot.discovery.SlotFamilyService;
import sh.harold.fulcrum.fundamentals.slot.SimpleSlotOrchestrator;
import sh.harold.fulcrum.lifecycle.ServiceLocatorImpl;
import sh.harold.fulcrum.lobby.config.LobbyConfiguration;
import sh.harold.fulcrum.lobby.config.LobbyConfigurationRegistry;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

public final class ConfigLoaderFeature implements LobbyFeature {
    private static final String[] FAMILY_PATHS = {
            "modules.lobby.familyId",
            "lobby.familyId"
    };

    private static final String[] MAP_PATHS = {
            "modules.lobby.mapId",
            "lobby.mapId"
    };

    private static final String[] MIN_PLAYERS_PATHS = {
            "modules.lobby.minPlayers",
            "lobby.minPlayers"
    };

    private static final String[] MAX_PLAYERS_PATHS = {
            "modules.lobby.maxPlayers",
            "lobby.maxPlayers"
    };

    private static final String[] PLAYER_FACTOR_PATHS = {
            "modules.lobby.playerEquivalentFactor",
            "lobby.playerEquivalentFactor"
    };

    private static final String[] SPAWN_WORLD_PATHS = {
            "modules.lobby.spawn.world",
            "lobby.spawn.world"
    };

    private static final String[] SPAWN_X_PATHS = {
            "modules.lobby.spawn.x",
            "lobby.spawn.x"
    };

    private static final String[] SPAWN_Y_PATHS = {
            "modules.lobby.spawn.y",
            "lobby.spawn.y"
    };

    private static final String[] SPAWN_Z_PATHS = {
            "modules.lobby.spawn.z",
            "lobby.spawn.z"
    };

    private static final String[] SPAWN_YAW_PATHS = {
            "modules.lobby.spawn.yaw",
            "lobby.spawn.yaw"
    };

    private static final String[] SPAWN_PITCH_PATHS = {
            "modules.lobby.spawn.pitch",
            "lobby.spawn.pitch"
    };

    private static final String[] SPAWN_PROP_PATHS = {
            "modules.lobby.spawnProp",
            "lobby.spawnProp"
    };

    @Override
    public String id() {
        return "config-loader";
    }

    @Override
    public int priority() {
        return 400;
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
            locator.findService(SlotFamilyService.class).ifPresent(service -> service.refreshDescriptors());
            locator.findService(SimpleSlotOrchestrator.class).ifPresent(SimpleSlotOrchestrator::advertiseFamilies);
        }
    }

    @Override
    public void shutdown(LobbyFeatureContext context) {
        context.register(LobbyConfiguration.class, null);
        LobbyConfigurationRegistry.reset();
    }

    private LobbyConfiguration resolveConfiguration(Logger logger) {
        ServiceLocatorImpl locator = ServiceLocatorImpl.getInstance();
        NetworkConfigService networkConfig = locator != null
                ? locator.findService(NetworkConfigService.class).orElse(null)
                : null;

        LobbyConfiguration.Builder builder = LobbyConfiguration.builder();
        if (networkConfig == null) {
            logger.warning("NetworkConfigService unavailable; using default lobby configuration.");
            return builder.build();
        }

        NetworkProfileView profile = networkConfig.getActiveProfile();
        String familyId = firstNonBlank(networkConfig, FAMILY_PATHS)
                .orElse(LobbyConfiguration.DEFAULT_FAMILY_ID);
        String mapId = firstNonBlank(networkConfig, MAP_PATHS)
                .orElse(LobbyConfiguration.DEFAULT_MAP_ID);
        int minPlayers = parseInteger(networkConfig, MIN_PLAYERS_PATHS).orElse(0);
        int maxPlayers = parseInteger(networkConfig, MAX_PLAYERS_PATHS).orElse(120);
        int playerFactor = parseInteger(networkConfig, PLAYER_FACTOR_PATHS).orElse(10);

        builder.familyId(familyId)
                .mapId(mapId)
                .minPlayers(minPlayers)
                .maxPlayers(maxPlayers)
                .playerEquivalentFactor(playerFactor);

        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("mapId", mapId);
        firstNonBlank(networkConfig, SPAWN_WORLD_PATHS).ifPresent(value -> metadata.put("spawnWorld", value));
        firstNonBlank(networkConfig, SPAWN_PROP_PATHS).ifPresent(value -> metadata.put("spawnProp", value));
        putIfPresent(metadata, "spawnX", parseDecimal(networkConfig, SPAWN_X_PATHS));
        putIfPresent(metadata, "spawnY", parseDecimal(networkConfig, SPAWN_Y_PATHS));
        putIfPresent(metadata, "spawnZ", parseDecimal(networkConfig, SPAWN_Z_PATHS));
        putIfPresent(metadata, "spawnYaw", parseDecimal(networkConfig, SPAWN_YAW_PATHS));
        putIfPresent(metadata, "spawnPitch", parseDecimal(networkConfig, SPAWN_PITCH_PATHS));

        Map<String, Object> customMetadata = profile != null
                ? profile.getMap("modules.lobby.metadata")
                : Map.of();
        if (customMetadata.isEmpty() && profile != null) {
            customMetadata = profile.getMap("lobby.metadata");
        }
        if (!customMetadata.isEmpty()) {
            customMetadata.forEach((key, value) -> {
                if (key != null && value != null) {
                    metadata.putIfAbsent(key, value.toString());
                }
            });
        }

        builder.addAllMetadata(metadata);
        return builder.build();
    }

    private Optional<String> firstNonBlank(NetworkConfigService service, String[] paths) {
        if (paths == null) {
            return Optional.empty();
        }
        for (String path : paths) {
            Optional<String> value = service.getString(path)
                    .map(String::trim)
                    .filter(str -> !str.isEmpty());
            if (value.isPresent()) {
                return value;
            }
        }
        return Optional.empty();
    }

    private Optional<Integer> parseInteger(NetworkConfigService service, String[] paths) {
        return firstNonBlank(service, paths).map(value -> {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException ex) {
                return null;
            }
        }).filter(value -> value != null);
    }

    private Optional<String> parseDecimal(NetworkConfigService service, String[] paths) {
        return firstNonBlank(service, paths).map(value -> {
            try {
                double parsed = Double.parseDouble(value);
                return trimTrailingZeros(parsed);
            } catch (NumberFormatException ex) {
                return null;
            }
        }).filter(value -> value != null);
    }

    private void putIfPresent(Map<String, String> metadata, String key, Optional<String> value) {
        value.ifPresent(v -> metadata.put(key, v));
    }

    private String trimTrailingZeros(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return Double.toString(value);
        }
        if (value == Math.rint(value)) {
            return String.format(Locale.ROOT, "%.0f", value);
        }
        return Double.toString(value);
    }
}
