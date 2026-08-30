package xyz.zyrex.bedrockantidupe;

import java.util.UUID;

/**
 * Immutable information describing the tracked provenance
 * of an item stack involved in an anti-dupe transaction.
 *
 * This class does not claim that an item is duplicated by itself.
 * It provides evidence that can be compared by the detector
 * and transaction ledger.
 */
public record ItemProvenance(

        UUID provenanceId,

        UUID playerId,

        String itemType,

        int amount,

        String transactionId,

        String containerType,

        String world,

        int x,

        int y,

        int z,

        long timestamp

) {

    public ItemProvenance {

        if (provenanceId == null) {
            provenanceId = UUID.randomUUID();
        }

        if (itemType == null
                || itemType.isBlank()) {

            throw new IllegalArgumentException(
                    "itemType cannot be blank"
            );
        }

        if (amount < 0) {

            throw new IllegalArgumentException(
                    "amount cannot be negative"
            );
        }

        if (transactionId == null) {
            transactionId = "";
        }

        if (containerType == null) {
            containerType = "UNKNOWN";
        }

        if (world == null) {
            world = "UNKNOWN";
        }

        if (timestamp <= 0) {
            timestamp = System.currentTimeMillis();
        }
    }

    /**
     * Creates provenance information without a physical location.
     */
    public static ItemProvenance create(
            UUID playerId,
            String itemType,
            int amount,
            String transactionId,
            String containerType
    ) {

        return new ItemProvenance(
                UUID.randomUUID(),
                playerId,
                itemType,
                amount,
                transactionId,
                containerType,
                "UNKNOWN",
                0,
                0,
                0,
                System.currentTimeMillis()
        );
    }

    /**
     * Creates provenance information with a location.
     */
    public static ItemProvenance create(
            UUID playerId,
            String itemType,
            int amount,
            String transactionId,
            String containerType,
            String world,
            int x,
            int y,
            int z
    ) {

        return new ItemProvenance(
                UUID.randomUUID(),
                playerId,
                itemType,
                amount,
                transactionId,
                containerType,
                world,
                x,
                y,
                z,
                System.currentTimeMillis()
        );
    }

    /**
     * Returns whether this record belongs to a player.
     */
    public boolean belongsTo(
            UUID uuid
    ) {

        return playerId != null
                && playerId.equals(uuid);
    }

    /**
     * Returns whether this provenance record references
     * a specific transaction.
     */
    public boolean belongsToTransaction(
            String id
    ) {

        if (id == null
                || transactionId == null) {

            return false;
        }

        return transactionId.equals(id);
    }

    /**
     * Returns a stable item identity.
     *
     * The amount is intentionally excluded because the same
     * item type can legitimately appear in different stack sizes.
     */
    public String itemIdentity() {

        return itemType.toUpperCase();
    }

    /**
     * Returns whether this is a shulker box of any color.
     */
    public boolean isShulkerBox() {

        String normalized =
                itemType
                        .toUpperCase()
                        .trim();

        return normalized.endsWith(
                "_SHULKER_BOX"
        );
    }

    /**
     * Returns whether this record has a known physical location.
     */
    public boolean hasLocation() {

        return world != null
                && !world.isBlank()
                && !world.equalsIgnoreCase(
                        "UNKNOWN"
                );
    }

    /**
     * Returns a readable location string.
     */
    public String locationString() {

        if (!hasLocation()) {
            return "UNKNOWN";
        }

        return world
                + " "
                + x
                + ","
                + y
                + ","
                + z;
    }

    /**
     * Creates a copy with a different amount.
     *
     * Useful when the detector identifies only a subset
     * of an observed stack as suspicious.
     */
    public ItemProvenance withAmount(
            int newAmount
    ) {

        if (newAmount < 0) {

            throw new IllegalArgumentException(
                    "newAmount cannot be negative"
            );
        }

        return new ItemProvenance(
                provenanceId,
                playerId,
                itemType,
                newAmount,
                transactionId,
                containerType,
                world,
                x,
                y,
                z,
                timestamp
        );
    }

    /**
     * Creates a copy associated with another transaction.
     */
    public ItemProvenance withTransaction(
            String newTransactionId
    ) {

        return new ItemProvenance(
                provenanceId,
                playerId,
                itemType,
                amount,
                newTransactionId,
                containerType,
                world,
                x,
                y,
                z,
                timestamp
        );
    }

    /**
     * Creates a copy with a new location.
     */
    public ItemProvenance withLocation(
            String newWorld,
            int newX,
            int newY,
            int newZ
    ) {

        return new ItemProvenance(
                provenanceId,
                playerId,
                itemType,
                amount,
                transactionId,
                containerType,
                newWorld,
                newX,
                newY,
                newZ,
                timestamp
        );
    }
    }
