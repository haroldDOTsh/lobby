package sh.harold.fulcrum.lobby.cosmetics.runtime;

import org.bukkit.Particle;

import java.util.Objects;
import java.util.UUID;

/**
 * DTO representing a single particle emission to be flushed on the main thread.
 */
public record ParticleInstruction(
        Particle particle,
        UUID worldId,
        Vector3d position,
        Vector3d offset,
        int count,
        double extra,
        Object data,
        boolean force
) {

    public ParticleInstruction {
        Objects.requireNonNull(particle, "particle");
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(position, "position");
        offset = offset == null ? Vector3d.ZERO : offset;
        if (count <= 0) {
            count = 1;
        }
    }

    public static ParticleInstruction trail(Particle particle, PlayerContext context, Vector3d offset) {
        return new ParticleInstruction(
                particle,
                context.worldId(),
                context.position(),
                offset,
                1,
                0.0D,
                null,
                false
        );
    }
}
