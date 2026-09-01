package xyz.zyrex.bedrockantidupe;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ShopIntegrationContractTest {
    @Test
    void economyTransactionCarriesStableIdentityAndExactValue() {
        UUID player = UUID.randomUUID();
        UUID tx = UUID.randomUUID();
        EconomyTransaction value = new EconomyTransaction(tx, player, 25.0, true, true,
                "EXTERNAL_SHOP", "DIAMOND", 3);
        assertEquals(tx, value.transactionId());
        assertEquals(player, value.playerId());
        assertEquals(25.0, value.rollbackAmount(), 1e-9);
        assertEquals(3, value.itemAmount());
    }

    @Test
    void shopIdentityCanBeFingerprintCheckedAtByteContractLevel() {
        String fingerprint = ItemFingerprint.sha256Serialized(
                new byte[] {10, 20, 30},
                java.util.List.of("minecraft:custom_data")
        );
        assertNotNull(fingerprint);
        assertFalse(fingerprint.isBlank());
    }
}
