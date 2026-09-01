package xyz.zyrex.bedrockantidupe;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;

public final class BedrockAntiDupe extends JavaPlugin {
    private TransactionLedger transactionLedger;
    private DupeDetector dupeDetector;
    private DiscordAlertManager discordAlertManager;
    private DupeActionManager dupeActionManager;
    private EconomyRollbackManager economyRollbackManager;
    private EvidenceManager evidenceManager;
    private ExploitProtectionListener exploitProtectionListener;
    private ShopTransactionListener shopTransactionListener;
    private RecoveryManager recoveryManager;
    private TransactionJournal transactionJournal;
    private NativeExploitPreventionListener nativePreventionListener;
    private PlayerStateListener playerStateListener;
    private DatabaseManager databaseManager;
    private BukkitTask maintenanceTask;
    private LoadedContainerScanner loadedContainerScanner;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("messages.yml", false);
        validateConfiguration();

        transactionLedger = new TransactionLedger(this);
        discordAlertManager = new DiscordAlertManager(this);
        evidenceManager = new EvidenceManager(this);
        dupeDetector = new DupeDetector(this, transactionLedger);
        economyRollbackManager = new EconomyRollbackManager(this, discordAlertManager);
        dupeActionManager = new DupeActionManager(this, discordAlertManager, evidenceManager, economyRollbackManager);
        exploitProtectionListener = new ExploitProtectionListener(this, transactionLedger, dupeDetector, dupeActionManager);
        shopTransactionListener = new ShopTransactionListener(this, transactionLedger);
        recoveryManager = new RecoveryManager(this);
        transactionJournal = new TransactionJournal(this);
        databaseManager = new DatabaseManager(this);
        nativePreventionListener = new NativeExploitPreventionListener(this);
        playerStateListener = new PlayerStateListener(this);
        loadedContainerScanner = new LoadedContainerScanner(this);
        loadedContainerScanner.start();

        Bukkit.getPluginManager().registerEvents(exploitProtectionListener, this);
        Bukkit.getPluginManager().registerEvents(shopTransactionListener, this);
        Bukkit.getPluginManager().registerEvents(nativePreventionListener, this);
        Bukkit.getPluginManager().registerEvents(playerStateListener, this);
        registerCommands();
        startMaintenanceTask();

