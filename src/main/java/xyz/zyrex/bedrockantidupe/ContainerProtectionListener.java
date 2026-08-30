package xyz.zyrex.bedrockantidupe;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ContainerProtectionListener
 *
 * Protects container-related transactions that can be involved
 * in duplication chains:
 *
 * - Shulker boxes
 * - Chests
 * - Trapped chests
 * - Ender chests
 * - Hoppers
 * - Droppers
 * - Dispensers
 * - Container movement
 * - Piston movement
 * - TNT/entity explosions
 *
 * This listener is intentionally event-driven.
 * It does NOT scan every loaded chunk or every block in the world.
 */
public final class ContainerProtectionListener
        implements Listener {

    private final BedrockAntiDupe plugin;
    private final DupeDetector detector;

    private final Map<UUID, Long> eventCooldown =
            new ConcurrentHashMap<>();

    public ContainerProtectionListener(
            BedrockAntiDupe plugin,
            DupeDetector detector
    ) {
        this.plugin = plugin;
        this.detector = detector;
    }

    /**
     * Container opened.
     */
    @EventHandler(
            priority = EventPriority.LOWEST,
            ignoreCancelled = true
    )
    public void onOpen(
            InventoryOpenEvent event
    ) {

        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        if (!plugin.isProtected(player)) {
            return;
        }

        if (!isProtectedInventory(
                event.getInventory()
        )) {
            return;
        }

        detector.begin(player);
    }

    /**
     * Container click.
     *
     * Capture the state before Bukkit processes the transaction.
     */
    @EventHandler(
            priority = EventPriority.LOWEST,
            ignoreCancelled = true
    )
    public void onClickBefore(
            InventoryClickEvent event
    ) {

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (!plugin.isProtected(player)) {
            return;
        }

        if (!isProtectedInventory(
                event.getView().getTopInventory()
        )) {
            return;
        }

        detector.begin(player);
    }

    /**
     * Validate after the click.
     */
    @EventHandler(
            priority = EventPriority.MONITOR,
            ignoreCancelled = true
    )
    public void onClickAfter(
            InventoryClickEvent event
    ) {

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (!plugin.isProtected(player)) {
            return;
        }

        if (!isProtectedInventory(
                event.getView().getTopInventory()
        )) {
            return;
        }

        detector.validateLater(
                player,
                "protected container click"
        );
    }

    /**
     * Container drag.
     */
    @EventHandler(
            priority = EventPriority.LOWEST,
            ignoreCancelled = true
    )
    public void onDragBefore(
            InventoryDragEvent event
    ) {

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (!plugin.isProtected(player)) {
            return;
        }

        if (!isProtectedInventory(
                event.getView().getTopInventory()
        )) {
            return;
        }

        detector.begin(player);
    }

    @EventHandler(
            priority = EventPriority.MONITOR,
            ignoreCancelled = true
    )
    public void onDragAfter(
            InventoryDragEvent event
    ) {

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (!plugin.isProtected(player)) {
            return;
        }

        if (!isProtectedInventory(
                event.getView().getTopInventory()
        )) {
            return;
        }

        detector.validateLater(
                player,
                "protected container drag"
        );
    }

    /**
     * Hopper/dropper/dispenser item movement.
     *
     * This is important because the transaction can happen
     * without a player clicking the container.
     */
    @EventHandler(
            priority = EventPriority.MONITOR,
            ignoreCancelled = true
    )
    public void onInventoryMove(
            InventoryMoveItemEvent event
    ) {

        if (!plugin.getConfig().getBoolean(
                "detection.container-movement",
                true
        )) {
            return;
        }

        Inventory source =
                event.getSource();

        Inventory destination =
                event.getDestination();

        if (!isProtectedInventory(source)
                && !isProtectedInventory(destination)) {
            return;
        }

        checkNearbyPlayers(
                source,
                "container item movement"
        );

        checkNearbyPlayers(
                destination,
                "container item movement"
        );
    }

    /**
     * Piston extension.
     */
    @EventHandler(
            priority = EventPriority.HIGHEST,
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

            if (!isProtectedBlock(
                    block
            )) {
                continue;
            }

            /*
             * Do not let this plugin alter normal piston mechanics.
             * We establish transaction baselines and validate afterwards.
             */
            checkNearbyPlayers(
                    block,
                    "piston moved protected container"
            );
        }
    }

    /**
     * Piston retraction.
     */
    @EventHandler(
            priority = EventPriority.HIGHEST,
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

            if (!isProtectedBlock(
                    block
            )) {
                continue;
            }

            checkNearbyPlayers(
                    block,
                    "piston retracted protected container"
            );
        }
    }

    /**
     * Entity explosion.
     *
     * The listener does not cancel TNT globally.
     * It only observes whether protected blocks are involved.
     */
    @EventHandler(
            priority = EventPriority.MONITOR,
            ignoreCancelled = true
    )
    public void onEntityExplode(
            EntityExplodeEvent event
    ) {

        if (!plugin.getConfig().getBoolean(
                "explosion.enabled",
                true
        )) {
            return;
        }

        for (Block block :
                event.blockList()) {

            if (!isProtectedBlock(
                    block
            )) {
                continue;
            }

            checkNearbyPlayers(
                    block,
                    "explosion affected protected container"
            );
        }
    }

    /**
     * Block-powered explosion.
     *
     * Covers explosion sources that expose BlockExplodeEvent.
     */
    @EventHandler(
            priority = EventPriority.MONITOR,
            ignoreCancelled = true
    )
    public void onBlockExplode(
            BlockExplodeEvent event
    ) {

        if (!plugin.getConfig().getBoolean(
                "explosion.enabled",
                true
        )) {
            return;
        }

        for (Block block :
                event.blockList()) {

            if (!isProtectedBlock(
                    block
            )) {
                continue;
            }

            checkNearbyPlayers(
                    block,
                    "block explosion affected protected container"
            );
        }
    }

    /**
     * Check a container's nearby players.
     */
    private void checkNearbyPlayers(
            Inventory inventory,
            String reason
    ) {

        if (inventory == null) {
            return;
        }

        if (inventory.getLocation() == null) {
            return;
        }

        Block block =
                inventory.getLocation()
                        .getBlock();

        checkNearbyPlayers(
                block,
                reason
        );
    }

    /**
     * Check players near a protected block.
     */
    private void checkNearbyPlayers(
            Block block,
            String reason
    ) {

        if (block == null
                || block.getWorld() == null) {
            return;
        }

        double radius =
                plugin.getConfig().getDouble(
                        "detection.nearby-radius",
                        8.0
                );

        long now =
                System.currentTimeMillis();

        for (Player player :
                block.getWorld().getPlayers()) {

            if (!plugin.isProtected(player)) {
                continue;
            }

            if (player.getLocation()
                    .distanceSquared(
                            block.getLocation()
                    )
                    > radius * radius) {
                continue;
            }

            UUID uuid =
                    player.getUniqueId();

            long cooldown =
                    plugin.getConfig().getLong(
                            "detection.event-cooldown-ms",
                            250L
                    );

            Long previous =
                    eventCooldown.get(uuid);

            if (previous != null
                    && now - previous < cooldown) {
                continue;
            }

            eventCooldown.put(
                    uuid,
                    now
            );

            detector.begin(player);

            /*
             * One tick delay is enough for the normal Bukkit
             * transaction to finish before comparison.
             */
            detector.validateLater(
                    player,
                    reason
            );
        }
    }

    /**
     * Checks whether an inventory is a protected container.
     *
     * We inspect the holder instead of relying only on inventory
     * size/type, because plugins can create custom inventories.
     */
    private boolean isProtectedInventory(
            Inventory inventory
    ) {

        if (inventory == null) {
            return false;
        }

        Object holder =
                inventory.getHolder();

        if (holder instanceof ShulkerBox) {
            return plugin.getConfig().getBoolean(
                    "containers.shulker-box",
                    true
            );
        }

        if (holder instanceof org.bukkit.block.Chest) {
            return plugin.getConfig().getBoolean(
                    "containers.chest",
                    true
            );
        }

        if (holder instanceof org.bukkit.block.EnderChest) {
            return plugin.getConfig().getBoolean(
                    "containers.ender-chest",
                    true
            );
        }

        Material type =
                inventory.getType() == null
                        ? null
                        : switch (
                        inventory.getType()
                ) {
                    default -> null;
                };

        /*
         * Fallback: inspect contents for configured protected
         * container items. This avoids missing plugin-created
         * inventories without aggressively scanning everything.
         */
        if (type == null) {

            for (ItemStack item :
                    inventory.getContents()) {

                if (plugin.isProtectedItem(item)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean isProtectedBlock(
            Block block
    ) {

        if (block == null) {
            return false;
        }

        Material material =
                block.getType();

        if (material == null) {
            return false;
        }

        if (material.name()
                .contains("SHULKER_BOX")) {

            return plugin.getConfig().getBoolean(
                    "containers.shulker-box",
                    true
            );
        }

        return switch (material) {

            case CHEST,
                 TRAPPED_CHEST -> plugin
                    .getConfig()
                    .getBoolean(
                            "containers.chest",
                            true
                    );

            case ENDER_CHEST -> plugin
                    .getConfig()
                    .getBoolean(
                            "containers.ender-chest",
                            true
                    );

            case BARREL,
                 HOPPER,
                 DROPPER,
                 DISPENSER -> plugin
                    .getConfig()
                    .getBoolean(
                            "containers.other",
                            true
                    );

            default -> false;
        };
    }

    /**
     * Runtime cleanup.
     */
    public void clear() {
        eventCooldown.clear();
    }
          }
