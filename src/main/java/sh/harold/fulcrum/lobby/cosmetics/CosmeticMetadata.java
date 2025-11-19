package sh.harold.fulcrum.lobby.cosmetics;

import org.bukkit.Material;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Describes a cosmetic class and is used by {@link sh.harold.fulcrum.lobby.cosmetics.registry.CosmeticRegistry}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface CosmeticMetadata {
    /**
     * Globally unique identifier. For example {@code trail:helix} or {@code suit:phoenix}.
     */
    String id();

    /**
     * Human friendly name shown in menus.
     */
    String name();

    /**
     * Showcase item used in menus.
     */
    Material icon();

    /**
     * Rarity associated with this cosmetic.
     */
    CosmeticRarity rarity();

    /**
     * Long form description used for lore.
     */
    String description();

    /**
     * Optional limited time lore snippet injected straight into the tooltip.
     */
    String limited() default "";
}
