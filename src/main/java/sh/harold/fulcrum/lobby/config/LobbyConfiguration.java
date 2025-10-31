package sh.harold.fulcrum.lobby.config;

import sh.harold.fulcrum.api.slot.SlotFamilyDescriptor;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class LobbyConfiguration {
    public static final String DEFAULT_FAMILY_ID = "lobby.main";
    public static final String DEFAULT_MAP_ID = "main_lobby";
    public static final String DEFAULT_JOIN_MESSAGE = "&7%player% has joined the lobby!";
    public static final String DEFAULT_DONATOR_JOIN_MESSAGE = "&6>>> %player% has joined the lobby! <<<";
    public static final String DEFAULT_TOP_DONATOR_JOIN_MESSAGE = "&d[MVP++] %player% has entered!";
    private static final List<String> DEFAULT_DONATOR_RANKS = List.of(
            "DONATOR_1",
            "DONATOR_2",
            "DONATOR_3",
            "DONATOR_4"
    );

    private final String familyId;
    private final String mapId;
    private final int minPlayers;
    private final int maxPlayers;
    private final int playerEquivalentFactor;
    private final Map<String, String> descriptorMetadata;
    private final String joinDefaultMessage;
    private final String joinDonatorMessage;
    private final Set<String> joinDonatorRanks;
    private final String joinTopDonatorMessage;

    private LobbyConfiguration(Builder builder) {
        this.familyId = builder.familyId;
        this.mapId = builder.mapId;
        this.minPlayers = builder.minPlayers;
        this.maxPlayers = builder.maxPlayers;
        this.playerEquivalentFactor = builder.playerEquivalentFactor;
        this.descriptorMetadata = Collections.unmodifiableMap(new LinkedHashMap<>(builder.metadata));
        this.joinDefaultMessage = builder.joinDefaultMessage;
        this.joinDonatorMessage = builder.joinDonatorMessage;
        this.joinDonatorRanks = Collections.unmodifiableSet(new LinkedHashSet<>(builder.joinDonatorRanks));
        this.joinTopDonatorMessage = builder.joinTopDonatorMessage;
    }

    public static LobbyConfiguration defaults() {
        return builder()
                .familyId(DEFAULT_FAMILY_ID)
                .mapId(DEFAULT_MAP_ID)
                .minPlayers(0)
                .maxPlayers(120)
                .playerEquivalentFactor(10)
                .putMetadata("mapId", DEFAULT_MAP_ID)
                .joinDefaultMessage(DEFAULT_JOIN_MESSAGE)
                .joinDonatorMessage(DEFAULT_DONATOR_JOIN_MESSAGE)
                .joinDonatorRanks(DEFAULT_DONATOR_RANKS)
                .joinTopDonatorMessage(DEFAULT_TOP_DONATOR_JOIN_MESSAGE)
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

    public String joinDefaultMessage() {
        return joinDefaultMessage;
    }

    public String joinDonatorMessage() {
        return joinDonatorMessage;
    }

    public Set<String> joinDonatorRanks() {
        return joinDonatorRanks;
    }

    public String joinTopDonatorMessage() {
        return joinTopDonatorMessage;
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
                .joinDefaultMessage(joinDefaultMessage)
                .joinDonatorMessage(joinDonatorMessage)
                .joinDonatorRanks(joinDonatorRanks)
                .joinTopDonatorMessage(joinTopDonatorMessage)
                .addAllMetadata(descriptorMetadata);
    }

    public static final class Builder {
        private String familyId = DEFAULT_FAMILY_ID;
        private String mapId = DEFAULT_MAP_ID;
        private int minPlayers = 0;
        private int maxPlayers = 120;
        private int playerEquivalentFactor = 10;
        private final Map<String, String> metadata = new LinkedHashMap<>();
        private String joinDefaultMessage = DEFAULT_JOIN_MESSAGE;
        private String joinDonatorMessage = DEFAULT_DONATOR_JOIN_MESSAGE;
        private final Set<String> joinDonatorRanks = new LinkedHashSet<>(DEFAULT_DONATOR_RANKS);
        private String joinTopDonatorMessage = DEFAULT_TOP_DONATOR_JOIN_MESSAGE;

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

        public Builder joinDefaultMessage(String message) {
            if (message != null && !message.isBlank()) {
                this.joinDefaultMessage = message;
            }
            return this;
        }

        public Builder joinDonatorMessage(String message) {
            if (message != null && !message.isBlank()) {
                this.joinDonatorMessage = message;
            }
            return this;
        }

        public Builder joinDonatorRanks(Iterable<String> ranks) {
            if (ranks == null) {
                return this;
            }
            this.joinDonatorRanks.clear();
            for (String rank : ranks) {
                if (rank != null && !rank.isBlank()) {
                    this.joinDonatorRanks.add(rank.trim().toUpperCase());
                }
            }
            if (this.joinDonatorRanks.isEmpty()) {
                this.joinDonatorRanks.addAll(DEFAULT_DONATOR_RANKS);
            }
            return this;
        }

        public Builder joinTopDonatorMessage(String message) {
            if (message != null && !message.isBlank()) {
                this.joinTopDonatorMessage = message;
            }
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
