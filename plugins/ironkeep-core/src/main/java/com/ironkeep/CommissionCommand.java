package com.ironkeep;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

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
            case "new" -> handleNew(player);
            case "submit" -> handleSubmit(player);
            case "status" -> handleStatus(player);
            default -> sendUsage(player);
        }
    }

    private void handleNew(Player player) {
        CommissionManager manager = plugin.getCommissionManager();
        UUID uuid = player.getUniqueId();

        if (manager.hasActiveCommission(uuid)) {
            player.sendMessage(PREFIX + ChatColor.RED + "You already have an active commission. Use "
                    + ChatColor.YELLOW + "/commission status" + ChatColor.RED + " to view it.");
            return;
        }

        Commission commission = manager.assignCommission(uuid);
        if (commission == null) {
            player.sendMessage(PREFIX + ChatColor.RED + "No commissions are available right now.");
            return;
        }

        player.sendMessage(PREFIX + ChatColor.GREEN + "New commission assigned!");
        player.sendMessage(ChatColor.GOLD + "  Item:     " + ChatColor.YELLOW + formatItem(commission.getItem()));
        player.sendMessage(ChatColor.GOLD + "  Quantity: " + ChatColor.YELLOW + commission.getQuantity());
        player.sendMessage(ChatColor.GOLD + "  Reward:   " + ChatColor.YELLOW + formatCoins(commission.getReward()));
    }

    private void handleSubmit(Player player) {
        CommissionManager manager = plugin.getCommissionManager();
        UUID uuid = player.getUniqueId();

        if (!manager.hasActiveCommission(uuid)) {
            player.sendMessage(PREFIX + ChatColor.RED + "You have no active commission. Use "
                    + ChatColor.YELLOW + "/commission new" + ChatColor.RED + " to get one.");
            return;
        }

        Commission commission = manager.getActiveCommission(uuid);
        Material material = Material.matchMaterial(commission.getItem());

        if (material == null) {
            player.sendMessage(PREFIX + ChatColor.RED + "Unknown item in commission: " + commission.getItem());
            return;
        }

        int needed = commission.getQuantity();
        int inInventory = countItems(player, material);

        if (inInventory < needed) {
            int missing = needed - inInventory;
            player.sendMessage(PREFIX + ChatColor.RED + "You still need " + ChatColor.YELLOW + missing + "x "
                    + formatItem(commission.getItem()) + ChatColor.RED + " to complete this commission.");
            return;
        }

        // Remove items and award coins
        removeItems(player, material, needed);
        manager.addBalance(uuid, commission.getReward());
        manager.clearCommission(uuid);

        player.sendMessage(PREFIX + ChatColor.GREEN + "Commission complete! You earned "
                + ChatColor.YELLOW + formatCoins(commission.getReward()) + ChatColor.GREEN + ".");
        player.sendMessage(ChatColor.GOLD + "  Balance: " + ChatColor.YELLOW + formatCoins(manager.getBalance(uuid)));
    }

    private void handleStatus(Player player) {
        CommissionManager manager = plugin.getCommissionManager();
        UUID uuid = player.getUniqueId();

        if (!manager.hasActiveCommission(uuid)) {
            player.sendMessage(PREFIX + ChatColor.YELLOW + "You have no active commission. Use "
                    + ChatColor.GOLD + "/commission new" + ChatColor.YELLOW + " to get one.");
            return;
        }

        Commission commission = manager.getActiveCommission(uuid);
        player.sendMessage(PREFIX + ChatColor.GOLD + "Active Commission:");
        player.sendMessage(ChatColor.GOLD + "  Item:     " + ChatColor.YELLOW + formatItem(commission.getItem()));
        player.sendMessage(ChatColor.GOLD + "  Quantity: " + ChatColor.YELLOW + commission.getQuantity());
        player.sendMessage(ChatColor.GOLD + "  Reward:   " + ChatColor.YELLOW + formatCoins(commission.getReward()));
    }

    private void sendUsage(Player player) {
        player.sendMessage(ChatColor.GOLD + "--- Commission Commands ---");
        player.sendMessage(ChatColor.YELLOW + "/commission new" + ChatColor.GRAY + " — Get a new commission");
        player.sendMessage(ChatColor.YELLOW + "/commission submit" + ChatColor.GRAY + " — Turn in your commission");
        player.sendMessage(ChatColor.YELLOW + "/commission status" + ChatColor.GRAY + " — View active commission");
    }

    private int countItems(Player player, Material material) {
        int count = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getType() == material) {
                count += stack.getAmount();
            }
        }
        return count;
    }

    private void removeItems(Player player, Material material, int amount) {
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType() != material) continue;
            if (stack.getAmount() <= remaining) {
                remaining -= stack.getAmount();
                contents[i] = null;
            } else {
                stack.setAmount(stack.getAmount() - remaining);
                remaining = 0;
            }
        }
        player.getInventory().setContents(contents);
    }

    private String formatItem(String materialName) {
        String lower = materialName.replace('_', ' ').toLowerCase();
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private String formatCoins(double amount) {
        if (amount == Math.floor(amount)) {
            return (long) amount + " coins";
        }
        return String.format("%.2f coins", amount);
    }
}
