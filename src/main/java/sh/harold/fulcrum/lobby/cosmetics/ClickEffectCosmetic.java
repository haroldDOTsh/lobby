package sh.harold.fulcrum.lobby.cosmetics;

import org.bukkit.entity.Player;

import java.util.Objects;

/**
 * Click reactions triggered when other players interact with the owner.
 */
public abstract non-sealed class ClickEffectCosmetic implements Cosmetic {
    private final CosmeticDescriptor descriptor;

    protected ClickEffectCosmetic(CosmeticDescriptor descriptor) {
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
    }

    @Override
    public CosmeticDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public CosmeticCategory category() {
        return CosmeticCategory.CLICK;
    }

    /**
     * Executes on the main thread when another player left or right clicks the owner.
     */
    public abstract void onClick(Player owner, Player clicker);
}
