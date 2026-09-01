package xyz.zyrex.bedrockantidupe;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles actions after a duplication event has been independently
 * confirmed.
 *
 * Safety rule:
 * suspicious != confirmed.
 *
 * Items are only removed when the detection result is confirmed.
 */
public final class DupeActionManager {

    private final BedrockAntiDupe plugin;
    private final DiscordAlertManager discord;
    private final EvidenceManager evidence;

    private final Map<UUID, Long> handledTransactions =
            new ConcurrentHashMap<>();

    public DupeActionManager(BedrockAntiDupe plugin, DiscordAlertManager discord) {
        this(plugin, discord, null);
    }

    public DupeActionManager(BedrockAntiDupe plugin, DiscordAlertManager discord, EvidenceManager evidence) {
        this.plugin = plugin;
        this.discord = discord;
        this.evidence = evidence;
    }

    /**
     * Handles a confirmed duplication event.
     */
    public void handleConfirmedDupe(
            Player player,
            DupeDetector.DetectionResult result,
            String source
    ) {

        if (player == null
                || result == null) {
            return;
        }

        if (!result.isConfirmedSuspicious()) {
            return;
        }

        // Only transaction sources that have a before-state captured before
        // the mutation are eligible for automatic removal.
        if (source == null || (!source.equals("INVENTORY_CLICK")
                && !source.equals("INVENTORY_DRAG")
                && !source.equals("INVENTORY_AUTOMATION"))) {
            plugin.getLogger().warning("[AntiDupe] Confirmed-looking transaction ignored for automatic removal: source=" + source);
            return;
        }

        TransactionLedger.TransactionRecord transaction =
                result.transaction();

        if (transaction == null) {
            return;
        }

        UUID transactionId =
                transaction.transactionId();

        /*
         * Prevent the same confirmed transaction from being
         * processed multiple times.
         */
        if (handledTransactions.putIfAbsent(
                transactionId,
                System.currentTimeMillis()
        ) != null) {

            return;
        }

        if (evidence != null) {
            java.util.Map<org.bukkit.Material, Integer> increases = new java.util.EnumMap<>(org.bukkit.Material.class);
            for (DupeDetector.Change change : result.changes()) {
                if (change.material() != null && change.increase() > 0) {
                    increases.merge(change.material(), change.increase(), Integer::sum);
                }
            }
            evidence.record(player, result.reason(), increases);
        }

        int removed =
                removeConfirmedItems(
                        player,
                        result
                );

        String action =
                "CONFIRMED DUPE: removed "
                        + removed
                        + " item(s)";

        if (discord != null) {

            for (DupeDetector.Change change :
                    result.changes()) {

                discord.sendDupeAlert(
                        player.getName(),
                        player.getUniqueId()
                                .toString(),
                        detectPlatform(player),
                        change.material()
                                .name(),
                        change.increase(),
                        source,
                        player.getLocation(),
                        action
                );
            }
        }

        plugin.getLogger().warning(
                "[AntiDupe] Confirmed duplication for "
                        + player.getName()
                        + " | transaction="
                        + transactionId
                        + " | removed="
                        + removed
        );

        if (plugin.getConfig().getBoolean("actions.staff-notification", true)) {
            String staffMessage = "§c[AntiDupe] §4CONFIRMED DUPE §7player=§f" + player.getName()
                    + " §7amount=§f" + result.totalIncrease()
                    + " §7source=§f" + source;
            for (Player online : plugin.getServer().getOnlinePlayers()) {
                if (online.hasPermission("bedrockantidupe.notify")) online.sendMessage(staffMessage);
            }
        }
    }

