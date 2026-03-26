package com.ironkeep;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.UUID;

@SuppressWarnings("UnstableApiUsage")
public class BalanceCommand implements BasicCommand {

    private final IronKeepPlugin plugin;

    public BalanceCommand(IronKeepPlugin plugin) {
        this.plugin = plugin;
    }

    public void register(Commands commands) {
        commands.register("balance", "Check your currency balances", this);
    }

    @Override
    public void execute(CommandSourceStack stack, String[] args) {
        if (!(stack.getSender() instanceof Player player)) {
            stack.getSender().sendMessage(ChatColor.RED + "Only players can use this command.");
            return;
        }

        UUID uuid = player.getUniqueId();
        CurrencyManager currency = plugin.getCurrencyManager();

        double goldCoins = currency.getBalance(uuid);
        double shards = currency.getShards(uuid);

        player.sendMessage(ChatColor.GOLD + "--- " + ChatColor.YELLOW + "Your Balance" + ChatColor.GOLD + " ---");
        player.sendMessage(ChatColor.GOLD + "  Gold Coins: " + ChatColor.YELLOW + format(goldCoins));
        player.sendMessage(ChatColor.AQUA + "  Shards:     " + ChatColor.WHITE + format(shards));
    }

    private String format(double amount) {
        return String.format("%,d", Math.round(amount));
    }
}
