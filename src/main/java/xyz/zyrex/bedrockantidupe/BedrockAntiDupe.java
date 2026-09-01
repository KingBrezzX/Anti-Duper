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
    private BukkitTask maintenanceTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("messages.yml", false);

        transactionLedger = new TransactionLedger(this);
        discordAlertManager = new DiscordAlertManager(this);
        evidenceManager = new EvidenceManager(this);
        dupeDetector = new DupeDetector(this, transactionLedger);
        dupeActionManager = new DupeActionManager(this, discordAlertManager, evidenceManager);
        economyRollbackManager = new EconomyRollbackManager(this, discordAlertManager);
        exploitProtectionListener = new ExploitProtectionListener(this, transactionLedger, dupeDetector, dupeActionManager);
        shopTransactionListener = new ShopTransactionListener(this, transactionLedger);

        Bukkit.getPluginManager().registerEvents(exploitProtectionListener, this);
        Bukkit.getPluginManager().registerEvents(shopTransactionListener, this);
        registerCommands();
        startMaintenanceTask();

        getLogger().info("BedrockAntiDupe 2.1.0 enabled | Paper 26.2 | plugin bytecode target Java 21");
        getLogger().info("Detection: " + getConfig().getBoolean("detection.enabled", true));
        getLogger().info("Shulker protection: " + getConfig().getBoolean("shulker.enabled", true));
        getLogger().info("Vault economy: " + economyRollbackManager.isAvailable());
        getLogger().info("Discord webhook: " + getConfig().getBoolean("discord.enabled", false));
    }

    @Override
    public void onDisable() {
        if (maintenanceTask != null) maintenanceTask.cancel();
        if (exploitProtectionListener != null) exploitProtectionListener.clear();
        if (shopTransactionListener != null) shopTransactionListener.clearAll();
        if (dupeActionManager != null) dupeActionManager.clear();
        if (evidenceManager != null) evidenceManager.clear();
        if (economyRollbackManager != null) economyRollbackManager.clear();
        if (discordAlertManager != null) discordAlertManager.cleanup();
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
        if (economyRollbackManager != null) economyRollbackManager.cleanup(maxAge);
    }

    public void reloadPlugin() {
        reloadConfig();
        if (dupeDetector != null) dupeDetector.reload();
        getLogger().info("Configuration reloaded.");
    }

    public void scanPlayerInventory(Player player) {
        if (player == null || !player.isOnline()) return;
        Map<org.bukkit.Material, Integer> totals = new java.util.EnumMap<>(org.bukkit.Material.class);
        for (var item : player.getInventory().getContents()) {
            if (item == null || item.getType().isAir() || !dupeDetector.getTrackedMaterials().contains(item.getType())) continue;
            totals.merge(item.getType(), item.getAmount(), Integer::sum);
        }
        getLogger().info("Inventory scan for " + player.getName() + ": " + totals);
    }

    public TransactionLedger getTransactionLedger() { return transactionLedger; }
    public DupeDetector getDupeDetector() { return dupeDetector; }
    public DiscordAlertManager getDiscordAlertManager() { return discordAlertManager; }
    public DupeActionManager getDupeActionManager() { return dupeActionManager; }
    public EconomyRollbackManager getEconomyRollbackManager() { return economyRollbackManager; }
    public EvidenceManager getEvidenceManager() { return evidenceManager; }
    public ExploitProtectionListener getExploitProtectionListener() { return exploitProtectionListener; }
    public ShopTransactionListener getShopTransactionListener() { return shopTransactionListener; }
}
