package com.ironkeep;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@SuppressWarnings("UnstableApiUsage")
public class ResetPlayerCommand implements BasicCommand {

    private final IronKeepPlugin plugin;

    public ResetPlayerCommand(IronKeepPlugin plugin) {
        this.plugin = plugin;
    }

    public void register(Commands commands) {
        commands.register("resetplayer", "[OP] Reset all data for a player", this);
    }

    @Override
    public Collection<String> suggest(CommandSourceStack stack, String[] args) {
        if (args.length <= 1) {
            String partial = args.length == 1 ? args[0].toLowerCase() : "";
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(partial))
                    .toList();
        }
        return List.of();
    }

    @Override
    public void execute(CommandSourceStack stack, String[] args) {
        if (!stack.getSender().isOp()) {
            stack.getSender().sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return;
        }

        if (args.length == 0) {
            stack.getSender().sendMessage(ChatColor.GOLD + "Usage: " + ChatColor.YELLOW + "/resetplayer <player>");
            return;
        }

        String targetName = args[0];

        // Try online player first
        Player online = Bukkit.getPlayerExact(targetName);
        UUID uuid;
        String displayName;

        if (online != null) {
            uuid = online.getUniqueId();
            displayName = online.getName();
        } else {
            // Fall back to offline player lookup by name
            @SuppressWarnings("deprecation")
            OfflinePlayer offline = Bukkit.getOfflinePlayer(targetName);
            if (!offline.hasPlayedBefore() && offline.getName() == null) {
                stack.getSender().sendMessage(ChatColor.RED + "Player '" + targetName + "' not found.");
                return;
            }
            uuid = offline.getUniqueId();
            displayName = offline.getName() != null ? offline.getName() : targetName;
        }

        resetAll(uuid);

        stack.getSender().sendMessage(ChatColor.GREEN + "Reset all data for " + ChatColor.YELLOW + displayName + ChatColor.GREEN + ".");
        if (online != null) {
            online.sendMessage(ChatColor.YELLOW + "Your player data has been reset by an admin.");
            // Re-run join logic so the tutorial, warden label, and starter kit fire
            // immediately without needing a reconnect.
            plugin.getTutorialManager().onPlayerJoin(online);
        }
    }

    private void resetAll(UUID uuid) {
        // Currency
        plugin.getCurrencyManager().setBalance(uuid, 0);
        plugin.getCurrencyManager().setShards(uuid, 0);

        // Commission
        plugin.getCommissionManager().cancelCommission(uuid);

        // Rank
        plugin.getRankManager().setPlayerRank(uuid, 1);

        // Escape
        plugin.getEscapeManager().setEscapeLevel(uuid, 0);

        // Skills
        plugin.getSkillManager().resetSkills(uuid);

        // Daily quest
        plugin.getDailyQuestManager().clearCompletion(uuid);

        // Daily commission bonuses
        plugin.getDailyBonusManager().clearBonuses(uuid);

        // Starter kit
        plugin.getStarterKitManager().clearReceived(uuid);

        // Tutorial progress
        plugin.getTutorialManager().resetTutorial(uuid);
    }
}
