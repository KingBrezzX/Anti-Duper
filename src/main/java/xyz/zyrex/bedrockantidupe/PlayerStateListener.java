package xyz.zyrex.bedrockantidupe;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * Flushes transaction state at player lifecycle boundaries. These boundaries
 * are deliberately non-destructive: they establish a clean transaction fence
 * without inventing rollback or changing inventory state.
 */
public final class PlayerStateListener implements Listener {
    private final BedrockAntiDupe plugin;
    public PlayerStateListener(BedrockAntiDupe plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onQuit(PlayerQuitEvent event) {
        flush(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        flush(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getTo() == null) return;
        flush(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRespawn(PlayerRespawnEvent event) {
        flush(event.getPlayer());
    }

    private void flush(Player player) {
        if (player != null) plugin.getTransactionLedger().finishAll(player);
    }
}
