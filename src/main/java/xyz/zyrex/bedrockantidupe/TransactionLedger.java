package xyz.zyrex.bedrockantidupe;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TransactionLedger
 *
 * Keeps short-lived snapshots of protected inventories.
 *
 * Purpose:
 * - Detect unexpected item increases.
 * - Detect unexpected protected-container increases.
 * - Compare inventory state before/after risky operations.
 * - Avoid scanning the entire world.
 * - Keep the ledger memory bounded.
 *
 * This class does not punish players by itself.
 * BedrockAntiDupe decides what action to take.
 */
public final class TransactionLedger {

    private static final int DEFAULT_MAX_SNAPSHOT_ITEMS = 256;

    private final Map<UUID, InventorySnapshot> snapshots =
            new ConcurrentHashMap<>();

    private final Map<UUID, Long> transactionIds =
            new ConcurrentHashMap<>();

    private final int maxSnapshotItems;

    public TransactionLedger() {
        this(DEFAULT_MAX_SNAPSHOT_ITEMS);
    }

    public TransactionLedger(int maxSnapshotItems) {

        this.maxSnapshotItems = Math.max(
                32,
                maxSnapshotItems
        );
    }

    /**
     * Creates a snapshot of the player's inventory.
     */
    public InventorySnapshot snapshot(Player player) {

        if (player == null) {
            return null;
        }

        Inventory inventory = player.getInventory();

        Map<Integer, ItemFingerprint> items =
                new HashMap<>();

        ItemStack[] contents =
                inventory.getContents();

        int stored = 0;

        for (int slot = 0;
             slot < contents.length && stored < maxSnapshotItems;
             slot++) {

            ItemStack item = contents[slot];

            if (isEmpty(item)) {
                continue;
            }

            items.put(
                    slot,
                    ItemFingerprint.from(item)
            );

            stored++;
        }

        long transactionId =
                transactionIds.merge(
                        player.getUniqueId(),
                        1L,
                        Long::sum
                );

        InventorySnapshot snapshot =
                new InventorySnapshot(
                        player.getUniqueId(),
                        transactionId,
                        System.currentTimeMillis(),
                        items
                );

        snapshots.put(
                player.getUniqueId(),
                snapshot
        );

        return snapshot;
    }

    /**
     * Gets the latest snapshot for a player.
     */
    public InventorySnapshot getSnapshot(
            UUID uuid
    ) {

        return snapshots.get(uuid);
    }

    /**
     * Removes a player's snapshot.
     */
    public void remove(UUID uuid) {

        if (uuid == null) {
            return;
        }

        snapshots.remove(uuid);
        transactionIds.remove(uuid);
    }

    /**
     * Clears every snapshot.
     */
    public void clear() {

        snapshots.clear();
        transactionIds.clear();
    }

    /**
     * Compare a previous snapshot against the current inventory.
     */
    public TransactionResult compare(
            Player player
    ) {

        if (player == null) {

            return TransactionResult.invalid(
                    "player is null"
            );
        }

        InventorySnapshot before =
                snapshots.get(
                        player.getUniqueId()
                );

        if (before == null) {

            return TransactionResult.invalid(
                    "no previous snapshot"
            );
        }

        InventorySnapshot after =
                snapshotWithoutReplacing(
                        player,
                        before.transactionId()
                );

        return compare(
                before,
                after
        );
    }

    /**
     * Compare two snapshots.
     */
    public TransactionResult compare(
            InventorySnapshot before,
            InventorySnapshot after
    ) {

        if (before == null || after == null) {

            return TransactionResult.invalid(
                    "missing snapshot"
            );
        }

        Map<Material, Integer> beforeTotals =
                aggregate(before);

        Map<Material, Integer> afterTotals =
                aggregate(after);

        Map<Material, Integer> increases =
                new HashMap<>();

        Map<Material, Integer> decreases =
                new HashMap<>();

        for (Material material : union(
                beforeTotals,
                afterTotals
        )) {

            int oldAmount =
                    beforeTotals.getOrDefault(
                            material,
                            0
                    );

            int newAmount =
                    afterTotals.getOrDefault(
                            material,
                            0
                    );

            if (newAmount > oldAmount) {

                increases.put(
                        material,
                        newAmount - oldAmount
                );
            }

            if (oldAmount > newAmount) {

                decreases.put(
                        material,
                        oldAmount - newAmount
                );
            }
        }

        return new TransactionResult(
                true,
                before,
                after,
                increases,
                decreases
        );
    }

