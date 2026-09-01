package xyz.zyrex.bedrockantidupe;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.block.Container;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Incremental loaded-container scanner. It never loads chunks and uses a small per-tick budget. */
public final class LoadedContainerScanner {
    private final BedrockAntiDupe plugin;
    private final Deque<Chunk> queue = new ArrayDeque<>();
    private BukkitTask task;
    private long scanned;
    private long findings;
    private long nextAutomaticScanAt;
    private long intervalMillis;

    public LoadedContainerScanner(BedrockAntiDupe plugin) { this.plugin = plugin; }

    public void start() {
        stop();
        if (!plugin.getConfig().getBoolean("protection.loaded-container-scan", true)) return;
        intervalMillis = Math.max(30L, plugin.getConfig().getLong("protection.scan-interval-seconds", 120L)) * 1000L;
        nextAutomaticScanAt = System.currentTimeMillis() + intervalMillis;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
        queue.clear();
    }

    public void trigger() {
        queue.clear();
        for (var world : Bukkit.getWorlds()) for (Chunk chunk : world.getLoadedChunks()) queue.addLast(chunk);
        scanned = 0; findings = 0; nextAutomaticScanAt = System.currentTimeMillis() + intervalMillis;
    }

    private void tick() {
        if (queue.isEmpty()) {
            if (System.currentTimeMillis() < nextAutomaticScanAt) return;
            trigger();
        }
        int budget = Math.max(1, plugin.getConfig().getInt("protection.max-scan-chunks-per-tick", 2));
        for (int i = 0; i < budget && !queue.isEmpty(); i++) scanChunk(queue.pollFirst());
        if (queue.isEmpty() && plugin.getConfig().getBoolean("debug.console", false)) {
            plugin.getLogger().info("[AntiDupe] Incremental loaded-container scan complete. chunks=" + scanned + " findings=" + findings);
        }
    }

    private void scanChunk(Chunk chunk) {
        if (chunk == null || !chunk.isLoaded()) return;
        scanned++;
        for (var state : chunk.getTileEntities()) {
            if (!(state instanceof Container container)) continue;
            for (ItemStack item : container.getInventory().getContents()) {
                if (item == null || item.getType().isAir()) continue;
                if (item.getAmount() > item.getMaxStackSize()) {
                    findings++;
                    plugin.getLogger().warning("[AntiDupe] Impossible container stack at " + state.getLocation() + ": " + item.getType() + " x" + item.getAmount());
                }
                if (plugin.getConfig().getBoolean("protection.nested-shulker", true) && plugin.getDupeDetector().isShulker(item) && containsNestedShulker(item)) {
                    findings++;
                    plugin.getLogger().warning("[AntiDupe] Nested shulker detected at " + state.getLocation());
                }
            }
        }
    }

    private boolean containsNestedShulker(ItemStack item) {
        try {
            if (!(item.getItemMeta() instanceof org.bukkit.inventory.meta.BlockStateMeta meta)) return false;
            if (!(meta.getBlockState() instanceof org.bukkit.block.ShulkerBox box)) return false;
            for (ItemStack child : box.getInventory().getContents()) if (plugin.getDupeDetector().isShulker(child)) return true;
        } catch (RuntimeException ignored) { }
        return false;
    }

    public long getScanned() { return scanned; }
    public long getFindings() { return findings; }
}
