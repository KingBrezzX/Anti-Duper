package xyz.zyrex.bedrockantidupe;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import net.milkbowl.vault.economy.Economy;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles economy transactions that are associated with
 * suspicious/duplicated item transactions.
 *
 * IMPORTANT:
 * This class does not blindly remove money.
 * It only rolls back amounts that were previously registered
 * through this manager.
 */
public final class EconomyRollbackManager {

    private final JavaPlugin plugin;

    private final Map<UUID, EconomyTransactionRecord> transactions =
            new ConcurrentHashMap<>();

    private Economy economy;

    public EconomyRollbackManager(
            JavaPlugin plugin
    ) {

        this.plugin = plugin;

        setupEconomy();
    }

    /**
     * Attempts to hook into Vault.
     */
    private void setupEconomy() {

        if (!plugin.getConfig().getBoolean(
                "economy.enabled",
                true
        )) {
            plugin.getLogger().info(
                    "Economy rollback is disabled."
            );
            return;
        }

        if (Bukkit.getPluginManager()
                .getPlugin("Vault") == null) {

            plugin.getLogger().warning(
                    "Vault was not found. "
                            + "Economy rollback is unavailable."
            );

            return;
        }

        RegisteredServiceProvider<Economy> provider =
                Bukkit.getServicesManager()
                        .getRegistration(
                                Economy.class
                        );

        if (provider == null) {

            plugin.getLogger().warning(
                    "No Vault economy provider was found."
            );

            return;
        }

        economy =
                provider.getProvider();

        if (economy != null) {

            plugin.getLogger().info(
                    "Economy provider hooked: "
                            + economy.getName()
            );
        }
    }

    /**
     * Returns whether an economy provider is available.
     */
    public boolean isAvailable() {

        return economy != null;
    }

    /**
     * Records a completed sale.
     *
     * The amount is the number of items sold.
     * Unit price is the actual price paid for one item.
     */
    public UUID recordSale(
            UUID playerId,
            String itemType,
            int amount,
            double unitPrice,
            String shop,
            String sourceTransactionId
    ) {

        if (playerId == null) {
            throw new IllegalArgumentException(
                    "playerId cannot be null"
            );
        }

        if (itemType == null
                || itemType.isBlank()) {

            throw new IllegalArgumentException(
                    "itemType cannot be blank"
            );
        }

        if (amount <= 0) {
            throw new IllegalArgumentException(
                    "amount must be greater than zero"
            );
        }

        if (unitPrice < 0) {
            throw new IllegalArgumentException(
                    "unitPrice cannot be negative"
            );
        }

        UUID transactionId =
                UUID.randomUUID();

        double total =
                unitPrice * amount;

        EconomyTransactionRecord record =
                new EconomyTransactionRecord(
                        transactionId,
                        playerId,
                        itemType,
                        amount,
                        unitPrice,
                        total,
                        shop,
                        sourceTransactionId,
                        System.currentTimeMillis()
                );

        transactions.put(
                transactionId,
                record
        );

        return transactionId;
    }

    /**
     * Returns a transaction by ID.
     */
    public EconomyTransactionRecord getTransaction(
            UUID transactionId
    ) {

        if (transactionId == null) {
            return null;
        }

        return transactions.get(
                transactionId
        );
    }

    /**
     * Roll back a registered sale.
     *
     * Only the recorded amount is considered.
     */
    public RollbackResult rollback(
            UUID transactionId
    ) {

        EconomyTransactionRecord record =
                transactions.get(
                        transactionId
                );

        if (record == null) {

            return RollbackResult.failed(
                    transactionId,
                    0.0,
                    0.0,
                    "Transaction not found."
            );
        }

        if (record.rolledBack()) {

            return RollbackResult.failed(
                    transactionId,
                    record.totalPrice(),
                    0.0,
                    "Transaction was already rolled back."
            );
        }

        if (!isAvailable()) {

            return RollbackResult.failed(
                    transactionId,
                    record.totalPrice(),
                    0.0,
                    "Economy provider unavailable."
            );
        }

        OfflinePlayer player =
                Bukkit.getOfflinePlayer(
                        record.playerId()
                );

        double balance =
                economy.getBalance(
                        player
                );

        double requested =
                record.totalPrice();

        /*
         * Never withdraw more money than the player currently owns.
         *
         * This prevents an accidental negative balance.
         */
        double withdrawAmount =
                Math.min(
                        balance,
                        requested
                );

        if (withdrawAmount <= 0) {

            return RollbackResult.partial(
                    transactionId,
                    requested,
                    0.0,
                    "Player has no available balance."
            );
        }

        EconomyResponse response =
                withdraw(
                        player,
                        withdrawAmount
                );

        if (!response.transactionSuccess()) {

            return RollbackResult.failed(
                    transactionId,
                    requested,
                    0.0,
                    response.errorMessage
            );
        }

        record.markRolledBack(
                withdrawAmount
        );

        saveAuditRecord(
                record,
                withdrawAmount
        );

        if (withdrawAmount < requested) {

            return RollbackResult.partial(
                    transactionId,
                    requested,
                    withdrawAmount,
                    "Player balance was lower than "
                            + "the recorded sale value."
            );
        }

        return RollbackResult.success(
                transactionId,
                requested,
                withdrawAmount
        );
    }

