package sh.harold.fulcrum.lobby.feature;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
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

    private JavaPlugin plugin;
    private Logger logger;
    private ScoreboardService scoreboardService;
    private RankService rankService;

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
        }

        if (scoreboardService == null) {
            logger.warning("ScoreboardService unavailable; lobby scoreboard feature disabled.");
            return;
        }

        ScoreboardModule rankModule = new RankModule();
        ScoreboardDefinition definition = new ScoreboardBuilder(SCOREBOARD_ID)
                .headerLabel("Lobby")
                .module(rankModule)
                .build();

        if (scoreboardService.isScoreboardRegistered(SCOREBOARD_ID)) {
            scoreboardService.unregisterScoreboard(SCOREBOARD_ID);
        }
        scoreboardService.registerScoreboard(SCOREBOARD_ID, definition);

        PluginManager pluginManager = plugin.getServer().getPluginManager();
        pluginManager.registerEvents(this, plugin);

        Bukkit.getOnlinePlayers().forEach(player -> showScoreboard(player.getUniqueId()));
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

        scoreboardService = null;
        rankService = null;
        plugin = null;
        logger = null;
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
}
