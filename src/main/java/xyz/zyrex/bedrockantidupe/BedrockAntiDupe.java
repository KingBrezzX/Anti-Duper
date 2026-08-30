package xyz.zyrex.bedrockantidupe;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BedrockAntiDupe extends JavaPlugin
        implements Listener {

    private final Map<UUID, Integer> violations =
            new ConcurrentHashMap<>();

    private final Map<UUID, Long> transactionCooldown =
            new ConcurrentHashMap<>();

    private final Map<UUID, Long> staffCooldown =
            new ConcurrentHashMap<>();

    private final Map<UUID, Long> discordCooldown =
            new ConcurrentHashMap<>();

    private TransactionLedger ledger;
    private DupeDetector detector;
    private EvidenceManager evidenceManager;

    private String actionMode;

    private HttpClient httpClient;

    @Override
    public void onEnable() {

        saveDefaultConfig();

        loadSettings();

        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        ledger = new TransactionLedger();

        evidenceManager =
                new EvidenceManager(this);

        detector =
                new DupeDetector(
                        this,
                        ledger
                );

        Bukkit.getPluginManager()
                .registerEvents(this, this);

        Bukkit.getPluginManager()
                .registerEvents(
                        new ProtectionListener(
                                this,
                                detector
                        ),
                        this
                );

        Bukkit.getPluginManager()
                .registerEvents(
                        new ContainerProtectionListener(
                                this,
                                detector
                        ),
                        this
                );

        Bukkit.getPluginManager()
                .registerEvents(
                        new ShopTransactionListener(
                                this,
                                detector
                        ),
                        this
                );

        if (getCommand("antidupe") != null) {

            getCommand("antidupe")
                    .setExecutor(
                            new AntiDupeCommand(this)
                    );
        }

        getLogger().info(
                "BedrockAntiDupe enabled."
        );

        getLogger().info(
                "Target: Paper 26.2 | Java 25"
        );

        getLogger().info(
                "Protection mode: "
                        + actionMode
        );
    }

    @Override
    public void onDisable() {

        if (detector != null) {
            detector.clear();
        }

        if (evidenceManager != null) {
            evidenceManager.clear();
        }

        violations.clear();
        transactionCooldown.clear();
        staffCooldown.clear();
        discordCooldown.clear();

        getLogger().info(
                "BedrockAntiDupe disabled."
        );
    }

    public void loadSettings() {

        actionMode =
                getConfig()
                        .getString(
                                "action.mode",
                                "REMOVE_AND_ALERT"
                        )
                        .toUpperCase(
                                Locale.ROOT
                        );
    }

    public void reloadPlugin() {

        reloadConfig();
        loadSettings();
    }

    public boolean isEnabled() {

        return getConfig()
                .getBoolean(
                        "settings.enabled",
                        true
                );
    }

    public boolean isProtected(
            Player player
    ) {

        if (player == null
                || !isEnabled()) {
            return false;
        }

        if (isBedrockPlayer(player)) {

            return getConfig()
                    .getBoolean(
                            "settings.protect-bedrock",
                            true
                    );
        }

        return getConfig()
                .getBoolean(
                        "settings.protect-java",
                        true
                );
    }

    public boolean isBedrockPlayer(
            Player player
    ) {

        if (player == null) {
            return false;
        }

        try {

            Class<?> apiClass =
                    Class.forName(
                            "org.geysermc.floodgate.api.FloodgateApi"
                    );

            Object api =
                    apiClass
                            .getMethod(
                                    "getInstance"
                            )
                            .invoke(null);

            Object result =
                    apiClass
                            .getMethod(
                                    "isFloodgatePlayer",
                                    UUID.class
                            )
                            .invoke(
                                    api,
                                    player.getUniqueId()
                            );

            return result instanceof Boolean
                    && (Boolean) result;

        } catch (Throwable ignored) {

            return false;
        }
    }

    public boolean isTransactionRateLimited(
            Player player
    ) {

        if (player == null) {
            return true;
        }

        long now =
                System.currentTimeMillis();

        long cooldown =
                getConfig().getLong(
                        "settings.transaction-cooldown-ms",
                        150L
                );

        Long previous =
                transactionCooldown.put(
                        player.getUniqueId(),
                        now
                );

        return previous != null
                && now - previous < cooldown;
    }

    public boolean isProtectedItem(
            ItemStack item
    ) {

        if (item == null
                || item.getType().isAir()) {
            return false;
        }

        Material material =
                item.getType();

        String name =
                material.name();

        if (name.contains(
                "SHULKER_BOX"
        )) {

            return getConfig()
                    .getBoolean(
                            "containers.shulker-box",
                            true
                    );
        }

        if (material ==
                Material.ENDER_CHEST) {

            return getConfig()
                    .getBoolean(
                            "containers.ender-chest",
                            true
                    );
        }

        if (material ==
                Material.CHEST
                || material ==
                Material.TRAPPED_CHEST) {

            return getConfig()
                    .getBoolean(
                            "containers.chest",
                            true
                    );
        }

        return false;
    }

    public boolean containsInvalidStack(
            Player player
    ) {

        if (player == null) {
            return false;
        }

        for (ItemStack item :
                player.getInventory()
                        .getContents()) {

            if (item == null
                    || item.getType().isAir()) {
                continue;
            }

            if (item.getAmount()
                    > item.getMaxStackSize()) {

                return true;
            }
        }

        return false;
    }

    public void removeInvalidStacks(
            Player player
    ) {

        if (player == null) {
            return;
        }

        ItemStack[] contents =
                player.getInventory()
                        .getContents();

        boolean changed = false;

        for (int slot = 0;
             slot < contents.length;
             slot++) {

            ItemStack item =
                    contents[slot];

            if (item == null
                    || item.getType().isAir()) {
                continue;
            }

            if (item.getAmount()
                    > item.getMaxStackSize()) {

                contents[slot] = null;
                changed = true;
            }
        }

        if (!changed) {
            return;
        }

        player.getInventory()
                .setContents(contents);

        if (getConfig().getBoolean(
                "action.remove.resync-inventory",
                true
        )) {

            player.updateInventory();
        }
    }

    public void handleViolation(
            Player player,
            String reason
    ) {

        if (player == null
                || !player.isOnline()) {
            return;
        }

        if (!isProtected(player)) {
            return;
        }

        int violation =
                violations.merge(
                        player.getUniqueId(),
                        1,
                        Integer::sum
                );

        if (evidenceManager != null) {

            evidenceManager.record(
                    player,
                    reason,
                    Map.of()
            );
        }

        boolean remove =
                actionMode.equals("REMOVE")
                        || actionMode.equals(
                        "REMOVE_AND_ALERT"
                );

        boolean alert =
                actionMode.equals("ALERT")
                        || actionMode.equals(
                        "REMOVE_AND_ALERT"
                );

        if (remove
                && getConfig().getBoolean(
                "action.remove.enabled",
                true
        )) {

            removeInvalidStacks(player);
        }

        if (alert
                && getConfig().getBoolean(
                "action.alert.enabled",
                true
        )) {

            sendStaffAlert(
                    player,
                    reason,
                    violation
            );

            sendDiscordAlert(
                    player,
                    reason,
                    violation
            );
        }

        checkPunishment(
                player,
                violation
        );
    }

    @EventHandler(
            priority = EventPriority.MONITOR,
            ignoreCancelled = true
    )
    public void onInventoryClick(
            InventoryClickEvent event
    ) {

        if (!(event.getWhoClicked()
                instanceof Player player)) {
            return;
        }

        if (!isProtected(player)) {
            return;
        }

        if (!getConfig().getBoolean(
                "detection.inventory",
                true
        )) {
            return;
        }

        ItemStack current =
                event.getCurrentItem();

        ItemStack cursor =
                event.getCursor();

        if (isProtectedItem(current)
                || isProtectedItem(cursor)) {

            detector.validateLater(
                    player,
                    "inventory transaction"
            );
        }
    }

    @EventHandler(
            priority = EventPriority.MONITOR,
            ignoreCancelled = true
    )
    public void onInventoryDrag(
            InventoryDragEvent event
    ) {

        if (!(event.getWhoClicked()
                instanceof Player player)) {
            return;
        }

        if (!isProtected(player)) {
            return;
        }

        if (!getConfig().getBoolean(
                "detection.inventory",
                true
        )) {
            return;
        }

        for (ItemStack item :
                event.getNewItems().values()) {

            if (isProtectedItem(item)) {

                detector.validateLater(
                        player,
                        "inventory drag transaction"
                );

                return;
            }
        }
    }

    @EventHandler(
            priority = EventPriority.MONITOR,
            ignoreCancelled = true
    )
    public void onPistonExtend(
            BlockPistonExtendEvent event
    ) {

        if (!getConfig().getBoolean(
                "piston.enabled",
                true
        )) {
            return;
        }

        if (!getConfig().getBoolean(
                "piston.extend",
                true
        )) {
            return;
        }

        for (var block :
                event.getBlocks()) {

            if (!isProtectedBlock(
                    block.getType()
            )) {
                continue;
            }

            notifyNearbyPlayers(
                    block,
                    "piston extension"
            );
        }
    }

    @EventHandler(
            priority = EventPriority.MONITOR,
            ignoreCancelled = true
    )
    public void onPistonRetract(
            BlockPistonRetractEvent event
    ) {

        if (!getConfig().getBoolean(
                "piston.enabled",
                true
        )) {
            return;
        }

        if (!getConfig().getBoolean(
                "piston.retract",
                true
        )) {
            return;
        }

        for (var block :
                event.getBlocks()) {

            if (!isProtectedBlock(
                    block.getType()
            )) {
                continue;
            }

            notifyNearbyPlayers(
                    block,
                    "piston retraction"
            );
        }
    }

    @EventHandler(
            priority = EventPriority.MONITOR,
            ignoreCancelled = true
    )
    public void onExplosion(
            EntityExplodeEvent event
    ) {

        if (!getConfig().getBoolean(
                "explosion.enabled",
                true
        )) {
            return;
        }

        for (var block :
                event.blockList()) {

            if (!isProtectedBlock(
                    block.getType()
            )) {
                continue;
            }

            notifyNearbyPlayers(
                    block,
                    "explosion affected protected container"
            );
        }
    }

    @EventHandler(
            priority = EventPriority.MONITOR,
            ignoreCancelled = true
    )
    public void onBlockBreak(
            BlockBreakEvent event
    ) {

        Player player =
                event.getPlayer();

        if (!isProtected(player)) {
            return;
        }

        Material material =
                event.getBlock()
                        .getType();

        if (material.name()
                .contains("SHULKER_BOX")) {

            if (!getConfig().getBoolean(
                    "detection.shulker",
                    true
            )) {
                return;
            }

            detector.begin(player);

            detector.validateLater(
                    player,
                    "shulker break"
            );

            return;
        }

        if (isProtectedBlock(material)) {

            detector.begin(player);

            detector.validateLater(
                    player,
                    "container break"
            );
        }
    }

    @EventHandler
    public void onJoin(
            PlayerJoinEvent event
    ) {

        Player player =
                event.getPlayer();

        if (!isProtected(player)) {
            return;
        }

        Bukkit.getScheduler()
                .runTaskLater(
                        this,
                        () -> {

                            if (player.isOnline()) {
                                detector.reset(
                                        player
                                );
                            }

                        },
                        20L
                );
    }

    @EventHandler
    public void onQuit(
            PlayerQuitEvent event
    ) {

        UUID uuid =
                event.getPlayer()
                        .getUniqueId();

        transactionCooldown.remove(uuid);
    }

    private void notifyNearbyPlayers(
            org.bukkit.block.Block block,
            String reason
    ) {

        if (block == null
                || block.getWorld() == null) {
            return;
        }

        double radius =
                getConfig().getDouble(
                        "detection.nearby-radius",
                        8.0
                );

        for (Player player :
                block.getWorld()
                        .getPlayers()) {

            if (!isProtected(player)) {
                continue;
            }

            if (player.getLocation()
                    .distanceSquared(
                            block.getLocation()
                    )
                    > radius * radius) {
                continue;
            }

            detector.begin(player);

            detector.validateLater(
                    player,
                    reason
            );
        }
    }

    private boolean isProtectedBlock(
            Material material
    ) {

        if (material == null) {
            return false;
        }

        if (material.name()
                .contains("SHULKER_BOX")) {

            return getConfig()
                    .getBoolean(
                            "containers.shulker-box",
                            true
                    );
        }

        return switch (material) {

            case CHEST,
                 TRAPPED_CHEST ->
                    getConfig().getBoolean(
                            "containers.chest",
                            true
                    );

            case ENDER_CHEST ->
                    getConfig().getBoolean(
                            "containers.ender-chest",
                            true
                    );

            case BARREL,
                 HOPPER,
                 DROPPER,
                 DISPENSER ->
                    getConfig().getBoolean(
                            "containers.other",
                            true
                    );

            default -> false;
        };
    }

    private void sendStaffAlert(
            Player player,
            String reason,
            int violation
    ) {

        if (!getConfig().getBoolean(
                "staff.enabled",
                true
        )) {
            return;
        }

        long now =
                System.currentTimeMillis();

        long cooldown =
                getConfig().getLong(
                        "staff.cooldown-seconds",
                        5L
                ) * 1000L;

        Long previous =
                staffCooldown.get(
                        player.getUniqueId()
                );

        if (previous != null
                && now - previous < cooldown) {
            return;
        }

        staffCooldown.put(
                player.getUniqueId(),
                now
        );

        String permission =
                getConfig().getString(
                        "staff.permission",
                        "bedrockantidupe.alert"
                );

        String message =
                getConfig().getString(
                        "messages.staff",
                        "&cDupe suspected &7| &f%player% &7| &f%reason%"
                );

        message = message
                .replace(
                        "%player%",
                        player.getName()
                )
                .replace(
                        "%reason%",
                        reason
                )
                .replace(
                        "%violations%",
                        String.valueOf(
                                violation
                        )
                );

        String prefix =
                getConfig().getString(
                        "messages.prefix",
                        "&8[&bAntiDupe&8] "
                );

        message =
                ChatColor.translateAlternateColorCodes(
                        '&',
                        prefix + message
                );

        for (Player staff :
                Bukkit.getOnlinePlayers()) {

            if (staff.hasPermission(
                    permission
            )) {

                staff.sendMessage(
                        message
                );
            }
        }
    }

    private void sendDiscordAlert(
            Player player,
            String reason,
            int violation
    ) {

        if (!getConfig().getBoolean(
                "discord.enabled",
                false
        )) {
            return;
        }

        if (!getConfig().getBoolean(
                "action.alert.discord",
                true
        )) {
            return;
        }

        String webhook =
                getConfig().getString(
                        "discord.webhook-url",
                        ""
                );

        if (webhook == null
                || webhook.isBlank()) {
            return;
        }

        long now =
                System.currentTimeMillis();

        long cooldown =
                getConfig().getLong(
                        "discord.cooldown-seconds",
                        10L
                ) * 1000L;

        Long previous =
                discordCooldown.get(
                        player.getUniqueId()
                );

        if (previous != null
                && now - previous < cooldown) {
            return;
        }

        discordCooldown.put(
                player.getUniqueId(),
                now
        );

        String username =
                getConfig().getString(
                        "discord.username",
                        "BedrockAntiDupe"
                );

        String content =
                "**Duplication suspected**\n"
                        + "Player: `"
                        + player.getName()
                        + "`\n"
                        + "Platform: `"
                        + (
                        isBedrockPlayer(player)
                                ? "Bedrock"
                                : "Java"
                )
                        + "`\n"
                        + "World: `"
                        + player.getWorld()
                        .getName()
                        + "`\n"
                        + "Location: `"
                        + player.getLocation()
                        .getBlockX()
                        + ", "
                        + player.getLocation()
                        .getBlockY()
                        + ", "
                        + player.getLocation()
                        .getBlockZ()
                        + "`\n"
                        + "Reason: `"
                        + reason
                        + "`\n"
                        + "Violations: `"
                        + violation
                        + "`";

        String json =
                "{"
                        + "\"username\":\""
                        + escapeJson(username)
                        + "\","
                        + "\"content\":\""
                        + escapeJson(content)
                        + "\""
                        + "}";

        Bukkit.getScheduler()
                .runTaskAsynchronously(
                        this,
                        () -> {

                            try {

                                HttpRequest request =
                                        HttpRequest
                                                .newBuilder(
                                                        URI.create(
                                                                webhook
                                                        )
                                                )
                                                .timeout(
                                                        Duration.ofSeconds(
                                                                5
                                                        )
                                                )
                                                .header(
                                                        "Content-Type",
                                                        "application/json"
                                                )
                                                .POST(
                                                        HttpRequest
                                                                .BodyPublishers
                                                                .ofString(
                                                                        json
                                                                )
                                                )
                                                .build();

                                httpClient.send(
                                        request,
                                        HttpResponse
                                                .BodyHandlers
                                                .discarding()
                                );

                            } catch (Exception exception) {

                                if (getConfig()
                                        .getBoolean(
                                                "console.errors",
                                                true
                                        )) {

                                    getLogger().warning(
                                            "Discord webhook failed: "
                                                    + exception
                                                    .getMessage()
                                    );
                                }
                            }
                        }
                );
    }

    private String escapeJson(
            String text
    ) {

        return text
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
                        " "
                )
                .replace(
                        "\n",
                        "\\n"
                );
    }

    private void checkPunishment(
            Player player,
            int violation
    ) {

        if (!getConfig().getBoolean(
                "punishment.enabled",
                false
        )) {
            return;
        }

        int threshold =
                getConfig().getInt(
                        "violations.thresholds.punish",
                        5
                );

        if (violation < threshold) {
            return;
        }

        String command =
                getConfig().getString(
                        "punishment.command",
                        "tempban %player% 7d Duplication exploit"
                );

        command =
                command.replace(
                        "%player%",
                        player.getName()
                );

        Bukkit.dispatchCommand(
                Bukkit.getConsoleSender(),
                command
        );
    }

    public int getViolationCount(
            Player player
    ) {

        if (player == null) {
            return 0;
        }

        return violations.getOrDefault(
                player.getUniqueId(),
                0
        );
    }

    public String getActionMode() {
        return actionMode;
    }

    public Map<UUID, Integer>
    getViolations() {
        return violations;
    }

    public TransactionLedger
    getLedger() {
        return ledger;
    }

    public DupeDetector
    getDetector() {
        return detector;
    }

    public EvidenceManager
    getEvidenceManager() {
        return evidenceManager;
    }

    public static final class AntiDupeCommand
            implements CommandExecutor {

        private final BedrockAntiDupe plugin;

        public AntiDupeCommand(
                BedrockAntiDupe plugin
        ) {
            this.plugin = plugin;
        }

        @Override
        public boolean onCommand(
                CommandSender sender,
                Command command,
                String label,
                String[] args
        ) {

            if (!sender.hasPermission(
                    "bedrockantidupe.admin"
            )) {

                sender.sendMessage(
                        ChatColor.RED
                                + "You do not have permission."
                );

                return true;
            }

            if (args.length == 0) {
                sendHelp(sender);
                return true;
            }

            switch (
                    args[0].toLowerCase(
                            Locale.ROOT
                    )
            ) {

                case "reload" -> {

                    plugin.reloadPlugin();

                    sender.sendMessage(
                            ChatColor.GREEN
                                    + "BedrockAntiDupe reloaded."
                    );
                }

                case "status" -> {

                    sender.sendMessage(
                            ChatColor.AQUA
                                    + "===== BedrockAntiDupe ====="
                    );

                    sender.sendMessage(
                            ChatColor.GRAY
                                    + "Enabled: "
                                    + ChatColor.WHITE
                                    + plugin.isEnabled()
                    );

                    sender.sendMessage(
                            ChatColor.GRAY
                                    + "Mode: "
                                    + ChatColor.WHITE
                                    + plugin.getActionMode()
                    );

                    sender.sendMessage(
                            ChatColor.GRAY
                                    + "Tracked players: "
                                    + ChatColor.WHITE
                                    + plugin
                                    .getViolations()
                                    .size()
                    );
                }

                case "check" -> {

                    if (args.length < 2) {

                        sender.sendMessage(
                                ChatColor.RED
                                        + "Usage: /antidupe check <player>"
                        );

                        return true;
                    }

                    Player target =
                            Bukkit.getPlayerExact(
                                    args[1]
                            );

                    if (target == null) {

                        sender.sendMessage(
                                ChatColor.RED
                                        + "Player not found."
                        );

                        return true;
                    }

                    sender.sendMessage(
                            ChatColor.AQUA
                                    + "Player: "
                                    + ChatColor.WHITE
                                    + target.getName()
                    );

                    sender.sendMessage(
                            ChatColor.GRAY
                                    + "Platform: "
                                    + ChatColor.WHITE
                                    + (
                                    plugin.isBedrockPlayer(
                                            target
                                    )
                                            ? "Bedrock"
                                            : "Java"
                            )
                    );

                    sender.sendMessage(
                            ChatColor.GRAY
                                    + "Protected: "
                                    + ChatColor.WHITE
                                    + plugin.isProtected(
                                    target
                            )
                    );

                    sender.sendMessage(
                            ChatColor.GRAY
                                    + "Violations: "
                                    + ChatColor.WHITE
                                    + plugin.getViolationCount(
                                    target
                            )
                    );

                    sender.sendMessage(
                            ChatColor.GRAY
                                    + "Invalid stack: "
                                    + ChatColor.WHITE
                                    + plugin.containsInvalidStack(
                                    target
                            )
                    );
                }

                case "violations" -> {

                    sender.sendMessage(
                            ChatColor.AQUA
                                    + "Tracked violation players: "
                                    + ChatColor.WHITE
                                    + plugin
                                    .getViolations()
                                    .size()
                    );
                }

                default -> sendHelp(sender);
            }

            return true;
        }

        private void sendHelp(
                CommandSender sender
        ) {

            sender.sendMessage(
                    ChatColor.AQUA
                            + "===== BedrockAntiDupe ====="
            );

            sender.sendMessage(
                    ChatColor.WHITE
                            + "/antidupe reload"
            );

            sender.sendMessage(
                    ChatColor.WHITE
                            + "/antidupe status"
            );

            sender.sendMessage(
                    ChatColor.WHITE
                            + "/antidupe check <player>"
            );

            sender.sendMessage(
                    ChatColor.WHITE
                            + "/antidupe violations"
            );
        }
    }
                    }