        getLogger().info("BedrockAntiDupe 2.7.4 enabled | Paper 26.2 | Java 25");
        getLogger().info("Detection: " + getConfig().getBoolean("detection.enabled", true));
        getLogger().info("Shulker protection: " + getConfig().getBoolean("shulker.enabled", true));
        getLogger().info("Vault economy: " + economyRollbackManager.isAvailable());
        getLogger().info("Discord webhook: " + getConfig().getBoolean("discord.enabled", false));
        getLogger().info("SQLite database: " + (databaseManager != null && databaseManager.isAvailable()));
    }

    @Override
    public void onDisable() {
        if (maintenanceTask != null) maintenanceTask.cancel();
        if (loadedContainerScanner != null) loadedContainerScanner.stop();

        // Close the transaction fence before clearing in-memory state so a
        // graceful server shutdown cannot silently discard pending snapshots.
        if (transactionLedger != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                transactionLedger.finishAll(player);
            }
        }
        if (exploitProtectionListener != null) exploitProtectionListener.clear();
        if (shopTransactionListener != null) shopTransactionListener.clearAll();
        if (dupeActionManager != null) dupeActionManager.clear();
        if (evidenceManager != null) evidenceManager.clear();
        if (economyRollbackManager != null) economyRollbackManager.clear();
        if (discordAlertManager != null) discordAlertManager.cleanup();
        if (transactionJournal != null) transactionJournal.close();
        if (databaseManager != null) databaseManager.close();
        getLogger().info("BedrockAntiDupe disabled.");
    }

    private void registerCommands() {
        var command = getCommand("antidupe");
        if (command == null) {
            getLogger().severe("Command 'antidupe' is missing from plugin.yml!");
            return;
        }
        AntiDupeCommand executor = new AntiDupeCommand(this);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    private void startMaintenanceTask() {
        long seconds = Math.max(10L, getConfig().getLong("performance.cleanup-interval-seconds", 60L));
        maintenanceTask = Bukkit.getScheduler().runTaskTimer(this, this::cleanupCaches, seconds * 20L, seconds * 20L);
    }

    public void cleanupCaches() {
        long maxAge = Math.max(30_000L,
                getConfig().getLong("settings.transaction-retention-seconds", 300L) * 1000L);
        if (exploitProtectionListener != null) exploitProtectionListener.cleanup(maxAge);
        if (shopTransactionListener != null) shopTransactionListener.cleanup(maxAge);
        if (dupeActionManager != null) dupeActionManager.cleanup(maxAge);
        if (dupeDetector != null) dupeDetector.cleanup(maxAge);
        if (economyRollbackManager != null) economyRollbackManager.cleanup(maxAge);
        if (databaseManager != null) databaseManager.cleanup(maxAge);
    }

    public void reloadPlugin() {
        reloadConfig();
        validateConfiguration();
        if (dupeDetector != null) dupeDetector.reload();
        if (loadedContainerScanner != null) loadedContainerScanner.start();
        if (transactionJournal != null) transactionJournal.close();
        transactionJournal = new TransactionJournal(this);
        if (databaseManager != null) databaseManager.close();
        databaseManager = new DatabaseManager(this);
        getLogger().info("Configuration reloaded.");
    }

    private void validateConfiguration() {
        if (getConfig().getBoolean("actions.remove-confirmed-items", false) && getConfig().getBoolean("recovery.require-backup-before-removal", true) && !getConfig().getBoolean("recovery.enabled", true)) {
            getLogger().warning("[AntiDupe] Automatic removal requested but recovery is disabled; forcing removal OFF for safety.");
            getConfig().set("actions.remove-confirmed-items", false);
            saveConfig();
        }
        if (getConfig().getInt("protection.max-pending-transactions", 64) < 1) {
            getConfig().set("protection.max-pending-transactions", 64);
            saveConfig();
        }
    }

    public int scanPlayerInventory(Player player) {
        if (player == null || !player.isOnline()) return 0;
        int findings = 0;
        for (var item : player.getInventory().getContents()) {
            if (item == null || item.getType().isAir()) continue;
            if (item.getAmount() > item.getMaxStackSize()) {
                findings++;
                getLogger().warning("[AntiDupe] Impossible player stack: " + player.getName() + " " + item.getType() + " x" + item.getAmount());
            }
            if (getConfig().getBoolean("protection.nested-shulker", true) && dupeDetector.isShulker(item) && containsNestedShulker(item)) {
                findings++;
                getLogger().warning("[AntiDupe] Nested shulker detected in player inventory: " + player.getName());
            }
        }
        return findings;
    }

    public int scanLoadedInventories() {
        int findings = 0;
        for (var world : Bukkit.getWorlds()) {
            for (var chunk : world.getLoadedChunks()) {
                for (var state : chunk.getTileEntities()) {
                    if (!(state instanceof org.bukkit.block.Container container)) continue;
                    var inv = container.getInventory();
                    for (var item : inv.getContents()) {
                        if (item == null || item.getType().isAir()) continue;
                        if (item.getAmount() > item.getMaxStackSize()) {
                            findings++;
                            getLogger().warning("[AntiDupe] Impossible stack in " + world.getName() + " @ " + state.getLocation().toVector() + ": " + item.getType() + " x" + item.getAmount());
                        }
                    }
                }
            }
        }
        return findings;
    }

    private boolean containsNestedShulker(org.bukkit.inventory.ItemStack shulker) {
        try {
            org.bukkit.inventory.meta.BlockStateMeta meta = shulker.getItemMeta() instanceof org.bukkit.inventory.meta.BlockStateMeta b ? b : null;
            if (meta == null || !(meta.getBlockState() instanceof org.bukkit.block.ShulkerBox box)) return false;
            for (var item : box.getInventory().getContents()) if (dupeDetector.isShulker(item)) return true;
        } catch (RuntimeException ignored) { }
        return false;
    }

    public TransactionLedger getTransactionLedger() { return transactionLedger; }
    public DupeDetector getDupeDetector() { return dupeDetector; }
    public DiscordAlertManager getDiscordAlertManager() { return discordAlertManager; }
    public DupeActionManager getDupeActionManager() { return dupeActionManager; }
    public EconomyRollbackManager getEconomyRollbackManager() { return economyRollbackManager; }
    public EvidenceManager getEvidenceManager() { return evidenceManager; }
    public ExploitProtectionListener getExploitProtectionListener() { return exploitProtectionListener; }
    public ShopTransactionListener getShopTransactionListener() { return shopTransactionListener; }
    public RecoveryManager getRecoveryManager() { return recoveryManager; }
    public TransactionJournal getTransactionJournal() { return transactionJournal; }
    public NativeExploitPreventionListener getNativePreventionListener() { return nativePreventionListener; }
    public PlayerStateListener getPlayerStateListener() { return playerStateListener; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public LoadedContainerScanner getLoadedContainerScanner() { return loadedContainerScanner; }
}
