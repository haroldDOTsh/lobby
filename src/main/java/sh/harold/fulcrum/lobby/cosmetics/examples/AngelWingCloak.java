package sh.harold.fulcrum.lobby.cosmetics.examples;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import sh.harold.fulcrum.lobby.cosmetics.CosmeticDescriptor;
import sh.harold.fulcrum.lobby.cosmetics.CosmeticMetadata;
import sh.harold.fulcrum.lobby.cosmetics.CosmeticRarity;

import java.util.Map;

/**
 * Idle cloak that renders luminous angel wings using an 8x8 bitmap.
 */
@CosmeticMetadata(
        id = "cloak:angel_wings",
        name = "Angel Wings",
        description = "Radiant wings shimmer gently when you stand idle.",
        icon = Material.FEATHER,
        rarity = CosmeticRarity.LEGENDARY,
        limited = "<aqua>Seasonal prototype reward")
public final class AngelWingCloak extends PatternCloakCosmetic {
    private static final int[][] PATTERN = new int[][]{
            {0, 1, 2, 2, 2, 1, 0},
            {1, 2, 2, 3, 2, 2, 1},
            {1, 2, 3, 3, 3, 2, 1},
            {1, 2, 3, 3, 3, 2, 1},
            {0, 1, 2, 3, 2, 1, 0},
            {0, 0, 1, 2, 1, 0, 0},
            {0, 0, 0, 1, 0, 0, 0}
    };

    private static final Map<Integer, Pixel> PALETTE = Map.ofEntries(
            Map.entry(1, Pixel.of(Particle.CLOUD)),
            Map.entry(2, Pixel.ofDust(Color.fromRGB(240, 248, 255), 1.0F)),
            Map.entry(3, Pixel.ofDust(Color.fromRGB(120, 200, 255), 1.2F))
    );

    public AngelWingCloak(CosmeticDescriptor descriptor) {
        super(descriptor, PATTERN, PALETTE, 3.0D, 3.0D, 0.45D, 1.25D);
    }
}
