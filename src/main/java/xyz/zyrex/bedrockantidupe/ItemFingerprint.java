package xyz.zyrex.bedrockantidupe;

import org.bukkit.inventory.ItemStack;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;

/** Paper 26.2 item identity using the full serialized item state, including modern data components. */
public final class ItemFingerprint {
    private ItemFingerprint() {}

    public static String sha256(ItemStack item) {
        if (item == null || item.getType().isAir()) return "AIR";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            // serializeAsBytes is the canonical Paper/Bukkit NBT representation and therefore
            // includes the runtime data-component state. getDataTypes() is included as an
            // explicit component inventory so component additions cannot silently collapse IDs.
            String types = item.getDataTypes().stream().map(String::valueOf).sorted().reduce((a,b)->a+"\n"+b).orElse("");
            md.update(types.getBytes(StandardCharsets.UTF_8));
            md.update((byte) 0);
            md.update(item.serializeAsBytes());
            return HexFormat.of().formatHex(md.digest());
        } catch (Exception ex) {
            try {
                MessageDigest fallback = MessageDigest.getInstance("SHA-256");
                return HexFormat.of().formatHex(fallback.digest(item.toString().getBytes(StandardCharsets.UTF_8)));
            } catch (Exception ignored) {
                return Integer.toHexString(item.toString().hashCode());
            }
        }
    }

    public static String canonical(ItemStack item) {
        return item == null || item.getType().isAir() ? "AIR" : item.getType().name()+"|"+item.getAmount()+"|"+sha256(item);
    }

    public static String base64(ItemStack item) {
        return item == null ? "" : Base64.getEncoder().encodeToString(item.serializeAsBytes());
    }
}
