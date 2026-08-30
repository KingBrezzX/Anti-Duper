package xyz.zyrex.bedrockantidupe;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class TransactionLedger {

    private final BedrockAntiDupe plugin;

    private final Map<UUID, TransactionSnapshot> active =
            new ConcurrentHashMap<>();

    private final Map<UUID, TransactionRecord> history =
            new ConcurrentHashMap<>();

    public TransactionLedger(BedrockAntiDupe plugin) {
        this.plugin = plugin;
    }

    /**
     * Takes the BEFORE snapshot.
     */
    public TransactionSnapshot begin(
            Player player,
            String source
    ) {

        Objects.requireNonNull(player, "player");

        UUID transactionId =
                UUID.randomUUID();

        Map<Integer, ItemStack> contents =
                snapshotInventory(player);

        TransactionSnapshot snapshot =
                new TransactionSnapshot(
                        transactionId,
                        player.getUniqueId(),
                        source == null
                                ? "UNKNOWN"
                                : source,
                        contents,
                        System.currentTimeMillis()
                );

        active.put(
                player.getUniqueId(),
                snapshot
        );

        return snapshot;
    }

    /**
     * Takes the AFTER snapshot and creates a record.
     */
    public TransactionRecord finish(
            Player player
    ) {

        if (player == null) {
            return null;
        }

        TransactionSnapshot before =
                active.remove(
                        player.getUniqueId()
                );

        if (before == null) {
            return null;
        }

        Map<Integer, ItemStack> after =
                snapshotInventory(player);

        TransactionRecord record =
                TransactionRecord.from(
                        before,
                        after
                );

        history.put(
                record.transactionId(),
                record
        );

        return record;
    }

    public TransactionSnapshot getActive(
            UUID playerId
    ) {

        return active.get(
                playerId
        );
    }

    public TransactionRecord get(
            UUID transactionId
    ) {

        return history.get(
                transactionId
        );
    }

    public Collection<TransactionRecord> getHistory() {

        return Collections.unmodifiableCollection(
                history.values()
        );
    }

    /**
     * Removes stale transactions.
     */
    public void cleanup(
            long maxAgeMillis
    ) {

        long now =
                System.currentTimeMillis();

        active.entrySet()
                .removeIf(
                        entry ->
                                now
                                        - entry.getValue()
                                        .timestamp()
                                        > maxAgeMillis
                );

        history.entrySet()
                .removeIf(
                        entry ->
                                now
                                        - entry.getValue()
                                        .timestamp()
                                        > maxAgeMillis
                );
    }

    public void clear() {

        active.clear();
        history.clear();
    }

    private Map<Integer, ItemStack> snapshotInventory(
            Player player
    ) {

        Map<Integer, ItemStack> snapshot =
                new HashMap<>();

        ItemStack[] contents =
                player.getInventory()
                        .getContents();

        for (int slot = 0;
             slot < contents.length;
             slot++) {

            ItemStack item =
                    contents[slot];

            if (item == null
                    || item.getType().isAir()) {

                continue;
            }

            snapshot.put(
                    slot,
                    item.clone()
            );
        }

        return snapshot;
    }

    public record TransactionSnapshot(

            UUID transactionId,

            UUID playerId,

            String source,

            Map<Integer, ItemStack> contents,

            long timestamp

    ) {

        public TransactionSnapshot {

            contents =
                    Collections.unmodifiableMap(
                            new HashMap<>(
                                    contents
                            )
                    );
        }
    }

    public record TransactionRecord(

            UUID transactionId,

            UUID playerId,

            String source,

            Map<Integer, ItemStack> before,

            Map<Integer, ItemStack> after,

            List<ItemChange> changes,

            long timestamp

    ) {

        public TransactionRecord {

            before =
                    Collections.unmodifiableMap(
                            new HashMap<>(before)
                    );

            after =
                    Collections.unmodifiableMap(
                            new HashMap<>(after)
                    );

            changes =
                    List.copyOf(changes);
        }

        public static TransactionRecord from(
                TransactionSnapshot snapshot,
                Map<Integer, ItemStack> after
        ) {

            List<ItemChange> changes =
                    new ArrayList<>();

            Set<Integer> slots =
                    new HashSet<>();

            slots.addAll(
                    snapshot.contents().keySet()
            );

            slots.addAll(
                    after.keySet()
            );

            for (Integer slot : slots) {

                ItemStack oldItem =
                        snapshot.contents()
                                .get(slot);

                ItemStack newItem =
                        after.get(slot);

                if (sameItem(
                        oldItem,
                        newItem
                )) {
                    continue;
                }

                changes.add(
                        new ItemChange(
                                slot,
                                oldItem == null
                                        ? null
                                        : oldItem.clone(),
                                newItem == null
                                        ? null
                                        : newItem.clone()
                        )
                );
            }

            return new TransactionRecord(
                    snapshot.transactionId(),
                    snapshot.playerId(),
                    snapshot.source(),
                    snapshot.contents(),
                    after,
                    changes,
                    System.currentTimeMillis()
            );
        }

        /**
         * Returns the total positive item increase.
         */
        public int totalPositiveIncrease() {

            int total = 0;

            for (ItemChange change :
                    changes) {

                int beforeAmount =
                        amount(change.before());

                int afterAmount =
                        amount(change.after());

                if (afterAmount > beforeAmount) {

                    total +=
                            afterAmount
                                    - beforeAmount;
                }
            }

            return total;
        }

        /**
         * Returns whether the transaction contains
         * any positive inventory increase.
         */
        public boolean hasPositiveIncrease() {

            return totalPositiveIncrease() > 0;
        }

        /**
         * Returns all changed slots.
         */
        public List<ItemChange> getChanges() {

            return changes;
        }

        private static int amount(
                ItemStack item
        ) {

            return item == null
                    ? 0
                    : item.getAmount();
        }

        private static boolean sameItem(
                ItemStack a,
                ItemStack b
        ) {

            if (a == null && b == null) {
                return true;
            }

            if (a == null || b == null) {
                return false;
            }

            return a.isSimilar(b)
                    && a.getAmount()
                    == b.getAmount();
        }
    }

    public record ItemChange(

            int slot,

            ItemStack before,

            ItemStack after

    ) {

        public int amountBefore() {

            return before == null
                    ? 0
                    : before.getAmount();
        }

        public int amountAfter() {

            return after == null
                    ? 0
                    : after.getAmount();
        }

        public int amountDelta() {

            return amountAfter()
                    - amountBefore();
        }

        public boolean increased() {

            return amountDelta() > 0;
        }

        public boolean decreased() {

            return amountDelta() < 0;
        }
    }
                }
