package sh.harold.fulcrum.lobby.cosmetics;

import sh.harold.fulcrum.lobby.cosmetics.runtime.ParticleInstruction;
import sh.harold.fulcrum.lobby.cosmetics.runtime.PlayerContext;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Idle cloak that triggers when the player has been stationary for a short dwell period.
 */
public abstract non-sealed class CloakCosmetic implements Cosmetic {
    private final CosmeticDescriptor descriptor;

    protected CloakCosmetic(CosmeticDescriptor descriptor) {
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
    }

    @Override
    public CosmeticDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public CosmeticCategory category() {
        return CosmeticCategory.CLOAK;
    }

    public void onIdleStart(PlayerContext ctx) {
        // No-op by default.
    }

    public void onCancel(PlayerContext ctx) {
        // No-op by default.
    }

    public List<ParticleInstruction> tick(PlayerContext ctx) {
        return Collections.emptyList();
    }
}
