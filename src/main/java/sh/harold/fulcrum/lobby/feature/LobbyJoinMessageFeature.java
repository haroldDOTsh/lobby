package sh.harold.fulcrum.lobby.feature;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.fulcrum.api.rank.Rank;
import sh.harold.fulcrum.api.rank.RankUtils;
import sh.harold.fulcrum.lobby.config.LobbyConfiguration;
import sh.harold.fulcrum.lobby.config.LobbyConfigurationRegistry;
import sh.harold.fulcrum.lobby.system.LobbyFeature;
import sh.harold.fulcrum.lobby.system.LobbyFeatureContext;
import sh.harold.fulcrum.common.settings.PlayerSettingsService;
import sh.harold.fulcrum.lifecycle.ServiceLocatorImpl;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Broadcasts rank-aware join messages using lobby configuration.
 */
public final class LobbyJoinMessageFeature implements LobbyFeature, Listener {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final String DEFAULT_JOIN_MESSAGE = "%player% &7joined the lobby!";
    private static final String DEFAULT_DONATOR_JOIN_MESSAGE = "%player% &6joined the lobby!";
    private static final String DEFAULT_TOP_DONATOR_JOIN_MESSAGE = "&b>&c>&a> %player% &6has joined the lobby! &a<&c<&b<";
    private static final long SETTINGS_FETCH_TIMEOUT_MILLIS = 500L;
    private static final String SETTINGS_SCOPE = "lobby";
    private static final String SEND_JOIN_MESSAGE_KEY = "sendJoinMessage";
    private static final boolean SEND_JOIN_MESSAGE_DEFAULT = true;

    private JavaPlugin plugin;
    private Logger logger;
    private Supplier<LobbyConfiguration> configurationSupplier = LobbyConfigurationRegistry::current;
    private PlayerSettingsService playerSettings;
    private PlayerSettingsService.GameSettingsScope lobbySettingsScope;
    private final ConcurrentHashMap<UUID, Boolean> joinVisibilityCache = new ConcurrentHashMap<>();

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

        ServiceLocatorImpl locator = ServiceLocatorImpl.getInstance();
        if (locator != null) {
            playerSettings = locator.findService(PlayerSettingsService.class).orElse(null);
            if (playerSettings != null) {
                lobbySettingsScope = playerSettings.forGame(SETTINGS_SCOPE);
            }
        }

