package xyz.zyrex.bedrockantidupe;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TransactionConservationRegressionTest {
    @Test
    void normalPlayerToContainerTransferIsNotNetPositive() {
        UUID id = UUID.randomUUID();
        ItemStack diamond = new ItemStack(Material.DIAMOND, 10);
        var beforePlayer = Map.of(0, diamond);
        var afterPlayer = Map.<Integer, ItemStack>of();
        var beforeContainer = Map.<Integer, ItemStack>of();
        var afterContainer = Map.of(0, diamond.clone());
        var tx = new TransactionLedger.TransactionRecord(id, id, "INVENTORY_CLICK", beforePlayer, afterPlayer,
                beforeContainer, afterContainer, List.of(new TransactionLedger.ItemChange(0, diamond, null)),
                List.of(new TransactionLedger.ItemChange(0, null, diamond.clone())), System.currentTimeMillis());
        assertEquals(0, tx.netDelta(Material.DIAMOND));
        assertEquals(-10, tx.playerDelta(Material.DIAMOND));
        assertEquals(10, tx.containerDelta(Material.DIAMOND));
    }

    @Test
    void duplicatedTrackedItemProducesPositiveConservationDelta() {
        UUID id = UUID.randomUUID();
        ItemStack before = new ItemStack(Material.DIAMOND, 10);
        ItemStack after = new ItemStack(Material.DIAMOND, 11);
        var tx = new TransactionLedger.TransactionRecord(id, id, "INVENTORY_CLICK",
                Map.of(0, before), Map.of(0, after), Map.of(), Map.of(),
                List.of(new TransactionLedger.ItemChange(0, before, after)), List.of(), System.currentTimeMillis());
        assertEquals(1, tx.netDelta(Material.DIAMOND));
        assertTrue(tx.hasPositiveIncrease());
    }

    @Test
    void fingerprintChangesWhenItemStateChanges() {
        ItemStack a = new ItemStack(Material.DIAMOND, 1);
        ItemStack b = a.clone();
        b.setAmount(2);
        assertNotEquals(ItemFingerprint.sha256(a), ItemFingerprint.sha256(b));
    }
}
