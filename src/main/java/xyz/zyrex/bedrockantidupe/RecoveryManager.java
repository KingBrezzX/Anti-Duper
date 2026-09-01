package xyz.zyrex.bedrockantidupe;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.Base64;

/** Safe recovery vault. Item bytes use Paper's NBT serializer rather than legacy Map serialization. */
public final class RecoveryManager {
    private final BedrockAntiDupe plugin;
    private final File directory;
    public RecoveryManager(BedrockAntiDupe plugin) {
        this.plugin=plugin;
        this.directory=new File(plugin.getDataFolder(), plugin.getConfig().getString("recovery.directory","evidence"));
        if(plugin.getConfig().getBoolean("recovery.enabled",true)) directory.mkdirs();
    }
    public void backup(UUID playerId, UUID transactionId, List<ItemStack> items, String reason) {
        if(!plugin.getConfig().getBoolean("recovery.enabled",true)||items==null||items.isEmpty())return;
        List<String> serialized=new ArrayList<>();
        for(ItemStack item:items) if(item!=null&&!item.getType().isAir()) serialized.add(Base64.getEncoder().encodeToString(item.serializeAsBytes()));
        File file=new File(directory,"recovery.yml");
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin,()->{
            synchronized(this){
                YamlConfiguration y=YamlConfiguration.loadConfiguration(file); String key=transactionId.toString();
                y.set(key+".player",playerId.toString()); y.set(key+".reason",reason==null?"UNKNOWN":reason); y.set(key+".time",System.currentTimeMillis()); y.set(key+".items",serialized); trim(y);
                try{y.save(file);}catch(IOException ex){plugin.getLogger().warning("[AntiDupe] Recovery save failed: "+ex.getMessage());}
            }
        });
    }
    /** Durable backup used immediately before a destructive action. */
    public boolean backupSync(UUID playerId, UUID transactionId, List<ItemStack> items, String reason) {
        if(!plugin.getConfig().getBoolean("recovery.enabled",true)||items==null||items.isEmpty()) return false;
        try {
            if(!directory.exists() && !directory.mkdirs() && !directory.isDirectory()) return false;
            File file=new File(directory,"recovery.yml");
            YamlConfiguration y=YamlConfiguration.loadConfiguration(file); String key=transactionId.toString();
            List<String> serialized=new ArrayList<>();
            for(ItemStack item:items) if(item!=null&&!item.getType().isAir()) serialized.add(Base64.getEncoder().encodeToString(item.serializeAsBytes()));
            y.set(key+".player",playerId.toString()); y.set(key+".reason",reason==null?"UNKNOWN":reason); y.set(key+".time",System.currentTimeMillis()); y.set(key+".items",serialized); trim(y); y.save(file);
            return true;
        } catch(IOException ex) { plugin.getLogger().warning("[AntiDupe] Durable recovery backup failed: "+ex.getMessage()); return false; }
    }

    public boolean restore(Player player, UUID transactionId) {
        if(player==null||transactionId==null)return false;
        File file=new File(directory,"recovery.yml"); if(!file.isFile())return false;
        YamlConfiguration y=YamlConfiguration.loadConfiguration(file); String key=transactionId.toString();
        List<?> list=y.getList(key+".items"); if(list==null)return false;
        boolean restoredAny=false;
        for(Object obj:list){
            if(!(obj instanceof String encoded))continue;
            try{
                ItemStack item=ItemStack.deserializeBytes(Base64.getDecoder().decode(encoded));
                Map<Integer,ItemStack> overflow=player.getInventory().addItem(item);
                for(ItemStack left:overflow.values()) player.getWorld().dropItemNaturally(player.getLocation(),left);
                restoredAny=true;
            }catch(Exception ex){plugin.getLogger().warning("[AntiDupe] Recovery item decode failed: "+ex.getMessage());}
        }
        if(!restoredAny)return false;
        y.set(key+".restored",true); y.set(key+".restoredAt",System.currentTimeMillis());
        try{y.save(file);return true;}catch(IOException ex){return false;}
    }
    public List<String> list(){File file=new File(directory,"recovery.yml");if(!file.isFile())return List.of();YamlConfiguration y=YamlConfiguration.loadConfiguration(file);return new ArrayList<>(y.getKeys(false));}
    private void trim(YamlConfiguration y){int max=Math.max(100,plugin.getConfig().getInt("recovery.max-records",10000));List<String> keys=new ArrayList<>(y.getKeys(false));if(keys.size()<=max)return;keys.sort(Comparator.comparingLong(k->y.getLong(k+".time",0L)));for(int i=0;i<keys.size()-max;i++)y.set(keys.get(i),null);}
}
