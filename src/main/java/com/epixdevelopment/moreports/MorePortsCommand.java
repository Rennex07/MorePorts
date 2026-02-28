package com.epixdevelopment.moreports;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class MorePortsCommand implements CommandExecutor {

    private final MorePorts plugin;

    public MorePortsCommand(MorePorts plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!sender.hasPermission("moreports.admin")) {
            sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(Component.text("MorePorts " + plugin.getPluginMeta().getVersion(), NamedTextColor.GREEN));
            sender.sendMessage(Component.text("/moreports reload", NamedTextColor.YELLOW)
                    .append(Component.text(" - Reload config and rebind ports", NamedTextColor.WHITE)));
            sender.sendMessage(Component.text("/moreports status", NamedTextColor.YELLOW)
                    .append(Component.text(" - Show active ports", NamedTextColor.WHITE)));
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            plugin.reloadConfig();
            plugin.reloadPorts();
            sender.sendMessage(Component.text("Configuration reloaded and ports updated.", NamedTextColor.GREEN));
            return true;
        }

        if (args[0].equalsIgnoreCase("status")) {
            sender.sendMessage(Component.text("Active Ports:", NamedTextColor.GOLD));
            sender.sendMessage(Component.text("Check console for details (PortManager tracking to be implemented).", NamedTextColor.YELLOW));
            return true;
        }

        return true;
    }
}
