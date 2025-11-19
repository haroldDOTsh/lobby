package sh.harold.fulcrum.lobby.cosmetics.loadout;

import sh.harold.fulcrum.lobby.cosmetics.CosmeticSlot;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Snapshot of a player's cosmetic ledger and equipped state.
 */
public record CosmeticLoadout(Set<String> unlocked, Map<CosmeticSlot, String> equipped) {
    public static final CosmeticLoadout EMPTY = new CosmeticLoadout(Set.of(), Map.of());

    public CosmeticLoadout {
        Objects.requireNonNull(unlocked, "unlocked");
        Objects.requireNonNull(equipped, "equipped");
    }

    public static CosmeticLoadout copyOf(Set<String> unlocked, Map<CosmeticSlot, String> equipped) {
        Set<String> unlockedCopy = Collections.unmodifiableSet(new HashSet<>(unlocked));
        Map<CosmeticSlot, String> equippedCopy = Collections.unmodifiableMap(new EnumMap<>(equipped));
        return new CosmeticLoadout(unlockedCopy, equippedCopy);
    }

    public boolean isUnlocked(String key) {
        return unlocked.contains(key);
    }

    public Optional<String> equipped(CosmeticSlot slot) {
        return Optional.ofNullable(equipped.get(slot));
    }

    public boolean isEmpty() {
        return unlocked.isEmpty() && equipped.isEmpty();
    }
}
