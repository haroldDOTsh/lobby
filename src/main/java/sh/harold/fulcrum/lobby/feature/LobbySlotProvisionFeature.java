package sh.harold.fulcrum.lobby.feature;

import sh.harold.fulcrum.lobby.system.LobbyFeature;
import sh.harold.fulcrum.lobby.system.LobbyFeatureContext;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.generator.ChunkGenerator;
import sh.harold.fulcrum.api.messagebus.messages.SlotLifecycleStatus;
import sh.harold.fulcrum.api.world.generator.VoidChunkGenerator;
import sh.harold.fulcrum.fundamentals.slot.SimpleSlotOrchestrator;
import sh.harold.fulcrum.fundamentals.world.WorldManager;
import sh.harold.fulcrum.fundamentals.world.WorldManager.WorldPasteResult;
import sh.harold.fulcrum.fundamentals.world.WorldService;
import sh.harold.fulcrum.fundamentals.world.model.LoadedWorld;
import sh.harold.fulcrum.lifecycle.ServiceLocatorImpl;
import sh.harold.fulcrum.lobby.config.LobbyConfiguration;
import sh.harold.fulcrum.lobby.config.LobbyConfigurationRegistry;

import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public final class LobbySlotProvisionFeature implements LobbyFeature {
    private static final double DEFAULT_SPAWN_X = 0.5;
    private static final double DEFAULT_SPAWN_Y = 64.0;
    private static final double DEFAULT_SPAWN_Z = 0.5;

    private final Set<String> provisioningSlots = ConcurrentHashMap.newKeySet();
    private final Map<String, LobbyInstance> activeSlots = new ConcurrentHashMap<>();

    private LobbyConfiguration configuration;
    private SimpleSlotOrchestrator orchestrator;
    private WorldService worldService;
    private WorldManager worldManager;
    private Logger logger;

    @Override
    public String id() {
        return "slot-provision";
    }

    @Override
    public int priority() {
        return 30;
    }

    @Override
    public void initialize(LobbyFeatureContext context) {
        this.logger = context.logger();
        this.configuration = context.get(LobbyConfiguration.class)
                .orElseGet(LobbyConfigurationRegistry::current);

        ServiceLocatorImpl locator = ServiceLocatorImpl.getInstance();
        if (locator != null) {
            orchestrator = locator.findService(SimpleSlotOrchestrator.class).orElse(null);
            worldService = locator.findService(WorldService.class).orElse(null);
            worldManager = locator.findService(WorldManager.class).orElse(null);
        }

        if (orchestrator == null) {
            logger.warning("SimpleSlotOrchestrator unavailable; lobby slots will not be provisioned.");
            return;
        }
        if (worldService == null || worldManager == null) {
            logger.warning("World services unavailable; lobby slots cannot be hydrated.");
            return;
        }

        orchestrator.addProvisionListener(slot -> handleProvision(context, slot));
        logger.info("Lobby slot provisioning feature ready (family=" + configuration.familyId()
                + ", variant=" + configuration.familyVariant() + ").");
    }

    @Override
    public void shutdown(LobbyFeatureContext context) {
        if (activeSlots.isEmpty()) {
            return;
        }
        logger.info("Tearing down " + activeSlots.size() + " lobby slot(s).");
        activeSlots.values().forEach(instance ->
                context.plugin().getServer().getScheduler().runTask(context.plugin(), () ->
                        teardownSlot(instance, SlotLifecycleStatus.COOLDOWN, Map.of("reason", "shutdown"))));
        activeSlots.clear();
    }

    private void handleProvision(LobbyFeatureContext context, SimpleSlotOrchestrator.ProvisionedSlot slot) {
        if (!isTargetSlot(slot)) {
            return;
        }
        if (!provisioningSlots.add(slot.slotId())) {
            return;
        }

        context.plugin().getServer().getScheduler().runTask(context.plugin(), () -> {
            if (!activeSlots.isEmpty()) {
                logger.warning("Existing lobby slot detected; recycling before provisioning new slot " + slot.slotId());
                for (LobbyInstance existing : List.copyOf(activeSlots.values())) {
                    teardownSlot(existing, SlotLifecycleStatus.COOLDOWN, Map.of("reason", "reprovision"));
                }
            }
            try {
                provisionSlot(context, slot);
            } finally {
                provisioningSlots.remove(slot.slotId());
            }
        });
    }

    private void provisionSlot(LobbyFeatureContext context, SimpleSlotOrchestrator.ProvisionedSlot slot) {
        if (activeSlots.containsKey(slot.slotId())) {
            logger.warning("Ignoring duplicate provision request for slot " + slot.slotId());
            return;
        }

        String requestedMapId = resolveMapId(slot.metadata());
        LoadedWorld template = locateTemplate(requestedMapId).orElse(null);
        if (template == null) {
            failProvision(slot.slotId(), "Missing cached world for mapId=" + requestedMapId);
            return;
        }

        World world = prepareWorld(slot.slotId());
        if (world == null) {
            failProvision(slot.slotId(), "Unable to create or load Bukkit world for slot");
            return;
        }

        Location spawn = resolveSpawn(world, slot.metadata());
        world.setSpawnLocation(spawn.getBlockX(), spawn.getBlockY(), spawn.getBlockZ());
        world.getChunkAt(spawn).load();

        Map<String, String> metadata = new LinkedHashMap<>(slot.metadata());
        metadata.putIfAbsent("mapId", template.getMapId());
        metadata.put("targetWorld", world.getName());
        metadata.put("spawnX", Double.toString(spawn.getX()));
        metadata.put("spawnY", Double.toString(spawn.getY()));
        metadata.put("spawnZ", Double.toString(spawn.getZ()));
        metadata.put("spawnYaw", Float.toString(spawn.getYaw()));
        metadata.put("spawnPitch", Float.toString(spawn.getPitch()));

        LobbyInstance instance = new LobbyInstance(
                slot.slotId(),
                world,
                spawn,
                template,
                metadata
        );
        activeSlots.put(slot.slotId(), instance);

        CompletableFuture<WorldPasteResult> pasteTask = worldManager
                .pasteWorld(template.getId(), world, spawn.toBlockLocation())
                .whenComplete((result, throwable) ->
                        context.plugin().getServer().getScheduler().runTask(context.plugin(), () ->
                                handlePasteCompletion(instance, result, throwable)));
        instance.setPasteTask(pasteTask);
    }

    private void handlePasteCompletion(LobbyInstance instance,
                                       WorldPasteResult result,
                                       Throwable throwable) {
        if (throwable != null) {
            logger.severe("Failed to paste lobby world for slot " + instance.slotId + ": " + throwable.getMessage());
            throwable.printStackTrace();
            teardownSlot(instance, SlotLifecycleStatus.FAULTED, Map.of("error", throwable.getMessage()));
            return;
        }
        if (result == null) {
            logger.warning("World paste returned no result for slot " + instance.slotId);
            teardownSlot(instance, SlotLifecycleStatus.FAULTED, Map.of("error", "paste-result-null"));
            return;
        }
        if (!result.success()) {
            logger.warning("World paste failed for slot " + instance.slotId + ": " + result.message());
            teardownSlot(instance, SlotLifecycleStatus.FAULTED, Map.of("error", result.message()));
            return;
        }

        instance.markReady();
        instance.metadata.put("worldUpdatedAt", result.world() != null
                ? result.world().getUpdatedAt().toString()
                : "");

        if (orchestrator != null) {
            orchestrator.updateSlotStatus(
                    instance.slotId,
                    SlotLifecycleStatus.AVAILABLE,
                    0,
                    Collections.unmodifiableMap(new LinkedHashMap<>(instance.metadata))
            );
        }

        logger.info("Lobby slot " + instance.slotId + " ready (world=" + instance.world.getName()
                + ", map=" + instance.template.getDisplayName() + ").");
    }

    private void teardownSlot(LobbyInstance instance,
                              SlotLifecycleStatus status,
                              Map<String, String> metadata) {
        activeSlots.remove(instance.slotId);

        if (instance.pasteTask != null && !instance.pasteTask.isDone()) {
            instance.pasteTask.cancel(true);
        }

        if (orchestrator != null) {
            orchestrator.removeSlot(instance.slotId, status, metadata);
        }

        World world = instance.world;
        if (world != null) {
            World fallback = resolveFallbackWorld(world);
            world.getPlayers().forEach(player -> {
                if (fallback != null) {
                    player.teleport(fallback.getSpawnLocation());
                }
            });
            Bukkit.unloadWorld(world, false);
        }
    }

    private boolean isTargetSlot(SimpleSlotOrchestrator.ProvisionedSlot slot) {
        if (!configuration.familyId().equalsIgnoreCase(slot.familyId())) {
            return false;
        }
        String expectedVariant = normalizeVariant(configuration.familyVariant());
        if (expectedVariant == null) {
            return true;
        }
        String slotVariant = normalizeVariant(slot.variant());
        if (slotVariant == null || slotVariant.equals(expectedVariant)) {
            if (slotVariant == null) {
                logger.fine(() -> "Provision for slot " + slot.slotId() + " missing variant; "
                        + "handling as '" + expectedVariant + "' for backward compatibility.");
            }
            return true;
        }
        logger.fine(() -> "Ignoring provision for slot " + slot.slotId() + " (variant="
                + slot.variant() + "); expected '" + expectedVariant + "'.");
        return false;
    }

    private String normalizeVariant(String variant) {
        if (variant == null) {
            return null;
        }
        String trimmed = variant.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private Optional<LoadedWorld> locateTemplate(String mapId) {
        if (worldService == null) {
            return Optional.empty();
        }

        Optional<LoadedWorld> match = worldService.getWorldByMapId(mapId);
        if (match.isPresent()) {
            return match;
        }
        if (!mapId.equalsIgnoreCase(configuration.mapId())) {
            return worldService.getWorldByMapId(configuration.mapId());
        }
        return Optional.empty();
    }

    private World prepareWorld(String slotId) {
        String worldName = buildWorldName(slotId);
        World existing = Bukkit.getWorld(worldName);
        if (existing != null) {
            World fallback = resolveFallbackWorld(existing);
            existing.getPlayers().forEach(player -> {
                if (fallback != null) {
                    player.teleport(fallback.getSpawnLocation());
                }
            });
            Bukkit.unloadWorld(existing, false);
        }

        WorldCreator creator = new WorldCreator(worldName);
        creator.generator(resolveChunkGenerator());
        World world = creator.createWorld();
        if (world != null) {
            world.setAutoSave(false);
        }
        return world;
    }

    private ChunkGenerator resolveChunkGenerator() {
        return new VoidChunkGenerator();
    }

    private Location resolveSpawn(World world, Map<String, String> metadata) {
        double x = parseOrDefault(metadata.get("spawnX"), DEFAULT_SPAWN_X);
        double y = parseOrDefault(metadata.get("spawnY"), DEFAULT_SPAWN_Y);
        double z = parseOrDefault(metadata.get("spawnZ"), DEFAULT_SPAWN_Z);
        float yaw = (float) parseOrDefault(metadata.get("spawnYaw"), 0.0);
        float pitch = (float) parseOrDefault(metadata.get("spawnPitch"), 0.0);

        return new Location(world, x, y, z, yaw, pitch);
    }

    private World resolveFallbackWorld(World current) {
        for (World candidate : Bukkit.getWorlds()) {
            if (!candidate.equals(current)) {
                return candidate;
            }
        }
        return null;
    }

    private double parseOrDefault(String value, double fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private void failProvision(String slotId, String reason) {
        logger.warning("Provision failed for slot " + slotId + ": " + reason);
        if (orchestrator != null) {
            orchestrator.updateSlotStatus(slotId, SlotLifecycleStatus.FAULTED, 0, Map.of("error", reason));
            orchestrator.removeSlot(slotId, SlotLifecycleStatus.FAULTED, Map.of("error", reason));
        }
    }

    private String resolveMapId(Map<String, String> metadata) {
        if (metadata != null) {
            String candidate = metadata.get("mapId");
            if (candidate == null) {
                candidate = metadata.get("mapid");
            }
            if (candidate != null && !candidate.isBlank()) {
                return candidate.trim();
            }
        }
        return configuration.mapId();
    }

    private String buildWorldName(String slotId) {
        return "lobby_" + Objects.requireNonNull(slotId, "slotId").toLowerCase(Locale.ROOT);
    }

    private static final class LobbyInstance {
        private final String slotId;
        private final World world;
        private final Location spawnLocation;
        private final LoadedWorld template;
        private final Map<String, String> metadata;
        private CompletableFuture<WorldPasteResult> pasteTask;
        private boolean ready;

        private LobbyInstance(String slotId,
                              World world,
                              Location spawnLocation,
                              LoadedWorld template,
                              Map<String, String> metadata) {
            this.slotId = slotId;
            this.world = world;
            this.spawnLocation = spawnLocation;
            this.template = template;
            this.metadata = metadata;
        }

        private void setPasteTask(CompletableFuture<WorldPasteResult> pasteTask) {
            this.pasteTask = pasteTask;
        }

        private void markReady() {
            this.ready = true;
        }
    }
}
