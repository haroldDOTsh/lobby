package sh.harold.fulcrum.lobby.cosmetics.examples;

import org.bukkit.Material;
import org.bukkit.Particle;
import sh.harold.fulcrum.lobby.cosmetics.CosmeticDescriptor;
import sh.harold.fulcrum.lobby.cosmetics.CosmeticMetadata;
import sh.harold.fulcrum.lobby.cosmetics.CosmeticRarity;
import sh.harold.fulcrum.lobby.cosmetics.ParticleTrailCosmetic;
import sh.harold.fulcrum.lobby.cosmetics.runtime.ParticleInstruction;
import sh.harold.fulcrum.lobby.cosmetics.runtime.PlayerContext;
import sh.harold.fulcrum.lobby.cosmetics.runtime.Vector3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Emits counter-rotating flame helixes around the player.
 */
@CosmeticMetadata(
        id = "trail:ember_helix",
        name = "Ember Helix",
        description = "Twin spirals of embers orbit your steps.",
        icon = Material.BLAZE_POWDER,
        rarity = CosmeticRarity.EPIC
)
public final class EmberHelixTrail extends ParticleTrailCosmetic {

    public EmberHelixTrail(CosmeticDescriptor descriptor) {
        super(descriptor);
    }

    @Override
    public List<ParticleInstruction> tick(PlayerContext ctx) {
        double time = ctx.epochMillis() * 0.004D;
        double radius = 0.6D;
        double verticalOscillation = Math.sin(time) * 0.1D;
        List<ParticleInstruction> instructions = new ArrayList<>(4);
        Vector3d origin = ctx.position().add(new Vector3d(0.0D, 0.2D, 0.0D));

        for (int i = 0; i < 2; i++) {
            double angle = time * 2.0D + (Math.PI * i);
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            Vector3d offset = new Vector3d(x, 0.2D + verticalOscillation * (i == 0 ? 1 : -1), z);
            instructions.add(new ParticleInstruction(
                    Particle.FLAME,
                    ctx.worldId(),
                    origin.add(offset),
                    new Vector3d(0.0D, 0.02D, 0.0D),
                    2,
                    0.0D,
                    null,
                    false
            ));
        }
        return instructions;
    }
}
