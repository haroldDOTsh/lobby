package sh.harold.fulcrum.lobby.cosmetics.loadout;

import sh.harold.fulcrum.lobby.cosmetics.CosmeticSlot;

import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Abstraction over the persistence layer handling unlock/equip state.
 */
public interface LoadoutService {
    CompletionStage<CosmeticLoadout> loadout(UUID playerId);

    CompletionStage<Boolean> addUnlocked(UUID playerId, String cosmeticKey);

    CompletionStage<Boolean> removeUnlocked(UUID playerId, String cosmeticKey);

    CompletionStage<Void> setEquipped(UUID playerId, CosmeticSlot slot, String cosmeticKey);

    CompletionStage<Void> clearEquipped(UUID playerId, CosmeticSlot slot);

    CompletionStage<Void> clearAll(UUID playerId);
}
