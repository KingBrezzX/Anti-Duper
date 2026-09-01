package xyz.zyrex.bedrockantidupe;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Main-thread inventory transaction ledger.
 *
 * A transaction is captured BEFORE an inventory event and reconciled
 * on the next tick.  The ledger snapshots both the player's inventory
 * and the inventory being viewed. This makes a legitimate transfer
 * (container -> player or player -> container) conservation-safe while
 * still exposing net-positive item creation.
 */
public final class TransactionLedger {
    private final BedrockAntiDupe plugin;
    private final Map<UUID, TransactionSnapshot> active = new ConcurrentHashMap<>();
    private final Map<UUID, TransactionRecord> history = new ConcurrentHashMap<>();

    public TransactionLedger(BedrockAntiDupe plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public TransactionSnapshot begin(Player player, String source) {
        return begin(player, null, source);
    }

    public TransactionSnapshot begin(Player player, Inventory viewedInventory, String source) {
        Objects.requireNonNull(player, "player");
        UUID uuid = player.getUniqueId();
        TransactionSnapshot existing = active.get(uuid);
        if (existing != null) {
            return existing;
        }

        TransactionSnapshot snapshot = new TransactionSnapshot(
                UUID.randomUUID(), uuid,
                source == null ? "UNKNOWN" : source,
                snapshotInventory(player.getInventory()),
                snapshotInventory(viewedInventory),
                System.currentTimeMillis()
        );
        active.put(uuid, snapshot);
        return snapshot;
    }

    public TransactionRecord finish(Player player) {
        return finish(player, null);
    }

    public TransactionRecord finish(Player player, Inventory viewedInventory) {
        if (player == null) return null;
        TransactionSnapshot before = active.remove(player.getUniqueId());
        if (before == null) return null;

        TransactionRecord record = TransactionRecord.from(
                before,
                snapshotInventory(player.getInventory()),
                snapshotInventory(viewedInventory)
        );
        history.put(record.transactionId(), record);
        return record;
    }

    public TransactionSnapshot getActive(UUID playerId) {
        return playerId == null ? null : active.get(playerId);
    }

    public TransactionRecord get(UUID transactionId) {
        return transactionId == null ? null : history.get(transactionId);
    }

    public Collection<TransactionRecord> getHistory() {
        return Collections.unmodifiableCollection(new ArrayList<>(history.values()));
    }

    public void cleanup(long maxAgeMillis) {
        long now = System.currentTimeMillis();
        active.entrySet().removeIf(e -> now - e.getValue().timestamp() > maxAgeMillis);
        history.entrySet().removeIf(e -> now - e.getValue().timestamp() > maxAgeMillis);
    }

    public void clear() {
        active.clear();
        history.clear();
    }

    private static Map<Integer, ItemStack> snapshotInventory(Inventory inventory) {
        Map<Integer, ItemStack> result = new HashMap<>();
        if (inventory == null) return result;
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null && !item.getType().isAir()) result.put(i, item.clone());
        }
        return result;
    }

    public record TransactionSnapshot(
            UUID transactionId,
            UUID playerId,
            String source,
            Map<Integer, ItemStack> playerContents,
            Map<Integer, ItemStack> containerContents,
            long timestamp
    ) {
        public TransactionSnapshot {
            playerContents = immutable(playerContents);
            containerContents = immutable(containerContents);
        }
    }

    public record TransactionRecord(
            UUID transactionId,
            UUID playerId,
            String source,
            Map<Integer, ItemStack> before,
            Map<Integer, ItemStack> after,
            Map<Integer, ItemStack> containerBefore,
            Map<Integer, ItemStack> containerAfter,
            List<ItemChange> changes,
            List<ItemChange> containerChanges,
            long timestamp
    ) {
        public TransactionRecord {
            before = immutable(before);
            after = immutable(after);
            containerBefore = immutable(containerBefore);
            containerAfter = immutable(containerAfter);
            changes = List.copyOf(changes);
            containerChanges = List.copyOf(containerChanges);
        }

        private static Map<Integer, ItemStack> immutable(Map<Integer, ItemStack> map) {
            Map<Integer, ItemStack> copy = new HashMap<>();
            if (map != null) {
                map.forEach((k, v) -> copy.put(k, v == null ? null : v.clone()));
            }
            return Collections.unmodifiableMap(copy);
        }

        static TransactionRecord from(TransactionSnapshot snapshot,
                                      Map<Integer, ItemStack> after,
                                      Map<Integer, ItemStack> containerAfter) {
            return new TransactionRecord(
                    snapshot.transactionId(), snapshot.playerId(), snapshot.source(),
                    snapshot.playerContents(), after,
                    snapshot.containerContents(), containerAfter,
                    diff(snapshot.playerContents(), after),
                    diff(snapshot.containerContents(), containerAfter),
                    System.currentTimeMillis()
            );
        }

        public int totalPositiveIncrease() {
            int total = 0;
            for (ItemChange c : changes) if (c.increased()) total += c.amountDelta();
            return total;
        }

        public boolean hasPositiveIncrease() {
            return totalPositiveIncrease() > 0;
        }

        /** Net change across player + viewed container, by material. */
        public int netDelta(Material material) {
            if (material == null) return 0;
            return count(material, after) + count(material, containerAfter)
                    - count(material, before) - count(material, containerBefore);
        }

        public int playerDelta(Material material) {
            if (material == null) return 0;
            return count(material, after) - count(material, before);
        }

        public int containerDelta(Material material) {
            if (material == null) return 0;
            return count(material, containerAfter) - count(material, containerBefore);
        }

        public boolean conservationBroken(Material material) {
            return netDelta(material) > 0;
        }

        /**
         * Detects exact shulker-stack duplication by comparing the number
         * of identical stack signatures across player + viewed inventory.
         */
        public int duplicatedShulkerStacks() {
            Map<String, Integer> beforeCounts = signatureCounts(before, containerBefore, true);
            Map<String, Integer> afterCounts = signatureCounts(after, containerAfter, true);
            int duplicated = 0;
            for (Map.Entry<String, Integer> e : afterCounts.entrySet()) {
                int delta = e.getValue() - beforeCounts.getOrDefault(e.getKey(), 0);
                if (delta > 0) duplicated += delta;
            }
            return duplicated;
        }

        private static int count(Material material, Map<Integer, ItemStack> map) {
            int total = 0;
            for (ItemStack item : map.values()) {
                if (item != null && item.getType() == material) total += item.getAmount();
            }
            return total;
        }

        private static Map<String, Integer> signatureCounts(Map<Integer, ItemStack> a,
                                                              Map<Integer, ItemStack> b,
                                                              boolean shulkersOnly) {
            Map<String, Integer> result = new HashMap<>();
            for (ItemStack item : a.values()) addSignature(result, item, shulkersOnly);
            for (ItemStack item : b.values()) addSignature(result, item, shulkersOnly);
            return result;
        }

        private static void addSignature(Map<String, Integer> result, ItemStack item, boolean shulkersOnly) {
            if (item == null || item.getType().isAir()) return;
            if (shulkersOnly && !item.getType().name().endsWith("_SHULKER_BOX")) return;
            String signature = item.getType().name() + "|" + String.valueOf(item.getItemMeta());
            result.merge(signature, item.getAmount(), Integer::sum);
        }

        private static List<ItemChange> diff(Map<Integer, ItemStack> before, Map<Integer, ItemStack> after) {
            List<ItemChange> changes = new ArrayList<>();
            Set<Integer> slots = new HashSet<>(before.keySet());
            slots.addAll(after.keySet());
            for (Integer slot : slots) {
                ItemStack oldItem = before.get(slot);
                ItemStack newItem = after.get(slot);
                if (!sameItem(oldItem, newItem)) {
                    changes.add(new ItemChange(slot,
                            oldItem == null ? null : oldItem.clone(),
                            newItem == null ? null : newItem.clone()));
                }
            }
            return changes;
        }

        private static boolean sameItem(ItemStack a, ItemStack b) {
            if (a == null && b == null) return true;
            if (a == null || b == null) return false;
            return a.isSimilar(b) && a.getAmount() == b.getAmount();
        }
    }

    public record ItemChange(int slot, ItemStack before, ItemStack after) {
        public int amountBefore() { return before == null ? 0 : before.getAmount(); }
        public int amountAfter() { return after == null ? 0 : after.getAmount(); }
        public int amountDelta() { return amountAfter() - amountBefore(); }
        public boolean increased() { return amountDelta() > 0; }
        public boolean decreased() { return amountDelta() < 0; }
    }

    private static Map<Integer, ItemStack> immutable(Map<Integer, ItemStack> map) {
        Map<Integer, ItemStack> copy = new HashMap<>();
        if (map != null) map.forEach((k, v) -> copy.put(k, v == null ? null : v.clone()));
        return Collections.unmodifiableMap(copy);
    }
}
