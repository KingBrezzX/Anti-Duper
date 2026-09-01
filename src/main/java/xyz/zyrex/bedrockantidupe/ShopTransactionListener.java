package xyz.zyrex.bedrockantidupe;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks shop-related inventory transactions.
 *
 * This class records the transaction context. It does NOT assume
 * that every shop click is a sale or a dupe.
 *
 * IMPORTANT:
 * Different shop plugins use different inventories and APIs.
 * Therefore this listener uses configurable title keywords and
 * records the observed transaction context for later correlation.
 */
public final class ShopTransactionListener implements Listener {

    private final BedrockAntiDupe plugin;
    private final TransactionLedger ledger;

    private final Map<UUID, PendingShopTransaction> pending =
            new ConcurrentHashMap<>();

    public ShopTransactionListener(
            BedrockAntiDupe plugin,
            TransactionLedger ledger
    ) {
        this.plugin = plugin;
        this.ledger = ledger;
    }

    @EventHandler(
            priority = EventPriority.MONITOR,
            ignoreCancelled = true
    )
    public void onInventoryClick(
            InventoryClickEvent event
    ) {

        if (!(event.getWhoClicked()
                instanceof Player player)) {
            return;
        }

        String title =
                event.getView()
                        .getTitle();

        if (!isShopInventory(title)) {
            return;
        }

        String transactionId =
                UUID.randomUUID().toString();

        ItemStack cursor =
                event.getCursor();

        ItemStack current =
                event.getCurrentItem();

        String itemType =
                resolveItemType(
                        current,
                        cursor
                );

        int amount =
                resolveAmount(
                        current,
                        cursor
                );

        PendingShopTransaction transaction =
                new PendingShopTransaction(
                        transactionId,
                        player.getUniqueId(),
                        itemType,
                        amount,
                        title,
                        event.getRawSlot(),
                        System.currentTimeMillis()
                );

        pending.put(
                player.getUniqueId(),
                transaction
        );
    }

    @EventHandler(
            priority = EventPriority.MONITOR,
            ignoreCancelled = true
    )
    public void onInventoryClose(
            InventoryCloseEvent event
    ) {

        if (!(event.getPlayer()
                instanceof Player player)) {
            return;
        }

        PendingShopTransaction transaction =
                pending.remove(
                        player.getUniqueId()
                );

        if (transaction == null) {
            return;
        }

        /*
         * Keep the transaction context available to the rest of
         * the anti-dupe pipeline. The actual money value should
         * come from the shop/economy integration, not from an
         * arbitrary guess here.
         */
        if (plugin.getConfig().getBoolean(
                "shop.record-context",
                true
        )) {

            plugin.getLogger().fine(
                    "Recorded shop transaction "
                            + transaction.transactionId()
                            + " for "
                            + player.getName()
            );
        }
    }

    /**
     * Records an externally supplied economy transaction.
     *
     * This method is intended to be called by a dedicated
     * shop/economy integration when it knows the exact price.
     */
    public void recordEconomyTransaction(
            EconomyTransaction transaction
    ) {

        if (transaction == null) {
            return;
        }

        if (!transaction.rollbackEligible()) {
            return;
        }

        String key =
                transaction.transactionId()
                        .toString();

        if (plugin.getDatabaseManager() != null) plugin.getDatabaseManager().recordEconomy(transaction);
        plugin.getLogger().fine(
                "Registered economy transaction " + key + " for " + transaction.playerId()
        );
    }

    /**
     * Returns the current pending shop transaction for a player.
     */
    public PendingShopTransaction getPending(
            UUID playerId
    ) {

        if (playerId == null) {
            return null;
        }

        return pending.get(
                playerId
        );
    }

    /**
     * Removes a pending transaction.
     */
    public void clear(
            UUID playerId
    ) {

        if (playerId != null) {
            pending.remove(
                    playerId
            );
        }
    }

    /**
     * Clears all pending transactions.
     */
    public void clearAll() {

        pending.clear();
    }

    /**
     * Removes stale pending records.
     */
    public void cleanup(
            long maxAgeMillis
    ) {

        long now =
                System.currentTimeMillis();

        pending.entrySet()
                .removeIf(
                        entry ->
                                now
                                        - entry.getValue()
                                        .timestamp()
                                        > maxAgeMillis
                );
    }

    /**
     * Determines whether an inventory title looks like a shop.
     *
     * Keywords are configurable so this can work with different
     * shop/menu plugins.
     */
    private boolean isShopInventory(
            String title
    ) {

        if (title == null
                || title.isBlank()) {
            return false;
        }

        String normalized =
                stripFormatting(
                        title
                ).toLowerCase();

        var keywords =
                plugin.getConfig()
                        .getStringList(
                                "shop.title-keywords"
                        );

        if (keywords.isEmpty()) {

            keywords =
                    java.util.List.of(
                            "shop",
                            "store",
                            "market",
                            "sell",
                            "buy",
                            "order",
                            "auction"
                    );
        }

        for (String keyword :
                keywords) {

            if (keyword == null
                    || keyword.isBlank()) {
                continue;
            }

            if (normalized.contains(
                    stripFormatting(
                            keyword
                    ).toLowerCase()
            )) {
                return true;
            }
        }

        return false;
    }

    private String resolveItemType(
            ItemStack current,
            ItemStack cursor
    ) {

        if (current != null
                && !current.getType()
                .isAir()) {

            return current.getType()
                    .name();
        }

        if (cursor != null
                && !cursor.getType()
                .isAir()) {

            return cursor.getType()
                    .name();
        }

        return "UNKNOWN";
    }

    private int resolveAmount(
            ItemStack current,
            ItemStack cursor
    ) {

        if (current != null
                && !current.getType()
                .isAir()) {

            return current.getAmount();
        }

        if (cursor != null
                && !cursor.getType()
                .isAir()) {

            return cursor.getAmount();
        }

        return 0;
    }

    private String stripFormatting(
            String text
    ) {

        if (text == null) {
            return "";
        }

        return text
                .replaceAll(
                        "§[0-9a-fk-or]",
                        ""
                )
                .replaceAll(
                        "&#[A-Fa-f0-9]{6}",
                        ""
                );
    }

    public record PendingShopTransaction(

            String transactionId,

            UUID playerId,

            String itemType,

            int amount,

            String shopTitle,

            int slot,

            long timestamp

    ) {

        public boolean isShulker() {

            return itemType != null
                    && itemType
                    .toUpperCase()
                    .endsWith(
                            "_SHULKER_BOX"
                    );
        }
    }
    }
