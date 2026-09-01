package xyz.zyrex.bedrockantidupe;

import java.util.Map;

/**
 * Transaction-scoped conservation arithmetic.
 *
 * A key that appears only after a transaction is not automatically evidence
 * of duplication. It may be unrelated state, a legitimate world pickup, or
 * another transaction. Explicit provenance should use correlatedNetDelta.
 */
public final class ConservationMath {
    private ConservationMath() {}

    /**
     * Convenience calculation for a key that exists in the transaction's
     * BEFORE state. Keys with no BEFORE provenance are ignored.
     */
    public static int netDelta(Map<String, Integer> beforePlayer,
                               Map<String, Integer> afterPlayer,
                               Map<String, Integer> beforeContainer,
                               Map<String, Integer> afterContainer,
                               String key) {
        if (!isBaselineKey(beforePlayer, beforeContainer, key)) return 0;
        return rawNetDelta(beforePlayer, afterPlayer, beforeContainer, afterContainer, key);
    }

    /**
     * Calculates a conservation delta only when the caller has explicitly
     * established that the key belongs to this transaction.
     */
    public static int correlatedNetDelta(Map<String, Integer> beforePlayer,
                                         Map<String, Integer> afterPlayer,
                                         Map<String, Integer> beforeContainer,
                                         Map<String, Integer> afterContainer,
                                         String key,
                                         boolean correlated) {
        if (!correlated) return 0;
        return rawNetDelta(beforePlayer, afterPlayer, beforeContainer, afterContainer, key);
    }

    public static int playerDelta(Map<String, Integer> before, Map<String, Integer> after, String key) {
        return amount(after, key) - amount(before, key);
    }

    public static int containerDelta(Map<String, Integer> before, Map<String, Integer> after, String key) {
        return amount(after, key) - amount(before, key);
    }

    private static boolean isBaselineKey(Map<String, Integer> beforePlayer,
                                          Map<String, Integer> beforeContainer,
                                          String key) {
        if (key == null) return false;
        return (beforePlayer != null && beforePlayer.containsKey(key))
                || (beforeContainer != null && beforeContainer.containsKey(key));
    }

    private static int rawNetDelta(Map<String, Integer> beforePlayer,
                                   Map<String, Integer> afterPlayer,
                                   Map<String, Integer> beforeContainer,
                                   Map<String, Integer> afterContainer,
                                   String key) {
        return amount(afterPlayer, key) + amount(afterContainer, key)
                - amount(beforePlayer, key) - amount(beforeContainer, key);
    }

    private static int amount(Map<String, Integer> values, String key) {
        if (values == null || key == null) return 0;
        return Math.max(0, values.getOrDefault(key, 0));
    }
}
