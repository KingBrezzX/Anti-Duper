package xyz.zyrex.bedrockantidupe;

import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class AntiDupeCommand implements CommandExecutor, TabCompleter {
    private final BedrockAntiDupe plugin;
    public AntiDupeCommand(BedrockAntiDupe plugin) { this.plugin = plugin; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("bedrockantidupe.admin")) {
            sender.sendMessage(msg("general.no-permission", "&cNo permission."));
            return true;
        }
        if (args.length == 0) { help(sender); return true; }
        switch (args[0].toLowerCase()) {
            case "reload" -> { plugin.reloadPlugin(); sender.sendMessage(msg("general.reloaded", "&aConfiguration reloaded.")); }
            case "status" -> status(sender);
            case "scan" -> {
                if (args.length > 1 && args[1].equalsIgnoreCase("loaded")) {
                    sender.sendMessage(msg("admin.scan-start", "&eScanning loaded containers..."));
                    int findings = plugin.scanLoadedInventories();
                    sender.sendMessage(color("&aScan complete. &7Findings: &f" + findings));
                } else {
                    if (!(sender instanceof Player player)) { sender.sendMessage(msg("general.player-only", "&cPlayer only.")); return true; }
                    sender.sendMessage(msg("admin.scan-start", "&eScanning..."));
                    int findings = plugin.scanPlayerInventory(player);
                    sender.sendMessage(color("&aScan complete. &7Findings: &f" + findings));
                }
            }
            case "cleanup" -> { plugin.cleanupCaches(); sender.sendMessage(color("&a[AntiDupe] Runtime caches cleaned.")); }
            case "history" -> history(sender);
            case "recovery" -> {
                if (args.length >= 3 && args[1].equalsIgnoreCase("restore") && sender instanceof Player player) {
                    try {
                        boolean ok = plugin.getRecoveryManager().restore(player, java.util.UUID.fromString(args[2]));
                        sender.sendMessage(color(ok ? "&aRecovery restored." : "&cRecovery not found or failed."));
                    } catch (IllegalArgumentException ex) { sender.sendMessage(color("&cInvalid transaction UUID.")); }
                } else {
                    sender.sendMessage(color("&6Recovery records: &f" + plugin.getRecoveryManager().list().size()));
                    for (String id : plugin.getRecoveryManager().list()) sender.sendMessage(color("&7- &f" + id));
                }
            }
            case "debug" -> {
                sender.sendMessage(color("&7Detection=" + plugin.getConfig().getBoolean("detection.enabled", true)
                        + " | Shulker=" + plugin.getConfig().getBoolean("shulker.enabled", true)
                        + " | Vault=" + plugin.getEconomyRollbackManager().isAvailable()));
            }
            default -> help(sender);
        }
        return true;
    }

    private void status(CommandSender sender) {
        sender.sendMessage(msg("status.header", "&8&m-----------------------------"));
        sender.sendMessage(msg("status.title", "&b&lBedrockAntiDupe Status"));
        sender.sendMessage(msg("status.enabled", "&7Status: %status%").replace("%status%", yes(plugin.getConfig().getBoolean("settings.enabled", true))));
        sender.sendMessage(msg("status.detection", "&7Detection: %detection%").replace("%detection%", yes(plugin.getConfig().getBoolean("detection.enabled", true))));
        sender.sendMessage(msg("status.shulker", "&7Shulker Protection: %shulker%").replace("%shulker%", yes(plugin.getConfig().getBoolean("shulker.enabled", true))));
        sender.sendMessage(msg("status.economy", "&7Economy Rollback: %economy%").replace("%economy%", yes(plugin.getEconomyRollbackManager().isAvailable())));
        sender.sendMessage(msg("status.discord", "&7Discord Webhook: %discord%").replace("%discord%", yes(plugin.getConfig().getBoolean("discord.enabled", false))));
        sender.sendMessage(msg("status.footer", "&8&m-----------------------------"));
    }

    private void history(CommandSender sender) {
        sender.sendMessage(color("&6&lRecent AntiDupe Transactions"));
        int shown = 0;
        java.util.List<TransactionLedger.TransactionRecord> records = new java.util.ArrayList<>(plugin.getTransactionLedger().getHistory());
        records.sort(java.util.Comparator.comparingLong(TransactionLedger.TransactionRecord::timestamp).reversed());
        for (TransactionLedger.TransactionRecord record : records) {
            sender.sendMessage(color("&7" + record.timestamp() + " &f" + record.transactionId()
                    + " &8| &e" + record.source() + " &8| &b+" + record.totalPositiveIncrease()));
            if (++shown >= 10) break;
        }
        if (shown == 0) sender.sendMessage(color("&7No recent transactions."));
    }

    private void help(CommandSender sender) {
        sender.sendMessage(color("&6&lAntiDupe Commands"));
        sender.sendMessage(color("&e/antidupe reload &7- Reload configuration"));
        sender.sendMessage(color("&e/antidupe status &7- Protection status"));
        sender.sendMessage(color("&e/antidupe scan [loaded] &7- Scan inventory or loaded containers"));
        sender.sendMessage(color("&e/antidupe cleanup &7- Clean runtime state"));
        sender.sendMessage(color("&e/antidupe history &7- Recent transactions"));
        sender.sendMessage(color("&e/antidupe recovery [restore <id>] &7- Safe item recovery"));
        sender.sendMessage(color("&e/antidupe debug &7- Debug status"));
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) return List.of();
        List<String> options = List.of("reload", "status", "scan", "cleanup", "history", "recovery", "debug");
        String input = args[0].toLowerCase();
        List<String> result = new ArrayList<>();
        for (String option : options) if (option.startsWith(input)) result.add(option);
        return result;
    }

    private String msg(String path, String fallback) {
        // messages.yml is deliberately loaded from the plugin data folder.
        // Keep command behavior resilient if the file is missing/corrupt.
        try {
            org.bukkit.configuration.file.YamlConfiguration messages = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                    new java.io.File(plugin.getDataFolder(), "messages.yml"));
            String value = messages.getString(path, fallback);
            String prefix = messages.getString("prefix", "");
            return color((prefix == null ? "" : prefix) + value);
        } catch (Exception ignored) { return color(fallback); }
    }
    private String yes(boolean value) { return color(value ? "&aYES" : "&cNO"); }
    private String color(String value) { return ChatColor.translateAlternateColorCodes('&', value); }
}
