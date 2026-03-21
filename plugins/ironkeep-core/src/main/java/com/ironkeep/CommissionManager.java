package com.ironkeep;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.UUID;

public class CommissionManager {

    private static final String PREFIX = ChatColor.GOLD + "[Commission] " + ChatColor.RESET;

    private final CommissionRegistry registry;
    private final CommissionStateStore stateStore;
    private final CurrencyManager currencyManager;

    public CommissionManager(CommissionRegistry registry, CommissionStateStore stateStore,
                             CurrencyManager currencyManager) {
        this.registry = registry;
        this.stateStore = stateStore;
        this.currencyManager = currencyManager;
    }

    public void assignCommission(Player player) {
        if (hasActiveCommission(player)) {
            player.sendMessage(PREFIX + ChatColor.RED + "You already have an active commission. "
                    + "Use " + ChatColor.YELLOW + "/commission status" + ChatColor.RED + " to view it.");
            return;
        }
        CommissionDefinition def = registry.getRandom();
        if (def == null) {
            player.sendMessage(PREFIX + ChatColor.RED + "No commissions are available right now.");
            return;
        }
        UUID uuid = player.getUniqueId();
        PlayerCommissionState state = new PlayerCommissionState(uuid);
        state.setActiveCommissionId(def.getId());
        state.setProgress(0);
        stateStore.setState(uuid, state);

        player.sendMessage(PREFIX + ChatColor.GREEN + "New commission assigned!");
        player.sendMessage(ChatColor.GOLD + "  Name:     " + ChatColor.YELLOW + def.getDisplayName());
        player.sendMessage(ChatColor.GOLD + "  Task:     " + ChatColor.YELLOW + def.getDescription());
        player.sendMessage(ChatColor.GOLD + "  Goal:     " + ChatColor.YELLOW
                + def.getObjectiveQuantity() + "x " + formatItem(def.getObjectiveItem()));
        player.sendMessage(ChatColor.GOLD + "  Reward:   " + ChatColor.YELLOW + formatCoins(def.getRewardAmount()));
    }

    public void recordProgress(Player player, int amount) {
        UUID uuid = player.getUniqueId();
        PlayerCommissionState state = stateStore.getState(uuid);
        if (state == null || state.getActiveCommissionId() == null) return;
        CommissionDefinition def = registry.getById(state.getActiveCommissionId());
        if (def == null) return;

        int newProgress = state.getProgress() + amount;
        state.setProgress(newProgress);
        stateStore.setState(uuid, state);

        if (newProgress >= def.getObjectiveQuantity()) {
            player.sendMessage(ChatColor.GREEN
                    + "Commission objective reached! Use /commission complete to turn in.");
        }
    }

    public CommissionDefinition getActiveCommission(Player player) {
        PlayerCommissionState state = stateStore.getState(player.getUniqueId());
        if (state == null || state.getActiveCommissionId() == null) return null;
        return registry.getById(state.getActiveCommissionId());
    }

    public PlayerCommissionState getPlayerState(Player player) {
        return stateStore.getState(player.getUniqueId());
    }

    public boolean hasActiveCommission(Player player) {
        PlayerCommissionState state = stateStore.getState(player.getUniqueId());
        return state != null && state.getActiveCommissionId() != null;
    }

    public void completeCommission(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerCommissionState state = stateStore.getState(uuid);
        if (state == null || state.getActiveCommissionId() == null) {
            player.sendMessage(PREFIX + ChatColor.RED + "You have no active commission.");
            return;
        }
        CommissionDefinition def = registry.getById(state.getActiveCommissionId());
        if (def == null) {
            player.sendMessage(PREFIX + ChatColor.RED
                    + "Your commission is no longer valid. It has been cleared.");
            stateStore.clearState(uuid);
            return;
        }
        if (state.getProgress() < def.getObjectiveQuantity()) {
            int remaining = def.getObjectiveQuantity() - state.getProgress();
            player.sendMessage(PREFIX + ChatColor.RED + "Commission not yet complete. Progress: "
                    + ChatColor.YELLOW + state.getProgress() + "/" + def.getObjectiveQuantity()
                    + ChatColor.RED + " (" + remaining + " remaining).");
            return;
        }
        currencyManager.addBalance(uuid, def.getRewardAmount());
        stateStore.clearState(uuid);
        player.sendMessage(PREFIX + ChatColor.GREEN + "Commission complete! You earned "
                + ChatColor.YELLOW + formatCoins(def.getRewardAmount()) + ChatColor.GREEN + ".");
        player.sendMessage(ChatColor.GOLD + "  Balance: "
                + ChatColor.YELLOW + formatCoins(currencyManager.getBalance(uuid)));
    }

    private String formatItem(String materialName) {
        String lower = materialName.replace('_', ' ').toLowerCase();
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private String formatCoins(double amount) {
        if (amount == Math.floor(amount)) {
            return (long) amount + " Gold Coins";
        }
        return String.format("%.2f Gold Coins", amount);
    }
}
