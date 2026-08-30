package xyz.zyrex.bedrockantidupe;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central transaction ledger for the anti-dupe system.
 *
 * The ledger keeps short-lived transaction snapshots and
 * provenance information so suspicious inventory changes
 * can be correlated with later shop/economy transactions.
 *
 * It deliberately does NOT assume that every inventory change
 * is a dupe. Detection must be performed by DupeDetector.
 */
public final class TransactionLedger {

    private final BedrockAntiDupe plugin;

    private final Map<UUID, TransactionSnapshot> activeSnapshots =
            new ConcurrentHashMap<>();

    private final Map<String, TransactionRecord> transactions =
            new ConcurrentHashMap<>();

    private final Map<String, ItemProvenance> provenance =
            new ConcurrentHashMap<>();

    public TransactionLedger(
            BedrockAntiDupe plugin
    ) {

        this.plugin = plugin;
    }

    /**
     * Starts tracking an inventory transaction.
     */
    public TransactionSnapshot begin(
            Player player,
            String source
    ) {

        if (player == null) {
            throw new IllegalArgumentException(
                    "player cannot be null"
            );
        }

        String transactionId =
                UUID.randomUUID().toString();

        TransactionSnapshot snapshot =
                createSnapshot(
                        player,
                        transactionId,
                        source
                );

        activeSnapshots.put(
                player.getUniqueId(),
                snapshot
        );

        return snapshot;
    }

    /**
     * Finishes the currently active transaction.
     */
    public TransactionRecord finish(
            Player player
    ) {

        if (player == null) {
            return null;
        }

        TransactionSnapshot before =
                activeSnapshots.remove(
                        player.getUniqueId()
                );

        if (before == null) {
            return null;
        }

        InventorySnapshot after =
                captureInventory(
                        player
                );

        TransactionRecord record =
                new TransactionRecord(
                        before.transactionId(),
                        player.getUniqueId(),
                        before.source(),
                        before.inventory(),
                        after,
                        System.currentTimeMillis()
                );

        transactions.put(
                before.transactionId(),
                record
        );

        return record;
    }

    /**
     * Cancels a transaction without registering an after-state.
     */
    public void cancel(
            Player player
    ) {

        if (player == null) {
            return;
        }

        activeSnapshots.remove(
                player.getUniqueId()
        );
    }

    /**
     * Registers item provenance.
     */
    public void registerProvenance(
            ItemProvenance itemProvenance
    ) {

        if (itemProvenance == null) {
            return;
        }

        provenance.put(
                itemProvenance.provenanceId()
                        .toString(),
                itemProvenance
        );
    }

    /**
     * Gets provenance by ID.
     */
    public ItemProvenance getProvenance(
            String provenanceId
    ) {

        if (provenanceId == null) {
            return null;
        }

        return provenance.get(
                provenanceId
        );
    }

    /**
     * Gets a transaction by ID.
     */
    public TransactionRecord getTransaction(
            String transactionId
    ) {

        if (transactionId == null) {
            return null;
        }

        return transactions.get(
                transactionId
        );
    }

    /**
     * Returns an immutable view of registered transactions.
     */
    public Map<String, TransactionRecord>
    getTransactions() {

        return Collections.unmodifiableMap(
                transactions
        );
    }

    /**
     * Returns the active transaction for a player.
     */
    public TransactionSnapshot getActiveSnapshot(
            UUID playerId
    ) {

        if (playerId == null) {
            return null;
        }

        return activeSnapshots.get(
                playerId
        );
    }

    /**
     * Creates an inventory snapshot.
     */
    private TransactionSnapshot createSnapshot(
            Player player,
            String transactionId,
            String source
    ) {

        return new TransactionSnapshot(
                transactionId,
                player.getUniqueId(),
                source == null
                        ? "UNKNOWN"
                        : source,
                captureInventory(player),
                System.currentTimeMillis()
        );
    }

    /**
     * Captures the player's complete inventory state.
     *
     * This includes:
     * - main inventory
     * - armor
     * - offhand
     *
     * ItemStack objects are cloned so later modifications
     * do not mutate the stored snapshot.
     */
    public InventorySnapshot captureInventory(
            Player player
    ) {

        PlayerInventory inventory =
                player.getInventory();

        List<ItemStack> contents =
                cloneContents(
                        inventory.getStorageContents()
                );

        List<ItemStack> armor =
                cloneContents(
                        inventory.getArmorContents()
                );

        ItemStack offhand =
                inventory.getItemInOffHand();

        if (offhand != null) {
            offhand = offhand.clone();
        }

        return new InventorySnapshot(
                contents,
                armor,
                offhand
        );
    }

