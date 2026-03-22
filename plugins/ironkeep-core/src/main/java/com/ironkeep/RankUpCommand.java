package com.ironkeep;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.UUID;

@SuppressWarnings("UnstableApiUsage")
public class RankUpCommand implements BasicCommand {

    private static final String PREFIX = ChatColor.GOLD + "[Rank] " + ChatColor.RESET;

    private final IronKeepPlugin plugin;

    public RankUpCommand(IronKeepPlugin plugin) {
        this.plugin = plugin;
    }

    public void register(Commands commands) {
        commands.register("rankup", "Rank up to the next tier. Costs Gold Coins.", this);
    }

    @Override
    public void execute(CommandSourceStack stack, String[] args) {
        if (!(stack.getSender() instanceof Player player)) {
            stack.getSender().sendMessage(ChatColor.RED + "Only players can use this command.");
            return;
        }

        UUID uuid = player.getUniqueId();
        RankManager rankManager = plugin.getRankManager();
        CurrencyManager currency = plugin.getCurrencyManager();

        int currentRank = rankManager.getPlayerRank(uuid);
        int maxRank = rankManager.getMaxRank();

        if (currentRank >= maxRank) {
            player.sendMessage(PREFIX + ChatColor.RED + "You are already at the highest rank (" 
                    + rankManager.getDefinition(maxRank).getDisplayName() + ").");
            return;
        }

        int nextRank = currentRank + 1;
        RankDefinition nextDef = rankManager.getDefinition(nextRank);
        if (nextDef == null) {
            player.sendMessage(PREFIX + ChatColor.RED + "Rank " + nextRank + " is not configured.");
            return;
        }

        double cost = nextDef.getCost();
        double balance = currency.getBalance(uuid);

        if (balance < cost) {
            player.sendMessage(PREFIX + ChatColor.RED + "Insufficient Gold Coins. You need "
                    + ChatColor.YELLOW + format(cost) + ChatColor.RED + " but have "
                    + ChatColor.YELLOW + format(balance) + ChatColor.RED + ".");
            return;
        }

        // Deduct cost and rank up
        currency.addBalance(uuid, -cost);
        rankManager.setPlayerRank(uuid, nextRank);

        // Cancel active commission only if the new rank (cumulatively) no longer allows it
        CommissionManager commManager = plugin.getCommissionManager();
        if (commManager.hasActiveCommission(player)) {
            CommissionDefinition active = commManager.getActiveCommission(player);
            if (active != null && !rankManager.canAccept(uuid, active.getType())) {
                commManager.cancelCommission(uuid);
                player.sendMessage(PREFIX + ChatColor.YELLOW
                        + "Your previous commission has been cancelled due to ranking up.");
            }
        }

        player.sendMessage(PREFIX + ChatColor.GREEN + "Congratulations! You've ranked up to "
                + ChatColor.GOLD + nextDef.getDisplayName() + ChatColor.GREEN + "!");
        player.sendMessage(ChatColor.YELLOW + "  Unlocked: " + String.join(", ", nextDef.getUnlockedTypes()));
        player.sendMessage(ChatColor.GRAY + "  Remaining balance: " + format(currency.getBalance(uuid)) + " Gold Coins");
    }

    private String format(double amount) {
        if (amount == Math.floor(amount)) return String.format("%,d", (long) amount);
        return String.format("%,.2f", amount);
    }
}
