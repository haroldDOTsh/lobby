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
import sh.harold.fulcrum.api.lifecycle.ServerIdentifier;
import sh.harold.fulcrum.api.network.NetworkConfigService;
import sh.harold.fulcrum.api.message.scoreboard.ScoreboardService;
import sh.harold.fulcrum.api.message.scoreboard.ScoreboardBuilder;
import sh.harold.fulcrum.api.message.scoreboard.module.ContentProvider;
import sh.harold.fulcrum.api.message.scoreboard.module.DynamicContentProvider;
import sh.harold.fulcrum.api.message.scoreboard.module.ScoreboardModule;
import sh.harold.fulcrum.api.message.scoreboard.registry.ScoreboardDefinition;
import sh.harold.fulcrum.api.rank.Rank;
import sh.harold.fulcrum.api.rank.RankService;
import sh.harold.fulcrum.api.rank.RankUtils;
import sh.harold.fulcrum.lifecycle.ServiceLocatorImpl;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Displays a simple lobby scoreboard with a rank module.
 */
public final class LobbyScoreboardFeature implements LobbyFeature, Listener {
    private static final String SCOREBOARD_ID = "lobby:main";
    private static final long RANK_REFRESH_INTERVAL_MS = TimeUnit.SECONDS.toMillis(1);
    private static final String DEFAULT_HEADER_LABEL = "Lobby";
    private static final String[] SCOREBOARD_TITLE_PATHS = {
            "modules.scoreboard.title",
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
            NetworkConfigService networkConfigService = locator.findService(NetworkConfigService.class).orElse(null);
            defaultScoreboardTitle = resolveDefaultScoreboardTitle(networkConfigService);
        }

        if (scoreboardService == null) {
            logger.warning("ScoreboardService unavailable; lobby scoreboard feature disabled.");
            return;
        }

        registerScoreboardDefinition(resolveHeaderLabel());

        PluginManager pluginManager = plugin.getServer().getPluginManager();
        pluginManager.registerEvents(this, plugin);

        startServerIdMonitor();
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
    }

    private void showScoreboard(UUID playerId) {
        if (scoreboardService == null) {
            return;
        }
        refreshScoreboardDefinitionIfNeeded();
        scoreboardService.showScoreboard(playerId, SCOREBOARD_ID);
        scoreboardService.refreshPlayerScoreboard(playerId);
    }

    private Rank resolveRank(UUID playerId) {
        Rank rank = null;
        if (rankService != null) {
            try {
                rank = rankService.getEffectiveRankSync(playerId);
            } catch (Exception exception) {
                if (logger != null) {
                    logger.log(Level.WARNING, "Failed to resolve rank via RankService for " + playerId, exception);
                }
            }
        }
        if (rank == null) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                rank = RankUtils.getEffectiveRank(player);
            }
        }
        return rank != null ? rank : Rank.DEFAULT;
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

    private String resolveDefaultScoreboardTitle(NetworkConfigService service) {
        if (service == null) {
            return null;
        }
        for (String path : SCOREBOARD_TITLE_PATHS) {
            String value = service.getString(path)
                    .map(String::trim)
                    .filter(title -> !title.isEmpty())
                    .orElse(null);
            if (value != null) {
                return value;
            }
        }
        return null;
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
}
