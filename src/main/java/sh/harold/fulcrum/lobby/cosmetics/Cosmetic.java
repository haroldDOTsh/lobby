package sh.harold.fulcrum.lobby.cosmetics;

/**
 * Marker interface implemented by all cosmetics. Every cosmetic exposes metadata and a category.
 */
public sealed interface Cosmetic permits SuitSet, ParticleTrailCosmetic, CloakCosmetic, ClickEffectCosmetic {
    CosmeticDescriptor descriptor();

    CosmeticCategory category();

    default String id() {
        return descriptor().id();
    }
}
