package xyz.zyrex.bedrockantidupe;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Pure unit tests; Bukkit/Paper registry state is tested by the Paper runtime smoke test. */
class TransactionConservationRegressionTest {
    @Test
    void normalPlayerToContainerTransferIsNotNetPositive() {
        var beforePlayer = Map.of("DIAMOND", 10);
        var afterPlayer = Map.<String, Integer>of();
        var beforeContainer = Map.<String, Integer>of();
        var afterContainer = Map.of("DIAMOND", 10);
        assertEquals(0, ConservationMath.netDelta(beforePlayer, afterPlayer, beforeContainer, afterContainer, "DIAMOND"));
        assertEquals(-10, ConservationMath.playerDelta(beforePlayer, afterPlayer, "DIAMOND"));
        assertEquals(10, ConservationMath.containerDelta(beforeContainer, afterContainer, "DIAMOND"));
    }

    @Test
    void duplicatedTrackedItemProducesPositiveConservationDelta() {
        var before = Map.of("DIAMOND", 10);
        var after = Map.of("DIAMOND", 11);
        assertEquals(1, ConservationMath.netDelta(before, after, Map.of(), Map.of(), "DIAMOND"));
        assertTrue(ConservationMath.netDelta(before, after, Map.of(), Map.of(), "DIAMOND") > 0);
    }

    @Test
    void fingerprintChangesWhenSerializedItemStateChanges() {
        byte[] a = new byte[] {1, 2, 3, 4};
        byte[] b = new byte[] {1, 2, 3, 5};
        assertNotEquals(ItemFingerprint.sha256Bytes(a), ItemFingerprint.sha256Bytes(b));
    }
}
