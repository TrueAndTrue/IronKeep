package com.ironkeep;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Handles the Daily Quest chat trigger and /daily command.
 * Quest: type exactly "Hi" (case-insensitive, trimmed) in chat.
 */
@SuppressWarnings({"UnstableApiUsage", "deprecation"})
public class DailyQuestListener implements Listener, BasicCommand {

    private static final String PREFIX = ChatColor.GREEN + "[Daily] " + ChatColor.RESET;
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC);

    private final IronKeepPlugin plugin;

    public DailyQuestListener(IronKeepPlugin plugin) {
        this.plugin = plugin;
    }

    public void register(Commands commands) {
        commands.register("daily", "Check your daily quest status and rewards.", this);
    }

    // /daily command
    @Override
    public void execute(CommandSourceStack stack, String[] args) {
        if (!(stack.getSender() instanceof Player player)) {
            stack.getSender().sendMessage(ChatColor.RED + "Only players can use this command.");
            return;
        }
        DailyQuestManager mgr = plugin.getDailyQuestManager();
        UUID uuid = player.getUniqueId();

        player.sendMessage(ChatColor.GREEN + "--- " + ChatColor.YELLOW + "Daily Quest" + ChatColor.GREEN + " ---");
        player.sendMessage(ChatColor.GOLD + "  Objective: " + ChatColor.WHITE + mgr.getQuestObjective());

        double goldReward = mgr.getGoldReward();
        // Apply escape bonus for display
        if (plugin.getEscapeManager() != null) {
            goldReward = plugin.getEscapeManager().applyBonus(uuid, goldReward);
        }
        player.sendMessage(ChatColor.GOLD + "  Reward:    " + ChatColor.YELLOW + format(goldReward) + " Gold Coins"
                + ChatColor.AQUA + " + " + format(mgr.getShardsReward()) + " Shards");

        if (mgr.hasCompleted(uuid)) {
            String nextReset = TIME_FMT.format(Instant.ofEpochSecond(mgr.nextResetEpoch()));
            player.sendMessage(ChatColor.GREEN + "  Status:    " + ChatColor.GRAY + "Completed ✔");
            player.sendMessage(ChatColor.GRAY + "  Next reset: " + nextReset + " UTC");
        } else {
            player.sendMessage(ChatColor.GREEN + "  Status:    " + ChatColor.YELLOW + "Not yet completed today");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        String message = event.getMessage().trim();
        if (!message.equalsIgnoreCase("hi")) return;

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        DailyQuestManager mgr = plugin.getDailyQuestManager();

        if (mgr.hasCompleted(uuid)) return; // already done, let message pass through normally

        // Grant rewards on main thread
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            double goldReward = mgr.getGoldReward();
            if (plugin.getEscapeManager() != null) {
                goldReward = plugin.getEscapeManager().applyBonus(uuid, goldReward);
            }
            plugin.getCurrencyManager().addBalance(uuid, goldReward);
            plugin.getCurrencyManager().addShards(uuid, mgr.getShardsReward());
            mgr.markCompleted(uuid);

            player.sendMessage(PREFIX + ChatColor.GREEN + "Daily quest complete! You earned "
                    + ChatColor.YELLOW + format(goldReward) + " Gold Coins"
                    + ChatColor.GREEN + " and "
                    + ChatColor.AQUA + format(mgr.getShardsReward()) + " Shards" + ChatColor.GREEN + ".");
        });
    }

    private String format(double amount) {
        if (amount == Math.floor(amount)) return String.format("%,d", (long) amount);
        return String.format("%,.2f", amount);
    }
}
