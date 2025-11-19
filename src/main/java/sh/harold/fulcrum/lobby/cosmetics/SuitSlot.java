package sh.harold.fulcrum.lobby.cosmetics;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * Slots that belong to a {@link SuitSet}.
 */
public enum SuitSlot {
    HELMET("head", CosmeticSlot.SUIT_HELMET),
    CHEST("chest", CosmeticSlot.SUIT_CHEST),
    LEGGINGS("leggings", CosmeticSlot.SUIT_LEGGINGS),
    BOOTS("boots", CosmeticSlot.SUIT_BOOTS);

    private final String storageSuffix;
    private final CosmeticSlot cosmeticSlot;

    SuitSlot(String storageSuffix, CosmeticSlot cosmeticSlot) {
        this.storageSuffix = storageSuffix;
        this.cosmeticSlot = cosmeticSlot;
    }

    public String storageSuffix() {
        return storageSuffix;
    }

    public CosmeticSlot cosmeticSlot() {
        return cosmeticSlot;
    }

    public static Optional<SuitSlot> fromStorageSuffix(String suffix) {
        if (suffix == null || suffix.isBlank()) {
            return Optional.empty();
        }
        String normalized = suffix.toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(slot -> slot.storageSuffix.equals(normalized))
                .findFirst();
    }
}
