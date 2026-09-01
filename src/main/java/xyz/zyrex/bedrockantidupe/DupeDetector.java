package xyz.zyrex.bedrockantidupe;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/** Conservation-based detector. It never calls an inventory increase alone a dupe. */
public final class DupeDetector {
    private final BedrockAntiDupe plugin;
    private final TransactionLedger ledger;
    private final Set<Material> trackedMaterials = EnumSet.noneOf(Material.class);

    public DupeDetector(BedrockAntiDupe plugin, TransactionLedger ledger) {
        this.plugin = plugin;
        this.ledger = ledger;
        loadTrackedMaterials();
    }

    private void loadTrackedMaterials() {
        trackedMaterials.clear();
        if (plugin.getConfig().getBoolean("shulker.enabled", true)) {
            for (Material material : Material.values()) if (isShulker(material)) trackedMaterials.add(material);
        }
        for (String name : plugin.getConfig().getStringList("detection.tracked-materials")) {
            if (name == null || name.isBlank()) continue;
            try { trackedMaterials.add(Material.valueOf(name.trim().toUpperCase(Locale.ROOT))); }
            catch (IllegalArgumentException ex) { plugin.getLogger().warning("Unknown tracked material: " + name); }
        }
    }

    public DetectionResult inspect(TransactionLedger.TransactionRecord record) {
        if (record == null) return DetectionResult.clean("No transaction.");
        if (!plugin.getConfig().getBoolean("settings.enabled", true)
                || !plugin.getConfig().getBoolean("detection.enabled", true)) {
            return DetectionResult.clean("Detection disabled.");
        }

        List<Change> suspicious = new ArrayList<>();
        int configuredThreshold = Math.max(1,
                plugin.getConfig().getInt("detection.instant-increase-threshold", 1));

        for (Material material : trackedMaterials) {
            int net = record.netDelta(material);
            int playerDelta = record.playerDelta(material);
            if (net <= 0 || playerDelta <= 0) continue;

            // Only the portion that actually appeared in the player's inventory
            // is eligible for removal. This prevents the action layer from ever
            // removing pre-existing items when the conservation break happened
            // elsewhere.
            int suspiciousAmount = Math.min(net, playerDelta);
            if (suspiciousAmount <= 0) continue;
            String reason = net >= configuredThreshold
                    ? "CONSERVATION_BREAK"
                    : "NET_POSITIVE_TRACKED_ITEM";
            suspicious.add(new Change(-1, material, suspiciousAmount, reason));
        }

        if (suspicious.isEmpty()) return DetectionResult.clean("Inventory conservation preserved.");

        boolean confirmed = true;
        return new DetectionResult(true, confirmed, record, suspicious,
                "Tracked inventory conservation was broken; no corresponding container reduction was observed.");
    }

    public boolean isShulker(ItemStack item) { return item != null && isShulker(item.getType()); }
    public boolean isShulker(Material material) {
        return material != null && material.name().endsWith("_SHULKER_BOX");
    }
    public Set<Material> getTrackedMaterials() { return Collections.unmodifiableSet(trackedMaterials); }
    public void reload() { loadTrackedMaterials(); }

    public record Change(int slot, Material material, int increase, String reason) {}

    public record DetectionResult(boolean suspicious, boolean confirmed,
                                  TransactionLedger.TransactionRecord transaction,
                                  List<Change> changes, String reason) {
        public DetectionResult { changes = List.copyOf(changes); }
        public static DetectionResult clean(String reason) {
            return new DetectionResult(false, false, null, List.of(), reason);
        }
        public boolean isConfirmedSuspicious() { return suspicious && confirmed; }
        public int totalIncrease() { return changes.stream().mapToInt(Change::increase).sum(); }
    }
}
