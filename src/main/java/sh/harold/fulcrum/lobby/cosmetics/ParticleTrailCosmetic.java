package sh.harold.fulcrum.lobby.cosmetics;

import sh.harold.fulcrum.lobby.cosmetics.runtime.ParticleInstruction;
import sh.harold.fulcrum.lobby.cosmetics.runtime.PlayerContext;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Trail emitted while the owner is moving around the lobby.
 */
public abstract non-sealed class ParticleTrailCosmetic implements Cosmetic {
    private final CosmeticDescriptor descriptor;

    protected ParticleTrailCosmetic(CosmeticDescriptor descriptor) {
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
    }

    @Override
    public CosmeticDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public CosmeticCategory category() {
        return CosmeticCategory.TRAIL;
    }

    /**
     * Determines whether this trail should emit for the provided context.
     */
    public boolean shouldTrigger(PlayerContext ctx) {
        return ctx.velocity().lengthSquared() > 0.0001D;
    }

    /**
     * Runs asynchronously and returns the particle instructions that should be flushed on the next tick.
     */
    public List<ParticleInstruction> tick(PlayerContext ctx) {
        return Collections.emptyList();
    }
}
