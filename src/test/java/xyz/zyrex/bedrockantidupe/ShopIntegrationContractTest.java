package xyz.zyrex.bedrockantidupe;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ShopIntegrationContractTest {
    @Test
    void economyTransactionCarriesStableIdentityAndExactValue() {
        UUID player = UUID.randomUUID();
        UUID tx = UUID.randomUUID();
        EconomyTransaction value = new EconomyTransaction(tx, player, 25.0, true, true, "EXTERNAL_SHOP", "DIAMOND", 3);
        assertEquals(tx, value.transactionId());
        assertEquals(player, value.playerId());
        assertEquals(25.0, value.rollbackAmount(), 1e-9);
        assertEquals(3, value.itemAmount());
    }

    @Test
    void shopResultIdentityCanBeFingerprintChecked() {
        byte[] serializedResult = new byte[] {0x01, 0x03, 0x44, 0x49, 0x41, 0x4d, 0x4f, 0x4e, 0x44};
        String fingerprint = ItemFingerprint.sha256Bytes(serializedResult);
        assertNotNull(fingerprint);
        assertFalse(fingerprint.isBlank());
        assertEquals(64, fingerprint.length());
    }
}
