package com.ironkeep;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class BalanceCommand implements CommandExecutor {

    private final IronKeepPlugin plugin;

    public BalanceCommand(IronKeepPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }

        double balance = plugin.getCommissionManager().getBalance(player.getUniqueId());
        String formatted;
        if (balance == Math.floor(balance)) {
            formatted = (long) balance + " coins";
        } else {
            formatted = String.format("%.2f coins", balance);
        }

        player.sendMessage(ChatColor.GOLD + "Your balance: " + ChatColor.YELLOW + formatted);
        return true;
    }
}
