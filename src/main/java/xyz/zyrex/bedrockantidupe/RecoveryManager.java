package xyz.zyrex.bedrockantidupe;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.Base64;

/** Durable per-transaction recovery vault. A recovery file is atomically replaced before removal. */
public final class RecoveryManager {
    private final BedrockAntiDupe plugin;
    private final File directory;
    private final java.util.Set<UUID> restoring = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public RecoveryManager(BedrockAntiDupe plugin) {
        this.plugin = plugin;
        this.directory = new File(plugin.getDataFolder(), plugin.getConfig().getString("recovery.directory", "recovery"));
        if (plugin.getConfig().getBoolean("recovery.enabled", true)) directory.mkdirs();
    }

    public void backup(UUID playerId, UUID transactionId, List<ItemStack> items, String reason) {
        if (!plugin.getConfig().getBoolean("recovery.enabled", true) || items == null || items.isEmpty()) return;
        List<String> serialized = serialize(items);
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> writeAtomically(playerId, transactionId, serialized, reason));
    }

    /** Synchronous durable backup. Safe to call immediately before a destructive inventory mutation. */
    public boolean backupSync(UUID playerId, UUID transactionId, List<ItemStack> items, String reason) {
        if (!plugin.getConfig().getBoolean("recovery.enabled", true) || items == null || items.isEmpty()) return false;
        try {
            if (!directory.exists() && !directory.mkdirs() && !directory.isDirectory()) return false;
            List<String> serialized = serialize(items);
            Path target = new File(directory, transactionId + ".recovery").toPath();
            Path temp = new File(directory, transactionId + ".recovery.tmp").toPath();
            try (FileOutputStream fos = new FileOutputStream(temp.toFile());
                 DataOutputStream out = new DataOutputStream(new BufferedOutputStream(fos))) {
                out.writeInt(1);
                out.writeUTF(playerId.toString());
                out.writeUTF(reason == null ? "UNKNOWN" : reason);
                out.writeLong(System.currentTimeMillis());
                out.writeInt(serialized.size());
                for (String value : serialized) out.writeUTF(value);
                out.flush();
                fos.getFD().sync();
            }
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException ex) {
            plugin.getLogger().warning("[AntiDupe] Durable recovery backup failed: " + ex.getMessage());
            return false;
        }
    }

    public boolean restore(Player player, UUID transactionId) {
        if (player == null || transactionId == null) return false;
        if (!restoring.add(transactionId)) return false;
        Path file = new File(directory, transactionId + ".recovery").toPath();
        Path restoringFile = new File(directory, transactionId + ".restoring").toPath();
        try {
            if (!Files.isRegularFile(file)) return false;
            try {
                Files.move(file, restoringFile, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(file, restoringFile, StandardCopyOption.REPLACE_EXISTING);
            }
            file = restoringFile;
            try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            int version = in.readInt();
            if (version != 1) return false;
            in.readUTF(); // player UUID is retained in the recovery record for auditing.
            in.readUTF();
            in.readLong();
            int count = in.readInt();
            if (count < 0 || count > 10000) return false;
            boolean restored = false;
            for (int i = 0; i < count; i++) {
                ItemStack item = ItemStack.deserializeBytes(Base64.getDecoder().decode(in.readUTF()));
                if (item == null || item.getType().isAir()) continue;
                Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
                for (ItemStack left : overflow.values()) player.getWorld().dropItemNaturally(player.getLocation(), left);
                restored = true;
            }
            if (!restored) return false;
                Files.move(file, new File(directory, transactionId + ".restored").toPath(), StandardCopyOption.REPLACE_EXISTING);
                return true;
            }
        } catch (IOException | RuntimeException ex) {
            plugin.getLogger().warning("[AntiDupe] Recovery restore failed: " + ex.getMessage());
            // Preserve an interrupted restore for manual recovery instead of silently replaying it.
            return false;
        } finally {
            restoring.remove(transactionId);
        }
    }

    public List<String> list() {
        if (!directory.isDirectory()) return List.of();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory.toPath(), "*.recovery")) {
            List<String> ids = new ArrayList<>();
            for (Path path : stream) {
                String name = path.getFileName().toString();
                ids.add(name.substring(0, name.length() - ".recovery".length()));
            }
            ids.sort(String::compareTo);
            return ids;
        } catch (IOException ex) {
            return List.of();
        }
    }

    private List<String> serialize(List<ItemStack> items) {
        List<String> out = new ArrayList<>();
        for (ItemStack item : items) if (item != null && !item.getType().isAir()) out.add(Base64.getEncoder().encodeToString(item.serializeAsBytes()));
        return out;
    }

    private void writeAtomically(UUID playerId, UUID transactionId, List<String> serialized, String reason) {
        if (serialized.isEmpty()) return;
        // Async non-critical copy. Destructive actions use backupSync instead.
        backupSync(playerId, transactionId, deserializeCopies(serialized), reason);
    }

    private List<ItemStack> deserializeCopies(List<String> serialized) {
        List<ItemStack> items = new ArrayList<>();
        for (String value : serialized) {
            try { items.add(ItemStack.deserializeBytes(Base64.getDecoder().decode(value))); }
            catch (RuntimeException ignored) { }
        }
        return items;
    }
}
