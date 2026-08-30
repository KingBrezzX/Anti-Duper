package xyz.zyrex.bedrockantidupe;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Sends anti-dupe alerts to Discord through a webhook.
 *
 * Discord requests are asynchronous and rate-limited locally
 * to avoid blocking the Minecraft main thread or spamming staff.
 */
public final class DiscordAlertManager {

    private final JavaPlugin plugin;

    private final HttpClient httpClient;

    private final Map<String, Long> cooldowns =
            new ConcurrentHashMap<>();

    private final AtomicBoolean sending =
            new AtomicBoolean(false);

    public DiscordAlertManager(
            JavaPlugin plugin
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
            String item,
            int amount,
            String source,
            Location location,
            String action
    ) {

        if (!enabled()) {
            return;
        }

        String key =
                "dupe:"
                        + safe(playerUuid)
                        + ":"
                        + safe(item);

        if (!passesCooldown(key)) {
            return;
        }

        String description =
                "**🚨 DUPE DETECTED**\n\n"
                        + "**Player:** "
                        + escape(playerName)
                        + "\n"
                        + "**Platform:** "
                        + escape(platform)
                        + "\n"
                        + "**Item:** "
                        + escape(item)
                        + "\n"
                        + "**Amount:** "
                        + amount
                        + "\n"
                        + "**Source:** "
                        + escape(source)
                        + "\n"
                        + "**Action:** "
                        + escape(action)
                        + "\n"
                        + formatLocation(location);

        sendEmbed(
                "Anti-Dupe Detection",
                description,
                configuredColor(
                        "webhook.colors.dupe",
                        0xFF0000
                )
        );
    }

    /**
     * Sends an economy rollback notification.
     */
    public void sendEconomyRollbackAlert(
            String playerName,
            String playerUuid,
            double requested,
            double withdrawn,
            String result,
            String transactionId
    ) {

        if (!enabled()) {
            return;
        }

        String key =
                "economy:"
                        + safe(playerUuid)
                        + ":"
                        + safe(transactionId);

        if (!passesCooldown(key)) {
            return;
        }

        String description =
                "**💰 ECONOMY ROLLBACK**\n\n"
                        + "**Player:** "
                        + escape(playerName)
                        + "\n"
                        + "**Transaction:** `"
                        + escape(transactionId)
                        + "`\n"
                        + "**Recorded value:** "
                        + formatMoney(requested)
                        + "\n"
                        + "**Rolled back:** "
                        + formatMoney(withdrawn)
                        + "\n"
                        + "**Result:** "
                        + escape(result);

        sendEmbed(
                "Anti-Dupe Economy Protection",
                description,
                configuredColor(
                        "webhook.colors.economy",
                        0xFFA500
                )
        );
    }

    /**
     * Sends a generic security notification.
     */
    public void sendSecurityAlert(
            String title,
            String message
    ) {

        if (!enabled()) {
            return;
        }

        String key =
                "security:"
                        + safe(title)
                        + ":"
                        + safe(message);

        if (!passesCooldown(key)) {
            return;
        }

        sendEmbed(
                title,
                message,
                configuredColor(
                        "webhook.colors.security",
                        0xFFFF00
                )
        );
    }

    /**
     * Checks whether Discord notifications are enabled.
     */
    private boolean enabled() {

        if (!plugin.getConfig().getBoolean(
                "webhook.enabled",
                false
        )) {
            return false;
        }

        String url =
                plugin.getConfig().getString(
                        "webhook.url",
                        ""
                );

        return url != null
                && !url.isBlank();
    }

    /**
     * Local anti-spam cooldown.
     */
    private boolean passesCooldown(
            String key
    ) {

        long cooldownSeconds =
                Math.max(
                        0L,
                        plugin.getConfig().getLong(
                                "webhook.cooldown-seconds",
                                10L
                        )
                );

        if (cooldownSeconds <= 0) {
            return true;
        }

        long now =
                System.currentTimeMillis();

        long cooldownMillis =
                cooldownSeconds * 1000L;

        Long previous =
                cooldowns.putIfAbsent(
                        key,
                        now
                );

        if (previous == null) {
            return true;
        }

        if (now - previous >= cooldownMillis) {

            cooldowns.put(
                    key,
                    now
            );

            return true;
        }

        return false;
    }

