package sh.harold.fulcrum.lobby.cosmetics;

import java.util.Locale;
import java.util.Optional;

/**
 * Normalises cosmetic identifiers and flat keys.
 */
public final class CosmeticKeys {
    private CosmeticKeys() {
    }

    public static Optional<CosmeticCategory> categoryFromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        int idx = id.indexOf(':');
        if (idx <= 0) {
            return Optional.empty();
        }
        String prefix = id.substring(0, idx);
        return CosmeticCategory.fromPrefix(prefix);
    }

    public static Optional<String> setIdFromPieceKey(String flatKey) {
        if (flatKey == null) {
            return Optional.empty();
        }
        String[] parts = flatKey.split(":");
        if (parts.length < 3) {
            return Optional.empty();
        }
        if (!CosmeticCategory.SUIT.prefix().equalsIgnoreCase(parts[0])) {
            return Optional.empty();
        }
        return Optional.of(parts[0].toLowerCase(Locale.ROOT) + ":" + parts[1].toLowerCase(Locale.ROOT));
    }

    public static Optional<SuitSlot> suitSlotFromPieceKey(String flatKey) {
        if (flatKey == null) {
            return Optional.empty();
        }
        String[] parts = flatKey.split(":");
        if (parts.length < 3) {
            return Optional.empty();
        }
        return SuitSlot.fromStorageSuffix(parts[2]);
    }

    public static String suitPieceKey(String setId, SuitSlot slot) {
        if (setId == null || slot == null) {
            throw new IllegalArgumentException("setId and slot must be provided");
        }
        return normalizeId(setId) + ":" + slot.storageSuffix();
    }

    public static String normalizeId(String raw) {
        if (raw == null) {
            return null;
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }
}
