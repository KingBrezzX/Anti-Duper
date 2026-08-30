package xyz.zyrex.bedrockantidupe;

import java.util.UUID;

/**
 * Immutable record of an economy transaction.
 *
 * Used to connect item transactions with money transactions
 * so that money originating from a confirmed dupe can be
 * identified and rolled back using the exact recorded value.
 */
public record EconomyTransaction(

        UUID transactionId,

        UUID playerId,

        Type type,

        String itemType,

        int amount,

        double unitPrice,

        double totalPrice,

        String shop,

        String sourceTransactionId,

        long timestamp

) {

    public enum Type {
        BUY,
        SELL,
        SELL_ALL,
        ORDER,
        OTHER
    }

    public EconomyTransaction {

        if (transactionId == null) {
            transactionId = UUID.randomUUID();
        }

        if (playerId == null) {
            throw new IllegalArgumentException(
                    "playerId cannot be null"
            );
        }

        if (type == null) {
            type = Type.OTHER;
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

        if (unitPrice < 0) {

            throw new IllegalArgumentException(
                    "unitPrice cannot be negative"
            );
        }

        if (totalPrice < 0) {

            throw new IllegalArgumentException(
                    "totalPrice cannot be negative"
            );
        }

        if (shop == null) {
            shop = "UNKNOWN";
        }

        if (sourceTransactionId == null) {
            sourceTransactionId = "";
        }

        if (timestamp <= 0) {
            timestamp = System.currentTimeMillis();
        }
    }

    /**
     * Creates a normal SELL transaction.
     *
     * totalPrice is calculated from amount × unitPrice.
     */
    public static EconomyTransaction sell(
            UUID playerId,
            String itemType,
            int amount,
            double unitPrice,
            String shop,
            String sourceTransactionId
    ) {

        double total =
                amount * unitPrice;

        return new EconomyTransaction(
                UUID.randomUUID(),
                playerId,
                Type.SELL,
                itemType,
                amount,
                unitPrice,
                total,
                shop,
                sourceTransactionId,
                System.currentTimeMillis()
        );
    }

    /**
     * Creates a SELL ALL transaction.
     */
    public static EconomyTransaction sellAll(
            UUID playerId,
            String itemType,
            int amount,
            double unitPrice,
            String shop,
            String sourceTransactionId
    ) {

        double total =
                amount * unitPrice;

        return new EconomyTransaction(
                UUID.randomUUID(),
                playerId,
                Type.SELL_ALL,
                itemType,
                amount,
                unitPrice,
                total,
                shop,
                sourceTransactionId,
                System.currentTimeMillis()
        );
    }

    /**
     * Creates a BUY transaction.
     */
    public static EconomyTransaction buy(
            UUID playerId,
            String itemType,
            int amount,
            double unitPrice,
            String shop,
            String sourceTransactionId
    ) {

        double total =
                amount * unitPrice;

        return new EconomyTransaction(
                UUID.randomUUID(),
                playerId,
                Type.BUY,
                itemType,
                amount,
                unitPrice,
                total,
                shop,
                sourceTransactionId,
                System.currentTimeMillis()
        );
    }

    /**
     * Creates an ORDER transaction.
     */
    public static EconomyTransaction order(
            UUID playerId,
            String itemType,
            int amount,
            double unitPrice,
            String shop,
            String sourceTransactionId
    ) {

        double total =
                amount * unitPrice;

        return new EconomyTransaction(
                UUID.randomUUID(),
                playerId,
                Type.ORDER,
                itemType,
                amount,
                unitPrice,
                total,
                shop,
                sourceTransactionId,
                System.currentTimeMillis()
        );
    }

    /**
     * Returns whether this transaction generated money
     * for the player.
     */
    public boolean generatedMoney() {

        return type == Type.SELL
                || type == Type.SELL_ALL;
    }

    /**
     * Returns whether this transaction represents
     * an item purchase.
     */
    public boolean purchasedItems() {

        return type == Type.BUY
                || type == Type.ORDER;
    }

    /**
     * Returns whether the transaction can potentially
     * participate in an economy rollback.
     */
    public boolean rollbackEligible() {

        return generatedMoney()
                && totalPrice > 0
                && amount > 0;
    }

    /**
     * Returns the normalized item identity.
     */
    public String itemIdentity() {

        return itemType
                .trim()
                .toUpperCase();
    }

    /**
     * Returns whether this transaction concerns
     * any color of shulker box.
     */
    public boolean isShulkerTransaction() {

        return itemIdentity()
                .endsWith(
                        "_SHULKER_BOX"
                );
    }

    /**
     * Checks whether this transaction belongs
     * to the specified player.
     */
    public boolean belongsTo(
            UUID uuid
    ) {

        return playerId.equals(uuid);
    }

    /**
     * Checks whether this transaction came from
     * a specific item transaction.
     */
    public boolean cameFrom(
            String sourceId
    ) {

        if (sourceId == null
                || sourceId.isBlank()) {

            return false;
        }

        return sourceId.equals(
                sourceTransactionId
        );
    }

    /**
     * Returns the exact expected value.
     *
     * The stored totalPrice is preferred so the rollback
     * uses the value recorded at transaction time.
     */
    public double exactValue() {

        return totalPrice;
    }

    /**
     * Creates a copy with a different source transaction.
     */
    public EconomyTransaction withSourceTransaction(
            String sourceId
    ) {

        return new EconomyTransaction(
                transactionId,
                playerId,
                type,
                itemType,
                amount,
                unitPrice,
                totalPrice,
                shop,
                sourceId,
                timestamp
        );
    }

    /**
     * Creates a copy with a different amount.
     *
     * The total value is recalculated using the original
     * unit price.
     */
    public EconomyTransaction withAmount(
            int newAmount
    ) {

        if (newAmount < 0) {

            throw new IllegalArgumentException(
                    "newAmount cannot be negative"
            );
        }

        double newTotal =
                newAmount * unitPrice;

        return new EconomyTransaction(
                transactionId,
                playerId,
                type,
                itemType,
                newAmount,
                unitPrice,
                newTotal,
                shop,
                sourceTransactionId,
                timestamp
        );
    }
              }
