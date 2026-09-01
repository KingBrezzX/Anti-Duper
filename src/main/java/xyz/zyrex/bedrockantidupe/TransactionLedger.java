package xyz.zyrex.bedrockantidupe;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Main-thread transaction journal. Multiple transactions may coexist for one
 * player; a later transaction never overwrites an earlier pending transaction.
 */
public final class TransactionLedger {
    private final BedrockAntiDupe plugin;
    private final Map<UUID, ConcurrentLinkedDeque<TransactionSnapshot>> active = new ConcurrentHashMap<>();
    private final Map<UUID, TransactionRecord> history = new ConcurrentHashMap<>();

    public TransactionLedger(BedrockAntiDupe plugin) { this.plugin = Objects.requireNonNull(plugin); }

    public TransactionSnapshot begin(Player player, String source) { return begin(player, null, source); }

    public TransactionSnapshot begin(Player player, Inventory viewedInventory, String source) {
        Objects.requireNonNull(player, "player");
        UUID id = player.getUniqueId();
        long tick = plugin.getServer().getCurrentTick();
        ConcurrentLinkedDeque<TransactionSnapshot> queue = active.computeIfAbsent(id, k -> new ConcurrentLinkedDeque<>());
        long burst = Math.max(0L, plugin.getConfig().getLong("protection.burst-window-ms", 75L));
        long now = System.currentTimeMillis();
        for (TransactionSnapshot existing : queue) {
            if (now - existing.timestamp() <= burst) return existing;
        }
        int maxPending = Math.max(1, plugin.getConfig().getInt("protection.max-pending-transactions", 64));
        while (queue.size() >= maxPending) queue.pollFirst();
        TransactionSnapshot snapshot = new TransactionSnapshot(
                UUID.randomUUID(), id, source == null ? "UNKNOWN" : source,
                snapshotInventory(player.getInventory()), snapshotInventory(viewedInventory),
                System.currentTimeMillis(), tick);
        queue.addLast(snapshot);
        return snapshot;
    }

    public TransactionRecord finish(Player player, Inventory viewedInventory) { return finish(player, viewedInventory, null); }

    public TransactionRecord finish(Player player, Inventory viewedInventory, UUID expectedId) {
        if (player == null) return null;
        ConcurrentLinkedDeque<TransactionSnapshot> queue = active.get(player.getUniqueId());
        if (queue == null) return null;
        TransactionSnapshot before = null;
        if (expectedId != null) {
            for (TransactionSnapshot candidate : queue) {
                if (expectedId.equals(candidate.transactionId())) { before = candidate; break; }
            }
        } else before = queue.peekFirst();
        if (before == null) return null;
        if (!queue.remove(before)) return null;
        if (queue.isEmpty()) active.remove(player.getUniqueId(), queue);

        TransactionRecord record = TransactionRecord.from(before,
                snapshotInventory(player.getInventory()), snapshotInventory(viewedInventory));
        history.put(record.transactionId(), record);
        plugin.getTransactionJournal().append(record);
        return record;
    }

    public List<TransactionSnapshot> getPending(UUID playerId) {
        ConcurrentLinkedDeque<TransactionSnapshot> q = playerId == null ? null : active.get(playerId);
        return q == null ? List.of() : List.copyOf(q);
    }

    public List<TransactionRecord> finishAll(Player player) {
        if (player == null) return List.of();
        List<TransactionRecord> out = new ArrayList<>();
        for (TransactionSnapshot snapshot : getPending(player.getUniqueId())) {
            TransactionRecord record = finish(player, null, snapshot.transactionId());
            if (record != null) out.add(record);
        }
        return out;
    }

    public TransactionSnapshot getActive(UUID playerId) {
        ConcurrentLinkedDeque<TransactionSnapshot> q = playerId == null ? null : active.get(playerId);
        return q == null ? null : q.peekFirst();
    }
    public TransactionRecord get(UUID id) { return id == null ? null : history.get(id); }
    public Collection<TransactionRecord> getHistory() { return Collections.unmodifiableCollection(new ArrayList<>(history.values())); }

    public void cleanup(long maxAgeMillis) {
        long now = System.currentTimeMillis();
        active.entrySet().removeIf(e -> { e.getValue().removeIf(s -> now - s.timestamp() > maxAgeMillis); return e.getValue().isEmpty(); });
        history.entrySet().removeIf(e -> now - e.getValue().timestamp() > maxAgeMillis);
    }
    public void clear() { active.clear(); history.clear(); }

