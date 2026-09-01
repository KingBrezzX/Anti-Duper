package xyz.zyrex.bedrockantidupe;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ShopIntegrationContractTest {
    @Test
    void economyTransactionCarriesStableIdentityAndExactValue() {
        UUID player = UUID.randomUUID();
        UUID tx = UUID.randomUUID();
        EconomyTransaction value = new EconomyTransaction(tx, player, 25.0, true, true, "EXTERNAL_SHOP", Material.DIAMOND.name(), 3);
        assertEquals(tx, value.transactionId());
        assertEquals(player, value.playerId());
        assertEquals(25.0, value.rollbackAmount(), 1e-9);
        assertEquals(3, value.itemAmount());
    }

    @Test
    void shopResultIdentityCanBeFingerprintChecked() {
        ItemStack result = new ItemStack(Material.DIAMOND, 3);
        assertNotNull(ItemFingerprint.sha256(result));
        assertFalse(ItemFingerprint.sha256(result).isBlank());
    }
}
