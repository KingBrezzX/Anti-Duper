package xyz.zyrex.bedrockantidupe;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class AntiDupeCommand implements CommandExecutor, TabCompleter {

    private final BedrockAntiDupe plugin;

    public AntiDupeCommand(BedrockAntiDupe plugin) {
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
                    color("&cNo permission.")
            );

            return true;
        }

        if (args.length == 0) {

            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {

            case "reload" -> {

                plugin.reloadConfig();

                sender.sendMessage(
                        color(
                                "&a[AntiDupe] Configuration reloaded."
                        )
                );
            }

            case "status" -> {

                sender.sendMessage(
                        color("&6&lAntiDupe Status")
                );

                sender.sendMessage(
                        color(
                                "&7Plugin: &a"
                                        + plugin.isEnabled()
                );

                sender.sendMessage(
                        color(
                                "&7Detection: &a"
                                        + plugin.getConfig()
                                        .getBoolean(
                                                "detection.enabled",
                                                true
                                        )
                );

                sender.sendMessage(
                        color(
                                "&7Shop protection: &a"
                                        + plugin.getConfig()
                                        .getBoolean(
                                                "shop.record-context",
                                                true
                                        )
                );

                sender.sendMessage(
                        color(
                                "&7Economy rollback: &a"
                                        + plugin
                                        .getEconomyRollbackManager()
                                        .isAvailable()
                );

                sender.sendMessage(
                        color(
                                "&7Discord webhook: &a"
                                        + plugin
                                        .getConfig()
                                        .getBoolean(
                                                "discord.enabled",
                                                false
                                        )
                );
            }

            case "scan" -> {

                if (!(sender instanceof Player player)) {

                    sender.sendMessage(
                            color(
                                    "&cThis command must be executed by a player."
                            )
                    );

                    return true;
                }

                sender.sendMessage(
                        color(
                                "&e[AntiDupe] Scanning your inventory..."
                        )
                );

                plugin.scanPlayerInventory(
                        player
                );

                sender.sendMessage(
                        color(
                                "&a[AntiDupe] Scan completed."
                        )
                );
            }

            case "cleanup" -> {

                plugin.cleanupCaches();

                sender.sendMessage(
                        color(
                                "&a[AntiDupe] Internal caches cleaned."
                        )
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
                color("&6&lAntiDupe Commands")
        );

        sender.sendMessage(
                color(
                        "&e/antidupe reload &7- Reload config"
                )
        );

        sender.sendMessage(
                color(
                        "&e/antidupe status &7- Show protection status"
                )
        );

        sender.sendMessage(
                color(
                        "&e/antidupe scan &7- Scan your inventory"
                )
        );

        sender.sendMessage(
                color(
                        "&e/antidupe cleanup &7- Clean internal caches"
                )
        );
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender,
            Command command,
            String alias,
            String[] args
    ) {

        if (args.length == 1) {

            List<String> options =
                    List.of(
                            "reload",
                            "status",
                            "scan",
                            "cleanup"
                    );

            String input =
                    args[0].toLowerCase();

            List<String> result =
                    new ArrayList<>();

            for (String option : options) {

                if (option.startsWith(input)) {
                    result.add(option);
                }
            }

            return result;
        }

        return List.of();
    }

    private String color(
            String message
    ) {

        return ChatColor.translateAlternateColorCodes(
                '&',
                message
        );
    }
                  }