    static Map<Integer, ItemStack> snapshotInventory(Inventory inventory) {
        Map<Integer, ItemStack> result = new HashMap<>();
        if (inventory == null) return result;
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null && !item.getType().isAir()) result.put(i, item.clone());
        }
        return result;
    }

    public record TransactionSnapshot(UUID transactionId, UUID playerId, String source,
                                      Map<Integer, ItemStack> playerContents,
                                      Map<Integer, ItemStack> containerContents,
                                      long timestamp, long tick) {
        public TransactionSnapshot {
            playerContents = immutable(playerContents); containerContents = immutable(containerContents);
        }
    }

    public record TransactionRecord(UUID transactionId, UUID playerId, String source,
                                    Map<Integer, ItemStack> before, Map<Integer, ItemStack> after,
                                    Map<Integer, ItemStack> containerBefore, Map<Integer, ItemStack> containerAfter,
                                    List<ItemChange> changes, List<ItemChange> containerChanges, long timestamp) {
        public TransactionRecord {
            before=immutable(before); after=immutable(after); containerBefore=immutable(containerBefore); containerAfter=immutable(containerAfter);
            changes=List.copyOf(changes); containerChanges=List.copyOf(containerChanges);
        }
        static TransactionRecord from(TransactionSnapshot s, Map<Integer,ItemStack> after, Map<Integer,ItemStack> containerAfter) {
            return new TransactionRecord(s.transactionId(),s.playerId(),s.source(),s.playerContents(),after,s.containerContents(),containerAfter,
                    diff(s.playerContents(),after),diff(s.containerContents(),containerAfter),System.currentTimeMillis());
        }
        public int totalPositiveIncrease() { return changes.stream().mapToInt(ItemChange::amountDelta).filter(i->i>0).sum(); }
        public boolean hasPositiveIncrease() { return totalPositiveIncrease()>0; }
        public int netDelta(Material m) { return count(m,after)+count(m,containerAfter)-count(m,before)-count(m,containerBefore); }
        public int playerDelta(Material m) { return count(m,after)-count(m,before); }
        public int containerDelta(Material m) { return count(m,containerAfter)-count(m,containerBefore); }
        public int duplicatedShulkerStacks() {
            Map<String,Integer> a=signatureCounts(before,containerBefore), b=signatureCounts(after,containerAfter); int n=0;
            for(var e:b.entrySet()) n+=Math.max(0,e.getValue()-a.getOrDefault(e.getKey(),0)); return n;
        }
        private static int count(Material m, Map<Integer,ItemStack> map) { int n=0; for(ItemStack i:map.values()) if(i!=null&&i.getType()==m)n+=i.getAmount(); return n; }
        private static Map<String,Integer> signatureCounts(Map<Integer,ItemStack> a,Map<Integer,ItemStack>b){Map<String,Integer>r=new HashMap<>();a.values().forEach(i->add(r,i));b.values().forEach(i->add(r,i));return r;}
        private static void add(Map<String,Integer> r,ItemStack i){if(i==null||i.getType().isAir()||!i.getType().name().endsWith("_SHULKER_BOX"))return;r.merge(ItemFingerprint.sha256(i),i.getAmount(),Integer::sum);}
        private static List<ItemChange> diff(Map<Integer,ItemStack>a,Map<Integer,ItemStack>b){List<ItemChange>r=new ArrayList<>();Set<Integer>s=new HashSet<>(a.keySet());s.addAll(b.keySet());for(Integer slot:s){ItemStack x=a.get(slot),y=b.get(slot);if(!same(x,y))r.add(new ItemChange(slot,x==null?null:x.clone(),y==null?null:y.clone()));}return r;}
        private static boolean same(ItemStack a,ItemStack b){if(a==null&&b==null)return true;if(a==null||b==null)return false;return a.getAmount()==b.getAmount()&&a.isSimilar(b);}
        private static Map<Integer,ItemStack> immutable(Map<Integer,ItemStack>m){Map<Integer,ItemStack>c=new HashMap<>();if(m!=null)m.forEach((k,v)->c.put(k,v==null?null:v.clone()));return Collections.unmodifiableMap(c);}
    }
    public record ItemChange(int slot,ItemStack before,ItemStack after){public int amountBefore(){return before==null?0:before.getAmount();}public int amountAfter(){return after==null?0:after.getAmount();}public int amountDelta(){return amountAfter()-amountBefore();}public boolean increased(){return amountDelta()>0;}public boolean decreased(){return amountDelta()<0;}}
    private static Map<Integer,ItemStack> immutable(Map<Integer,ItemStack>m){Map<Integer,ItemStack>c=new HashMap<>();if(m!=null)m.forEach((k,v)->c.put(k,v==null?null:v.clone()));return Collections.unmodifiableMap(c);}
}
