package xyz.zyrex.bedrockantidupe;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Conservative defense-in-depth detector. A single legitimate-looking increase is suspicious, not automatically a confirmed dupe. */
public final class DupeDetector {
    private final BedrockAntiDupe plugin;
    private final TransactionLedger ledger;
    private final Set<Material> trackedMaterials = EnumSet.noneOf(Material.class);
    private final Map<String, Long> recentSignals = new ConcurrentHashMap<>();

    public DupeDetector(BedrockAntiDupe plugin, TransactionLedger ledger){this.plugin=plugin;this.ledger=ledger;loadTrackedMaterials();}
    private void loadTrackedMaterials(){
        trackedMaterials.clear();
        if(plugin.getConfig().getBoolean("detection.track-all-stackable-items", false)){
            for(Material m:Material.values()) if(m.isItem()&&!m.isAir()&&m.getMaxStackSize()>1) trackedMaterials.add(m);
        }
        if(plugin.getConfig().getBoolean("shulker.enabled",true)) for(Material m:Material.values()) if(isShulker(m)) trackedMaterials.add(m);
        for(String name:plugin.getConfig().getStringList("detection.tracked-materials")){
            if(name==null||name.isBlank())continue; try{trackedMaterials.add(Material.valueOf(name.trim().toUpperCase(Locale.ROOT)));}catch(IllegalArgumentException ex){plugin.getLogger().warning("Unknown tracked material: "+name);}
        }
    }
    public DetectionResult inspect(TransactionLedger.TransactionRecord record){
        if(record==null)return DetectionResult.clean("No transaction.");
        // Crafting/smithing legitimately transform one item type into another.
        // They are still journaled, but are not treated as conservation violations
        // unless a future recipe-aware detector explicitly proves an anomaly.
        if (record.source().equals("CRAFT") || record.source().equals("SMITHING")) {
            DetectionResult clean = DetectionResult.clean("Recipe transformation observed; conservation heuristic skipped.");
            if(plugin.getDatabaseManager()!=null) plugin.getDatabaseManager().record(record, clean);
            return clean;
        }
        DetectionResult result;
        if(!plugin.getConfig().getBoolean("settings.enabled",true)||!plugin.getConfig().getBoolean("detection.enabled",true)) {
            result=DetectionResult.clean("Detection disabled.");
        } else {
            List<Change> suspicious=new ArrayList<>();
            int threshold=Math.max(1,plugin.getConfig().getInt("detection.instant-increase-threshold",1));
            for(Material m:trackedMaterials){
                int net=record.netDelta(m), playerDelta=record.playerDelta(m);
                if(net<=0||playerDelta<=0)continue;
                int amount=Math.min(net,playerDelta); if(amount<=0)continue;
                suspicious.add(new Change(-1,m,amount,net>=threshold?"CONSERVATION_BREAK":"NET_POSITIVE"));
            }
            int shulkerStacks=record.duplicatedShulkerStacks();
            if(suspicious.isEmpty() && shulkerStacks<=0) result=DetectionResult.clean("Inventory conservation preserved.");
            else {
                boolean confirmed=false;
                if(shulkerStacks>0) confirmed=true;
                if(plugin.getConfig().getBoolean("settings.require-confirmation",true)){
                    long window=Math.max(25L,plugin.getConfig().getLong("protection.burst-window-ms",75L));
                    for(Change c:suspicious){
                        String key=record.playerId()+"|"+c.material()+"|"+c.increase();
                        Long previous=recentSignals.put(key,System.currentTimeMillis());
                        if(previous!=null&&System.currentTimeMillis()-previous<=window)confirmed=true;
                    }
                } else confirmed=!suspicious.isEmpty();
                result=new DetectionResult(true,confirmed,record,suspicious,
                        confirmed?"Repeated or fingerprinted conservation anomaly confirmed.":"Conservation anomaly observed; awaiting independent confirmation.");
            }
        }
        if(plugin.getDatabaseManager()!=null) plugin.getDatabaseManager().record(record,result);
        return result;
    }

    public boolean isShulker(ItemStack item){return item!=null&&isShulker(item.getType());}
    public boolean isShulker(Material m){return m!=null&&m.name().endsWith("_SHULKER_BOX");}
    public Set<Material> getTrackedMaterials(){return Collections.unmodifiableSet(trackedMaterials);}
    public void reload(){loadTrackedMaterials();recentSignals.clear();}
    public void cleanup(long maxAge){long now=System.currentTimeMillis();recentSignals.entrySet().removeIf(e->now-e.getValue()>maxAge);}
    public record Change(int slot,Material material,int increase,String reason){}
    public record DetectionResult(boolean suspicious,boolean confirmed,TransactionLedger.TransactionRecord transaction,List<Change> changes,String reason){
        public DetectionResult{changes=List.copyOf(changes);} public static DetectionResult clean(String r){return new DetectionResult(false,false,null,List.of(),r);} public boolean isConfirmedSuspicious(){return suspicious&&confirmed;} public int totalIncrease(){return changes.stream().mapToInt(Change::increase).sum();}
    }
}
