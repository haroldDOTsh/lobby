package sh.harold.fulcrum.lobby.feature;

import sh.harold.fulcrum.lobby.system.LobbyFeature;
import sh.harold.fulcrum.lobby.system.LobbyFeatureContext;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import sh.harold.fulcrum.api.environment.directory.EnvironmentDescriptorView;
import sh.harold.fulcrum.api.environment.directory.EnvironmentDirectoryService;
import sh.harold.fulcrum.api.lifecycle.ServerIdentifier;
import sh.harold.fulcrum.api.message.scoreboard.ScoreboardService;
import sh.harold.fulcrum.api.message.scoreboard.ScoreboardBuilder;
import sh.harold.fulcrum.api.message.scoreboard.module.ContentProvider;
import sh.harold.fulcrum.api.message.scoreboard.module.DynamicContentProvider;
import sh.harold.fulcrum.api.message.scoreboard.module.ScoreboardModule;
import sh.harold.fulcrum.api.message.scoreboard.registry.ScoreboardDefinition;
import sh.harold.fulcrum.api.module.FulcrumEnvironment;
import sh.harold.fulcrum.api.rank.Rank;
import sh.harold.fulcrum.api.rank.RankService;
import sh.harold.fulcrum.api.rank.RankUtils;
import sh.harold.fulcrum.lifecycle.ServiceLocatorImpl;
import sh.harold.fulcrum.lobby.system.EnvironmentSettings;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Displays a simple lobby scoreboard with a rank module.
 */
public final class LobbyScoreboardFeature implements LobbyFeature, Listener {
    private static final String SCOREBOARD_ID = "lobby:main";
    private static final long RANK_REFRESH_INTERVAL_MS = TimeUnit.SECONDS.toMillis(1);
    private static final long RANK_CACHE_TTL_MS = TimeUnit.SECONDS.toMillis(10);
    private static final long RANK_REFRESH_TASK_INTERVAL_TICKS = 100L;
    private static final String DEFAULT_HEADER_LABEL = "Lobby";
    private static final String[] SCOREBOARD_TITLE_PATHS = {
            "lobby.scoreboard.title",
            "scoreboard.title"
    };

    private JavaPlugin plugin;
    private Logger logger;
    private ScoreboardService scoreboardService;
    private RankService rankService;
    private ServerIdentifier serverIdentifier;
    private String defaultScoreboardTitle;
    private String currentHeaderLabel = DEFAULT_HEADER_LABEL;
    private BukkitTask serverIdMonitorTask;
    private BukkitTask rankRefreshTask;
    private final Map<UUID, RankSnapshot> rankCache = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<Rank>> rankRefreshes = new ConcurrentHashMap<>();

    @Override
    public String id() {
        return "scoreboard";
    }

    @Override
    public int priority() {
        return 240;
    }

    @Override
    public void initialize(LobbyFeatureContext context) {
        this.plugin = context.plugin();
        this.logger = context.logger();

        ServiceLocatorImpl locator = ServiceLocatorImpl.getInstance();
        if (locator != null) {
            scoreboardService = locator.findService(ScoreboardService.class).orElse(null);
            rankService = locator.findService(RankService.class).orElse(null);
            serverIdentifier = locator.findService(ServerIdentifier.class).orElse(null);
            EnvironmentDirectoryService directoryService = locator.findService(EnvironmentDirectoryService.class)
                    .orElse(null);
            defaultScoreboardTitle = resolveDefaultScoreboardTitle(directoryService);
        }

        if (scoreboardService == null) {
            logger.warning("ScoreboardService unavailable; lobby scoreboard feature disabled.");
            return;
        }

        registerScoreboardDefinition(resolveHeaderLabel());

        PluginManager pluginManager = plugin.getServer().getPluginManager();
        pluginManager.registerEvents(this, plugin);

        startServerIdMonitor();
        startRankRefreshTask();
        logger.info("Lobby scoreboard feature initialised.");
    }

