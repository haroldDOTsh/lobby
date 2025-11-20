package sh.harold.fulcrum.lobby.cosmetics.examples;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import sh.harold.fulcrum.lobby.cosmetics.ClickEffectCosmetic;
import sh.harold.fulcrum.lobby.cosmetics.CosmeticDescriptor;
import sh.harold.fulcrum.lobby.cosmetics.CosmeticMetadata;
import sh.harold.fulcrum.lobby.cosmetics.CosmeticRarity;

/**
 * Emits a celebratory spark burst whenever someone clicks the owner.
 */
@CosmeticMetadata(
        id = "click:spark_burst",
        name = "Spark Burst",
        description = "Firework sparks erupt whenever someone greets you.",
        icon = Material.FIREWORK_ROCKET,
        rarity = CosmeticRarity.RARE
)
public final class SparkBurstClickEffect extends ClickEffectCosmetic {
    private static final Component MESSAGE = Component.text("Spark burst!", NamedTextColor.GOLD);

    public SparkBurstClickEffect(CosmeticDescriptor descriptor) {
        super(descriptor);
    }

    @Override
    public void onClick(Player owner, Player clicker) {
        Location origin = owner.getLocation().clone().add(0.0D, 1.2D, 0.0D);
        owner.getWorld().spawnParticle(Particle.FIREWORK, origin, 35, 0.3D, 0.4D, 0.3D, 0.02D);
        owner.getWorld().playSound(origin, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.8F, 1.2F);
        owner.sendMessage(MESSAGE);
        if (!owner.equals(clicker)) {
            clicker.sendMessage(MESSAGE);
        }
    }
}
