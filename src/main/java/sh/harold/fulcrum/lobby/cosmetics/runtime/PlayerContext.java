package sh.harold.fulcrum.lobby.cosmetics.runtime;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.UUID;

/**
 * Thread-safe snapshot of the owner's state that can safely be used off the main thread.
 */
public record PlayerContext(
        UUID playerId,
        UUID worldId,
        Vector3d position,
        Vector3d velocity,
        float yaw,
        float pitch,
        boolean onGround,
        long epochMillis
) {

    public PlayerContext {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(velocity, "velocity");
    }

    public static PlayerContext fromPlayer(Player player, Vector3d previousPosition) {
        Objects.requireNonNull(player, "player");
        Location location = player.getLocation();
        Vector3d current = new Vector3d(location.getX(), location.getY(), location.getZ());
        Vector3d velocity = previousPosition == null ? Vector3d.ZERO : current.subtract(previousPosition);
        UUID worldId = player.getWorld().getUID();
        return new PlayerContext(
                player.getUniqueId(),
                worldId,
                current,
                velocity,
                location.getYaw(),
                location.getPitch(),
                player.isOnGround(),
                System.currentTimeMillis()
        );
    }

    /**
     * Resolves a Bukkit player on the main thread when cosmetic logic needs it.
     */
    public Player resolvePlayer(Server server) {
        Objects.requireNonNull(server, "server");
        return server.getPlayer(playerId);
    }

    public Location toLocation(Server server) {
        Objects.requireNonNull(server, "server");
        World world = server.getWorld(worldId);
        if (world == null) {
            return null;
        }
        return new Location(world, position.x(), position.y(), position.z(), yaw, pitch);
    }
}
