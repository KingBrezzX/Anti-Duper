package xyz.zyrex.bedrockantidupe;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * EvidenceManager
 *
 * Stores lightweight evidence when a suspicious transaction is detected.
 *
 * Design goals:
 * - no database required
 * - asynchronous disk writes
 * - bounded in-memory state
 * - no synchronous file I/O on the server thread
 * - useful evidence for staff/Discord
 *
 * The manager never decides whether an item is legitimate.
 * It only records what the detector reports.
 */
public final class EvidenceManager {

    private final BedrockAntiDupe plugin;

    private final Map<UUID, Long> lastWrite =
            new ConcurrentHashMap<>();

    public EvidenceManager(
            BedrockAntiDupe plugin
    ) {
        this.plugin = plugin;
    }

    /**
     * Record a detected transaction.
     */
    public void record(
            Player player,
            String reason,
            Map<Material, Integer> increases
    ) {

        if (player == null) {
            return;
        }

        if (!plugin.getConfig().getBoolean(
                "evidence.enabled",
                true
        )) {
            return;
        }

        long now =
                System.currentTimeMillis();

        long cooldown =
                plugin.getConfig().getLong(
                        "evidence.write-cooldown-ms",
                        1000L
                );

        Long previous =
                lastWrite.putIfAbsent(
                        player.getUniqueId(),
                        now
                );

        if (previous != null
                && now - previous < cooldown) {

            return;
        }

        lastWrite.put(
                player.getUniqueId(),
                now
        );

        String evidence =
                buildEvidence(
                        player,
                        reason,
                        increases
                );

        writeAsync(
                player.getUniqueId(),
                evidence
        );
    }

    /**
     * Build a human-readable evidence entry.
     */
    private String buildEvidence(
            Player player,
            String reason,
            Map<Material, Integer> increases
    ) {

        StringBuilder items =
                new StringBuilder();

        if (increases != null) {

            for (Map.Entry<Material, Integer> entry :
                    increases.entrySet()) {

                if (items.length() > 0) {
                    items.append(", ");
                }

                items.append(
                        entry.getKey().name()
                );

                items.append(" +");

                items.append(
                        entry.getValue()
                );
            }
        }

        return
                "==================================================\n"
                        + "BedrockAntiDupe Evidence\n"
                        + "Time: "
                        + Instant.now()
                        + "\n"
                        + "Player: "
                        + player.getName()
                        + "\n"
                        + "UUID: "
                        + player.getUniqueId()
                        + "\n"
                        + "Platform: "
                        + (
                        plugin.isBedrockPlayer(player)
                                ? "Bedrock"
                                : "Java"
                )
                        + "\n"
                        + "World: "
                        + player.getWorld()
                        .getName()
                        + "\n"
                        + "Location: "
                        + player.getLocation()
                        .getBlockX()
                        + ", "
                        + player.getLocation()
                        .getBlockY()
                        + ", "
                        + player.getLocation()
                        .getBlockZ()
                        + "\n"
                        + "Reason: "
                        + sanitize(reason)
                        + "\n"
                        + "Unexpected protected increases: "
                        + items
                        + "\n"
                        + "==================================================\n";
    }

    /**
     * Asynchronous append.
     */
    private void writeAsync(
            UUID uuid,
            String evidence
    ) {

        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {

                            try {

                                File directory =
                                        new File(
                                                plugin.getDataFolder(),
                                                "evidence"
                                        );

                                if (!directory.exists()
                                        && !directory.mkdirs()) {

                                    plugin.getLogger()
                                            .warning(
                                                    "Could not create evidence directory."
                                            );

                                    return;
                                }

                                File file =
                                        new File(
                                                directory,
                                                uuid + ".log"
                                        );

                                Files.writeString(
                                        file.toPath(),
                                        evidence,
                                        StandardCharsets.UTF_8,
                                        StandardOpenOption.CREATE,
                                        StandardOpenOption.WRITE,
                                        StandardOpenOption.APPEND
                                );

                            } catch (IOException exception) {

                                if (plugin.getConfig()
                                        .getBoolean(
                                                "console.errors",
                                                true
                                        )) {

                                    plugin.getLogger()
                                            .warning(
                                                    "Failed to write dupe evidence: "
                                                            + exception
                                                            .getMessage()
                                            );
                                }
                            }
                        }
                );
    }

    /**
     * Delete evidence for a player.
     */
    public void delete(
            UUID uuid
    ) {

        if (uuid == null) {
            return;
        }

        lastWrite.remove(uuid);

        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {

                            File file =
                                    new File(
                                            plugin.getDataFolder()
                                                    .getPath()
                                                    + File.separator
                                                    + "evidence",
                                            uuid + ".log"
                                    );

                            if (!file.exists()) {
                                return;
                            }

                            try {

                                Files.deleteIfExists(
                                        file.toPath()
                                );

                            } catch (IOException exception) {

                                if (plugin.getConfig()
                                        .getBoolean(
                                                "console.errors",
                                                true
                                        )) {

                                    plugin.getLogger()
                                            .warning(
                                                    "Failed to delete evidence: "
                                                            + exception
                                                            .getMessage()
                                            );
                                }
                            }
                        }
                );
    }

    /**
     * Clear runtime cooldowns.
     */
    public void clear() {

        lastWrite.clear();
    }

    private String sanitize(
            String value
    ) {

        if (value == null) {
            return "unknown";
        }

        return value
                .replace('\n', ' ')
                .replace('\r', ' ');
    }
}
