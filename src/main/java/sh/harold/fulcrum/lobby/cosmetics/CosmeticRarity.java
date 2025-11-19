package sh.harold.fulcrum.lobby.cosmetics;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Ordered rarity tiers used for cosmetics.
 */
public enum CosmeticRarity {
    COMMON(NamedTextColor.WHITE, 0),
    UNCOMMON(NamedTextColor.DARK_AQUA, 1),
    RARE(NamedTextColor.BLUE, 2),
    EPIC(NamedTextColor.DARK_PURPLE, 3),
    LEGENDARY(NamedTextColor.GOLD, 4),
    SPECIAL(NamedTextColor.LIGHT_PURPLE, 5),
    ADMIN(NamedTextColor.RED, 6);

    private final Component display;
    private final NamedTextColor baseColor;
    private final int progressionRank;

    CosmeticRarity(NamedTextColor baseColor, int progressionRank) {
        this.baseColor = baseColor;
        this.progressionRank = progressionRank;
        this.display = Component.text(name(), baseColor)
                .decoration(TextDecoration.BOLD, true);
    }

    public Component displayName() {
        return display;
    }

    public NamedTextColor baseColor() {
        return baseColor;
    }

    public int progressionRank() {
        return progressionRank;
    }
}
