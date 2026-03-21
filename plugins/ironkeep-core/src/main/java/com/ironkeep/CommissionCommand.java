package com.ironkeep;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.Map;

@SuppressWarnings("UnstableApiUsage")
public class CommissionCommand implements BasicCommand {

    private static final String PREFIX = ChatColor.GOLD + "[Commission] " + ChatColor.RESET;

    private final IronKeepPlugin plugin;

    public CommissionCommand(IronKeepPlugin plugin) {
        this.plugin = plugin;
    }

    public void register(Commands commands) {
        commands.register("commission", "Manage your commissions", this);
    }

    @Override
    public void execute(CommandSourceStack stack, String[] args) {
        if (!(stack.getSender() instanceof Player player)) {
            stack.getSender().sendMessage(ChatColor.RED + "Only players can use this command.");
            return;
        }

        if (args.length == 0) {
            sendUsage(player);
            return;
        }

        switch (args[0].toLowerCase()) {
            case "new" -> plugin.getCommissionManager().assignCommission(player);
            case "status" -> handleStatus(player);
            case "complete" -> plugin.getCommissionManager().completeCommission(player);
            case "list" -> handleList(player);
            default -> sendUsage(player);
        }
    }

    private void handleStatus(Player player) {
        CommissionManager manager = plugin.getCommissionManager();
        if (!manager.hasActiveCommission(player)) {
            player.sendMessage(PREFIX + ChatColor.YELLOW + "You have no active commission. Use "
                    + ChatColor.GOLD + "/commission new" + ChatColor.YELLOW + " to get one.");
            return;
        }
        CommissionDefinition def = manager.getActiveCommission(player);
        PlayerCommissionState state = manager.getPlayerState(player);
        player.sendMessage(PREFIX + ChatColor.GOLD + "Active Commission:");
        player.sendMessage(ChatColor.GOLD + "  Name:     " + ChatColor.YELLOW + def.getDisplayName());
        player.sendMessage(ChatColor.GOLD + "  Task:     " + ChatColor.YELLOW + def.getDescription());
        player.sendMessage(ChatColor.GOLD + "  Progress: " + ChatColor.YELLOW
                + state.getProgress() + "/" + def.getObjectiveQuantity());
        player.sendMessage(ChatColor.GOLD + "  Reward:   " + ChatColor.YELLOW + formatCoins(def.getRewardAmount()));
    }

    private void handleList(Player player) {
        Map<String, CommissionDefinition> all = plugin.getCommissionRegistry().getAll();
        if (all.isEmpty()) {
            player.sendMessage(PREFIX + ChatColor.YELLOW + "No commissions are currently available.");
            return;
        }
        player.sendMessage(ChatColor.GOLD + "--- Available Commissions ---");
        for (CommissionDefinition def : all.values()) {
            player.sendMessage(ChatColor.YELLOW + def.getId()
                    + ChatColor.GRAY + " — " + def.getDisplayName());
        }
    }

    private void sendUsage(Player player) {
        player.sendMessage(ChatColor.GOLD + "--- Commission Commands ---");
        player.sendMessage(ChatColor.YELLOW + "/commission new"
                + ChatColor.GRAY + " — Get a new commission");
        player.sendMessage(ChatColor.YELLOW + "/commission status"
                + ChatColor.GRAY + " — View active commission and progress");
        player.sendMessage(ChatColor.YELLOW + "/commission complete"
                + ChatColor.GRAY + " — Turn in your commission");
        player.sendMessage(ChatColor.YELLOW + "/commission list"
                + ChatColor.GRAY + " — List all available commissions");
    }

    private String formatCoins(double amount) {
        if (amount == Math.floor(amount)) {
            return (long) amount + " Gold Coins";
        }
        return String.format("%.2f Gold Coins", amount);
    }
}
