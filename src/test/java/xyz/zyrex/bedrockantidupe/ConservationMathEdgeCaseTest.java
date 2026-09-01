package xyz.zyrex.bedrockantidupe;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConservationMathEdgeCaseTest {
    @Test
    void absentItemCountsAsZero() {
        assertEquals(0, ConservationMath.netDelta(
                Map.of(), Map.of(), Map.of(), Map.of(), "DIAMOND"));
    }

    @Test
    void unrelatedItemsDoNotCreateDelta() {
        assertEquals(0, ConservationMath.netDelta(
                Map.of("IRON", 10),
                Map.of("IRON", 10),
                Map.of(),
                Map.of("DIAMOND", 5),
                "DIAMOND"));
    }

    @Test
    void negativeDeltaIsNotDuplication() {
        assertTrue(ConservationMath.netDelta(
                Map.of("DIAMOND", 10),
                Map.of("DIAMOND", 9),
                Map.of(),
                Map.of(),
                "DIAMOND") < 0);
    }
}
