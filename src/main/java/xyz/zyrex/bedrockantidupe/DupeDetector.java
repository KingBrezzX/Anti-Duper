package xyz.zyrex.bedrockantidupe;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Detects suspicious inventory duplication by comparing
 * transaction snapshots.
 *
 * Important:
 * This class does NOT punish a player merely because an item
 * increased. Normal gameplay can legitimately increase items.
 *
 * A confirmed dupe requires the transaction to be marked
 * suspicious by the surrounding listeners/checks.
 */
public final class DupeDetector {

    private final BedrockAntiDupe plugin;

    private final TransactionLedger ledger;

    private final Set<UUID> checking =
            ConcurrentHashMap.newKeySet();

    private final Set<Material> trackedMaterials =
            EnumSet.noneOf(Material.class);

    public DupeDetector(
            BedrockAntiDupe plugin,
            TransactionLedger ledger
    ) {

        this.plugin = plugin;
        this.ledger = ledger;

        loadTrackedMaterials();
    }

    /**
     * Loads materials that are especially useful for
     * duplication detection.
     *
     * Shulker boxes are included regardless of color.
     */
    private void loadTrackedMaterials() {

        trackedMaterials.clear();

        for (Material material :
                Material.values()) {

            String name =
                    material.name();

            if (name.endsWith(
                    "_SHULKER_BOX"
            )) {

                trackedMaterials.add(
                        material
                );
            }
        }

        addIfExists("DIAMOND");
        addIfExists("EMERALD");
        addIfExists("NETHERITE_INGOT");
        addIfExists("NETHERITE_BLOCK");
        addIfExists("ANCIENT_DEBRIS");
        addIfExists("GOLD_INGOT");
        addIfExists("IRON_INGOT");
    }

    private void addIfExists(
            String name
    ) {

        try {

            trackedMaterials.add(
                    Material.valueOf(name)
            );

        } catch (IllegalArgumentException ignored) {
            // Material does not exist on this server version.
        }
    }

    /**
     * Checks a completed transaction.
     *
     * Returns a detection result instead of immediately
     * deleting items.
     */
    public DetectionResult inspect(
            TransactionLedger.TransactionRecord transaction
    ) {

        if (transaction == null) {

            return DetectionResult.clean(
                    null,
                    null,
                    "No transaction."
            );
        }

        UUID playerId =
                transaction.playerId();

        if (playerId == null) {

            return DetectionResult.clean(
                    transaction.transactionId(),
                    null,
                    "No player UUID."
            );
        }

        /*
         * Prevent recursive checks caused by inventory changes
         * made by the protection system itself.
         */
        if (!checking.add(playerId)) {

            return DetectionResult.clean(
                    transaction.transactionId(),
                    playerId,
                    "Already checking player."
            );
        }

        try {

            for (Material material :
                    trackedMaterials) {

                int delta =
                        transaction.materialDelta(
                                material
                        );

                if (delta <= 0) {
                    continue;
                }

                if (looksSuspicious(
                        transaction,
                        material,
                        delta
                )) {

                    return DetectionResult.suspicious(
                            transaction.transactionId(),
                            playerId,
                            material,
                            delta,
                            reason(
                                    transaction,
                                    material,
                                    delta
                            )
                    );
                }
            }

            return DetectionResult.clean(
                    transaction.transactionId(),
                    playerId,
                    "No suspicious inventory duplication detected."
            );

        } finally {

            checking.remove(playerId);
        }
    }

    /**
     * Performs additional conservative checks.
     */
    private boolean looksSuspicious(
            TransactionLedger.TransactionRecord transaction,
            Material material,
            int delta
    ) {

        String source =
                transaction.source();

        if (source == null) {
            source = "";
        }

        source =
                source.toUpperCase();

        /*
         * These sources require deeper transaction correlation.
         * A simple inventory increase must not automatically be
         * considered a confirmed dupe.
         */
        boolean sensitiveSource =
                source.contains("SHOP")
                        || source.contains("SELL")
                        || source.contains("ORDER")
                        || source.contains("SHULKER")
                        || source.contains("PISTON")
                        || source.contains("CLICK")
                        || source.contains("CONTAINER")
                        || source.contains("CRAFT");

        /*
         * Extremely large instantaneous increases are suspicious,
         * but the actual threshold is configurable.
         */
        int threshold =
                Math.max(
                        1,
                        plugin.getConfig().getInt(
                                "detection.instant-increase-threshold",
                                256
                        )
                );

        if (delta >= threshold) {
            return true;
        }

        /*
         * Sensitive transactions are returned for correlation
         * by the higher-level detection pipeline.
         */
        return sensitiveSource
                && delta > 0
                && plugin.getConfig().getBoolean(
                        "detection.inspect-sensitive-transactions",
                        true
                );
    }

    private String reason(
            TransactionLedger.TransactionRecord transaction,
            Material material,
            int delta
    ) {

        return "Suspicious increase of "
                + delta
                + "x "
                + material.name()
                + " during "
                + transaction.source()
                + " transaction.";
    }

    /**
     * Checks whether a material is a tracked shulker.
     */
    public boolean isShulker(
            Material material
    ) {

        return material != null
                && material.name()
                .endsWith("_SHULKER_BOX");
    }

    /**
     * Checks whether an ItemStack is a shulker box.
     *
     * This automatically covers every vanilla shulker color
     * available on the server version.
     */
    public boolean isShulker(
            ItemStack item
    ) {

        return item != null
                && isShulker(
                        item.getType()
                );
    }

    /**
     * Checks a player's current inventory for tracked materials.
     */
    public int count(
            Player player,
            Material material
    ) {

        if (player == null
                || material == null) {

            return 0;
        }

        int total = 0;

        for (ItemStack item :
                player.getInventory()
                        .getContents()) {

            if (item != null
                    && item.getType() == material) {

                total += item.getAmount();
            }
        }

        return total;
    }

    /**
     * Returns a copy of the tracked material set.
     */
    public Set<Material>
    getTrackedMaterials() {

        return Set.copyOf(
                trackedMaterials
        );
    }

    /**
     * Manually marks a transaction as confirmed suspicious.
     *
     * This is intended for specialized exploit listeners,
     * not ordinary inventory events.
     */
    public DetectionResult confirm(
            TransactionLedger.TransactionRecord transaction,
            Material material,
            int amount,
            String reason
    ) {

        if (transaction == null
                || material == null
                || amount <= 0) {

            return DetectionResult.clean(
                    transaction == null
                            ? null
                            : transaction.transactionId(),
                    transaction == null
                            ? null
                            : transaction.playerId(),
                    "Invalid confirmation."
            );
        }

        return DetectionResult.suspicious(
                transaction.transactionId(),
                transaction.playerId(),
                material,
                amount,
                reason == null
                        ? "Confirmed suspicious transaction."
                        : reason
        );
    }

    /**
     * Result returned by the detector.
     */
    public record DetectionResult(

            String transactionId,

            UUID playerId,

            Material material,

            int amount,

            boolean suspicious,

            String reason

    ) {

        public static DetectionResult suspicious(
                String transactionId,
                UUID playerId,
                Material material,
                int amount,
                String reason
        ) {

            return new DetectionResult(
                    transactionId,
                    playerId,
                    material,
                    amount,
                    true,
                    reason
            );
        }

        public static DetectionResult clean(
                String transactionId,
                UUID playerId,
                String reason
        ) {

            return new DetectionResult(
                    transactionId,
                    playerId,
                    null,
                    0,
                    false,
                    reason
            );
        }

        public boolean isConfirmedSuspicious() {

            return suspicious
                    && material != null
                    && amount > 0;
        }
    }
                }
