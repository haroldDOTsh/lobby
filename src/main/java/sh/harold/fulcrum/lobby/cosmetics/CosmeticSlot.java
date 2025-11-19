package sh.harold.fulcrum.lobby.cosmetics;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * Slots that can contain an equipped cosmetic.
 */
public enum CosmeticSlot {
    SUIT_HELMET("suit_helmet", CosmeticCategory.SUIT),
    SUIT_CHEST("suit_chest", CosmeticCategory.SUIT),
    SUIT_LEGGINGS("suit_leggings", CosmeticCategory.SUIT),
    SUIT_BOOTS("suit_boots", CosmeticCategory.SUIT),
    TRAIL("trail", CosmeticCategory.TRAIL),
    CLOAK("cloak", CosmeticCategory.CLOAK),
    CLICK("click", CosmeticCategory.CLICK);

    private final String storageKey;
    private final CosmeticCategory category;

    CosmeticSlot(String storageKey, CosmeticCategory category) {
        this.storageKey = storageKey;
        this.category = category;
    }

    public String storageKey() {
        return storageKey;
    }

    public CosmeticCategory category() {
        return category;
    }

    public static Optional<CosmeticSlot> fromStorageKey(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(slot -> slot.storageKey.equals(normalized))
                .findFirst();
    }
}
