package xyz.zyrex.bedrockantidupe;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DupeDetector
 *
 * Coordinates short-lived inventory snapshots around risky
 * operations. It deliberately does not continuously scan worlds.
 *
 * Detection is conservative:
 * - snapshot before a risky operation
 * - wait for Bukkit/Paper to finish the transaction
 * - compare the resulting inventory
 * - only report unexpected increases of protected materials
 *
 * This class does not directly delete legitimate items.
 * BedrockAntiDupe decides the configured response.
 */
public final class DupeDetector {

    private final BedrockAntiDupe plugin;
    private final TransactionLedger ledger;

    private final Map<UUID, Long> pendingValidation =
            new ConcurrentHashMap<>();

    private final Map<UUID, Long> lastViolation =
            new ConcurrentHashMap<>();

    public DupeDetector(
            BedrockAntiDupe plugin,
            TransactionLedger ledger
    ) {
        this.plugin = plugin;
        this.ledger = ledger;
    }

    /**
     * Capture the player's inventory before a risky operation.
     */
    public void begin(Player player) {

        if (player == null || !player.isOnline()) {
            return;
        }

        if (!plugin.isProtected(player)) {
            return;
        }

        ledger.snapshot(player);
    }

    /**
     * Schedule validation after the current server transaction.
     *
     * One validation per player is enough for a short window,
     * preventing event storms from generating dozens of tasks.
     */
    public void validateLater(
            Player player,
            String reason
    ) {

        if (player == null || !player.isOnline()) {
            return;
        }

        if (!plugin.isProtected(player)) {
            return;
        }

        long now = System.currentTimeMillis();

        Long previous =
                pendingValidation.put(
                        player.getUniqueId(),
                        now
                );

        if (previous != null) {
            return;
        }

        /*
         * One tick lets Bukkit/Paper finish the inventory
         * operation before the comparison is performed.
         */
        Bukkit.getScheduler().runTask(
                plugin,
                () -> {

                    pendingValidation.remove(
                            player.getUniqueId()
                    );

                    if (!player.isOnline()) {
                        return;
                    }

                    validate(
                            player,
                            reason
                    );
                }
        );
    }

    /**
     * Compare the latest snapshot with the current inventory.
     */
    public void validate(
            Player player,
            String reason
    ) {

        if (player == null || !player.isOnline()) {
            return;
        }

        TransactionLedger.InventorySnapshot before =
                ledger.getSnapshot(
                        player.getUniqueId()
                );

        /*
         * There is no before-state. Create one and wait for
         * the next risky transaction instead of guessing.
         */
        if (before == null) {
            ledger.snapshot(player);
            return;
        }

        TransactionLedger.TransactionResult result =
                ledger.compare(player);

        if (!result.valid()) {
            return;
        }

        if (!ledger.hasUnexpectedProtectedIncrease(result)) {
            return;
        }

        Map<Material, Integer> increases =
                ledger.getProtectedIncreases(result);

        StringBuilder details =
                new StringBuilder();

        for (Map.Entry<Material, Integer> entry :
                increases.entrySet()) {

            if (details.length() > 0) {
                details.append(", ");
            }

            details.append(entry.getKey().name())
                    .append(" +")
                    .append(entry.getValue());
        }

        /*
         * Avoid repeated reports from the same inventory state.
         */
        long now = System.currentTimeMillis();

        long cooldown =
                plugin.getConfig().getLong(
                        "settings.transaction-cooldown-ms",
                        150
                );

        Long previous =
                lastViolation.get(
                        player.getUniqueId()
                );

        if (previous != null
                && now - previous < cooldown) {

            return;
        }

        lastViolation.put(
                player.getUniqueId(),
                now
        );

        plugin.handleViolation(
                player,
                reason
                        + " - unexpected protected item increase: "
                        + details
        );

        /*
         * Take a fresh baseline after handling the event.
         * This prevents the same state being reported repeatedly.
         */
        ledger.snapshot(player);
    }

    /**
     * Explicitly establish a fresh baseline.
     */
    public void reset(Player player) {

        if (player == null) {
            return;
        }

        pendingValidation.remove(
                player.getUniqueId()
        );

        lastViolation.remove(
                player.getUniqueId()
        );

        ledger.snapshot(player);
    }

    /**
     * Remove all state for a player.
     */
    public void remove(Player player) {

        if (player == null) {
            return;
        }

        UUID uuid = player.getUniqueId();

        pendingValidation.remove(uuid);
        lastViolation.remove(uuid);
        ledger.remove(uuid);
    }

    /**
     * Clear detector state.
     */
    public void clear() {

        pendingValidation.clear();
        lastViolation.clear();
        ledger.clear();
    }
            }