    /**
     * Detects whether a protected item unexpectedly increased.
     *
     * This is deliberately conservative.
     */
    public boolean hasUnexpectedProtectedIncrease(
            TransactionResult result
    ) {

        if (result == null || !result.valid()) {
            return false;
        }

        for (Material material :
                result.increases().keySet()) {

            if (isProtectedMaterial(material)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Returns only protected-item increases.
     */
    public Map<Material, Integer> getProtectedIncreases(
            TransactionResult result
    ) {

        if (result == null || !result.valid()) {

            return Collections.emptyMap();
        }

        Map<Material, Integer> output =
                new HashMap<>();

        for (Map.Entry<Material, Integer> entry :
                result.increases().entrySet()) {

            if (isProtectedMaterial(
                    entry.getKey()
            )) {

                output.put(
                        entry.getKey(),
                        entry.getValue()
                );
            }
        }

        return output;
    }

    /**
     * Protected containers and container-like items.
     */
    public boolean isProtectedMaterial(
            Material material
    ) {

        if (material == null) {
            return false;
        }

        String name = material.name();

        if (name.contains("SHULKER_BOX")) {
            return true;
        }

        return switch (material) {

            case CHEST,
                 TRAPPED_CHEST,
                 ENDER_CHEST,
                 BARREL,
                 HOPPER,
                 DROPPER,
                 DISPENSER,
                 FURNACE,
                 BLAST_FURNACE,
                 SMOKER,
                 CRAFTER -> true;

            default -> false;
        };
    }

    /**
     * Snapshot without replacing the player's stored snapshot.
     */
    private InventorySnapshot snapshotWithoutReplacing(
            Player player,
            long transactionId
    ) {

        Inventory inventory =
                player.getInventory();

        Map<Integer, ItemFingerprint> items =
                new HashMap<>();

        ItemStack[] contents =
                inventory.getContents();

        int stored = 0;

        for (int slot = 0;
             slot < contents.length && stored < maxSnapshotItems;
             slot++) {

            ItemStack item = contents[slot];

            if (isEmpty(item)) {
                continue;
            }

            items.put(
                    slot,
                    ItemFingerprint.from(item)
            );

            stored++;
        }

        return new InventorySnapshot(
                player.getUniqueId(),
                transactionId,
                System.currentTimeMillis(),
                items
        );
    }

    private Map<Material, Integer> aggregate(
            InventorySnapshot snapshot
    ) {

        Map<Material, Integer> totals =
                new HashMap<>();

        for (ItemFingerprint item :
                snapshot.items().values()) {

            totals.merge(
                    item.material(),
                    item.amount(),
                    Integer::sum
            );
        }

        return totals;
    }

    private List<Material> union(
            Map<Material, Integer> first,
            Map<Material, Integer> second
    ) {

        List<Material> result =
                new ArrayList<>(
                        first.keySet()
                );

        for (Material material : second.keySet()) {

            if (!result.contains(material)) {
                result.add(material);
            }
        }

        return result;
    }

    private boolean isEmpty(ItemStack item) {

        return item == null
                || item.getType().isAir()
                || item.getAmount() <= 0;
    }

    /**
     * Immutable inventory snapshot.
     */
    public record InventorySnapshot(
            UUID playerId,
            long transactionId,
            long timestamp,
            Map<Integer, ItemFingerprint> items
    ) {

        public InventorySnapshot {

            items = Map.copyOf(items);
        }
    }

    /**
     * Lightweight item representation.
     *
     * We intentionally do not store the entire ItemStack object
     * in the ledger to keep memory usage low.
     */
    public record ItemFingerprint(
            Material material,
            int amount,
            String displayName,
            String customModelData
    ) {

        public static ItemFingerprint from(
                ItemStack item
        ) {

            String displayName = null;
            String customModelData = null;

            if (item.hasItemMeta()) {

                if (item.getItemMeta().hasDisplayName()) {

                    displayName =
                            item.getItemMeta()
                                    .getDisplayName();
                }

                /*
                 * Do not assume a particular Paper
                 * custom-model-data API here.
                 *
                 * The material + amount + display name
                 * is enough for this lightweight ledger.
                 */
            }

            return new ItemFingerprint(
                    item.getType(),
                    item.getAmount(),
                    displayName,
                    customModelData
            );
        }
    }

    /**
     * Result of comparing two inventory states.
     */
    public record TransactionResult(
            boolean valid,
            InventorySnapshot before,
            InventorySnapshot after,
            Map<Material, Integer> increases,
            Map<Material, Integer> decreases
    ) {

        public TransactionResult {

            increases = Map.copyOf(increases);
            decreases = Map.copyOf(decreases);
        }

        public static TransactionResult invalid(
                String reason
        ) {

            return new TransactionResult(
                    false,
                    null,
                    null,
                    Collections.emptyMap(),
                    Collections.emptyMap()
            );
        }

        public boolean hasIncrease(
                Material material
        ) {

            return increases.getOrDefault(
                    material,
                    0
            ) > 0;
        }

        public int increasedAmount(
                Material material
        ) {

            return increases.getOrDefault(
                    material,
                    0
            );
        }

        public int decreasedAmount(
                Material material
        ) {

            return decreases.getOrDefault(
                    material,
                    0
            );
        }
    }
      }
