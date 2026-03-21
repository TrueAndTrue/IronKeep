package com.ironkeep;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

@SuppressWarnings("UnstableApiUsage")
public class BalanceCommand implements BasicCommand {

    private final IronKeepPlugin plugin;

    public BalanceCommand(IronKeepPlugin plugin) {
        this.plugin = plugin;
    }

    public void register(Commands commands) {
        commands.register("balance", "Check your Gold Coin balance", this);
    }

    @Override
    public void execute(CommandSourceStack stack, String[] args) {
        if (!(stack.getSender() instanceof Player player)) {
            stack.getSender().sendMessage(ChatColor.RED + "Only players can use this command.");
            return;
        }

        double balance = plugin.getCurrencyManager().getBalance(player.getUniqueId());
        player.sendMessage(ChatColor.GOLD + "Your balance: " + ChatColor.YELLOW + formatCoins(balance));
    }

    private String formatCoins(double amount) {
        if (amount == Math.floor(amount)) {
            return (long) amount + " Gold Coins";
        }
        return String.format("%.2f Gold Coins", amount);
    }
}
