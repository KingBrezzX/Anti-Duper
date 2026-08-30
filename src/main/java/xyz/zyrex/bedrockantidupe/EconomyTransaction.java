package xyz.zyrex.bedrockantidupe;

import java.util.UUID;

/**
 * Immutable record of an economy transaction.
 *
 * The anti-dupe system uses this record to make sure an economy
 * rollback is tied to an actual recorded transaction.
 */
public record EconomyTransaction(

        UUID transactionId,

        UUID playerId,

        double exactValue,

        boolean generatedMoney,

        boolean rollbackEligible,

        String source,

        String itemType,

        int itemAmount

) {

    public EconomyTransaction {

        if (transactionId == null) {
            throw new IllegalArgumentException(
                    "transactionId cannot be null"
            );
        }

        if (playerId == null) {
            throw new IllegalArgumentException(
                    "playerId cannot be null"
            );
        }

        if (!Double.isFinite(exactValue)
                || exactValue < 0.0D) {

            throw new IllegalArgumentException(
                    "exactValue must be a finite positive value"
            );
        }

        if (itemAmount < 0) {
            throw new IllegalArgumentException(
                    "itemAmount cannot be negative"
            );
        }

        source =
                source == null
                        ? "UNKNOWN"
                        : source;

        itemType =
                itemType == null
                        ? "UNKNOWN"
                        : itemType;
    }

    /**
     * Creates a normal recorded sale transaction.
     */
    public static EconomyTransaction sale(
            UUID playerId,
            double amount,
            String source,
            String itemType,
            int itemAmount
    ) {

        return new EconomyTransaction(
                UUID.randomUUID(),
                playerId,
                amount,
                true,
                true,
                source,
                itemType,
                itemAmount
        );
    }

    /**
     * Creates a transaction that must never be rolled back.
     */
    public static EconomyTransaction nonSale(
            UUID playerId,
            double amount,
            String source
    ) {

        return new EconomyTransaction(
                UUID.randomUUID(),
                playerId,
                amount,
                false,
                false,
                source,
                "UNKNOWN",
                0
        );
    }

    /**
     * Returns whether this is a valid money-generating
     * transaction that can be rolled back.
     */
    public boolean isValidRollbackTransaction() {

        return rollbackEligible
                && generatedMoney
                && exactValue > 0.0D;
    }

    /**
     * Returns the amount that may be rolled back.
     */
    public double rollbackAmount() {

        if (!isValidRollbackTransaction()) {
            return 0.0D;
        }

        return exactValue;
    }
}
