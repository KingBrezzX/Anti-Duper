package xyz.zyrex.bedrockantidupe;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/** Minimal live Paper API smoke checks. Must run on the server main thread. */
final class PaperRuntimeSelfTest {
    private PaperRuntimeSelfTest() {}

    static boolean run(BedrockAntiDupe plugin) {
        try {
            ItemStack one = new ItemStack(Material.DIAMOND, 1);
            ItemStack two = one.clone();
            two.setAmount(2);

            String a = ItemFingerprint.sha256(one);
            String b = ItemFingerprint.sha256(two);
            if (a.equals(b)) {
                plugin.getLogger().severe("[AntiDupe] Runtime self-test failed: item fingerprint did not change with amount.");
                return false;
            }

            if (one.getDataTypes() == null) {
                plugin.getLogger().severe("[AntiDupe] Runtime self-test failed: Paper Data Component API unavailable.");
                return false;
            }

            plugin.getLogger().info("[AntiDupe] Paper runtime self-test: PASS (ItemStack + Data Components + fingerprint).");
            return true;
        } catch (Throwable ex) {
            plugin.getLogger().severe("[AntiDupe] Paper runtime self-test FAILED: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            return false;
        }
    }
}
