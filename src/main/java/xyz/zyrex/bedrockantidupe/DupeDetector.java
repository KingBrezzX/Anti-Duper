package xyz.zyrex.bedrockantidupe;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public final class DupeDetector {

    private final BedrockAntiDupe plugin;
    private final TransactionLedger ledger;

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

    private void loadTrackedMaterials() {

        trackedMaterials.clear();

        /*
         * Shulker protection.
         *
         * This catches every vanilla colored shulker box because
         * all of them use the *_SHULKER_BOX material naming scheme.
         */
        if (plugin.getConfig().getBoolean(
                "shulker.enabled",
                true
        )) {

            for (Material material : Material.values()) {

                if (isShulker(material)) {
                    trackedMaterials.add(material);
                }
            }
        }

        /*
         * Optional configured materials.
         */
        List<String> configured =
                plugin.getConfig()
                        .getStringList(
                                "detection.tracked-materials"
                        );

        for (String name : configured) {

            if (name == null || name.isBlank()) {
                continue;
            }

            try {

                Material material =
                        Material.valueOf(
                                name.toUpperCase()
                        );

                trackedMaterials.add(material);

            } catch (IllegalArgumentException ignored) {

                plugin.getLogger().warning(
                        "Unknown tracked material: "
                                + name
                );
            }
        }
    }

    /**
     * Inspects one completed inventory transaction.
     */
    public DetectionResult inspect(
            TransactionLedger.TransactionRecord record
    ) {

        if (record == null) {

            return DetectionResult.clean(
                    "No transaction."
            );
        }

        if (!plugin.getConfig().getBoolean(
                "detection.enabled",
                true
        )) {

            return DetectionResult.clean(
                    "Detection disabled."
            );
        }

        List<Change> suspicious =
                new ArrayList<>();

        for (TransactionLedger.ItemChange change :
                record.changes()) {

            ItemStack before =
                    change.before();

            ItemStack after =
                    change.after();

            if (after == null
                    || after.getType().isAir()) {
                continue;
            }

            Material material =
                    after.getType();

            /*
             * We only perform the high-confidence inventory
             * increase check on tracked materials.
             */
            if (!trackedMaterials.contains(material)) {
                continue;
            }

            int beforeAmount =
                    before == null
                            ? 0
                            : before.getAmount();

            int afterAmount =
                    after.getAmount();

            int increase =
                    afterAmount
                            - beforeAmount;

            if (increase <= 0) {
                continue;
            }

            if (isShulker(material)
                    && plugin.getConfig()
                    .getBoolean(
                            "shulker.inspect-container-transactions",
                            true
                    )) {

                suspicious.add(
                        new Change(
                                change.slot(),
                                material,
                                increase,
                                "SHULKER_TRANSACTION"
                        )
                );

                continue;
            }

            int threshold =
                    Math.max(
                            1,
                            plugin.getConfig()
                                    .getInt(
                                            "detection.instant-increase-threshold",
                                            256
                                    )
                    );

            if (increase >= threshold) {

                suspicious.add(
                        new Change(
                                change.slot(),
                                material,
                                increase,
                                "LARGE_INVENTORY_INCREASE"
                        )
                );
            }
        }

        /*
         * No suspicious inventory increase.
         */
        if (suspicious.isEmpty()) {

            return DetectionResult.clean(
                    "No confirmed suspicious increase."
            );
        }

        /*
         * IMPORTANT:
         *
         * A suspicious inventory increase is NOT automatically
         * treated as a confirmed dupe.
         *
         * The action layer must correlate this result with
         * additional evidence before deleting anything.
         */
        return DetectionResult.suspicious(
                record,
                suspicious
        );
    }

    public boolean isShulker(
            ItemStack item
    ) {

        return item != null
                && isShulker(
                        item.getType()
                );
    }

    public boolean isShulker(
            Material material
    ) {

        return material != null
                && material.name()
                .endsWith(
                        "_SHULKER_BOX"
                );
    }

    public Set<Material> getTrackedMaterials() {

        return Collections.unmodifiableSet(
                trackedMaterials
        );
    }

    public void reload() {

        loadTrackedMaterials();
    }

    public record Change(

            int slot,

            Material material,

            int increase,

            String reason

    ) {
    }

    public record DetectionResult(

            boolean suspicious,

            boolean confirmed,

            TransactionLedger.TransactionRecord transaction,

            List<Change> changes,

            String reason

    ) {

        public DetectionResult {

            changes =
                    List.copyOf(changes);
        }

        public static DetectionResult clean(
                String reason
        ) {

            return new DetectionResult(
                    false,
                    false,
                    null,
                    List.of(),
                    reason
            );
        }

        public static DetectionResult suspicious(
                TransactionLedger.TransactionRecord record,
                List<Change> changes
        ) {

            return new DetectionResult(
                    true,
                    false,
                    record,
                    changes,
                    "Suspicious inventory increase."
            );
        }

        /**
         * Creates a confirmed result only after another
         * subsystem has independently established that the
         * transaction is a duplicate.
         */
        public DetectionResult confirm(
                String confirmationReason
        ) {

            return new DetectionResult(
                    true,
                    true,
                    transaction,
                    changes,
                    confirmationReason
            );
        }

        public boolean isConfirmedSuspicious() {

            return suspicious && confirmed;
        }

        public int totalIncrease() {

            int total = 0;

            for (Change change : changes) {
                total += change.increase();
            }

            return total;
        }
    }
                }
