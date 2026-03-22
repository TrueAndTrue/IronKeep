package com.ironkeep;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class CommissionManager {

    private static final String PREFIX = ChatColor.GOLD + "[Commission] " + ChatColor.RESET;

    private final CommissionRegistry registry;
    private final CommissionStateStore stateStore;
    private final CurrencyManager currencyManager;
    private RankManager rankManager;     // set after construction to avoid circular dependency
    private EscapeManager escapeManager; // set after construction

    public CommissionManager(CommissionRegistry registry, CommissionStateStore stateStore,
                             CurrencyManager currencyManager) {
        this.registry = registry;
        this.stateStore = stateStore;
        this.currencyManager = currencyManager;
    }

    public void setRankManager(RankManager rankManager) {
        this.rankManager = rankManager;
    }

    public void setEscapeManager(EscapeManager escapeManager) {
        this.escapeManager = escapeManager;
    }

    public void assignCommission(Player player, String commissionId) {
        if (hasActiveCommission(player)) {
            player.sendMessage(PREFIX + ChatColor.RED
                    + "You already have an active commission. Complete it first.");
            return;
        }
        CommissionDefinition def = registry.getById(commissionId);
        if (def == null) {
            player.sendMessage(PREFIX + ChatColor.RED + "Unknown commission: " + commissionId);
            return;
        }
        // Rank access check
        if (rankManager != null && !rankManager.canAccept(player.getUniqueId(), def.getType())) {
            player.sendMessage(PREFIX + ChatColor.RED
                    + "Your rank does not permit this commission type.");
            return;
        }
        UUID uuid = player.getUniqueId();
        PlayerCommissionState state = new PlayerCommissionState(uuid);
        state.setActiveCommissionId(def.getId());
        state.setProgress(0);
        stateStore.setState(uuid, state);
        player.sendMessage(PREFIX + ChatColor.GREEN + "Commission accepted: "
                + def.getDisplayName() + " — " + def.getDescription());
    }

    /** Cancels a player's active commission without reward. */
    public void cancelCommission(UUID uuid) {
        stateStore.clearState(uuid);
    }

    public void assignCommission(Player player) {
        if (hasActiveCommission(player)) {
            player.sendMessage(PREFIX + ChatColor.RED + "You already have an active commission. "
                    + "Use " + ChatColor.YELLOW + "/commission status" + ChatColor.RED + " to view it.");
            return;
        }
        // Pick a random commission from rank-accessible pool
        UUID uuid = player.getUniqueId();
        java.util.List<CommissionDefinition> accessible = rankManager != null
                ? rankManager.getAccessibleCommissions(uuid)
                : new java.util.ArrayList<>(registry.getAll().values());
        CommissionDefinition def = accessible.isEmpty() ? null
                : accessible.get(new java.util.Random().nextInt(accessible.size()));
        if (def == null) {
            player.sendMessage(PREFIX + ChatColor.RED + "No commissions are available right now.");
            return;
        }
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

    public void incrementProgress(UUID uuid, int amount) {
        PlayerCommissionState state = stateStore.getState(uuid);
        if (state == null || state.getActiveCommissionId() == null) return;
        CommissionDefinition def = registry.getById(state.getActiveCommissionId());
        if (def == null) return;

        int newProgress = state.getProgress() + amount;
        state.setProgress(newProgress);
        stateStore.setState(uuid, state);

        if (newProgress >= def.getObjectiveQuantity()) {
            org.bukkit.entity.Player player = org.bukkit.Bukkit.getPlayer(uuid);
            if (player != null) {
                player.sendMessage(org.bukkit.ChatColor.GREEN
                        + "Commission objective reached! Use /commission complete to turn in.");
            }
        }
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

    public void skipCommission(Player player) {
        stateStore.clearState(player.getUniqueId());
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
        // Use turn-in item for inventory check (may differ from objective block, e.g. COAL_ORE -> COAL)
        Material material = Material.matchMaterial(def.getTurnInItem());
        if (material == null) {
            player.sendMessage(PREFIX + ChatColor.RED + "Commission item is misconfigured. Contact an admin.");
            return;
        }
        int needed = def.getObjectiveQuantity();
        int inInventory = countItem(player, material);
        if (inInventory < needed) {
            player.sendMessage(PREFIX + ChatColor.RED + "You don't have the required items. Need "
                    + ChatColor.YELLOW + needed + "x " + formatItem(def.getTurnInItem())
                    + ChatColor.RED + " but only have " + inInventory + ".");
            return;
        }
        player.getInventory().removeItem(new ItemStack(material, needed));

        // Apply escape bonus to Gold Coins only (Shards are unaffected)
        double goldReward = def.getRewardAmount();
        if (escapeManager != null) {
            goldReward = escapeManager.applyBonus(uuid, goldReward);
        }
        currencyManager.addBalance(uuid, goldReward);
        if (def.getShardsReward() > 0) {
            currencyManager.addShards(uuid, def.getShardsReward());
        }
        stateStore.clearState(uuid);

        // Build reward message
        String rewardMsg = ChatColor.YELLOW + formatCoins(goldReward) + " Gold Coins";
        if (def.getShardsReward() > 0) {
            rewardMsg += ChatColor.GREEN + " and " + ChatColor.AQUA + formatCoins(def.getShardsReward()) + " Shards";
        }
        // Mention escape bonus if applicable
        if (escapeManager != null && escapeManager.getEscapeLevel(uuid) > 0) {
            int bonusPct = (int) ((escapeManager.getGoldCoinMultiplier(uuid) - 1.0) * 100);
            rewardMsg += ChatColor.GRAY + " (+" + bonusPct + "% escape bonus)";
        }
        player.sendMessage(PREFIX + ChatColor.GREEN + "Commission complete! You earned " + rewardMsg + ChatColor.GREEN + ".");
        player.sendMessage(ChatColor.GOLD + "  Gold Coins: " + ChatColor.YELLOW + formatCoins(currencyManager.getBalance(uuid)));
        if (def.getShardsReward() > 0) {
            player.sendMessage(ChatColor.AQUA + "  Shards:     " + ChatColor.WHITE + formatCoins(currencyManager.getShards(uuid)));
        }
    }

    private int countItem(Player player, Material material) {
        int count = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getType() == material) {
                count += stack.getAmount();
            }
        }
        return count;
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
