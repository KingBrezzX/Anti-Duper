package xyz.zyrex.bedrockantidupe;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;

/** Safe recovery vault for items removed by confirmed actions. */
public final class RecoveryManager {
    private final BedrockAntiDupe plugin;
    private final File directory;

    public RecoveryManager(BedrockAntiDupe plugin) {
        this.plugin = plugin;
        this.directory = new File(plugin.getDataFolder(), plugin.getConfig().getString("recovery.directory", "evidence"));
        if (plugin.getConfig().getBoolean("recovery.enabled", true)) directory.mkdirs();
    }

    public void backup(UUID playerId, UUID transactionId, List<ItemStack> items, String reason) {
        if (!plugin.getConfig().getBoolean("recovery.enabled", true) || items == null || items.isEmpty()) return;
        File file = new File(directory, "recovery.yml");
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            synchronized (this) {
                YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
                String key = transactionId.toString();
                y.set(key + ".player", playerId.toString());
                y.set(key + ".reason", reason == null ? "UNKNOWN" : reason);
                y.set(key + ".time", System.currentTimeMillis());
                List<Map<String, Object>> serialized = new ArrayList<>();
                for (ItemStack item : items) if (item != null && !item.getType().isAir()) serialized.add(item.serialize());
                y.set(key + ".items", serialized);
                trim(y);
                try { y.save(file); } catch (IOException ex) { plugin.getLogger().warning("[AntiDupe] Recovery save failed: " + ex.getMessage()); }
            }
        });
    }

    public boolean restore(Player player, UUID transactionId) {
        if (player == null || transactionId == null) return false;
        File file = new File(directory, "recovery.yml");
        if (!file.isFile()) return false;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        String key = transactionId.toString();
        List<?> list = y.getList(key + ".items");
        if (list == null) return false;
        List<ItemStack> items = new ArrayList<>();
        for (Object obj : list) {
            if (obj instanceof Map<?, ?> raw) {
                Map<String, Object> map = new LinkedHashMap<>();
                for (Map.Entry<?, ?> e : raw.entrySet()) if (e.getKey() instanceof String k) map.put(k, e.getValue());
                try { items.add(ItemStack.deserialize(map)); } catch (Exception ignored) { }
            }
        }
        for (ItemStack item : items) {
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
            for (ItemStack left : overflow.values()) player.getWorld().dropItemNaturally(player.getLocation(), left);
        }
        y.set(key + ".restored", true);
        try { y.save(file); return true; } catch (IOException ex) { return false; }
    }

    public List<String> list() {
        File file = new File(directory, "recovery.yml");
        if (!file.isFile()) return List.of();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        return new ArrayList<>(y.getKeys(false));
    }

    private void trim(YamlConfiguration y) {
        int max = Math.max(100, plugin.getConfig().getInt("recovery.max-records", 10000));
        List<String> keys = new ArrayList<>(y.getKeys(false));
        if (keys.size() <= max) return;
        keys.sort(Comparator.comparingLong(k -> y.getLong(k + ".time", 0L)));
        for (int i = 0; i < keys.size() - max; i++) y.set(keys.get(i), null);
    }
}
