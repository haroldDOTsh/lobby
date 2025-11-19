package sh.harold.fulcrum.lobby.cosmetics.loadout;

import sh.harold.fulcrum.common.settings.PlayerSettingsService;
import sh.harold.fulcrum.lobby.cosmetics.CosmeticSlot;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Stores cosmetics inside {@link PlayerSettingsService} using the {@code lobby} scope.
 */
public final class PlayerSettingsLoadoutService implements LoadoutService {
    private static final String COSMETICS_NODE = "cosmetics";
    private static final String UNLOCKED_KEY = "unlocked";
    private static final String EQUIPPED_KEY = "equipped";

    private final PlayerSettingsService.GameSettingsScope scope;

    public PlayerSettingsLoadoutService(PlayerSettingsService.GameSettingsScope scope) {
        this.scope = Objects.requireNonNull(scope, "scope");
    }

    @Override
    public CompletionStage<CosmeticLoadout> loadout(UUID playerId) {
        return loadDocument(playerId).thenApply(Document::toLoadout);
    }

    @Override
    public CompletionStage<Boolean> addUnlocked(UUID playerId, String cosmeticKey) {
        Objects.requireNonNull(cosmeticKey, "cosmeticKey");
        return loadDocument(playerId).thenCompose(document -> {
            if (!document.unlocked.add(cosmeticKey)) {
                return CompletableFuture.completedFuture(Boolean.FALSE);
            }
            return persist(playerId, document).thenApply(v -> Boolean.TRUE);
        });
    }

    @Override
    public CompletionStage<Boolean> removeUnlocked(UUID playerId, String cosmeticKey) {
        Objects.requireNonNull(cosmeticKey, "cosmeticKey");
        return loadDocument(playerId).thenCompose(document -> {
            if (!document.unlocked.remove(cosmeticKey)) {
                return CompletableFuture.completedFuture(Boolean.FALSE);
            }
            document.cleanupEquipped();
            return persist(playerId, document).thenApply(v -> Boolean.TRUE);
        });
    }

    @Override
    public CompletionStage<Void> setEquipped(UUID playerId, CosmeticSlot slot, String cosmeticKey) {
        Objects.requireNonNull(slot, "slot");
        return loadDocument(playerId).thenCompose(document -> {
            if (cosmeticKey == null || cosmeticKey.isBlank()) {
                document.equipped.remove(slot);
            } else {
                document.equipped.put(slot, cosmeticKey);
            }
            return persist(playerId, document);
        });
    }

    @Override
    public CompletionStage<Void> clearEquipped(UUID playerId, CosmeticSlot slot) {
        Objects.requireNonNull(slot, "slot");
        return loadDocument(playerId).thenCompose(document -> {
            document.equipped.remove(slot);
            return persist(playerId, document);
        });
    }

    @Override
    public CompletionStage<Void> clearAll(UUID playerId) {
        return scope.remove(playerId, COSMETICS_NODE);
    }

    private CompletionStage<Document> loadDocument(UUID playerId) {
        return scope.get(playerId, COSMETICS_NODE, Map.class)
                .thenApply(optional -> optional.map(PlayerSettingsLoadoutService::asDocument)
                        .orElseGet(Document::new));
    }

    private CompletionStage<Void> persist(UUID playerId, Document document) {
        if (document.isEmpty()) {
            return scope.remove(playerId, COSMETICS_NODE);
        }
        return scope.set(playerId, COSMETICS_NODE, document.toRaw());
    }

    @SuppressWarnings("unchecked")
    private static Document asDocument(Map<String, Object> raw) {
        Document document = new Document();
        if (raw == null || raw.isEmpty()) {
            return document;
        }
        Object unlockedValue = raw.get(UNLOCKED_KEY);
        if (unlockedValue instanceof List<?> unlockedList) {
            for (Object entry : unlockedList) {
                if (entry instanceof String key && !key.isBlank()) {
                    document.unlocked.add(key);
                }
            }
        }
        Object equippedValue = raw.get(EQUIPPED_KEY);
        if (equippedValue instanceof Map<?, ?> equippedMap) {
            for (Map.Entry<?, ?> entry : equippedMap.entrySet()) {
                if (!(entry.getKey() instanceof String storageKey)) {
                    continue;
                }
                CosmeticSlot.fromStorageKey(storageKey).ifPresent(slot -> {
                    Object flatKey = entry.getValue();
                    if (flatKey instanceof String cosmeticKey && !cosmeticKey.isBlank()) {
                        document.equipped.put(slot, cosmeticKey);
                    }
                });
            }
        }
        return document;
    }

    private static final class Document {
        private final Set<String> unlocked = new HashSet<>();
        private final Map<CosmeticSlot, String> equipped = new EnumMap<>(CosmeticSlot.class);

        private Document() {
        }

        CosmeticLoadout toLoadout() {
            return CosmeticLoadout.copyOf(unlocked, equipped);
        }

        Map<String, Object> toRaw() {
            Map<String, Object> raw = new HashMap<>();
            if (!unlocked.isEmpty()) {
                raw.put(UNLOCKED_KEY, new ArrayList<>(unlocked));
            }
            if (!equipped.isEmpty()) {
                Map<String, Object> equippedRaw = new HashMap<>();
                equipped.forEach((slot, value) -> equippedRaw.put(slot.storageKey(), value));
                raw.put(EQUIPPED_KEY, equippedRaw);
            }
            return raw;
        }

        void cleanupEquipped() {
            equipped.entrySet().removeIf(entry -> !unlocked.contains(entry.getValue()));
        }

        boolean isEmpty() {
            return unlocked.isEmpty() && equipped.isEmpty();
        }
    }
}