    private List<ItemStack> cloneContents(
            ItemStack[] source
    ) {

        List<ItemStack> result =
                new ArrayList<>(
                        source.length
                );

        for (ItemStack item : source) {

            result.add(
                    item == null
                            ? null
                            : item.clone()
            );
        }

        return result;
    }

    /**
     * Calculates the total number of a material
     * in an inventory snapshot.
     */
    public int countMaterial(
            InventorySnapshot snapshot,
            Material material
    ) {

        if (snapshot == null
                || material == null) {

            return 0;
        }

        int total = 0;

        for (ItemStack item :
                snapshot.contents()) {

            if (item != null
                    && item.getType() == material) {

                total += item.getAmount();
            }
        }

        for (ItemStack item :
                snapshot.armor()) {

            if (item != null
                    && item.getType() == material) {

                total += item.getAmount();
            }
        }

        ItemStack offhand =
                snapshot.offhand();

        if (offhand != null
                && offhand.getType() == material) {

            total += offhand.getAmount();
        }

        return total;
    }

    /**
     * Calculates the difference in quantity of one material
     * between two snapshots.
     *
     * Positive = increased.
     * Negative = decreased.
     */
    public int materialDelta(
            InventorySnapshot before,
            InventorySnapshot after,
            Material material
    ) {

        return countMaterial(
                after,
                material
        ) - countMaterial(
                before,
                material
        );
    }

    /**
     * Returns all registered provenance records.
     */
    public List<ItemProvenance>
    getProvenanceRecords() {

        return List.copyOf(
                provenance.values()
        );
    }

    /**
     * Removes old records from memory.
     *
     * This should be called periodically to prevent
     * unlimited memory growth.
     */
    public void cleanup(
            long maxAgeMillis
    ) {

        long now =
                System.currentTimeMillis();

        transactions.entrySet()
                .removeIf(
                        entry -> now
                                - entry.getValue()
                                .timestamp()
                                > maxAgeMillis
                );

        provenance.entrySet()
                .removeIf(
                        entry -> now
                                - entry.getValue()
                                .timestamp()
                                > maxAgeMillis
                );
    }

    /**
     * Clears all in-memory transaction information.
     */
    public void clear() {

        activeSnapshots.clear();
        transactions.clear();
        provenance.clear();
    }

    /**
     * Immutable transaction-start snapshot.
     */
    public record TransactionSnapshot(

            String transactionId,

            UUID playerId,

            String source,

            InventorySnapshot inventory,

            long timestamp

    ) {
    }

    /**
     * Immutable inventory state.
     */
    public record InventorySnapshot(

            List<ItemStack> contents,

            List<ItemStack> armor,

            ItemStack offhand

    ) {

        public InventorySnapshot {

            contents =
                    contents == null
                            ? List.of()
                            : copy(contents);

            armor =
                    armor == null
                            ? List.of()
                            : copy(armor);

            if (offhand != null) {
                offhand =
                        offhand.clone();
            }
        }

        private static List<ItemStack> copy(
                List<ItemStack> source
        ) {

            List<ItemStack> result =
                    new ArrayList<>(
                            source.size()
                    );

            for (ItemStack item : source) {

                result.add(
                        item == null
                                ? null
                                : item.clone()
                );
            }

            return List.copyOf(
                    result
            );
        }
    }

    /**
     * Complete before/after transaction record.
     */
    public record TransactionRecord(

            String transactionId,

            UUID playerId,

            String source,

            InventorySnapshot before,

            InventorySnapshot after,

            long timestamp

    ) {

        /**
         * Returns whether the inventory changed.
         */
        public boolean changed() {

            return !before.equals(
                    after
            );
        }

        /**
         * Returns the change in quantity of a material.
         */
        public int materialDelta(
                Material material
        ) {

            int beforeAmount =
                    count(
                            before,
                            material
                    );

            int afterAmount =
                    count(
                            after,
                            material
                    );

            return afterAmount
                    - beforeAmount;
        }

        private static int count(
                InventorySnapshot snapshot,
                Material material
        ) {

            int total = 0;

            for (ItemStack item :
                    snapshot.contents()) {

                if (item != null
                        && item.getType() == material) {

                    total += item.getAmount();
                }
            }

            for (ItemStack item :
                    snapshot.armor()) {

                if (item != null
                        && item.getType() == material) {

                    total += item.getAmount();
                }
            }

            ItemStack offhand =
                    snapshot.offhand();

            if (offhand != null
                    && offhand.getType() == material) {

                total += offhand.getAmount();
            }

            return total;
        }
    }
    }
