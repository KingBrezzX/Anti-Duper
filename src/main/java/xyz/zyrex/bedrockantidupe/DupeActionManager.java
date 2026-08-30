package xyz.zyrex.bedrockantidupe;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles the response after a transaction has been confirmed
 * as a duplication.
 *
 * The manager supports:
 * - removing confirmed duplicated items
 * - preventing repeated removal of the same transaction
 * - Discord notification
 *
 * It intentionally does not punish players based on suspicion alone.
 */
public final class DupeActionManager {

    private final BedrockAntiDupe plugin;
    private final DiscordAlertManager discord;

    private final Map<String, Long> processedTransactions =
            new ConcurrentHashMap<>();

    public DupeActionManager(
            BedrockAntiDupe plugin,
            DiscordAlertManager discord
    ) {
        this.plugin = plugin;
        this.discord = discord;
    }

    /**
     * Handles a confirmed duplication.
     */
    public void handleConfirmedDupe(
            Player player,
            DupeDetector.DetectionResult result,
            String source
    ) {

        if (player == null || result == null) {
            return;
        }

        if (!result.isConfirmedSuspicious()) {
            return;
        }

        String transactionId =
                result.transactionId();

        if (transactionId == null
                || transactionId.isBlank()) {
            return;
        }

        /*
         * Prevent the same transaction from being processed
         * multiple times.
         */
        if (processedTransactions.putIfAbsent(
                transactionId,
                System.currentTimeMillis()
        ) != null) {
            return;
        }

        Material material =
                result.material();

        int amount =
                result.amount();

        int removed =
                removeItems(
                        player,
                        material,
                        amount
                );

        String action;

        if (removed >= amount) {
            action =
                    "CONFIRMED DUPE: removed "
                            + removed
                            + "x "
                            + material.name();
        } else if (removed > 0) {
            action =
                    "PARTIAL REMOVAL: removed "
                            + removed
                            + "/"
                            + amount
                            + "x "
                            + material.name();
        } else {
            action =
                    "NO ITEM REMOVED: suspicious quantity "
                            + amount
                            + "x "
                            + material.name()
                            + " was no longer present.";
        }

        /*
         * Refresh inventory after the protection action.
         */
        player.updateInventory();

        /*
         * Discord notification is asynchronous.
         */
        discord.sendDupeAlert(
                player.getName(),
                player.getUniqueId().toString(),
                detectPlatform(player),
                material.name(),
                removed,
                source == null
                        ? "UNKNOWN"
                        : source,
                player.getLocation(),
                action
        );

        /*
         * Optional in-game notification.
         */
        if (plugin.getConfig().getBoolean(
                "actions.notify-player",
                false
        )) {

            player.sendMessage(
                    color(
                            plugin.getConfig().getString(
                                    "messages.dupe-detected",
                                    "&c[AntiDupe] Suspicious duplicated item removed."
                            )
                    )
            );
        }
    }

    /**
     * Removes only the confirmed suspicious amount.
     *
     * It does not clear the player's whole inventory.
     */
    public int removeItems(
            Player player,
            Material material,
            int amount
    ) {

        if (player == null
                || material == null
                || amount <= 0) {
            return 0;
        }

        int remaining = amount;
        int removed = 0;

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
                    || item.getType() != material) {
                continue;
            }

            int stackAmount =
                    item.getAmount();

            int take =
                    Math.min(
                            stackAmount,
                            remaining
                    );

            if (take >= stackAmount) {

                player.getInventory()
                        .setItem(
                                slot,
                                null
                        );

            } else {

                item.setAmount(
                        stackAmount - take
                );

                player.getInventory()
                        .setItem(
                                slot,
                                item
                        );
            }

            remaining -= take;
            removed += take;
        }

        /*
         * Also inspect offhand.
         */
        if (remaining > 0) {

            ItemStack offhand =
                    player.getInventory()
                            .getItemInOffHand();

            if (offhand != null
                    && offhand.getType() == material) {

                int stackAmount =
                        offhand.getAmount();

                int take =
                        Math.min(
                                stackAmount,
                                remaining
                        );

                if (take >= stackAmount) {

                    player.getInventory()
                            .setItemInOffHand(
                                    null
                            );

                } else {

                    offhand.setAmount(
                            stackAmount - take
                    );

                    player.getInventory()
                            .setItemInOffHand(
                                    offhand
                            );
                }

                remaining -= take;
                removed += take;
            }
        }

        return removed;
    }

    /**
     * Removes a specific quantity from a player.
     */
    public int removeItems(
            UUID playerId,
            Material material,
            int amount
    ) {

        if (playerId == null) {
            return 0;
        }

        Player player =
                Bukkit.getPlayer(
                        playerId
                );

        if (player == null
                || !player.isOnline()) {
            return 0;
        }

        return removeItems(
                player,
                material,
                amount
        );
    }

    /**
     * Prevents old transaction IDs from growing forever.
     */
    public void cleanup(
            long maxAgeMillis
    ) {

        long now =
                System.currentTimeMillis();

        processedTransactions
                .entrySet()
                .removeIf(
                        entry ->
                                now - entry.getValue()
                                        > maxAgeMillis
                );
    }

    /**
     * Clears processed transaction state.
     */
    public void clear() {

        processedTransactions.clear();
    }

    private String detectPlatform(
            Player player
    ) {

        /*
         * The plugin is currently built for the Bedrock/Geyser
         * environment. If Floodgate is installed, a more precise
         * platform check can be connected later.
         */
        if (Bukkit.getPluginManager()
                .getPlugin("Floodgate") != null) {

            return "Bedrock/Geyser";
        }

        return "Java";
    }

    private String color(
            String message
    ) {

        if (message == null) {
            return "";
        }

        return message.replace(
                "&",
                "§"
        );
    }
              }
