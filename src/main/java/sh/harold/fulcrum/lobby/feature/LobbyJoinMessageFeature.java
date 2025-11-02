package sh.harold.fulcrum.lobby.feature;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.fulcrum.api.rank.Rank;
import sh.harold.fulcrum.api.rank.RankUtils;
import sh.harold.fulcrum.lobby.config.LobbyConfiguration;
import sh.harold.fulcrum.lobby.config.LobbyConfigurationRegistry;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Broadcasts rank-aware join messages using lobby configuration.
 */
public final class LobbyJoinMessageFeature implements LobbyFeature, Listener {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final String DEFAULT_JOIN_MESSAGE = "&7%player% has joined the lobby!";
    private static final String DEFAULT_DONATOR_JOIN_MESSAGE = "&6>>> %player% has joined the lobby! <<<";
    private static final String DEFAULT_TOP_DONATOR_JOIN_MESSAGE = "&d[MVP++] %player% has entered!";

    private JavaPlugin plugin;
    private Logger logger;
    private Supplier<LobbyConfiguration> configurationSupplier = LobbyConfigurationRegistry::current;

    @Override
    public String id() {
        return "join-messages";
    }

    @Override
    public int priority() {
        return 25;
    }

    @Override
    public void initialize(LobbyFeatureContext context) {
        this.plugin = context.plugin();
        this.logger = context.logger();
        this.configurationSupplier = () -> context.get(LobbyConfiguration.class)
                .orElseGet(LobbyConfigurationRegistry::current);

        PluginManager pluginManager = plugin.getServer().getPluginManager();
        pluginManager.registerEvents(this, plugin);
        logger.info("Lobby join message feature initialised.");
    }

    @Override
    public void shutdown(LobbyFeatureContext context) {
        HandlerList.unregisterAll(this);
        this.configurationSupplier = LobbyConfigurationRegistry::current;
        this.plugin = null;
        this.logger = null;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        LobbyConfiguration configuration = Optional.ofNullable(configurationSupplier.get())
                .orElseGet(LobbyConfigurationRegistry::current);

        Player player = event.getPlayer();
        Rank rank = resolveRank(player);

        String template = resolveTemplate(rank, configuration);
        if (template == null || template.isBlank()) {
            event.joinMessage(null);
            return;
        }

        Component message = formatMessage(template, player, rank);
        event.joinMessage(message);
    }

    private Rank resolveRank(Player player) {
        if (player == null) {
            return Rank.DEFAULT;
        }
        return Optional.ofNullable(RankUtils.getEffectiveRank(player)).orElse(Rank.DEFAULT);
    }

    private String resolveTemplate(Rank rank, LobbyConfiguration configuration) {
        Set<String> donatorRanks = configuration != null && configuration.joinDonatorRanks() != null
                ? configuration.joinDonatorRanks()
                : Set.of();

        String rankKey = rankKey(rank);

        String topMessage = sanitise(configuration != null ? configuration.joinTopDonatorMessage() : null);
        if (topMessage == null) {
            topMessage = DEFAULT_TOP_DONATOR_JOIN_MESSAGE;
        }

        String donatorMessage = sanitise(configuration != null ? configuration.joinDonatorMessage() : null);
        if (donatorMessage == null) {
            donatorMessage = DEFAULT_DONATOR_JOIN_MESSAGE;
        }

        String defaultMessage = sanitise(configuration != null ? configuration.joinDefaultMessage() : null);
        if (defaultMessage == null) {
            defaultMessage = DEFAULT_JOIN_MESSAGE;
        }

        if (isTopDonator(rank, rankKey, donatorRanks)) {
            return topMessage;
        }
        if (rankKey != null && donatorRanks.contains(rankKey)) {
            return donatorMessage;
        }
        return defaultMessage;
    }

    private boolean isTopDonator(Rank rank, String rankKey, Set<String> configuredRanks) {
        if (rank == null || rankKey == null || configuredRanks == null) {
            return false;
        }
        if (!configuredRanks.contains(rankKey)) {
            return false;
        }

        Rank highestResolved = null;
        String fallback = null;
        for (String configured : configuredRanks) {
            if (configured == null || configured.isBlank()) {
                continue;
            }
            String normalised = configured.trim().toUpperCase(Locale.ROOT);
            fallback = normalised;
            Rank resolved = resolveRankConstant(normalised);
            if (resolved != null && (highestResolved == null || resolved.ordinal() > highestResolved.ordinal())) {
                highestResolved = resolved;
            }
        }

        if (highestResolved != null) {
            return rank == highestResolved;
        }
        return Objects.equals(rankKey, fallback);
    }

    private Rank resolveRankConstant(String identifier) {
        try {
            return Rank.valueOf(identifier.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException exception) {
            return null;
        }
    }

    private String rankKey(Rank rank) {
        if (rank == null) {
            return null;
        }
        String key = rank.name();
        if (key == null || key.isBlank()) {
            key = rank.toString();
        }
        return key != null ? key.toUpperCase(Locale.ROOT) : null;
    }

    private Component formatMessage(String template, Player player, Rank rank) {
        String message = applyPlaceholders(template, player, rank);
        return LEGACY.deserialize(message);
    }

    private String applyPlaceholders(String template, Player player, Rank rank) {
        String result = template;
        if (player != null) {
            result = result.replace("%player%", player.getName());
        }
        String prefix = Optional.ofNullable(rank.getFullPrefix()).orElse("");
        result = result.replace("%prefix%", prefix);
        return result;
    }

    private String sanitise(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        return message;
    }
}
