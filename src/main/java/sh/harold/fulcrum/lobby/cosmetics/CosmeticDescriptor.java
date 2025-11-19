package sh.harold.fulcrum.lobby.cosmetics;

import org.bukkit.Material;

import java.util.Objects;
import java.util.Optional;

/**
 * Synthesised descriptor derived from {@link CosmeticMetadata}.
 */
public record CosmeticDescriptor(
        String id,
        String displayName,
        String description,
        Material icon,
        CosmeticRarity rarity,
        Optional<String> limitedLore
) {

    public CosmeticDescriptor {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(icon, "icon");
        Objects.requireNonNull(rarity, "rarity");
        limitedLore = limitedLore == null ? Optional.empty() : limitedLore;
    }

    public static CosmeticDescriptor fromMetadata(CosmeticMetadata metadata) {
        Objects.requireNonNull(metadata, "metadata");
        String limited = metadata.limited();
        Optional<String> limitedLore = limited == null || limited.isBlank()
                ? Optional.empty()
                : Optional.of(limited);
        return new CosmeticDescriptor(
                metadata.id(),
                metadata.name(),
                metadata.description(),
                metadata.icon(),
                metadata.rarity(),
                limitedLore
        );
    }
}
