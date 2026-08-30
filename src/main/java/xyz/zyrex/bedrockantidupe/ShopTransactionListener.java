package xyz.zyrex.bedrockantidupe;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ShopTransactionListener
 *
 * Covers shop-related inventory transactions.
 *
 * IMPORTANT:
 * This class does not directly modify money or execute shop commands.
 * It creates a before/after validation boundary around shop activity.
 *
 * It also watches common shop/sell/order command names because
 * different shop plugins expose different command APIs.
 *
 * The exact commands are configurable in config.yml.
 */
public final class ShopTransactionListener
        implements Listener {

    private final BedrockAntiDupe plugin;
    private final DupeDetector detector;

    private final Set<UUID> pendingShop =
            ConcurrentHashMap.newKeySet();

    private final ConcurrentHashMap<UUID, Long> commandCooldown =
            new ConcurrentHashMap<>();

    public ShopTransactionListener(
            BedrockAntiDupe plugin,
            DupeDetector detector
    ) {
        this.plugin = plugin;
        this.detector = detector;
    }

    /**
     * Detect shop GUI opening.
     *
     * The plugin does not assume a particular shop plugin.
     * It uses configurable inventory title keywords.
     */
    @EventHandler(
            priority = EventPriority.LOWEST,
            ignoreCancelled = true
    )
    public void onShopClickBefore(
            InventoryClickEvent event
    ) {

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (!plugin.isProtected(player)) {
            return;
        }

        if (!plugin.getConfig().getBoolean(
                "shop.enabled",
                true
        )) {
            return;
        }

        if (!isShopInventory(
                event.getView().getTitle()
        )) {
            return;
        }

        /*
         * Only snapshot when the transaction actually contains
         * an item relevant to anti-dupe protection.
         */
        if (containsProtectedItem(
                event.getCurrentItem()
        )
                || containsProtectedItem(
                event.getCursor()
        )
                || containsProtectedItem(
                event.getWhoClicked()
                        .getInventory()
                        .getItemInMainHand()
        )) {

            detector.begin(player);

            pendingShop.add(
                    player.getUniqueId()
            );
        }
    }

    /**
     * Validate shop click after Bukkit processes it.
     */
    @EventHandler(
            priority = EventPriority.MONITOR,
            ignoreCancelled = true
    )
    public void onShopClickAfter(
            InventoryClickEvent event
    ) {

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (!plugin.isProtected(player)) {
            return;
        }

        if (!plugin.getConfig().getBoolean(
                "shop.enabled",
                true
        )) {
            return;
        }

        if (!pendingShop.contains(
                player.getUniqueId()
        )) {
            return;
        }

        if (!isShopInventory(
                event.getView().getTitle()
        )) {
            return;
        }

        detector.validateLater(
                player,
                "shop GUI transaction"
        );

        pendingShop.remove(
                player.getUniqueId()
        );
    }

    /**
     * Shop GUI drag transaction.
     */
    @EventHandler(
            priority = EventPriority.LOWEST,
            ignoreCancelled = true
    )
    public void onShopDragBefore(
            InventoryDragEvent event
    ) {

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (!plugin.isProtected(player)) {
            return;
        }

        if (!plugin.getConfig().getBoolean(
                "shop.enabled",
                true
        )) {
            return;
        }

        if (!isShopInventory(
                event.getView().getTitle()
        )) {
            return;
        }

        for (ItemStack item :
                event.getNewItems().values()) {

            if (!containsProtectedItem(item)) {
                continue;
            }

            detector.begin(player);

            pendingShop.add(
                    player.getUniqueId()
            );

            return;
        }
    }

    @EventHandler(
            priority = EventPriority.MONITOR,
            ignoreCancelled = true
    )
    public void onShopDragAfter(
            InventoryDragEvent event
    ) {

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (!plugin.isProtected(player)) {
            return;
        }

        if (!pendingShop.remove(
                player.getUniqueId()
        )) {
            return;
        }

        detector.validateLater(
                player,
                "shop GUI drag transaction"
        );
    }

    /**
     * Validate when the shop GUI closes.
     *
     * This catches plugins that perform the actual sale/buy operation
     * during close rather than click.
     */
    @EventHandler(
            priority = EventPriority.MONITOR,
            ignoreCancelled = true
    )
    public void onShopClose(
            InventoryCloseEvent event
    ) {

        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        if (!plugin.isProtected(player)) {
            return;
        }

        if (!plugin.getConfig().getBoolean(
                "shop.validate-on-close",
                true
        )) {
            return;
        }

        if (!isShopInventory(
                event.getView().getTitle()
        )) {
            return;
        }

        detector.validateLater(
                player,
                "shop GUI close validation"
        );

        pendingShop.remove(
                player.getUniqueId()
        );
    }

    /**
     * Common command-based shop protection.
     *
     * Examples that can be configured:
     *
     * /shop
     * /sell
     * /sellall
     * /ah
     * /order
     *
     * The listener does not execute or cancel these commands.
     * It only establishes a validation boundary.
     */
    @EventHandler(
            priority = EventPriority.LOWEST,
            ignoreCancelled = true
    )
    public void onShopCommandBefore(
            PlayerCommandPreprocessEvent event
    ) {

        Player player =
                event.getPlayer();

        if (!plugin.isProtected(player)) {
            return;
        }

        if (!plugin.getConfig().getBoolean(
                "shop.command-detection",
                true
        )) {
            return;
        }

        String command =
                extractCommand(
                        event.getMessage()
                );

        if (command.isBlank()) {
            return;
        }

        if (!isConfiguredShopCommand(
                command
        )) {
            return;
        }

        long now =
                System.currentTimeMillis();

        long cooldown =
                plugin.getConfig().getLong(
                        "shop.command-cooldown-ms",
                        250L
                );

        Long previous =
                commandCooldown.put(
                        player.getUniqueId(),
                        now
                );

        if (previous != null
                && now - previous < cooldown) {

            return;
        }

        /*
         * Before /sell, /shop, /order, etc.
         */
        detector.begin(player);

        pendingShop.add(
                player.getUniqueId()
        );
    }

    /**
     * A command can execute through another plugin after
     * PlayerCommandPreprocessEvent. Schedule the comparison
     * one tick later.
     */
    @EventHandler(
            priority = EventPriority.MONITOR,
            ignoreCancelled = true
    )
    public void onShopCommandAfter(
            PlayerCommandPreprocessEvent event
    ) {

        Player player =
                event.getPlayer();

        if (!plugin.isProtected(player)) {
            return;
        }

        if (!plugin.getConfig().getBoolean(
                "shop.command-detection",
                true
        )) {
            return;
        }

        String command =
                extractCommand(
                        event.getMessage()
                );

        if (!isConfiguredShopCommand(
                command
        )) {
            return;
        }

        detector.validateLater(
                player,
                "shop command: /" + command
        );

        pendingShop.remove(
                player.getUniqueId()
        );
    }

    /**
     * Checks whether the inventory title belongs to a configured
     * shop interface.
     */
    private boolean isShopInventory(
            String title
    ) {

        if (title == null || title.isBlank()) {
            return false;
        }

        String normalized =
                stripColor(
                        title
                ).toLowerCase(
                        Locale.ROOT
                );

        var keywords =
                plugin.getConfig()
                        .getStringList(
                                "shop.inventory-title-keywords"
                        );

        if (keywords.isEmpty()) {

            keywords = java.util.List.of(
                    "shop",
                    "sell",
                    "buy",
                    "order",
                    "auction",
                    "market",
                    "store"
            );
        }

        for (String keyword :
                keywords) {

            if (keyword == null
                    || keyword.isBlank()) {
                continue;
            }

            if (normalized.contains(
                    keyword.toLowerCase(
                            Locale.ROOT
                    )
            )) {
                return true;
            }
        }

        return false;
    }

    /**
     * Reads the first command token.
     *
     * "/sell all diamond" -> "sell"
     */
    private String extractCommand(
            String message
    ) {

        if (message == null) {
            return "";
        }

        String value =
                message.trim();

        if (value.startsWith("/")) {
            value = value.substring(1);
        }

        int space =
                value.indexOf(' ');

        if (space >= 0) {
            value =
                    value.substring(
                            0,
                            space
                    );
        }

        int colon =
                value.indexOf(':');

        /*
         * Handles namespaced commands such as:
         * /plugin:sell
         */
        if (colon >= 0) {
            value =
                    value.substring(
                            colon + 1
                    );
        }

        return value.toLowerCase(
                Locale.ROOT
        );
    }

    /**
     * Configurable command list.
     */
    private boolean isConfiguredShopCommand(
            String command
    ) {

        if (command == null
                || command.isBlank()) {
            return false;
        }

        var commands =
                plugin.getConfig()
                        .getStringList(
                                "shop.commands"
                        );

        if (commands.isEmpty()) {

            commands = java.util.List.of(
                    "shop",
                    "sell",
                    "sellall",
                    "sellhand",
                    "buy",
                    "order",
                    "orders",
                    "ah",
                    "auction",
                    "market",
                    "store"
            );
        }

        for (String configured :
                commands) {

            if (configured == null
                    || configured.isBlank()) {
                continue;
            }

            String normalized =
                    configured
                            .toLowerCase(
                                    Locale.ROOT
                            )
                            .replace(
                                    "/",
                                    ""
                            );

            if (normalized.equals(
                    command
            )) {
                return true;
            }
        }

        return false;
    }

    private boolean containsProtectedItem(
            ItemStack item
    ) {

        return item != null
                && !item.getType().isAir()
                && plugin.isProtectedItem(
                item
        );
    }

    private String stripColor(
            String text
    ) {

        if (text == null) {
            return "";
        }

        return org.bukkit.ChatColor
                .stripColor(text)
                .replace(
                        "§",
                        ""
                );
    }

    /**
     * Runtime cleanup.
     */
    public void clear() {

        pendingShop.clear();
        commandCooldown.clear();
    }
          }
