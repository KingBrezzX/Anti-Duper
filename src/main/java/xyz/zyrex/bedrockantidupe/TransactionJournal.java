package xyz.zyrex.bedrockantidupe;

import org.bukkit.Material;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Append-only transaction journal. Writes only immutable summaries off-thread. */
public final class TransactionJournal implements AutoCloseable {
    private final BedrockAntiDupe plugin;
    private final Path file;
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "BedrockAntiDupe-Journal");
        t.setDaemon(true); return t;
    });

    public TransactionJournal(BedrockAntiDupe plugin) {
        this.plugin = plugin;
        this.file = plugin.getDataFolder().toPath().resolve("journal").resolve("transactions.jsonl");
        try { Files.createDirectories(file.getParent()); } catch (IOException e) { plugin.getLogger().warning("Journal init failed: "+e.getMessage()); }
    }

    public void append(TransactionLedger.TransactionRecord r) {
        if (r == null || !plugin.getConfig().getBoolean("journal.enabled", true)) return;
        StringBuilder s = new StringBuilder(512);
        s.append('{').append("\"id\":\"").append(r.transactionId()).append("\",")
                .append("\"player\":\"").append(r.playerId()).append("\",")
                .append("\"source\":\"").append(json(r.source())).append("\",")
                .append("\"time\":").append(r.timestamp()).append(',')
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
        s.append("]}\n");
        String line=s.toString();
        writer.execute(() -> { try { Files.writeString(file,line,StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.WRITE,StandardOpenOption.APPEND); } catch(IOException e){ plugin.getLogger().warning("Transaction journal write failed: "+e.getMessage()); } });
    }

    private static String json(String x){return x==null?"":x.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n");}
    @Override public void close(){ writer.shutdown(); try{writer.awaitTermination(2,java.util.concurrent.TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();} }
}
