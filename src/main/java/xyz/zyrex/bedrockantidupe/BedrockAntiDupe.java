package xyz.zyrex.bedrockantidupe;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class BedrockAntiDupe extends JavaPlugin {

    private TransactionLedger transactionLedger;
    private DupeDetector dupeDetector;
    private DiscordAlertManager discordAlertManager;
    private DupeActionManager dupeActionManager;
    private EconomyRollbackManager economyRollbackManager;
    private ExploitProtectionListener exploitProtectionListener;
    private ShopTransactionListener shopTransactionListener;

    @Override
    public void onEnable() {

        saveDefaultConfig();

        getLogger().info(
                "========================================"
        );
        getLogger().info(
                "       BedrockAntiDupe 2.0.0"
        );
        getLogger().info(
                "       Starting protection system..."
        );
        getLogger().info(
                "========================================"
        );

        /*
         * Core transaction ledger.
         */
        transactionLedger =
                new TransactionLedger(this);

        /*
         * Discord notification system.
         */
        discordAlertManager =
                new DiscordAlertManager(this);

        /*
         * Dupe detection.
         */
        dupeDetector =
                new DupeDetector(
                        this,
                        transactionLedger
                );

        /*
         * Actions after confirmed detection.
         */
        dupeActionManager =
                new DupeActionManager(
                        this,
                        discordAlertManager
                );

        /*
         * Economy rollback.
         */
        economyRollbackManager =
                new EconomyRollbackManager(
                        this,
                        discordAlertManager
                );

        /*
         * Inventory/container protection.
         */
        exploitProtectionListener =
                new ExploitProtectionListener(
                        this,
                        transactionLedger,
                        dupeDetector,
                        dupeActionManager
                );

        /*
         * Shop transaction tracking.
         */
        shopTransactionListener =
                new ShopTransactionListener(
                        this,
                        transactionLedger
                );

        registerListeners();
        registerCommands();
        startMaintenanceTask();

        getLogger().info(
                "BedrockAntiDupe 2.0.0 enabled."
        );

        getLogger().info(
                "Detection: "
                        + getConfig().getBoolean(
                                "detection.enabled",
                                true
                        )
        );

        getLogger().info(
                "Shop tracking: "
                        + getConfig().getBoolean(
                                "shop.record-context",
                                true
                        )
        );

        getLogger().info(
                "Economy rollback: "
                        + economyRollbackManager.isAvailable()
        );

        getLogger().info(
                "Discord webhook: "
                        + getConfig().getBoolean(
                                "discord.enabled",
                                false
                        )
        );
    }

    @Override
    public void onDisable() {

        if (exploitProtectionListener != null) {
            exploitProtectionListener.clear();
        }

        if (shopTransactionListener != null) {
            shopTransactionListener.clearAll();
        }

        if (dupeActionManager != null) {
            dupeActionManager.clear();
        }

        if (economyRollbackManager != null) {
            economyRollbackManager.clear();
        }

        getLogger().info(
                "BedrockAntiDupe disabled."
        );
    }

    private void registerListeners() {

        Bukkit.getPluginManager()
                .registerEvents(
                        exploitProtectionListener,
                        this
                );

        Bukkit.getPluginManager()
                .registerEvents(
                        shopTransactionListener,
                        this
                );
    }

    private void registerCommands() {

        var command =
                getCommand("antidupe");

        if (command == null) {

            getLogger().severe(
                    "Command 'antidupe' is missing from plugin.yml!"
            );

            return;
        }

        AntiDupeCommand executor =
                new AntiDupeCommand(this);

        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    /**
     * Periodic maintenance.
     *
     * Runs asynchronously only for cache cleanup.
     * Bukkit inventory/economy operations remain on the
     * main server thread.
     */
    private void startMaintenanceTask() {

        long interval =
                Math.max(
                        20L,
                        getConfig().getLong(
                                "maintenance.interval-ticks",
                                1200L
                        )
                );

        Bukkit.getScheduler()
                .runTaskTimer(
                        this,
                        this::cleanupCaches,
                        interval,
                        interval
                );
    }

    /**
     * Cleans temporary anti-dupe state.
     */
    public void cleanupCaches() {

        long maxAge =
                Math.max(
                        60_000L,
                        getConfig().getLong(
                                "maintenance.cache-max-age-ms",
                                300_000L
                        )
                );

        if (exploitProtectionListener != null) {

            exploitProtectionListener.cleanup(
                    maxAge
            );
        }

        if (shopTransactionListener != null) {

            shopTransactionListener.cleanup(
                    maxAge
            );
        }

        if (dupeActionManager != null) {

            dupeActionManager.cleanup(
                    maxAge
            );
        }

        if (economyRollbackManager != null) {

            economyRollbackManager.cleanup(
                    maxAge
            );
        }
    }

    /**
     * Manual player inventory scan.
     *
     * This does not automatically delete items merely because
     * they are valuable. It checks the inventory and reports
     * suspicious conditions to the protection pipeline.
     */
    public void scanPlayerInventory(
            Player player
    ) {

        if (player == null
                || !player.isOnline()) {
            return;
        }

        if (!getConfig().getBoolean(
                "detection.enabled",
                true
        )) {
            return;
        }

        /*
         * We inspect tracked materials only.
         * A scan alone is NOT proof of duplication.
         */
        int suspiciousStacks = 0;

        for (var item :
                player.getInventory()
                        .getContents()) {

            if (item == null
                    || item.getType().isAir()) {
                continue;
            }

            if (!dupeDetector
                    .getTrackedMaterials()
                    .contains(
                            item.getType()
                    )) {
                continue;
            }

            suspiciousStacks++;
        }

        getLogger().info(
                "Inventory scan for "
                        + player.getName()
                        + ": "
                        + suspiciousStacks
                        + " tracked stack(s)."
        );
    }

    public TransactionLedger getTransactionLedger() {
        return transactionLedger;
    }

    public DupeDetector getDupeDetector() {
        return dupeDetector;
    }

    public DiscordAlertManager getDiscordAlertManager() {
        return discordAlertManager;
    }

    public DupeActionManager getDupeActionManager() {
        return dupeActionManager;
    }

    public EconomyRollbackManager getEconomyRollbackManager() {
        return economyRollbackManager;
    }

    public ExploitProtectionListener getExploitProtectionListener() {
        return exploitProtectionListener;
    }

    public ShopTransactionListener getShopTransactionListener() {
        return shopTransactionListener;
    }
        }
