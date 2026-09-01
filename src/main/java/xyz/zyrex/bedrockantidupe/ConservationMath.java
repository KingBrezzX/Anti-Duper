package xyz.zyrex.bedrockantidupe;

import java.util.Map;

/** Pure arithmetic used by unit tests and transaction validation. */
public final class ConservationMath {
    private ConservationMath() {}

    public static int netDelta(Map<String, Integer> beforePlayer,
                               Map<String, Integer> afterPlayer,
                               Map<String, Integer> beforeContainer,
                               Map<String, Integer> afterContainer,
                               String key) {
        return amount(afterPlayer, key) + amount(afterContainer, key)
                - amount(beforePlayer, key) - amount(beforeContainer, key);
    }

    public static int playerDelta(Map<String, Integer> before, Map<String, Integer> after, String key) {
        return amount(after, key) - amount(before, key);
    }

    public static int containerDelta(Map<String, Integer> before, Map<String, Integer> after, String key) {
        return amount(after, key) - amount(before, key);
    }

    private static int amount(Map<String, Integer> values, String key) {
        if (values == null || key == null) return 0;
        return Math.max(0, values.getOrDefault(key, 0));
    }
}
