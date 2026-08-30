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

public final class DiscordAlertManager {

    private final BedrockAntiDupe plugin;

    private final HttpClient httpClient;

    private final Map<String, Long> cooldowns =
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
     * Confirmed dupe notification.
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

        if (!enabled("notify-dupe")) {
            return;
        }

        String key =
                "DUPE:"
                        + playerUuid
                        + ":"
                        + material
                        + ":"
                        + source;

        if (!checkCooldown(key)) {
            return;
        }

        String message =
                "🚨 **CONFIRMED DUPE DETECTED**\n\n"
                        + "**Player:** `"
                        + safe(playerName)
                        + "`\n"
                        + "**UUID:** `"
                        + safe(playerUuid)
                        + "`\n"
                        + "**Platform:** `"
                        + safe(platform)
                        + "`\n"
                        + "**Item:** `"
                        + safe(material)
                        + "`\n"
                        + "**Amount:** `"
                        + amount
                        + "`\n"
                        + "**Source:** `"
                        + safe(source)
                        + "`\n"
                        + "**Location:** `"
                        + formatLocation(location)
                        + "`\n"
                        + "**Action:** `"
                        + safe(action)
                        + "`";

        send(message);
    }

    /**
     * Economy rollback notification.
     */
    public void sendEconomyRollbackAlert(
            String playerName,
            String playerUuid,
            double originalValue,
            double rolledBackAmount,
            String status,
            String transactionId
    ) {

        if (!enabled(
                "notify-economy-rollback"
        )) {
            return;
        }

        String key =
                "ECONOMY:"
                        + transactionId;

        if (!checkCooldown(key)) {
            return;
        }

        String message =
                "💰 **ANTI-DUPE ECONOMY ROLLBACK**\n\n"
                        + "**Player:** `"
                        + safe(playerName)
                        + "`\n"
                        + "**UUID:** `"
                        + safe(playerUuid)
                        + "`\n"
                        + "**Original sale:** `$"
                        + money(originalValue)
                        + "`\n"
                        + "**Rolled back:** `$"
                        + money(rolledBackAmount)
                        + "`\n"
                        + "**Status:** `"
                        + safe(status)
                        + "`\n"
                        + "**Transaction:** `"
                        + safe(transactionId)
                        + "`";

        send(message);
    }

    /**
     * Generic webhook sender.
     *
     * Network I/O is always asynchronous.
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
                    "[AntiDupe] Discord webhook is enabled "
                            + "but webhook-url is empty."
            );

            return;
        }

        /*
         * Basic validation prevents accidentally treating
         * arbitrary text as a webhook endpoint.
         */
        if (!webhook.startsWith(
                "https://discord.com/api/webhooks/"
        )
                && !webhook.startsWith(
                "https://discordapp.com/api/webhooks/"
        )) {

            plugin.getLogger().warning(
                    "[AntiDupe] Invalid Discord webhook URL."
            );

            return;
        }

        String payload =
                "{"
                        + "\"content\":\""
                        + escapeJson(content)
                        + "\""
                        + "}";

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
                                                            payload
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
                                "[AntiDupe] Discord returned HTTP "
                                        + status
                        );
                    }

                } catch (
                        IOException
                                | InterruptedException
                                | IllegalArgumentException exception
                ) {

                    plugin.getLogger().warning(
                            "[AntiDupe] Discord notification failed: "
                                    + exception.getMessage()
                    );

                    if (exception instanceof InterruptedException) {

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
     * Anti-spam cooldown.
     */
    private boolean checkCooldown(
            String key
    ) {

        if (!plugin.getConfig()
                .getBoolean(
                        "discord.prevent-duplicate-alerts",
                        true
                )) {

            return true;
        }

        long cooldown =
                Math.max(
                        0L,
                        plugin.getConfig()
                                .getLong(
                                        "discord.cooldown-seconds",
                                        10L
                                )
                ) * 1000L;

        if (cooldown == 0L) {
            return true;
        }

        long now =
                System.currentTimeMillis();

        Long previous =
                cooldowns.get(key);

        if (previous != null
                && now - previous < cooldown) {

            return false;
        }

        cooldowns.put(
                key,
                now
        );

        return true;
    }

    private boolean enabled(
            String notification
    ) {

        return plugin.getConfig()
                .getBoolean(
                        "discord.enabled",
                        false
                )
                && plugin.getConfig()
                .getBoolean(
                        "discord." + notification,
                        true
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

    private String money(
            double value
    ) {

        return String.format(
                java.util.Locale.US,
                "%,.2f",
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

    public void cleanup() {
        cooldowns.clear();
    }
            }
