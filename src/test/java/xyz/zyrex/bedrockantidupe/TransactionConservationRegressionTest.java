package xyz.zyrex.bedrockantidupe;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TransactionConservationRegressionTest {
    @Test
    void normalPlayerToContainerTransferIsNotNetPositive() {
        assertEquals(0, ConservationMath.netDelta(10, 0, 0, 10));
    }

    @Test
    void duplicatedTrackedItemProducesPositiveConservationDelta() {
        int delta = ConservationMath.netDelta(10, 11, 0, 0);
        assertEquals(1, delta);
        assertTrue(delta > 0);
    }

    @Test
    void conservationDeltaSupportsBothSidesOfATransfer() {
        assertEquals(-2, ConservationMath.netDelta(10, 8, 4, 4));
        assertEquals(2, ConservationMath.netDelta(4, 4, 10, 12));
    }

    @Test
    void fingerprintHashChangesWhenSerializedItemStateChanges() {
        String a = ItemFingerprint.sha256Serialized(
                new byte[] {1, 2, 3},
                java.util.List.of("minecraft:custom_data")
        );
        String b = ItemFingerprint.sha256Serialized(
                new byte[] {1, 2, 4},
                java.util.List.of("minecraft:custom_data")
        );
        assertNotEquals(a, b);
        assertEquals(a, ItemFingerprint.sha256Serialized(
                new byte[] {1, 2, 3},
                java.util.List.of("minecraft:custom_data")
        ));
    }
}
