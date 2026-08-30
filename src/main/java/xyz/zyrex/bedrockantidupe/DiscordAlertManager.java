package xyz.zyrex.bedrockantidupe;

import org.bukkit.Location;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Discord webhook notification manager.
 *
 * Notifications are sent asynchronously so the main server thread
 * is not blocked by network requests.
 */
public final class DiscordAlertManager {

    private final BedrockAntiDupe plugin;

    private final HttpClient httpClient;

    private final Map<String, Long> lastAlerts =
            new ConcurrentHashMap<>();

    public DiscordAlertManager(
            BedrockAntiDupe plugin
    ) {

        this.plugin = plugin;

        this.httpClient =
                HttpClient.newBuilder()
                        .connectTimeout(
                                Duration.ofSeconds(5)
                        )
                        .build();
    }

    /**
     * Sends a confirmed dupe alert.
     */
    public void sendDupeAlert(
            String playerName,
            String playerUuid,
            String platform,
            String material,
            int amount,
            String source,
            Location location,
            String action
    ) {

        if (!isEnabled()
                || !plugin.getConfig()
                .getBoolean(
                        "discord.notify-dupe",
                        true
                )) {

            return;
        }

        String key =
                "DUPE:"
                        + playerUuid
                        + ":"
                        + material
                        + ":"
                        + source;

        if (isOnCooldown(key)) {
            return;
        }

        String locationText =
                formatLocation(location);

        String content =
                "**🚨 CONFIRMED DUPE DETECTED**\n"
                        + "**Player:** "
                        + safe(playerName)
                        + "\n"
                        + "**UUID:** "
                        + safe(playerUuid)
                        + "\n"
                        + "**Platform:** "
                        + safe(platform)
                        + "\n"
                        + "**Item:** "
                        + safe(material)
                        + "\n"
                        + "**Amount:** "
                        + amount
                        + "\n"
                        + "**Source:** "
                        + safe(source)
                        + "\n"
                        + "**Location:** "
                        + locationText
                        + "\n"
                        + "**Action:** "
                        + safe(action);

        send(content);
    }

    /**
     * Sends an economy rollback alert.
     */
    public void sendEconomyRollbackAlert(
            String playerName,
            String playerUuid,
            double originalValue,
            double rolledBackAmount,
            String status,
            String transactionId
    ) {

        if (!isEnabled()
                || !plugin.getConfig()
                .getBoolean(
                        "discord.notify-economy-rollback",
                        true
                )) {

            return;
        }

        String key =
                "ECONOMY:"
                        + transactionId;

        if (isOnCooldown(key)) {
            return;
        }

        String content =
                "**💰 ANTI-DUPE ECONOMY ROLLBACK**\n"
                        + "**Player:** "
                        + safe(playerName)
                        + "\n"
                        + "**UUID:** "
                        + safe(playerUuid)
                        + "\n"
                        + "**Original sale:** $"
                        + formatMoney(originalValue)
                        + "\n"
                        + "**Rolled back:** $"
                        + formatMoney(rolledBackAmount)
                        + "\n"
                        + "**Status:** "
                        + safe(status)
                        + "\n"
                        + "**Transaction:** "
                        + safe(transactionId);

        send(content);
    }

    /**
     * Sends the webhook request asynchronously.
     */
    private void send(
            String content
    ) {

        String webhook =
                plugin.getConfig()
                        .getString(
                                "discord.webhook-url",
                                ""
                        );

        if (webhook == null
                || webhook.isBlank()) {

            plugin.getLogger().warning(
                    "Discord webhook is enabled but no webhook URL is configured."
            );

            return;
        }

        String json =
                "{\"content\":\""
                        + escapeJson(content)
                        + "\"}";

        new BukkitRunnable() {

            @Override
            public void run() {

                try {

                    HttpRequest request =
                            HttpRequest.newBuilder()
                                    .uri(
                                            URI.create(
                                                    webhook
                                            )
                                    )
                                    .timeout(
                                            Duration.ofSeconds(5)
                                    )
                                    .header(
                                            "Content-Type",
                                            "application/json"
                                    )
                                    .POST(
                                            HttpRequest.BodyPublishers
                                                    .ofString(
                                                            json
                                                    )
                                    )
                                    .build();

                    HttpResponse<String> response =
                            httpClient.send(
                                    request,
                                    HttpResponse.BodyHandlers
                                            .ofString()
                            );

                    int status =
                            response.statusCode();

                    if (status < 200
                            || status >= 300) {

                        plugin.getLogger().warning(
                                "Discord webhook returned HTTP "
                                        + status
                        );
                    }

                } catch (
                        IOException
                                | InterruptedException
                                | IllegalArgumentException ex
                ) {

                    plugin.getLogger().warning(
                            "Failed to send Discord anti-dupe alert: "
                                    + ex.getMessage()
                    );

                    if (ex instanceof InterruptedException) {

                        Thread.currentThread()
                                .interrupt();
                    }
                }
            }

        }.runTaskAsynchronously(
                plugin
        );
    }

    /**
     * Cooldown prevents Discord spam.
     */
    private boolean isOnCooldown(
            String key
    ) {

        long cooldown =
                Math.max(
                        0L,
                        plugin.getConfig()
                                .getLong(
                                        "discord.cooldown-seconds",
                                        10L
                                )
                ) * 1000L;

        if (cooldown <= 0L) {
            return false;
        }

        long now =
                System.currentTimeMillis();

        Long previous =
                lastAlerts.put(
                        key,
                        now
                );

        if (previous == null) {
            return false;
        }

        if (now - previous < cooldown) {
            return true;
        }

        lastAlerts.put(
                key,
                now
        );

        return false;
    }

    private boolean isEnabled() {

        return plugin.getConfig()
                .getBoolean(
                        "discord.enabled",
                        false
                );
    }

    private String formatLocation(
            Location location
    ) {

        if (location == null) {
            return "UNKNOWN";
        }

        String world =
                location.getWorld() == null
                        ? "UNKNOWN"
                        : location.getWorld()
                                .getName();

        return world
                + " "
                + location.getBlockX()
                + ","
                + location.getBlockY()
                + ","
                + location.getBlockZ();
    }

    private String formatMoney(
            double value
    ) {

        return String.format(
                java.util.Locale.US,
                "%.2f",
                value
        );
    }

    private String safe(
            String value
    ) {

        if (value == null
                || value.isBlank()) {

            return "UNKNOWN";
        }

        return value;
    }

    private String escapeJson(
            String value
    ) {

        return value
                .replace(
                        "\\",
                        "\\\\"
                )
                .replace(
                        "\"",
                        "\\\""
                )
                .replace(
                        "\r",
                        "\\r"
                )
                .replace(
                        "\n",
                        "\\n"
                );
    }

    /**
     * Clears old cooldown entries.
     */
    public void cleanup() {

        lastAlerts.clear();
    }
    }
