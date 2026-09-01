package xyz.zyrex.bedrockantidupe;

import org.bukkit.inventory.ItemStack;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Stable, content-aware fingerprint for forensic comparison. */
public final class ItemFingerprint {
    private ItemFingerprint() {}

    public static String of(ItemStack item) {
        if (item == null || item.getType().isAir()) return "AIR";
        try {
            Map<String, Object> data = new TreeMap<>();
            data.putAll(item.serialize());
            data.remove("amount");
            return sha256(canonical(data));
        } catch (Exception ex) {
            return item.getType().name() + ":" + String.valueOf(item.getItemMeta());
        }
    }

    private static String canonical(Object value) {
        if (value instanceof Map<?, ?> map) {
            List<String> parts = new ArrayList<>();
            for (Map.Entry<?, ?> e : map.entrySet()) parts.add(String.valueOf(e.getKey()) + "=" + canonical(e.getValue()));
            Collections.sort(parts);
            return "{" + String.join(",", parts) + "}";
        }
        if (value instanceof Collection<?> c) {
            List<String> parts = c.stream().map(ItemFingerprint::canonical).toList();
            return "[" + String.join(",", parts) + "]";
        }
        return String.valueOf(value);
    }

    private static String sha256(String input) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder(64);
        for (byte b : digest) out.append(String.format(Locale.ROOT, "%02x", b));
        return out.toString();
    }
}