        PluginManager pluginManager = plugin.getServer().getPluginManager();
        pluginManager.registerEvents(this, plugin);
        logger.info("Lobby join message feature initialised.");
    }

    @Override
    public void shutdown(LobbyFeatureContext context) {
        HandlerList.unregisterAll(this);
        this.configurationSupplier = LobbyConfigurationRegistry::current;
        this.joinVisibilityCache.clear();
        this.plugin = null;
        this.logger = null;
        this.playerSettings = null;
        this.lobbySettingsScope = null;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!shouldAnnounce(event.getPlayer())) {
            event.joinMessage(null);
            return;
        }

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

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event == null || event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        UUID playerId = event.getUniqueId();
        if (playerId == null) {
            return;
        }
        primeJoinVisibility(playerId);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (player != null) {
            joinVisibilityCache.remove(player.getUniqueId());
        }
    }

    private void primeJoinVisibility(UUID playerId) {
        if (playerId == null) {
            return;
        }
        if (lobbySettingsScope == null) {
            joinVisibilityCache.put(playerId, Boolean.TRUE);
            return;
        }

        CompletionStage<Optional<Boolean>> stage = lobbySettingsScope.get(playerId, SEND_JOIN_MESSAGE_KEY, Boolean.class);
        CompletableFuture<Optional<Boolean>> future = stage instanceof CompletableFuture
                ? (CompletableFuture<Optional<Boolean>>) stage
                : stage.toCompletableFuture();

        stage.whenComplete((level, throwable) -> {
            boolean enabled = resolveSendJoinMessage(level);
            if (throwable != null) {
                if (logger != null) {
                    logger.log(Level.FINE, "Deferred join message setting resolution failed for " + playerId, throwable);
                }
            }
            joinVisibilityCache.put(playerId, enabled);
        });

        try {
            Optional<Boolean> resolved = future.get(SETTINGS_FETCH_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            joinVisibilityCache.put(playerId, resolveSendJoinMessage(resolved));
        } catch (TimeoutException timeout) {
            joinVisibilityCache.putIfAbsent(playerId, Boolean.TRUE);
            if (logger != null) {
                logger.log(Level.FINE, "Timed out while resolving join message setting for " + playerId, timeout);
            }
        } catch (Exception exception) {
            joinVisibilityCache.put(playerId, Boolean.TRUE);
            if (logger != null) {
                logger.log(Level.FINE, "Failed to resolve join message setting for " + playerId, exception);
            }
        }
    }

    private boolean shouldAnnounce(Player player) {
        if (player == null) {
            return true;
        }
        UUID playerId = player.getUniqueId();
        Boolean cached = joinVisibilityCache.get(playerId);
        if (cached != null) {
            return cached;
        }

        if (lobbySettingsScope == null) {
            return true;
        }

        CompletionStage<Optional<Boolean>> stage = lobbySettingsScope.get(playerId, SEND_JOIN_MESSAGE_KEY, Boolean.class);

        Optional<Boolean> resolved = getIfCompleted(stage);
        if (resolved != null) {
            boolean enabled = resolveSendJoinMessage(resolved);
            joinVisibilityCache.put(playerId, enabled);
            return enabled;
        }

        stage.whenComplete((level, throwable) -> {
            boolean enabled = resolveSendJoinMessage(level);
            if (throwable != null) {
                if (logger != null) {
                    logger.log(Level.FINE, "Failed to resolve join message setting for " + playerId, throwable);
                }
            }
            joinVisibilityCache.put(playerId, enabled);
        });
        return true;
    }

    private Optional<Boolean> getIfCompleted(CompletionStage<Optional<Boolean>> stage) {
        if (stage == null) {
            return null;
        }
        CompletableFuture<Optional<Boolean>> future = stage instanceof CompletableFuture
                ? (CompletableFuture<Optional<Boolean>>) stage
                : stage.toCompletableFuture();
        if (!future.isDone()) {
            return null;
        }
        try {
            return future.join();
        } catch (Exception exception) {
            if (logger != null) {
                logger.log(Level.FINE, "Deferred join message resolution failed", exception);
            }
            return null;
        }
    }

    private static boolean resolveSendJoinMessage(Optional<Boolean> value) {
        return value != null ? value.orElse(SEND_JOIN_MESSAGE_DEFAULT) : SEND_JOIN_MESSAGE_DEFAULT;
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
        Component base = LEGACY.deserialize(template);

        Component playerName = renderPlayerName(player, rank);
        Component prefix = renderPrefix(rank);

        base = base.replaceText(TextReplacementConfig.builder()
                .matchLiteral("%player%")
                .replacement(playerName)
                .build());

        base = base.replaceText(TextReplacementConfig.builder()
                .matchLiteral("%prefix%")
                .replacement(prefix)
                .build());

        return base;
    }

    private Component renderPlayerName(Player player, Rank rank) {
        if (player == null) {
            return Component.empty();
        }
        NamedTextColor nameColor = Optional.ofNullable(rank != null ? rank.getNameColor() : null)
                .orElse(NamedTextColor.WHITE);
        return Component.text(player.getName(), nameColor);
    }

    private Component renderPrefix(Rank rank) {
        String prefix = rank != null ? rank.getFullPrefix() : null;
        if (prefix == null || prefix.isBlank()) {
            return Component.empty();
        }
        return LEGACY.deserialize(prefix).append(Component.space());
    }

    private String sanitise(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        return message;
    }
}
