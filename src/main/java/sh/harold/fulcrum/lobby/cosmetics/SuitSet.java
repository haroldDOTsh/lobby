package sh.harold.fulcrum.lobby.cosmetics;

import org.bukkit.inventory.ItemStack;
import sh.harold.fulcrum.lobby.cosmetics.runtime.PlayerContext;

import java.util.Objects;

/**
 * Base class for wardrobe cosmetics that occupy armour slots.
 */
public abstract non-sealed class SuitSet implements Cosmetic {
    private final CosmeticDescriptor descriptor;

    protected SuitSet(CosmeticDescriptor descriptor) {
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
    }

    @Override
    public CosmeticDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public CosmeticCategory category() {
        return CosmeticCategory.SUIT;
    }

    /**
     * Unique identifier for this set (matches the {@link CosmeticDescriptor#id()}).
     */
    public final String setId() {
        return descriptor.id();
    }

    public abstract ItemStack helmet();

    public abstract ItemStack chestplate();

    public abstract ItemStack leggings();

    public abstract ItemStack boots();

    /**
     * Optional hook allowing heavy models to be prepared off-thread prior to use.
     */
    public void prepareAssets() {
        // No-op by default.
    }

    /**
     * Called when the player is wearing all pieces from the same set.
     */
    public void onFullSetStart(PlayerContext ctx) {
        // No-op by default.
    }

    /**
     * Called when a player leaves the full suit state.
     */
    public void onFullSetEnd(PlayerContext ctx) {
        // No-op by default.
    }
}
