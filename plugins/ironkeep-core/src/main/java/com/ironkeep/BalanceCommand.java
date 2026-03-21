package com.ironkeep;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

@SuppressWarnings("UnstableApiUsage")
public class BalanceCommand implements BasicCommand {

    private final IronKeepPlugin plugin;

    public BalanceCommand(IronKeepPlugin plugin) {
        this.plugin = plugin;
    }

    public void register(Commands commands) {
        commands.register("balance", "Check your coin balance", this);
    }

    @Override
    public void execute(CommandSourceStack stack, String[] args) {
        if (!(stack.getSender() instanceof Player player)) {
            stack.getSender().sendMessage(ChatColor.RED + "Only players can use this command.");
            return;
        }

        double balance = plugin.getCommissionManager().getBalance(player.getUniqueId());
        String formatted;
        if (balance == Math.floor(balance)) {
            formatted = (long) balance + " coins";
        } else {
            formatted = String.format("%.2f coins", balance);
        }

        player.sendMessage(ChatColor.GOLD + "Your balance: " + ChatColor.YELLOW + formatted);
    }
}
