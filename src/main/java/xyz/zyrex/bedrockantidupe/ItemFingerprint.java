package xyz.zyrex.bedrockantidupe;

import org.bukkit.inventory.ItemStack;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Collection;
import java.util.HexFormat;

/** Paper 26.2 item identity using serialized state plus the declared data-component types. */
public final class ItemFingerprint {
    private ItemFingerprint() {}

    public static String sha256(ItemStack item) {
        if (item == null || item.getType().isAir()) return "AIR";
        return sha256Serialized(
                item.serializeAsBytes(),
                item.getDataTypes().stream().map(String::valueOf).toList()
        );
    }

    /** Pure byte-level hashing used by unit tests without initializing the Bukkit Registry. */
    static String sha256Serialized(byte[] serialized, Collection<String> dataTypes) {
        if (serialized == null) throw new IllegalArgumentException("serialized item must not be null");
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            if (dataTypes != null) {
                dataTypes.stream().filter(java.util.Objects::nonNull).sorted()
                        .forEach(type -> {
                            md.update(type.getBytes(StandardCharsets.UTF_8));
                            md.update((byte) 0);
                        });
            }
            md.update((byte) 1);
            md.update(serialized);
            return HexFormat.of().formatHex(md.digest());
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    public static String canonical(ItemStack item) {
        return item == null || item.getType().isAir()
                ? "AIR"
                : item.getType().name() + "|" + item.getAmount() + "|" + sha256(item);
    }

    public static String base64(ItemStack item) {
        return item == null ? "" : Base64.getEncoder().encodeToString(item.serializeAsBytes());
    }
}
