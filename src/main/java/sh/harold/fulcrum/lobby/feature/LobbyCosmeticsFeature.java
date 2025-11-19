package sh.harold.fulcrum.lobby.feature;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.fulcrum.api.rank.RankUtils;
import sh.harold.fulcrum.common.settings.PlayerSettingsService;
import sh.harold.fulcrum.lifecycle.ServiceLocatorImpl;
import sh.harold.fulcrum.lobby.cosmetics.CosmeticCategory;
import sh.harold.fulcrum.lobby.cosmetics.CosmeticDescriptor;
import sh.harold.fulcrum.lobby.cosmetics.CosmeticKeys;
import sh.harold.fulcrum.lobby.cosmetics.CosmeticSlot;
import sh.harold.fulcrum.lobby.cosmetics.SuitSlot;
import sh.harold.fulcrum.lobby.cosmetics.loadout.LoadoutService;
import sh.harold.fulcrum.lobby.cosmetics.loadout.PlayerSettingsLoadoutService;
import sh.harold.fulcrum.lobby.cosmetics.registry.CosmeticRegistry;
import sh.harold.fulcrum.lobby.cosmetics.runtime.CosmeticRuntime;
import sh.harold.fulcrum.lobby.system.LobbyFeature;
import sh.harold.fulcrum.lobby.system.LobbyFeatureContext;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Boots the cosmetics runtime and exposes a debug command for engineers.
 */
public final class LobbyCosmeticsFeature implements LobbyFeature {
    private static final String GAME_SCOPE = "lobby";
    private static final Component STAFF_ONLY_MESSAGE = Component.text(
            "Only staff members can run /debugcosmetic.", NamedTextColor.RED);
    private static final Component PLAYER_ONLY_MESSAGE = Component.text(
            "Only players can run this sub-command.", NamedTextColor.RED);
    private static final Component EQUIP_FAILURE = Component.text(
            "Failed to equip cosmetic. Check console for details.", NamedTextColor.RED);

    private CosmeticRuntime runtime;
    private LoadoutService loadoutService;
    private CosmeticRegistry registry;
    private Logger logger;
    private JavaPlugin plugin;

    private final SuggestionProvider<CommandSourceStack> cosmeticSuggestions = this::suggestCosmetics;

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

        registerDebugCommand();

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

