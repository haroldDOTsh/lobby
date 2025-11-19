package sh.harold.fulcrum.lobby.cosmetics;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * High level category for cosmetics. Used when parsing identifiers such as {@code trail:helix}.
 */
public enum CosmeticCategory {
    SUIT("suit"),
    TRAIL("trail"),
    CLOAK("cloak"),
    CLICK("click");

    private final String prefix;

    CosmeticCategory(String prefix) {
        this.prefix = prefix;
    }

    public String prefix() {
        return prefix;
    }

    public static Optional<CosmeticCategory> fromPrefix(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String normalized = raw.toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(category -> category.prefix.equals(normalized))
                .findFirst();
    }
}
