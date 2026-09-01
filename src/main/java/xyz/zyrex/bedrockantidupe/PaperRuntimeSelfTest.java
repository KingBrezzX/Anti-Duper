package xyz.zyrex.bedrockantidupe;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Minimal live Paper 26.2 API smoke checks.
 * Must run on the server main thread.
 */
final class PaperRuntimeSelfTest {
    private PaperRuntimeSelfTest() {}

    static boolean run(BedrockAntiDupe plugin) {
        try {
            ItemStack one = ItemStack.of(Material.DIAMOND, 1);
            ItemStack two = one.clone();
            two.setAmount(2);

            String a = ItemFingerprint.sha256(one);
            String b = ItemFingerprint.sha256(two);
            if (a.equals(b)) {
                plugin.getLogger().severe(
                        "[AntiDupe] Runtime self-test failed: fingerprint did not change with amount.");
                return false;
            }

            if (one.getDataTypes() == null) {
                plugin.getLogger().severe(
                        "[AntiDupe] Runtime self-test failed: ItemStack Data Component API unavailable.");
                return false;
            }

            // Exercise the actual component view rather than merely checking method linkage.
            one.getDataTypes().forEach(component -> {
                if (component == null) {
                    throw new IllegalStateException("Null Data Component type returned.");
                }
            });

            plugin.getLogger().info(
                    "[AntiDupe] Paper runtime self-test: PASS "
                    + "(ItemStack + Data Components + fingerprint).");
            return true;
        } catch (Throwable ex) {
            plugin.getLogger().severe(
                    "[AntiDupe] Paper runtime self-test FAILED: "
                            + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            return false;
        }
    }
}
