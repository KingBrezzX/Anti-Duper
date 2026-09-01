package xyz.zyrex.bedrockantidupe;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RollbackResultTest {
    @Test
    void successfulRollbackReportsRemainingZero() {
        UUID id = UUID.randomUUID();
        RollbackResult result = RollbackResult.success(id, 100.0, 100.0);
        assertTrue(result.isSuccess());
        assertTrue(result.changedEconomy());
        assertEquals(0.0, result.remainingAmount(), 0.000001);
    }

    @Test
    void rollbackAmountsAreSafeAndFinite() {
        UUID id = UUID.randomUUID();
        RollbackResult result = new RollbackResult(id, RollbackResult.Status.PARTIAL, 10.0, 99.0, "x");
        assertEquals(10.0, result.rolledBackAmount(), 0.000001);

        RollbackResult nan = new RollbackResult(id, RollbackResult.Status.FAILED, Double.NaN, Double.POSITIVE_INFINITY, null);
        assertEquals(0.0, nan.requestedAmount());
        assertEquals(0.0, nan.rolledBackAmount());
        assertEquals("", nan.message());
    }
}
