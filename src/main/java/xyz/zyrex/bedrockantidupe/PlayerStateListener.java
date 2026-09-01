package xyz.zyrex.bedrockantidupe;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/** Flushes pending transaction snapshots before a player leaves the server. */
public final class PlayerStateListener implements Listener {
    private final BedrockAntiDupe plugin;
    public PlayerStateListener(BedrockAntiDupe plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.getTransactionLedger().finishAll(player);
    }
}
