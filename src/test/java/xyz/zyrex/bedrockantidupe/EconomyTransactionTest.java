package xyz.zyrex.bedrockantidupe;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EconomyTransactionTest {
    @Test
    void saleIsRollbackEligibleAndKeepsIdentity() {
        UUID player = UUID.randomUUID();
        EconomyTransaction tx = EconomyTransaction.sale(player, 125.50, "SHOP", "DIAMOND", 2);
        assertEquals(player, tx.playerId());
        assertTrue(tx.generatedMoney());
        assertTrue(tx.rollbackEligible());
        assertEquals(125.50, tx.exactValue(), 0.000001);
        assertEquals(2, tx.itemAmount());
    }

    @Test
    void invalidValuesAreRejected() {
        UUID player = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class,
                () -> new EconomyTransaction(UUID.randomUUID(), player, Double.NaN, true, true, "SHOP", "DIAMOND", 1));
        assertThrows(IllegalArgumentException.class,
                () -> new EconomyTransaction(UUID.randomUUID(), player, Double.POSITIVE_INFINITY, true, true, "SHOP", "DIAMOND", 1));
        assertThrows(IllegalArgumentException.class,
                () -> new EconomyTransaction(UUID.randomUUID(), player, 1.0, true, true, "SHOP", "DIAMOND", -1));
    }
}
