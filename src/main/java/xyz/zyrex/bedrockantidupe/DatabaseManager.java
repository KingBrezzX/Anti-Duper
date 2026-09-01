package xyz.zyrex.bedrockantidupe;

import java.io.File;
import java.sql.*;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Small, bundled SQLite persistence layer. Bukkit/Paper API is never touched
 * from the database worker. Writes are serialized to avoid SQLite lock storms.
 */
public final class DatabaseManager implements AutoCloseable {
    private final BedrockAntiDupe plugin;
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "BedrockAntiDupe-Database");
        t.setDaemon(true);
        return t;
    });
    private Connection connection;
    private final Object lock = new Object();

    public DatabaseManager(BedrockAntiDupe plugin) {
        this.plugin = plugin;
        if (!plugin.getConfig().getBoolean("database.enabled", true)) return;
        try {
            File file = new File(plugin.getDataFolder(), plugin.getConfig().getString("database.file", "antiduper.db"));
            File parent = file.getParentFile();
            if (parent != null) parent.mkdirs();
            connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
            try (Statement st = connection.createStatement()) {
                int busy = Math.max(100, plugin.getConfig().getInt("database.busy-timeout-ms", 3000));
                st.execute("PRAGMA busy_timeout=" + busy);
                st.execute("PRAGMA journal_mode=WAL");
                st.execute("PRAGMA synchronous=NORMAL");
                st.execute("CREATE TABLE IF NOT EXISTS transactions (id TEXT PRIMARY KEY, player TEXT NOT NULL, source TEXT NOT NULL, created_at INTEGER NOT NULL, positive_increase INTEGER NOT NULL, suspicious INTEGER NOT NULL, confirmed INTEGER NOT NULL, reason TEXT NOT NULL)");
                st.execute("CREATE INDEX IF NOT EXISTS idx_transactions_player_time ON transactions(player, created_at)");
                st.execute("CREATE TABLE IF NOT EXISTS economy_transactions (id TEXT PRIMARY KEY, player TEXT NOT NULL, amount REAL NOT NULL, created_at INTEGER NOT NULL, rollback_eligible INTEGER NOT NULL)");
            }
        } catch (SQLException ex) {
            plugin.getLogger().severe("[AntiDupe] SQLite initialization failed: " + ex.getMessage());
            connection = null;
        }
    }

    public boolean isAvailable() { return connection != null; }

    public void record(TransactionLedger.TransactionRecord r, DupeDetector.DetectionResult result) {
        if (r == null || connection == null) return;
        final boolean suspicious = result != null && result.suspicious();
        final boolean confirmed = result != null && result.confirmed();
        final String reason = result == null ? "UNINSPECTED" : result.reason();
        writer.execute(() -> {
            synchronized (lock) {
                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT OR REPLACE INTO transactions(id,player,source,created_at,positive_increase,suspicious,confirmed,reason) VALUES(?,?,?,?,?,?,?,?)")) {
                    ps.setString(1, r.transactionId().toString());
                    ps.setString(2, r.playerId().toString());
                    ps.setString(3, r.source());
                    ps.setLong(4, r.timestamp());
                    ps.setInt(5, r.totalPositiveIncrease());
                    ps.setInt(6, suspicious ? 1 : 0);
                    ps.setInt(7, confirmed ? 1 : 0);
                    ps.setString(8, reason);
                    ps.executeUpdate();
                } catch (SQLException ex) {
                    plugin.getLogger().warning("[AntiDupe] SQLite transaction write failed: " + ex.getMessage());
                }
            }
        });
    }

    public void recordEconomy(EconomyTransaction tx) {
        if (tx == null || connection == null) return;
        writer.execute(() -> {
            synchronized (lock) {
                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT OR REPLACE INTO economy_transactions(id,player,amount,created_at,rollback_eligible) VALUES(?,?,?,?,?)")) {
                    ps.setString(1, tx.transactionId().toString());
                    ps.setString(2, tx.playerId().toString());
                    ps.setDouble(3, tx.exactValue());
                    ps.setLong(4, System.currentTimeMillis());
                    ps.setInt(5, tx.rollbackEligible() ? 1 : 0);
                    ps.executeUpdate();
                } catch (SQLException ex) {
                    plugin.getLogger().warning("[AntiDupe] SQLite economy write failed: " + ex.getMessage());
                }
            }
        });
    }

    public long countTransactions() {
        if (connection == null) return -1;
        synchronized (lock) {
            try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM transactions")) {
                return rs.next() ? rs.getLong(1) : 0;
            } catch (SQLException ex) {
                return -1;
            }
        }
    }

    public void cleanup(long maxAgeMillis) {
        if (connection == null) return;
        long cutoff = System.currentTimeMillis() - Math.max(60_000L, maxAgeMillis);
        writer.execute(() -> {
            synchronized (lock) {
                try (PreparedStatement a = connection.prepareStatement("DELETE FROM transactions WHERE created_at < ?");
                     PreparedStatement b = connection.prepareStatement("DELETE FROM economy_transactions WHERE created_at < ?")) {
                    a.setLong(1, cutoff); a.executeUpdate();
                    b.setLong(1, cutoff); b.executeUpdate();
                } catch (SQLException ex) {
                    plugin.getLogger().warning("[AntiDupe] SQLite cleanup failed: " + ex.getMessage());
                }
            }
        });
    }

    @Override public void close() {
        writer.shutdown();
        try { writer.awaitTermination(3, TimeUnit.SECONDS); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
        synchronized (lock) {
            if (connection != null) {
                try { connection.close(); } catch (SQLException ignored) { }
                connection = null;
            }
        }
    }
}