    @Override
    public void shutdown(LobbyFeatureContext context) {
        HandlerList.unregisterAll(this);

        if (scoreboardService != null) {
            Bukkit.getOnlinePlayers().forEach(player -> scoreboardService.hideScoreboard(player.getUniqueId()));
            if (scoreboardService.isScoreboardRegistered(SCOREBOARD_ID)) {
                scoreboardService.unregisterScoreboard(SCOREBOARD_ID);
            }
        }
        if (serverIdMonitorTask != null) {
            serverIdMonitorTask.cancel();
            serverIdMonitorTask = null;
        }
        stopRankRefreshTask();
        rankCache.clear();
        rankRefreshes.values().forEach(future -> future.cancel(true));
        rankRefreshes.clear();

        scoreboardService = null;
        rankService = null;
        plugin = null;
        logger = null;
        serverIdentifier = null;
        defaultScoreboardTitle = null;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        showScoreboard(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (scoreboardService != null) {
            scoreboardService.hideScoreboard(event.getPlayer().getUniqueId());
        }
        evictRankCache(event.getPlayer().getUniqueId());
    }

    private void showScoreboard(UUID playerId) {
        if (scoreboardService == null || playerId == null) {
            return;
        }
        refreshScoreboardDefinitionIfNeeded();
        primeRankCache(playerId);
        scoreboardService.showScoreboard(playerId, SCOREBOARD_ID);
        scoreboardService.refreshPlayerScoreboard(playerId);
    }

    private Rank resolveRank(UUID playerId) {
        return currentRank(playerId);
    }

    private final class RankModule implements ScoreboardModule {
        private final ContentProvider provider = new DynamicContentProvider(this::renderLines, RANK_REFRESH_INTERVAL_MS);

        @Override
        public String getModuleId() {
            return "rank";
        }

        @Override
        public ContentProvider getContentProvider() {
            return provider;
        }

        @Override
        public int getPriorityHint() {
            return 900;
        }

        private List<String> renderLines(UUID playerId) {
            Objects.requireNonNull(playerId, "playerId");
            Rank rank = resolveRank(playerId);
            String prefix = rank.getFullPrefix();
            if (prefix == null || prefix.isBlank()) {
                prefix = "&7[Default]";
            }
            String line = "&7Rank: &r" + prefix;
            return List.of(line);
        }
    }

    private String resolveHeaderLabel() {
        if (serverIdentifier != null) {
            String serverId = serverIdentifier.getServerId();
            if (serverId != null && !serverId.isBlank()) {
                return serverId;
            }
        }
        return DEFAULT_HEADER_LABEL;
    }

    private void registerScoreboardDefinition(String headerLabel) {
        if (scoreboardService == null) {
            return;
        }
        currentHeaderLabel = headerLabel != null && !headerLabel.isBlank()
                ? headerLabel
                : DEFAULT_HEADER_LABEL;
        ScoreboardBuilder builder = new ScoreboardBuilder(SCOREBOARD_ID)
                .headerLabel(currentHeaderLabel);
        if (defaultScoreboardTitle != null) {
            builder.title(defaultScoreboardTitle);
        }
        ScoreboardDefinition definition = builder
                .module(new RankModule())
                .build();
        if (scoreboardService.isScoreboardRegistered(SCOREBOARD_ID)) {
            scoreboardService.unregisterScoreboard(SCOREBOARD_ID);
        }
        scoreboardService.registerScoreboard(SCOREBOARD_ID, definition);
        Bukkit.getOnlinePlayers().forEach(player -> {
            UUID playerId = player.getUniqueId();
            primeRankCache(playerId);
            scoreboardService.showScoreboard(playerId, SCOREBOARD_ID);
            scoreboardService.refreshPlayerScoreboard(playerId);
        });
    }

    private void refreshScoreboardDefinitionIfNeeded() {
        String desiredHeader = resolveHeaderLabel();
        if (!Objects.equals(desiredHeader, currentHeaderLabel)) {
            registerScoreboardDefinition(desiredHeader);
        }
    }

    private String resolveDefaultScoreboardTitle(EnvironmentDirectoryService service) {
        EnvironmentDescriptorView descriptor = resolveEnvironmentDescriptor(service);
        if (descriptor == null) {
            return null;
        }
        Map<String, Object> settings = descriptor.settings();
        return EnvironmentSettings.getString(settings, SCOREBOARD_TITLE_PATHS).orElse(null);
    }

    private EnvironmentDescriptorView resolveEnvironmentDescriptor(EnvironmentDirectoryService service) {
        if (service == null) {
            return null;
        }
        String environmentId = FulcrumEnvironment.getCurrent();
        if (environmentId == null || environmentId.isBlank()) {
            return null;
        }
        return service.getEnvironment(environmentId).orElse(null);
    }

    private void startServerIdMonitor() {
        if (plugin == null || scoreboardService == null) {
            return;
        }
        if (serverIdMonitorTask != null) {
            return;
        }
        serverIdMonitorTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (scoreboardService == null) {
                return;
            }
            refreshScoreboardDefinitionIfNeeded();
            if (!isTemporaryIdentifier(currentHeaderLabel)) {
                BukkitTask task = serverIdMonitorTask;
                if (task != null) {
                    task.cancel();
                }
                serverIdMonitorTask = null;
            }
        }, 20L, 20L);
    }

    private boolean isTemporaryIdentifier(String identifier) {
        return identifier != null && identifier.startsWith("temp-");
    }

    private void startRankRefreshTask() {
        if (plugin == null || rankRefreshTask != null) {
            return;
        }
        rankRefreshTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (rankService == null) {
                return;
            }
            for (Player player : Bukkit.getOnlinePlayers()) {
                UUID playerId = player.getUniqueId();
                RankSnapshot snapshot = rankCache.get(playerId);
                if (snapshot == null || snapshot.isStale(RANK_CACHE_TTL_MS)) {
                    triggerRankRefresh(playerId);
                }
            }
        }, 20L, RANK_REFRESH_TASK_INTERVAL_TICKS);
    }

    private void stopRankRefreshTask() {
        if (rankRefreshTask != null) {
            rankRefreshTask.cancel();
            rankRefreshTask = null;
        }
    }

    private void primeRankCache(UUID playerId) {
        if (playerId == null) {
            return;
        }
        rankCache.computeIfAbsent(playerId, id -> new RankSnapshot(resolveRuntimeRank(id), System.currentTimeMillis()));
        triggerRankRefresh(playerId);
    }

    private Rank currentRank(UUID playerId) {
        if (playerId == null) {
            return Rank.DEFAULT;
        }
        RankSnapshot snapshot = rankCache.get(playerId);
        if (snapshot == null) {
            Rank runtime = resolveRuntimeRank(playerId);
            rankCache.put(playerId, new RankSnapshot(runtime, System.currentTimeMillis()));
            triggerRankRefresh(playerId);
            return runtime != null ? runtime : Rank.DEFAULT;
        }
        if (snapshot.isStale(RANK_CACHE_TTL_MS)) {
            triggerRankRefresh(playerId);
        }
        return snapshot.rank != null ? snapshot.rank : Rank.DEFAULT;
    }

    private Rank resolveRuntimeRank(UUID playerId) {
        Player player = playerId != null ? Bukkit.getPlayer(playerId) : null;
        return resolveRuntimeRank(player);
    }

    private Rank resolveRuntimeRank(Player player) {
        if (player == null) {
            return Rank.DEFAULT;
        }
        Rank rank = RankUtils.getEffectiveRank(player);
        return rank != null ? rank : Rank.DEFAULT;
    }

    private void triggerRankRefresh(UUID playerId) {
        if (playerId == null || rankService == null) {
            return;
        }
        rankRefreshes.compute(playerId, (id, existing) -> {
            if (existing != null && !existing.isDone()) {
                return existing;
            }
            CompletableFuture<Rank> future;
            try {
                future = rankService.getEffectiveRank(id);
            } catch (Exception exception) {
                if (logger != null) {
                    logger.log(Level.FINE, "Failed to schedule rank refresh for " + id, exception);
                }
                return null;
            }

            future = future.exceptionally(throwable -> {
                if (logger != null) {
                    logger.log(Level.FINE, "Async rank refresh failed for " + id, throwable);
                }
                return null;
            });

            future.whenComplete((rank, throwable) -> {
                if (rank != null) {
                    rankCache.put(id, new RankSnapshot(rank, System.currentTimeMillis()));
                } else {
                    rankCache.computeIfPresent(id, (key, snapshot) ->
                            new RankSnapshot(snapshot.rank, System.currentTimeMillis()));
                }
                rankRefreshes.remove(id);

                if (plugin != null && scoreboardService != null) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (scoreboardService != null && Bukkit.getPlayer(id) != null) {
                            scoreboardService.refreshPlayerScoreboard(id);
                        }
                    });
                }
            });
            return future;
        });
    }

    private void evictRankCache(UUID playerId) {
        if (playerId == null) {
            return;
        }
        rankCache.remove(playerId);
        CompletableFuture<Rank> future = rankRefreshes.remove(playerId);
        if (future != null) {
            future.cancel(true);
        }
    }

    private static final class RankSnapshot {
        private final Rank rank;
        private final long resolvedAt;

        private RankSnapshot(Rank rank, long resolvedAt) {
            this.rank = rank;
            this.resolvedAt = resolvedAt;
        }

        private boolean isStale(long ttlMillis) {
            return System.currentTimeMillis() - resolvedAt >= ttlMillis;
        }
    }
}
