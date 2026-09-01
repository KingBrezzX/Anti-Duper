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

    /** Synchronous durable backup for generic item recovery. */
    public boolean backupSync(UUID playerId, UUID transactionId, List<ItemStack> items, String reason) {
        if (!plugin.getConfig().getBoolean("recovery.enabled", true) || items == null || items.isEmpty()) return false;
        List<RecoveryEntry> entries = new ArrayList<>();
        for (ItemStack item : items) if (item != null && !item.getType().isAir()) entries.add(new RecoveryEntry(-1, null, null, item.clone()));
        return writeEntries(playerId, transactionId, entries, reason);
    }

    /** Durable slot-aware recovery used immediately before destructive inventory mutation. */
    public boolean backupSyncEntries(UUID playerId, UUID transactionId, List<RecoveryEntry> entries, String reason) {
        if (!plugin.getConfig().getBoolean("recovery.enabled", true) || entries == null || entries.isEmpty()) return false;
        return writeEntries(playerId, transactionId, entries, reason);
    }

    private boolean writeEntries(UUID playerId, UUID transactionId, List<RecoveryEntry> entries, String reason) {
        if (playerId == null || transactionId == null || entries == null || entries.isEmpty()) return false;
        try {
            if (!directory.exists() && !directory.mkdirs() && !directory.isDirectory()) return false;
            Path target = new File(directory, transactionId + ".recovery").toPath();
            Path temp = new File(directory, transactionId + ".recovery.tmp").toPath();
            try (FileOutputStream fos = new FileOutputStream(temp.toFile());
                 DataOutputStream out = new DataOutputStream(new BufferedOutputStream(fos))) {
                out.writeInt(2);
                out.writeUTF(playerId.toString());
                out.writeUTF(reason == null ? "UNKNOWN" : reason);
                out.writeLong(System.currentTimeMillis());
                out.writeInt(entries.size());
                for (RecoveryEntry entry : entries) {
                    out.writeInt(entry.slot());
                    writeItem(out, entry.before());
                    writeItem(out, entry.after());
                    writeItem(out, entry.recovered());
                }
                out.flush();
                fos.getFD().sync();
            }
            try { Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ex) { Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING); }
            return true;
        } catch (IOException | RuntimeException ex) {
            plugin.getLogger().warning("[AntiDupe] Durable recovery backup failed: " + ex.getMessage());
            return false;
        }
    }

    private static void writeItem(DataOutputStream out, ItemStack item) throws IOException {
        if (item == null || item.getType().isAir()) { out.writeBoolean(false); return; }
        out.writeBoolean(true);
        out.writeUTF(Base64.getEncoder().encodeToString(item.serializeAsBytes()));
    }

    private static ItemStack readItem(DataInputStream in) throws IOException {
        if (!in.readBoolean()) return null;
        try { return ItemStack.deserializeBytes(Base64.getDecoder().decode(in.readUTF())); }
        catch (RuntimeException ex) { throw new IOException("Invalid recovery item", ex); }
    }

    public boolean restore(Player player, UUID transactionId) {
        if (player == null || transactionId == null) return false;
        if (!restoring.add(transactionId)) return false;
        Path recoveryFile = new File(directory, transactionId + ".recovery").toPath();
        Path restoringFile = new File(directory, transactionId + ".restoring").toPath();
        Path restoredFile = new File(directory, transactionId + ".restored").toPath();
        try {
            if (!Files.isRegularFile(recoveryFile)) return false;
            List<RecoveryEntry> entries = readEntries(recoveryFile);
            if (entries.isEmpty()) return false;

            // v2 records are slot-aware. Recovery is allowed only when every affected
            // slot is still exactly at the state captured after removal, or already at
            // the captured before-state. Mixed state is refused to avoid item loss/dupe.
            boolean alreadyRestored = true;
            boolean readyToRestore = true;
            for (RecoveryEntry entry : entries) {
                if (entry.slot() < 0) { alreadyRestored = false; readyToRestore = false; break; }
                ItemStack current = player.getInventory().getItem(entry.slot());
                if (!same(current, entry.before())) alreadyRestored = false;
                if (!same(current, entry.after())) readyToRestore = false;
            }
            if (alreadyRestored) {
                Files.move(recoveryFile, restoredFile, StandardCopyOption.REPLACE_EXISTING);
                return true;
            }
            if (!readyToRestore || !player.isOnline()) return false;

            try { Files.move(recoveryFile, restoringFile, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ex) { Files.move(recoveryFile, restoringFile, StandardCopyOption.REPLACE_EXISTING); }

            // Re-check after claiming the record. The operation remains idempotent:
            // a restart can safely inspect the .restoring record without replaying it.
            for (RecoveryEntry entry : entries) {
                if (!same(player.getInventory().getItem(entry.slot()), entry.after())) {
                    restoreClaim(restoringFile, recoveryFile);
                    return false;
                }
            }
            for (RecoveryEntry entry : entries) player.getInventory().setItem(entry.slot(), cloneOrNull(entry.before()));
            for (RecoveryEntry entry : entries) {
                if (!same(player.getInventory().getItem(entry.slot()), entry.before())) {
                    plugin.getLogger().severe("[AntiDupe] Recovery verification failed; manual intervention required for " + transactionId);
                    return false;
                }
            }
            Files.move(restoringFile, restoredFile, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException | RuntimeException ex) {
            plugin.getLogger().warning("[AntiDupe] Recovery restore failed: " + ex.getMessage());
            return false;
        } finally { restoring.remove(transactionId); }
    }

    public boolean reconcileInterrupted(Player player, UUID transactionId) {
        if (player == null || transactionId == null) return false;
        Path restoringFile = new File(directory, transactionId + ".restoring").toPath();
        if (!Files.isRegularFile(restoringFile)) return false;
        try {
            List<RecoveryEntry> entries = readEntries(restoringFile);
            boolean before = entries.stream().allMatch(e -> e.slot() >= 0 && same(player.getInventory().getItem(e.slot()), e.before()));
            if (before) { Files.move(restoringFile, new File(directory, transactionId + ".restored").toPath(), StandardCopyOption.REPLACE_EXISTING); return true; }
            boolean after = entries.stream().allMatch(e -> e.slot() >= 0 && same(player.getInventory().getItem(e.slot()), e.after()));
            if (after) {
                Path recoveryFile = new File(directory, transactionId + ".recovery").toPath();
                Files.move(restoringFile, recoveryFile, StandardCopyOption.REPLACE_EXISTING);
                return restore(player, transactionId);
            }
            return false;
        } catch (IOException | RuntimeException ex) { return false; }
    }

    private List<RecoveryEntry> readEntries(Path file) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            int version = in.readInt();
            if (version != 2) throw new IOException("Unsupported recovery version: " + version);
            in.readUTF(); in.readUTF(); in.readLong();
            int count = in.readInt();
            if (count < 1 || count > 10000) throw new IOException("Invalid recovery entry count");
            List<RecoveryEntry> entries = new ArrayList<>(count);
            for (int i=0;i<count;i++) entries.add(new RecoveryEntry(in.readInt(), readItem(in), readItem(in), readItem(in)));
            return entries;
        }
    }

    private void restoreClaim(Path restoringFile, Path recoveryFile) throws IOException {
        if (Files.exists(restoringFile) && !Files.exists(recoveryFile)) Files.move(restoringFile, recoveryFile, StandardCopyOption.REPLACE_EXISTING);
    }
    private static ItemStack cloneOrNull(ItemStack item) { return item == null ? null : item.clone(); }
    private static boolean same(ItemStack a, ItemStack b) { if (a == null || a.getType().isAir()) return b == null || b.getType().isAir(); if (b == null || b.getType().isAir()) return false; return a.getAmount() == b.getAmount() && a.isSimilar(b); }
    public record RecoveryEntry(int slot, ItemStack before, ItemStack after, ItemStack recovered) { }

    public List<String> listInterrupted() {
        if (!directory.isDirectory()) return List.of();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory.toPath(), "*.restoring")) {
            List<String> ids = new ArrayList<>();
            for (Path path : stream) {
                String name = path.getFileName().toString();
                ids.add(name.substring(0, name.length() - ".restoring".length()));
            }
            ids.sort(String::compareTo);
            return ids;
        } catch (IOException ex) { return List.of(); }
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
        // Asynchronous copies are intentionally not used for destructive actions.
        // Keep this path for audit-only callers; the durable slot-aware overload is mandatory
        // whenever inventory mutation is possible.
        if (serialized == null || serialized.isEmpty()) return;
        List<ItemStack> items = new ArrayList<>();
        for (String value : serialized) {
            try { items.add(ItemStack.deserializeBytes(Base64.getDecoder().decode(value))); } catch (RuntimeException ignored) { }
        }
        if (!items.isEmpty()) backupSync(playerId, transactionId, items, reason);
    }

}
