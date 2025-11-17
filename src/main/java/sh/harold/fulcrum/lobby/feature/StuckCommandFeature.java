package sh.harold.fulcrum.lobby.feature;

import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.fulcrum.lobby.system.LobbyFeature;
import sh.harold.fulcrum.lobby.system.LobbyFeatureContext;

/**
 * Provides a player accessible `/stuck` command that returns them to the lobby spawn.
 */
public final class StuckCommandFeature implements LobbyFeature, Listener {
    private static final double FALL_THRESHOLD = -64.0;
    private static final Component PLAYER_ONLY_MESSAGE = Component.text(
            "Only players can use this command.",
            NamedTextColor.RED
    );

    private static final Component ERROR_MESSAGE = Component.text(
            "Teleport failed, please try again.",
            NamedTextColor.RED
    );

    private static final Component ZOOP_MESSAGE = Component.text("Zoop!", NamedTextColor.GRAY);

    private JavaPlugin plugin;

    @Override
    public String id() {
        return "stuck-command";
    }

    @Override
    public int priority() {
        return 82;
    }

    @Override
    public void initialize(LobbyFeatureContext context) {
        this.plugin = context.plugin();
        PluginManager pluginManager = plugin.getServer().getPluginManager();
        pluginManager.registerEvents(this, plugin);
        registerCommandHandler();
        context.logger().info("Stuck command feature initialised.");
    }

    @Override
    public void shutdown(LobbyFeatureContext context) {
        HandlerList.unregisterAll(this);
        plugin = null;
    }

    private void registerCommandHandler() {
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands commands = event.registrar();
            commands.register(
                    Commands.literal("stuck")
                            .executes(context -> handleCommand(context.getSource()))
                            .build(),
                    "Teleport back to the lobby spawn"
            );
        });
    }

    private int handleCommand(CommandSourceStack sourceStack) {
        if (!(sourceStack.getSender() instanceof Player player)) {
            sourceStack.getSender().sendMessage(PLAYER_ONLY_MESSAGE);
            return Command.SINGLE_SUCCESS;
        }

        teleportToSpawn(player);
        return Command.SINGLE_SUCCESS;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null || to.getY() >= FALL_THRESHOLD) {
            return;
        }
        Location from = event.getFrom();
        if (from != null && from.getY() < FALL_THRESHOLD) {
            return;
        }
        teleportToSpawn(event.getPlayer());
    }

    private void teleportToSpawn(Player player) {
        World world = player.getWorld();
        Location spawn = world.getSpawnLocation();

        player.teleportAsync(spawn).handle((success, throwable) -> {
            if (Boolean.TRUE.equals(success) && throwable == null) {
                player.sendMessage(ZOOP_MESSAGE);
                return null;
            }
            player.sendMessage(ERROR_MESSAGE);
            return null;
        });
    }
}