    /**
     * Withdraws money through Vault.
     *
     * This method is isolated so economy-specific
     * handling remains in one place.
     */
    private EconomyResponse withdraw(
            OfflinePlayer player,
            double amount
    ) {

        return new EconomyResponse(
                economy.withdrawPlayer(
                        player,
                        amount
                )
        );
    }

    /**
     * Writes an audit record asynchronously.
     */
    private void saveAuditRecord(
            EconomyTransactionRecord record,
            double withdrawn
    ) {

        if (!plugin.getConfig().getBoolean(
                "economy.audit-log",
                true
        )) {
            return;
        }

        Bukkit.getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {

                            try {

                                String path =
                                        "economy-audit."
                                                + record.transactionId()
                                                .toString();

                                plugin.getConfig()
                                        .set(
                                                path + ".player",
                                                record.playerId()
                                                        .toString()
                                        );

                                plugin.getConfig()
                                        .set(
                                                path + ".item",
                                                record.itemType()
                                        );

                                plugin.getConfig()
                                        .set(
                                                path + ".amount",
                                                record.amount()
                                        );

                                plugin.getConfig()
                                        .set(
                                                path + ".unit-price",
                                                record.unitPrice()
                                        );

                                plugin.getConfig()
                                        .set(
                                                path + ".total",
                                                record.totalPrice()
                                        );

                                plugin.getConfig()
                                        .set(
                                                path + ".withdrawn",
                                                withdrawn
                                        );

                                plugin.getConfig()
                                        .set(
                                                path + ".timestamp",
                                                System.currentTimeMillis()
                                        );

                                plugin.saveConfig();

                            } catch (Exception exception) {

                                plugin.getLogger().warning(
                                        "Failed to save economy audit: "
                                                + exception.getMessage()
                                );
                            }
                        }
                );
    }

    /**
     * Returns all recorded transactions.
     */
    public Map<UUID, EconomyTransactionRecord>
    getTransactions() {

        return transactions;
    }

    /**
     * Clears in-memory transaction data.
     */
    public void clear() {

        transactions.clear();
    }

    /**
     * Immutable sale record with controlled rollback state.
     */
    public static final class EconomyTransactionRecord {

        private final UUID transactionId;
        private final UUID playerId;
        private final String itemType;
        private final int amount;
        private final double unitPrice;
        private final double totalPrice;
        private final String shop;
        private final String sourceTransactionId;
        private final long timestamp;

        private volatile boolean rolledBack;
        private volatile double rolledBackAmount;

        public EconomyTransactionRecord(
                UUID transactionId,
                UUID playerId,
                String itemType,
                int amount,
                double unitPrice,
                double totalPrice,
                String shop,
                String sourceTransactionId,
                long timestamp
        ) {

            this.transactionId =
                    transactionId;

            this.playerId =
                    playerId;

            this.itemType =
                    itemType;

            this.amount =
                    amount;

            this.unitPrice =
                    unitPrice;

            this.totalPrice =
                    totalPrice;

            this.shop =
                    shop;

            this.sourceTransactionId =
                    sourceTransactionId;

            this.timestamp =
                    timestamp;
        }

        public UUID transactionId() {
            return transactionId;
        }

        public UUID playerId() {
            return playerId;
        }

        public String itemType() {
            return itemType;
        }

        public int amount() {
            return amount;
        }

        public double unitPrice() {
            return unitPrice;
        }

        public double totalPrice() {
            return totalPrice;
        }

        public String shop() {
            return shop;
        }

        public String sourceTransactionId() {
            return sourceTransactionId;
        }

        public long timestamp() {
            return timestamp;
        }

        public boolean rolledBack() {
            return rolledBack;
        }

        public double rolledBackAmount() {
            return rolledBackAmount;
        }

        private void markRolledBack(
                double amount
        ) {

            this.rolledBack = true;
            this.rolledBackAmount = amount;
        }
    }

    /**
     * Small adapter around Vault's EconomyResponse.
     */
    private static final class EconomyResponse {

        private final boolean success;
        private final String errorMessage;

        private EconomyResponse(
                net.milkbowl.vault.economy.EconomyResponse response
        ) {

            this.success =
                    response != null
                            && response.transactionSuccess();

            this.errorMessage =
                    response == null
                            ? "No economy response."
                            : response.errorMessage;
        }

        private boolean transactionSuccess() {
            return success;
        }
    }
    }