    /**
     * Removes only the amount that was identified by the
     * confirmed detector.
     *
     * It does not wipe the player's whole inventory.
     */
    private int removeConfirmedItems(Player player, DupeDetector.DetectionResult result) {
        if (!plugin.getConfig().getBoolean("actions.remove-confirmed-items", false)) return 0;
        if (result.transaction() == null) return 0;
        int totalRemoved = 0;
        java.util.List<ItemStack> removedItems = new java.util.ArrayList<>();
        java.util.Set<String> tracked = new java.util.HashSet<>();
        for (DupeDetector.Change c : result.changes()) if (c.material() != null) tracked.add(c.material().name());
        for (TransactionLedger.ItemChange change : result.transaction().changes()) {
            ItemStack after = change.after();
            if (after == null || after.getType().isAir() || !tracked.contains(after.getType().name())) continue;
            int delta = change.amountDelta();
            if (delta <= 0) continue;
            int remove = Math.min(delta, after.getAmount());
            ItemStack removed = after.clone(); removed.setAmount(remove); removedItems.add(removed);
            int slot = change.slot();
            ItemStack current = player.getInventory().getItem(slot);
            if (current == null || current.getType() != after.getType()) continue;
            int safeRemove = Math.min(remove, current.getAmount());
            current.setAmount(current.getAmount() - safeRemove);
            player.getInventory().setItem(slot, current.getAmount() <= 0 ? null : current);
            totalRemoved += safeRemove;
        }
        if (!removedItems.isEmpty() && plugin.getRecoveryManager() != null) {
            if (!plugin.getRecoveryManager().backupSync(player.getUniqueId(), result.transaction().transactionId(), removedItems, result.reason())) {
                plugin.getLogger().severe("[AntiDupe] Refusing destructive action: recovery backup was not durably written.");
                return 0;
            }
        }
        return totalRemoved;
    }

    /**
     * Removes a specific material from the player's inventory.
     *
     * This is deliberately limited to the confirmed increase,
     * rather than clearing every copy owned by the player.
     */
    private int removeMaterial(
            Player player,
            org.bukkit.Material material,
            int amount,
            java.util.List<ItemStack> removedItems
    ) {

        int remaining =
                amount;

        ItemStack[] contents =
                player.getInventory()
                        .getContents();

        for (int slot = 0;
             slot < contents.length;
             slot++) {

            if (remaining <= 0) {
                break;
            }

            ItemStack item =
                    contents[slot];

            if (item == null
                    || item.getType().isAir()) {
                continue;
            }

            if (item.getType() != material) {
                continue;
            }

            int remove = Math.min(remaining, item.getAmount());
            ItemStack removedStack = item.clone();
            removedStack.setAmount(remove);
            removedItems.add(removedStack);

            int newAmount =
                    item.getAmount()
                            - remove;

            if (newAmount <= 0) {

                player.getInventory()
                        .setItem(
                                slot,
                                null
                        );

            } else {

                item.setAmount(
                        newAmount
                );

                player.getInventory()
                        .setItem(
                                slot,
                                item
                        );
            }

            remaining -= remove;
        }

        /*
         * Return the amount actually removed.
         */
        return amount - remaining;
    }

    /**
     * Basic platform detection.
     *
     * Floodgate is checked through its Bukkit plugin presence.
     * If Floodgate is unavailable, the player is treated as Java.
     */
    private String detectPlatform(
            Player player
    ) {

        if (player == null) {
            return "UNKNOWN";
        }

        if (plugin.getServer()
                .getPluginManager()
                .getPlugin("floodgate") != null) {

            try {

                Class<?> apiClass =
                        Class.forName(
                                "org.geysermc.floodgate.api.FloodgateApi"
                        );

                Object api =
                        apiClass
                                .getMethod(
                                        "getInstance"
                                )
                                .invoke(null);

                Object bedrock =
                        apiClass
                                .getMethod(
                                        "isFloodgatePlayer",
                                        UUID.class
                                )
                                .invoke(
                                        api,
                                        player.getUniqueId()
                                );

                if (bedrock instanceof Boolean
                        && (Boolean) bedrock) {

                    return "BEDROCK";
                }

            } catch (
                    ReflectiveOperationException
                            | LinkageError ignored
            ) {

                /*
                 * Floodgate API is optional.
                 * Do not fail the anti-dupe plugin if the API
                 * changes or is unavailable.
                 */
            }
        }

        return "JAVA";
    }

    /**
     * Returns whether a transaction was already handled.
     */
    public boolean isHandled(
            UUID transactionId
    ) {

        return transactionId != null
                && handledTransactions
                .containsKey(
                        transactionId
                );
    }

    /**
     * Removes old transaction IDs from memory.
     */
    public void cleanup(
            long maxAgeMillis
    ) {

        long now =
                System.currentTimeMillis();

        handledTransactions.entrySet()
                .removeIf(
                        entry ->
                                now - entry.getValue()
                                        > maxAgeMillis
                );
    }

    public void clear() {

        handledTransactions.clear();
    }
    }
