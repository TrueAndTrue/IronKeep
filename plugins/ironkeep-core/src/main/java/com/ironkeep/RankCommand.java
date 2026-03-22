package com.ironkeep;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.UUID;

@SuppressWarnings("UnstableApiUsage")
public class RankCommand implements BasicCommand {

    private final IronKeepPlugin plugin;

    public RankCommand(IronKeepPlugin plugin) {
        this.plugin = plugin;
    }

    public void register(Commands commands) {
        commands.register("rank", "View your current rank and rankup cost.", this);
    }

    @Override
    public void execute(CommandSourceStack stack, String[] args) {
        if (!(stack.getSender() instanceof Player player)) {
            stack.getSender().sendMessage(ChatColor.RED + "Only players can use this command.");
            return;
        }

        UUID uuid = player.getUniqueId();
        RankManager rankManager = plugin.getRankManager();

        int currentRank = rankManager.getPlayerRank(uuid);
        int maxRank = rankManager.getMaxRank();

        player.sendMessage(ChatColor.GOLD + "--- " + ChatColor.YELLOW + "Your Rank" + ChatColor.GOLD + " ---");
        player.sendMessage(ChatColor.GOLD + "  Rank:      " + ChatColor.YELLOW + currentRank);

        int nextRank = currentRank + 1;
        RankDefinition nextDef = rankManager.getDefinition(nextRank);
        if (nextDef != null) {
            player.sendMessage(ChatColor.GOLD + "  Next Rank: " + ChatColor.YELLOW + nextRank);
            player.sendMessage(ChatColor.GOLD + "  Cost:      " + ChatColor.YELLOW + format(nextDef.getCost()) + " Gold Coins");
        } else {
            player.sendMessage(ChatColor.GOLD + "  Next Rank: " + ChatColor.GRAY + "Max rank reached — use /escape to prestige!");
        }

        // Escape level info
        EscapeManager escapeManager = plugin.getEscapeManager();
        if (escapeManager != null) {
            int escapeLevel = escapeManager.getEscapeLevel(uuid);
            int maxEscape = escapeManager.getMaxEscapeLevel();
            int bonusPct = (int) ((escapeManager.getGoldCoinMultiplier(uuid) - 1.0) * 100);
            player.sendMessage(ChatColor.LIGHT_PURPLE + "  Escape Lvl: " + ChatColor.WHITE + escapeLevel + "/" + maxEscape
                    + (bonusPct > 0 ? ChatColor.GRAY + " (+" + bonusPct + "% Gold Coin bonus)" : ""));
        }
    }

    private String format(double amount) {
        if (amount == Math.floor(amount)) return String.format("%,d", (long) amount);
        return String.format("%,.2f", amount);
    }
}
