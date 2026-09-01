package xyz.zyrex.bedrockantidupe;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Append-only immutable transaction journal. */
public final class TransactionJournal implements AutoCloseable {
    private final BedrockAntiDupe plugin;
    private final Path file;
    private final Object writeLock = new Object();
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "BedrockAntiDupe-Journal");
        t.setDaemon(true); return t;
    });

    public TransactionJournal(BedrockAntiDupe plugin) {
        this.plugin = plugin;
        String dir = plugin.getConfig().getString("journal.directory", "journal");
        this.file = plugin.getDataFolder().toPath().resolve(dir).resolve("transactions.jsonl");
        try { Files.createDirectories(file.getParent()); }
        catch (IOException e) { plugin.getLogger().warning("[AntiDupe] Journal init failed: " + e.getMessage()); }
    }

    public void append(TransactionLedger.TransactionRecord r) {
        if (r == null || !plugin.getConfig().getBoolean("journal.enabled", true)) return;
        String line = serialize(r);
        writer.execute(() -> write(line, false));
    }

    /** Durable journal entry for audit-sensitive paths. */
    public boolean appendSync(TransactionLedger.TransactionRecord r) {
        if (r == null || !plugin.getConfig().getBoolean("journal.enabled", true)) return false;
        return write(serialize(r), true);
    }

    private boolean write(String line, boolean force) {
        synchronized (writeLock) {
            try {
                Files.createDirectories(file.getParent());
                try (FileChannel channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
                    channel.write(ByteBuffer.wrap(line.getBytes(StandardCharsets.UTF_8)));
                    if (force) channel.force(true);
                }
                enforceSizeLimit();
                return true;
            } catch (IOException ex) {
                plugin.getLogger().warning("[AntiDupe] Journal write failed: " + ex.getMessage());
                return false;
            }
        }
    }

    private void enforceSizeLimit() {
        long max = Math.max(1L, plugin.getConfig().getLong("journal.max-size-mb", 128L)) * 1024L * 1024L;
        try {
            if (Files.size(file) <= max) return;
            Path rotated = file.resolveSibling("transactions-" + System.currentTimeMillis() + ".jsonl");
            Files.move(file, rotated, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            plugin.getLogger().warning("[AntiDupe] Journal rotation failed: " + ex.getMessage());
        }
    }

    private String serialize(TransactionLedger.TransactionRecord r) {
        StringBuilder s = new StringBuilder(1024);
        s.append('{').append("\"id\":\"").append(r.transactionId()).append("\",")
                .append("\"player\":\"").append(r.playerId()).append("\",")
                .append("\"source\":\"").append(json(r.source())).append("\",")
                .append("\"time\":").append(r.timestamp()).append(',')
                .append("\"positiveIncrease\":").append(r.totalPositiveIncrease()).append(',')
                .append("\"changes\":[");
        boolean first=true;
        for (TransactionLedger.ItemChange c : r.changes()) {
            if (!first) s.append(','); first=false;
            s.append('{').append("\"slot\":").append(c.slot()).append(',')
                    .append("\"before\":\"").append(ItemFingerprint.base64(c.before())).append("\",")
                    .append("\"after\":\"").append(ItemFingerprint.base64(c.after())).append("\"}");
        }
        s.append("],\"containerChanges\":["); first=true;
        for (TransactionLedger.ItemChange c : r.containerChanges()) {
            if (!first) s.append(','); first=false;
            s.append('{').append("\"slot\":").append(c.slot()).append(',')
                    .append("\"before\":\"").append(ItemFingerprint.base64(c.before())).append("\",")
                    .append("\"after\":\"").append(ItemFingerprint.base64(c.after())).append("\"}");
        }
        return s.append("]}\n").toString();
    }

    private static String json(String x) { return x == null ? "" : x.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n"); }
    @Override public void close() { writer.shutdown(); try { writer.awaitTermination(3, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } }
}
