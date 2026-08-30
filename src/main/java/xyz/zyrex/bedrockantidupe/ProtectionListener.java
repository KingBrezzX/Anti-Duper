package xyz.zyrex.bedrockantidupe;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ProtectionListener
 *
 * Connects risky Bukkit events to DupeDetector.
 *
 * IMPORTANT:
 * This listener does not continuously scan chunks or worlds.
 * It only creates short-lived checks when a relevant event occurs.
 */
public final class ProtectionListener implements Listener {

    private final BedrockAntiDupe plugin;
    private final DupeDetector detector;

    private final Map<UUID, Long> inventoryOpen =
            new ConcurrentHashMap<>();

    private final Map<UUID, Long> lastContainerEvent =
            new ConcurrentHashMap<>();

    public ProtectionListener(
            BedrockAntiDupe plugin,
            DupeDetector detector
    ) {
        this.plugin = plugin;
        this.detector = detector;
    }

    /**
     * Player joins/reconnects.
     *
     * Establish a clean inventory baseline after the player
     * has completely entered the server.
     */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {

        Player player = event.getPlayer();

        if (!plugin.isProtected(player)) {
            return;
        }

        Bukkit.getScheduler().runTaskLater(
                plugin,
                () -> {

                    if (!player.isOnline()) {
                        return;
                    }

                    detector.reset(player);

                },
                20L
        );
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {

        Player player = event.getPlayer();

        inventoryOpen.remove(
                player.getUniqueId()
        );

        lastContainerEvent.remove(
                player.getUniqueId()
        );

        detector.remove(player);
    }

    /**
     * Inventory opened.
     *
     * Establish a snapshot before the player can move items.
     */
    @EventHandler(
            priority = EventPriority.MONITOR,
            ignoreCancelled = true
    )
    public void onInventoryOpen(
            InventoryOpenEvent event
    ) {

        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        if (!plugin.isProtected(player)) {
            return;
        }

        if (!plugin.getConfig().getBoolean(
                "inventory.open",
                true
        )) {
            return;
        }

        inventoryOpen.put(
                player.getUniqueId(),
                System.currentTimeMillis()
        );

        detector.begin(player);
    }

    /**
     * Inventory close.
     *
     * Validate after the inventory transaction has finished.
     */
    @EventHandler(
            priority = EventPriority.MONITOR,
            ignoreCancelled = true
    )
    public void onInventoryClose(
            InventoryCloseEvent event
    ) {

        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        if (!plugin.isProtected(player)) {
            return;
        }

        if (!plugin.getConfig().getBoolean(
                "inventory.close-open",
                true
        )) {
            return;
        }

        detector.validateLater(
                player,
                "inventory close validation"
        );

        inventoryOpen.remove(
                player.getUniqueId()
        );
    }

    /**
     * Inventory click.
     *
     * Snapshot before the transaction.
     */
    @EventHandler(
            priority = EventPriority.LOWEST,
            ignoreCancelled = true
    )
    public void onInventoryClickBefore(
            InventoryClickEvent event
    ) {

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (!plugin.isProtected(player)) {
            return;
        }

        if (!plugin.getConfig().getBoolean(
                "detection.inventory",
                true
        )) {
            return;
        }

        if (containsProtectedItem(event)) {

            detector.begin(player);
        }
    }

    /**
     * Inventory click validation after Bukkit has processed it.
     */
    @EventHandler(
            priority = EventPriority.MONITOR,
            ignoreCancelled = true
    )
    public void onInventoryClickAfter(
            InventoryClickEvent event
    ) {

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (!plugin.isProtected(player)) {
            return;
        }

        if (!plugin.getConfig().getBoolean(
                "inventory.transaction-validation",
                true
        )) {
            return;
        }

        if (containsProtectedItem(event)) {

            detector.validateLater(
                    player,
                    "inventory click transaction"
            );
        }
    }

    /**
     * Inventory drag.
     */
    @EventHandler(
            priority = EventPriority.LOWEST,
            ignoreCancelled = true
    )
    public void onInventoryDragBefore(
            InventoryDragEvent event
    ) {

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (!plugin.isProtected(player)) {
            return;
        }

        if (!plugin.getConfig().getBoolean(
                "inventory.drag",
                true
        )) {
            return;
        }

        for (ItemStack item :
                event.getNewItems().values()) {

            if (plugin.isProtectedItem(item)) {

                detector.begin(player);
                return;
            }
        }
    }

    @EventHandler(
            priority = EventPriority.MONITOR,
            ignoreCancelled = true
    )
    public void onInventoryDragAfter(
            InventoryDragEvent event
    ) {

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (!plugin.isProtected(player)) {
            return;
        }

        if (!plugin.getConfig().getBoolean(
                "inventory.drag",
                true
        )) {
            return;
        }

        for (ItemStack item :
                event.getNewItems().values()) {

            if (plugin.isProtectedItem(item)) {

                detector.validateLater(
                        player,
                        "inventory drag transaction"
                );

                return;
            }
        }
    }

    /**
     * Item dropped.
     */
    @EventHandler(
            priority = EventPriority.LOWEST,
            ignoreCancelled = true
    )
    public void onDropBefore(
            PlayerDropItemEvent event
    ) {

        Player player = event.getPlayer();

        if (!plugin.isProtected(player)) {
            return;
        }

        if (!plugin.getConfig().getBoolean(
                "inventory.drop",
                true
        )) {
            return;
        }

        if (plugin.isProtectedItem(
                event.getItemDrop().getItemStack()
        )) {

            detector.begin(player);
        }
    }

    @EventHandler(
            priority = EventPriority.MONITOR,
            ignoreCancelled = true
    )
    public void onDropAfter(
            PlayerDropItemEvent event
    ) {

        Player player = event.getPlayer();

        if (!plugin.isProtected(player)) {
            return;
        }

        if (!plugin.getConfig().getBoolean(
                "inventory.drop",
                true
        )) {
            return;
        }

        if (plugin.isProtectedItem(
                event.getItemDrop().getItemStack()
        )) {

            detector.validateLater(
                    player,
                    "protected item drop"
            );
        }
    }

    /**
     * Item pickup.
     */
    @EventHandler(
            priority = EventPriority.LOWEST,
            ignoreCancelled = true
    )
    public void onPickupBefore(
            PlayerPickupItemEvent event
    ) {

        Player player = event.getPlayer();

        if (!plugin.isProtected(player)) {
            return;
        }

        if (!plugin.getConfig().getBoolean(
                "inventory.pickup",
                true
        )) {
            return;
        }

        if (plugin.isProtectedItem(
                event.getItem().getItemStack()
        )) {

            detector.begin(player);
        }
    }

    @EventHandler(
            priority = EventPriority.MONITOR,
            ignoreCancelled = true
    )
    public void onPickupAfter(
            PlayerPickupItemEvent event
    ) {

        Player player = event.getPlayer();

        if (!plugin.isProtected(player)) {
            return;
        }

        if (!plugin.getConfig().getBoolean(
                "inventory.pickup",
                true
        )) {
            return;
        }

        if (plugin.isProtectedItem(
                event.getItem().getItemStack()
        )) {

            detector.validateLater(
                    player,
                    "protected item pickup"
            );
        }
    }

    /**
     * Shulker/container block break.
     */
    @EventHandler(
            priority = EventPriority.MONITOR,
            ignoreCancelled = true
    )
    public void onBlockBreak(
            BlockBreakEvent event
    ) {

        Player player = event.getPlayer();
        Block block = event.getBlock();

        if (!plugin.isProtected(player)) {
            return;
        }

        Material material = block.getType();

        if (isShulker(material)
                && plugin.getConfig().getBoolean(
                "detection.shulker",
                true
        )) {

            detector.begin(player);

            detector.validateLater(
                    player,
                    "shulker block break"
            );

            return;
        }

        if (isContainer(material)
                && plugin.getConfig().getBoolean(
                "detection.chest",
                true
        )) {

            detector.begin(player);

            detector.validateLater(
                    player,
                    "container block break"
            );
        }
    }

    /**
     * Piston extension.
     *
     * We establish a baseline for nearby protected players
     * when a piston touches a protected block.
     */
    @EventHandler(
            priority = EventPriority.MONITOR,
            ignoreCancelled = true
    )
    public void onPistonExtend(
            BlockPistonExtendEvent event
    ) {

        if (!plugin.getConfig().getBoolean(
                "piston.enabled",
                true
        )) {
            return;
        }

        for (Block block :
                event.getBlocks()) {

            if (!isProtectedBlock(block)) {
                continue;
            }

            checkNearbyPlayers(
                    block,
                    "piston extension affecting protected block"
            );
        }
    }

    /**
     * Piston retraction.
     */
    @EventHandler(
            priority = EventPriority.MONITOR,
            ignoreCancelled = true
    )
    public void onPistonRetract(
            BlockPistonRetractEvent event
    ) {

        if (!plugin.getConfig().getBoolean(
                "piston.enabled",
                true
        )) {
            return;
        }

        for (Block block :
                event.getBlocks()) {

            if (!isProtectedBlock(block)) {
                continue;
            }

            checkNearbyPlayers(
                    block,
                    "piston retraction affecting protected block"
            );
        }
    }

    /**
     * Entity explosion.
     */
    @EventHandler(
            priority = EventPriority.MONITOR,
            ignoreCancelled = true
    )
    public void onEntityExplosion(
            EntityExplodeEvent event
    ) {

        if (!plugin.getConfig().getBoolean(
                "explosion.enabled",
                true
        )) {
            return;
        }

        boolean protectedBlockFound = false;

        for (Block block :
                event.blockList()) {

            if (isProtectedBlock(block)) {

                protectedBlockFound = true;

                checkNearbyPlayers(
                        block,
                        "explosion affecting protected block"
                );
            }
        }

        /*
         * If the explosion has no protected container,
         * there is no reason to run additional player checks.
         */
        if (!protectedBlockFound) {
            return;
        }
    }

    /**
     * Explosion prime.
     *
     * This catches the explosion before it happens and establishes
     * snapshots for nearby protected players.
     */
    @EventHandler(
            priority = EventPriority.MONITOR,
            ignoreCancelled = true
    )
    public void onExplosionPrime(
            ExplosionPrimeEvent event
    ) {

        if (!plugin.getConfig().getBoolean(
                "explosion.enabled",
                true
        )) {
            return;
        }

        Entity entity = event.getEntity();

        if (entity.getWorld() == null) {
            return;
        }

        double radius = 8.0;

        for (Player player :
                entity.getWorld().getPlayers()) {

            if (!plugin.isProtected(player)) {
                continue;
            }

            if (player.getLocation().distanceSquared(
                    entity.getLocation()
            ) > radius * radius) {
                continue;
            }

            detector.begin(player);
        }
    }

    /**
     * Finds players close to a piston/explosion/container event.
     *
     * Radius is intentionally small to avoid unnecessary work.
     */
    private void checkNearbyPlayers(
            Block block,
            String reason
    ) {

        if (block.getWorld() == null) {
            return;
        }

        long now = System.currentTimeMillis();

        double radius = 8.0;

        for (Player player :
                block.getWorld().getPlayers()) {

            if (!plugin.isProtected(player)) {
                continue;
            }

            if (player.getLocation().distanceSquared(
                    block.getLocation()
            ) > radius * radius) {
                continue;
            }

            UUID uuid = player.getUniqueId();

            Long previous =
                    lastContainerEvent.get(uuid);

            /*
             * Avoid repeatedly scheduling checks when a redstone
             * machine fires many piston events in a short period.
             */
            if (previous != null
                    && now - previous < 250L) {
                continue;
            }

            lastContainerEvent.put(
                    uuid,
                    now
            );

            detector.begin(player);

            detector.validateLater(
                    player,
                    reason
            );
        }
    }

    /**
     * Determines whether an inventory click involves a
     * protected item.
     */
    private boolean containsProtectedItem(
            InventoryClickEvent event
    ) {

        ItemStack current =
                event.getCurrentItem();

        ItemStack cursor =
                event.getCursor();

        if (plugin.isProtectedItem(current)) {
            return true;
        }

        if (plugin.isProtectedItem(cursor)) {
            return true;
        }

        Inventory clicked =
                event.getClickedInventory();

        if (clicked == null) {
            return false;
        }

        return containsProtectedItem(
                clicked
        );
    }

    private boolean containsProtectedItem(
            Inventory inventory
    ) {

        if (inventory == null) {
            return false;
        }

        /*
         * Do not scan huge inventories repeatedly.
         * Only the inventory involved in the transaction is checked.
         */
        for (ItemStack item :
                inventory.getContents()) {

            if (plugin.isProtectedItem(item)) {
                return true;
            }
        }

        return false;
    }

    private boolean isProtectedBlock(
            Block block
    ) {

        Material material =
                block.getType();

        if (isShulker(material)) {

            return plugin.getConfig().getBoolean(
                    "piston.protect-shulker",
                    true
            );
        }

        if (isContainer(material)) {

            return plugin.getConfig().getBoolean(
                    "piston.protect-container",
                    true
            );
        }

        return false;
    }

    private boolean isShulker(
            Material material
    ) {

        return material != null
                && material.name().contains(
                "SHULKER_BOX"
        );
    }

    private boolean isContainer(
            Material material
    ) {

        if (material == null) {
            return false;
        }

        return switch (material) {

            case CHEST,
                 TRAPPED_CHEST,
                 ENDER_CHEST,
                 BARREL,
                 HOPPER,
                 DROPPER,
                 DISPENSER,
                 FURNACE,
                 BLAST_FURNACE,
                 SMOKER,
                 CRAFTER -> true;

            default -> false;
        };
    }
          }
