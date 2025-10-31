package sh.harold.fulcrum.lobby.config;

import sh.harold.fulcrum.api.slot.SlotFamilyDescriptor;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class LobbyConfiguration {
    public static final String DEFAULT_FAMILY_ID = "lobby.main";
    public static final String DEFAULT_MAP_ID = "main_lobby";

    private final String familyId;
    private final String mapId;
    private final int minPlayers;
    private final int maxPlayers;
    private final int playerEquivalentFactor;
    private final Map<String, String> descriptorMetadata;

    private LobbyConfiguration(Builder builder) {
        this.familyId = builder.familyId;
        this.mapId = builder.mapId;
        this.minPlayers = builder.minPlayers;
        this.maxPlayers = builder.maxPlayers;
        this.playerEquivalentFactor = builder.playerEquivalentFactor;
        this.descriptorMetadata = Collections.unmodifiableMap(new LinkedHashMap<>(builder.metadata));
    }

    public static LobbyConfiguration defaults() {
        return builder()
                .familyId(DEFAULT_FAMILY_ID)
                .mapId(DEFAULT_MAP_ID)
                .minPlayers(0)
                .maxPlayers(120)
                .playerEquivalentFactor(10)
                .putMetadata("mapId", DEFAULT_MAP_ID)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public String familyId() {
        return familyId;
    }

    public String mapId() {
        return mapId;
    }

    public int minPlayers() {
        return minPlayers;
    }

    public int maxPlayers() {
        return maxPlayers;
    }

    public int playerEquivalentFactor() {
        return playerEquivalentFactor;
    }

    public Map<String, String> descriptorMetadata() {
        return descriptorMetadata;
    }

    public SlotFamilyDescriptor toDescriptor() {
        SlotFamilyDescriptor.Builder builder = SlotFamilyDescriptor.builder(familyId, minPlayers, maxPlayers)
                .playerEquivalentFactor(playerEquivalentFactor);
        descriptorMetadata.forEach(builder::putMetadata);
        return builder.build();
    }

    public Builder toBuilder() {
        return builder()
                .familyId(familyId)
                .mapId(mapId)
                .minPlayers(minPlayers)
                .maxPlayers(maxPlayers)
                .playerEquivalentFactor(playerEquivalentFactor)
                .addAllMetadata(descriptorMetadata);
    }

    public static final class Builder {
        private String familyId = DEFAULT_FAMILY_ID;
        private String mapId = DEFAULT_MAP_ID;
        private int minPlayers = 0;
        private int maxPlayers = 120;
        private int playerEquivalentFactor = 10;
        private final Map<String, String> metadata = new LinkedHashMap<>();

        public Builder familyId(String familyId) {
            if (familyId != null && !familyId.isBlank()) {
                this.familyId = familyId.trim();
            }
            return this;
        }

        public Builder mapId(String mapId) {
            if (mapId != null && !mapId.isBlank()) {
                this.mapId = mapId.trim();
            }
            return this;
        }

        public Builder minPlayers(int minPlayers) {
            this.minPlayers = Math.max(0, minPlayers);
            return this;
        }

        public Builder maxPlayers(int maxPlayers) {
            this.maxPlayers = Math.max(1, maxPlayers);
            if (this.maxPlayers < this.minPlayers) {
                this.minPlayers = this.maxPlayers;
            }
            return this;
        }

        public Builder playerEquivalentFactor(int playerEquivalentFactor) {
            this.playerEquivalentFactor = Math.max(10, playerEquivalentFactor);
            return this;
        }

        public Builder putMetadata(String key, String value) {
            if (key != null && value != null) {
                metadata.put(key, value);
            }
            return this;
        }

        public Builder addAllMetadata(Map<String, ?> metadata) {
            if (metadata == null) {
                return this;
            }
            metadata.forEach((key, value) -> {
                if (key != null && value != null) {
                    this.metadata.put(key, Objects.toString(value));
                }
            });
            return this;
        }

        public LobbyConfiguration build() {
            if (metadata.containsKey("mapId")) {
                metadata.put("mapId", metadata.get("mapId").trim());
            } else {
                metadata.put("mapId", mapId);
            }
            return new LobbyConfiguration(this);
        }
    }
}
