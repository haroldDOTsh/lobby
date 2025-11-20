package sh.harold.fulcrum.lobby.feature;

import sh.harold.fulcrum.lobby.system.LobbyFeature;
import sh.harold.fulcrum.lobby.system.LobbyFeatureContext;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;
import sh.harold.fulcrum.api.rank.Rank;
import sh.harold.fulcrum.api.rank.RankService;
import sh.harold.fulcrum.api.rank.RankUtils;
import sh.harold.fulcrum.lifecycle.ServiceLocatorImpl;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Applies rank-aware nametags to lobby players.
 */
public final class LobbyNametagFeature implements LobbyFeature, Listener {
    private static final String TEAM_PREFIX = "nt-";
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final Map<UUID, String> playerTeams = new ConcurrentHashMap<>();
    private JavaPlugin plugin;
    private Logger logger;
    private Scoreboard scoreboard;
    private RankService rankService;

    @Override
    public String id() {
        return "nametags";
    }

    @Override
    public int priority() {
        return 250;
    }

    @Override
    public void initialize(LobbyFeatureContext context) {
        this.plugin = context.plugin();
        this.logger = context.logger();

        this.scoreboard = resolveScoreboard();
        if (scoreboard == null) {
            logger.warning("Scoreboard manager unavailable; lobby nametags disabled.");
            return;
        }

        ServiceLocatorImpl locator = ServiceLocatorImpl.getInstance();
        if (locator != null) {
            rankService = locator.findService(RankService.class).orElse(null);
        }

        PluginManager pluginManager = plugin.getServer().getPluginManager();
        pluginManager.registerEvents(this, plugin);

        Bukkit.getOnlinePlayers().forEach(this::refreshNametag);
        logger.info("Lobby nametag feature initialised" + (rankService == null ? " (using cached rank state)" : "."));
    }

    @Override
    public void shutdown(LobbyFeatureContext context) {
        HandlerList.unregisterAll(this);

        Bukkit.getOnlinePlayers().forEach(this::resetNametag);
        cleanupTeams();

        playerTeams.clear();
        rankService = null;
        scoreboard = null;
        plugin = null;
        logger = null;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        refreshNametag(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        resetNametag(event.getPlayer());
    }

    private Scoreboard resolveScoreboard() {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        return manager != null ? manager.getMainScoreboard() : null;
    }

    private void refreshNametag(Player player) {
        if (player == null || scoreboard == null) {
            return;
        }
        loadRank(player).thenAccept(rank ->
                runOnMainThread(() -> applyNametag(player, Optional.ofNullable(rank).orElse(Rank.DEFAULT))));
    }

    private CompletableFuture<Rank> loadRank(Player player) {
        if (rankService == null) {
            return CompletableFuture.completedFuture(fallbackRank(player));
        }
        return rankService.getEffectiveRank(player.getUniqueId())
                .exceptionally(throwable -> {
                    if (logger != null) {
                        logger.log(Level.WARNING, "Failed to resolve rank for " + player.getName() + "; falling back to cached state.", throwable);
                    }
                    return fallbackRank(player);
                });
    }

    private Rank fallbackRank(Player player) {
        Rank rank = RankUtils.getEffectiveRank(player);
        return rank != null ? rank : Rank.DEFAULT;
    }

    private void applyNametag(Player player, Rank rank) {
        if (player == null || scoreboard == null || !player.isOnline()) {
            return;
        }

        String entry = player.getName();
        String desiredTeamName = teamNameFor(rank);

        String previousTeamName = playerTeams.put(player.getUniqueId(), desiredTeamName);
        if (previousTeamName != null && !Objects.equals(previousTeamName, desiredTeamName)) {
            Team previous = scoreboard.getTeam(previousTeamName);
            if (previous != null) {
                previous.removeEntry(entry);
                clearTeamIfEmpty(previous);
            }
        }

        Team existingEntryTeam = scoreboard.getEntryTeam(entry);
        if (existingEntryTeam != null && !Objects.equals(existingEntryTeam.getName(), desiredTeamName)) {
            existingEntryTeam.removeEntry(entry);
            clearTeamIfEmpty(existingEntryTeam);
        }

        Team team = ensureTeam(rank, desiredTeamName);
        if (team == null) {
            return;
        }

        if (!team.hasEntry(entry)) {
            team.addEntry(entry);
        }

        Component prefix = buildPrefix(rank);
        team.prefix(prefix);

        NamedTextColor nameColor = Optional.ofNullable(rank.getNameColor()).orElse(NamedTextColor.WHITE);
        team.color(nameColor);
        team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS);

        Component displayName = prefix.append(Component.text(player.getName(), nameColor));
        player.playerListName(displayName);
    }

    private void resetNametag(Player player) {
        if (player == null || scoreboard == null) {
            return;
        }

        String entry = player.getName();
        Team team = scoreboard.getEntryTeam(entry);
        if (team != null) {
            team.removeEntry(entry);
            clearTeamIfEmpty(team);
        }

        playerTeams.remove(player.getUniqueId());
        player.playerListName(null);
    }

    private Team ensureTeam(Rank rank, String teamName) {
        if (scoreboard == null) {
            return null;
        }
        Team team = scoreboard.getTeam(teamName);
        if (team == null) {
            team = scoreboard.registerNewTeam(teamName);
        }

        Component prefix = buildPrefix(rank);
        team.prefix(prefix);
        NamedTextColor nameColor = Optional.ofNullable(rank.getNameColor()).orElse(NamedTextColor.WHITE);
        team.color(nameColor);
        team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);

        return team;
    }

    private void clearTeamIfEmpty(Team team) {
        if (team == null || !team.getName().startsWith(TEAM_PREFIX)) {
            return;
        }
        if (!team.getEntries().isEmpty()) {
            return;
        }
        try {
            team.unregister();
        } catch (IllegalStateException ignored) {
            // Team already unregistered elsewhere.
        }
    }

    private void cleanupTeams() {
        if (scoreboard == null) {
            return;
        }
        for (Team team : Set.copyOf(scoreboard.getTeams())) {
            if (!team.getName().startsWith(TEAM_PREFIX)) {
                continue;
            }
            try {
                team.unregister();
            } catch (IllegalStateException ignored) {
                // Already removed.
            }
        }
    }

    private Component buildPrefix(Rank rank) {
        if (rank == null) {
            return Component.empty();
        }
        Component prefix = LEGACY.deserialize(rank.getFullPrefix());
        return prefix.append(Component.space());
    }

    private String teamNameFor(Rank rank) {
        return TEAM_PREFIX + (rank != null ? rank.ordinal() : Rank.DEFAULT.ordinal());
    }

    private void runOnMainThread(Runnable task) {
        if (task == null || plugin == null) {
            return;
        }
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }
}