    private void registerDebugCommand() {
        if (plugin == null) {
            return;
        }
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands commands = event.registrar();
            commands.register(
                    Commands.literal("debugcosmetic")
                            .then(infoArgument())
                            .then(equipArgument())
                            .build(),
                    "Inspect or equip lobby cosmetics"
            );
        });
    }

    private ArgumentBuilder<CommandSourceStack, ?> infoArgument() {
        return Commands.literal("info")
                .then(Commands.argument("id", StringArgumentType.string())
                        .suggests(cosmeticSuggestions)
                        .executes(context -> handleInfo(context, StringArgumentType.getString(context, "id"))));
    }

    private ArgumentBuilder<CommandSourceStack, ?> equipArgument() {
        return Commands.literal("equip")
                .then(Commands.argument("id", StringArgumentType.string())
                        .suggests(cosmeticSuggestions)
                        .executes(context -> handleEquip(context, StringArgumentType.getString(context, "id"))));
    }

    private int handleInfo(CommandContext<CommandSourceStack> context, String rawId) {
        CommandSourceStack source = context.getSource();
        if (!hasStaffPrivileges(source)) {
            source.getSender().sendMessage(STAFF_ONLY_MESSAGE);
            return Command.SINGLE_SUCCESS;
        }
        CosmeticDescriptor descriptor = registry.descriptor(rawId).orElse(null);
        if (descriptor == null) {
            source.getSender().sendMessage(unknownCosmetic(rawId));
            return Command.SINGLE_SUCCESS;
        }
        Component info = Component.text()
                .append(Component.text(descriptor.displayName(), NamedTextColor.GOLD)
                        .decoration(TextDecoration.BOLD, true))
                .append(Component.text(" (" + descriptor.id() + ")", NamedTextColor.DARK_GRAY))
                .append(Component.newline())
                .append(Component.text("Rarity: ", NamedTextColor.GRAY))
                .append(descriptor.rarity().displayName())
                .append(Component.newline())
                .append(Component.text("Icon: ", NamedTextColor.GRAY))
                .append(Component.text(descriptor.icon().name(), NamedTextColor.WHITE))
                .append(Component.newline())
                .append(Component.text(descriptor.description(), NamedTextColor.WHITE))
                .build();
        if (descriptor.limitedLore().isPresent()) {
            info = info.append(Component.newline())
                    .append(Component.text(descriptor.limitedLore().get(), NamedTextColor.LIGHT_PURPLE));
        }
        source.getSender().sendMessage(info);
        return Command.SINGLE_SUCCESS;
    }

    private int handleEquip(CommandContext<CommandSourceStack> context, String rawId) {
        CommandSourceStack source = context.getSource();
        if (!(source.getSender() instanceof Player player)) {
            source.getSender().sendMessage(PLAYER_ONLY_MESSAGE);
            return Command.SINGLE_SUCCESS;
        }
        if (!hasStaffPrivileges(source)) {
            player.sendMessage(STAFF_ONLY_MESSAGE);
            return Command.SINGLE_SUCCESS;
        }
        CosmeticDescriptor descriptor = registry.descriptor(rawId).orElse(null);
        if (descriptor == null) {
            player.sendMessage(unknownCosmetic(rawId));
            return Command.SINGLE_SUCCESS;
        }
        equipCosmetic(player, descriptor).whenComplete((ignored, throwable) -> {
            if (throwable != null) {
                if (logger != null) {
                    logger.log(Level.SEVERE, "Unable to equip cosmetic " + descriptor.id(), throwable);
                }
                plugin.getServer().getScheduler().runTask(plugin, () -> player.sendMessage(EQUIP_FAILURE));
                return;
            }
            plugin.getServer().getScheduler().runTask(plugin, () -> player.sendMessage(Component.text()
                    .append(Component.text("Equipped ", NamedTextColor.GRAY))
                    .append(Component.text(descriptor.displayName(), NamedTextColor.GOLD)
                            .decoration(TextDecoration.BOLD, true))
                    .append(Component.text(" (" + descriptor.id() + ")", NamedTextColor.DARK_GRAY))
                    .build()));
        });
        return Command.SINGLE_SUCCESS;
    }

    private CompletionStage<Void> equipCosmetic(Player player, CosmeticDescriptor descriptor) {
        UUID playerId = player.getUniqueId();
        CosmeticCategory category = CosmeticKeys.categoryFromId(descriptor.id()).orElse(null);
        if (category == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Unknown category for " + descriptor.id()));
        }
        CompletionStage<Void> action;
        if (category == CosmeticCategory.SUIT) {
            action = equipSuit(playerId, descriptor.id());
        } else {
            CosmeticSlot slot = CosmeticSlot.primaryForCategory(category).orElse(null);
            if (slot == null) {
                return CompletableFuture.failedFuture(new IllegalStateException("No slot mapped for " + descriptor.id()));
            }
            action = equipSingle(playerId, slot, descriptor.id());
        }
        return action.thenRun(() -> runtime.reloadPlayer(playerId));
    }

    private CompletionStage<Void> equipSuit(UUID playerId, String setId) {
        CompletionStage<Void> stage = CompletableFuture.completedFuture(null);
        for (SuitSlot slot : SuitSlot.values()) {
            String pieceKey = CosmeticKeys.suitPieceKey(setId, slot);
            stage = stage.thenCompose(v -> addUnlocked(playerId, pieceKey))
                    .thenCompose(v -> loadoutService.setEquipped(playerId, slot.cosmeticSlot(), pieceKey));
        }
        return stage;
    }

    private CompletionStage<Void> equipSingle(UUID playerId, CosmeticSlot slot, String key) {
        return addUnlocked(playerId, key)
                .thenCompose(v -> loadoutService.setEquipped(playerId, slot, key));
    }

    private CompletionStage<Void> addUnlocked(UUID playerId, String key) {
        return loadoutService.addUnlocked(playerId, key).thenApply(ignored -> null);
    }

    private CompletableFuture<Suggestions> suggestCosmetics(CommandContext<CommandSourceStack> context,
                                                            SuggestionsBuilder builder) {
        if (registry == null) {
            return builder.buildFuture();
        }
        String remaining = builder.getRemainingLowerCase();
        for (CosmeticDescriptor descriptor : registry.descriptors()) {
            String id = descriptor.id();
            if (remaining.isEmpty() || id.startsWith(remaining)) {
                builder.suggest(id);
            }
        }
        return builder.buildFuture();
    }

    private boolean hasStaffPrivileges(CommandSourceStack source) {
        if (!(source.getSender() instanceof Player player)) {
            return true;
        }
        return RankUtils.isStaff(player);
    }

    private Component unknownCosmetic(String rawId) {
        return Component.text("Unknown cosmetic '" + rawId + "'.", NamedTextColor.RED);
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
