package com.ironkeep;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.UUID;

@SuppressWarnings("UnstableApiUsage")
public class EscapeCommand implements BasicCommand {

    private static final String PREFIX = ChatColor.LIGHT_PURPLE + "[Escape] " + ChatColor.RESET;

    private final IronKeepPlugin plugin;

    public EscapeCommand(IronKeepPlugin plugin) {
        this.plugin = plugin;
    }

    public void register(Commands commands) {
        commands.register("escape", "Prestige by escaping the prison. Requires Rank 4.", this);
    }

    @Override
    public void execute(CommandSourceStack stack, String[] args) {
        if (!(stack.getSender() instanceof Player player)) {
            stack.getSender().sendMessage(ChatColor.RED + "Only players can use this command.");
            return;
        }

        UUID uuid = player.getUniqueId();
        RankManager rankManager = plugin.getRankManager();
        EscapeManager escapeManager = plugin.getEscapeManager();
        CurrencyManager currency = plugin.getCurrencyManager();

        // Must be Rank 4
        int currentRank = rankManager.getPlayerRank(uuid);
        if (currentRank < rankManager.getMaxRank()) {
            player.sendMessage(PREFIX + ChatColor.RED + "You must be Rank 4 ("
                    + rankManager.getDefinition(rankManager.getMaxRank()).getDisplayName()
                    + ") to escape.");
            return;
        }

        // Check max escape level
        int currentEscape = escapeManager.getEscapeLevel(uuid);
        int maxEscape = escapeManager.getMaxEscapeLevel();
        if (currentEscape >= maxEscape) {
            player.sendMessage(PREFIX + ChatColor.RED
                    + "You have already reached the maximum escape level (Escape " + maxEscape + ").");
            return;
        }

        // Check cost
        int nextEscape = currentEscape + 1;
        double cost = escapeManager.getEscapeCost(nextEscape);
        double balance = currency.getBalance(uuid);
        if (balance < cost) {
            player.sendMessage(PREFIX + ChatColor.RED + "Insufficient Gold Coins. You need "
                    + ChatColor.YELLOW + format(cost) + ChatColor.RED + " but have "
                    + ChatColor.YELLOW + format(balance) + ChatColor.RED + ".");
            return;
        }

        // Execute escape: deduct cost, zero out remaining coins, reset rank, cancel commission, increment escape
        currency.addBalance(uuid, -cost);
        currency.setBalance(uuid, 0); // zero out any remaining Gold Coins
        rankManager.setPlayerRank(uuid, 1);
        plugin.getCommissionManager().cancelCommission(uuid);
        escapeManager.setEscapeLevel(uuid, nextEscape);

        double bonusPct = (escapeManager.getGoldCoinMultiplier(uuid) - 1.0) * 100;

        player.sendMessage(PREFIX + ChatColor.LIGHT_PURPLE + "You have escaped the prison!");
        player.sendMessage(ChatColor.GOLD + "  Escape Level: " + ChatColor.YELLOW + nextEscape);
        player.sendMessage(ChatColor.GOLD + "  Bonus:        " + ChatColor.YELLOW + (int) bonusPct + "% Gold Coin income");
        player.sendMessage(ChatColor.GOLD + "  Rank reset to: " + ChatColor.YELLOW
                + rankManager.getDefinition(1).getDisplayName());
        player.sendMessage(ChatColor.GRAY + "  (Shards balance untouched)");
    }

    private String format(double amount) {
        if (amount == Math.floor(amount)) return String.format("%,d", (long) amount);
        return String.format("%,.2f", amount);
    }
}