    /**
     * Sends an embed asynchronously.
     */
    private void sendEmbed(
            String title,
            String description,
            int color
    ) {

        String webhookUrl =
                plugin.getConfig().getString(
                        "webhook.url",
                        ""
                );

        if (webhookUrl == null
                || webhookUrl.isBlank()) {
            return;
        }

        if (sending.get()) {

            if (plugin.getConfig().getBoolean(
                    "webhook.single-flight",
                    true
            )) {
                return;
            }
        }

        String username =
                plugin.getConfig().getString(
                        "webhook.username",
                        "BedrockAntiDupe"
                );

        String payload =
                "{"
                        + "\"username\":\""
                        + jsonEscape(username)
                        + "\","
                        + "\"embeds\":[{"
                        + "\"title\":\""
                        + jsonEscape(title)
                        + "\","
                        + "\"description\":\""
                        + jsonEscape(description)
                        + "\","
                        + "\"color\":"
                        + color
                        + "}]"
                        + "}";

        HttpRequest request;

        try {

            request =
                    HttpRequest.newBuilder()
                            .uri(
                                    URI.create(
                                            webhookUrl
                                    )
                            )
                            .timeout(
                                    Duration.ofSeconds(10)
                            )
                            .header(
                                    "Content-Type",
                                    "application/json"
                            )
                            .POST(
                                    HttpRequest.BodyPublishers
                                            .ofString(payload)
                            )
                            .build();

        } catch (IllegalArgumentException exception) {

            plugin.getLogger().warning(
                    "Invalid Discord webhook URL."
            );

            return;
        }

        Bukkit.getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {

                            boolean acquired =
                                    sending.compareAndSet(
                                            false,
                                            true
                                    );

                            if (!acquired
                                    && plugin.getConfig()
                                    .getBoolean(
                                            "webhook.single-flight",
                                            true
                                    )) {
                                return;
                            }

                            try {

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
                                            | InterruptedException exception
                            ) {

                                if (exception
                                        instanceof InterruptedException) {

                                    Thread.currentThread()
                                            .interrupt();
                                }

                                plugin.getLogger().warning(
                                        "Failed to send Discord webhook: "
                                                + exception.getMessage()
                                );

                            } finally {

                                sending.set(false);
                            }
                        }
                );
    }

    private String formatLocation(
            Location location
    ) {

        if (location == null
                || location.getWorld() == null) {

            return "**Location:** Unknown";
        }

        return "**World:** "
                + escape(
                        location.getWorld()
                                .getName()
                )
                + "\n"
                + "**Location:** `"
                + location.getBlockX()
                + ", "
                + location.getBlockY()
                + ", "
                + location.getBlockZ()
                + "`";
    }

    private int configuredColor(
            String path,
            int fallback
    ) {

        String value =
                plugin.getConfig().getString(
                        path,
                        ""
                );

        if (value == null
                || value.isBlank()) {
            return fallback;
        }

        value =
                value.trim()
                        .toLowerCase(
                                Locale.ROOT
                        );

        try {

            if (value.startsWith("#")) {

                value =
                        value.substring(1);
            }

            return Integer.parseInt(
                    value,
                    16
            );

        } catch (NumberFormatException ignored) {

            return fallback;
        }
    }

    private String formatMoney(
            double amount
    ) {

        return String.format(
                Locale.US,
                "$%,.2f",
                amount
        );
    }

    private String safe(
            String value
    ) {

        return value == null
                ? ""
                : value;
    }

    private String escape(
            String value
    ) {

        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("*", "\\*")
                .replace("_", "\\_")
                .replace("`", "\\`");
    }

    private String jsonEscape(
            String value
    ) {

        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }

    /**
     * Clears local cooldown state.
     */
    public void clearCooldowns() {

        cooldowns.clear();
    }

    /**
     * Shuts down the HTTP client.
     */
    public void shutdown() {

        cooldowns.clear();
    }
          }
