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
    private RankManager rankManager;         // set after construction to avoid circular dependency
    private EscapeManager escapeManager;     // set after construction
    private MailRoomManager mailRoomManager; // set after construction
    private KitchenManager kitchenManager;   // set after construction
    private SkillManager skillManager;       // set after construction

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

    public void setMailRoomManager(MailRoomManager mailRoomManager) {
        this.mailRoomManager = mailRoomManager;
    }

    public void setKitchenManager(KitchenManager kitchenManager) {
        this.kitchenManager = kitchenManager;
    }

    public void setSkillManager(SkillManager skillManager) {
        this.skillManager = skillManager;
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
        // Apply skill-based objective reduction
        if (skillManager != null) {
            int reduction = skillManager.getObjectiveReduction(uuid, def.getType());
            if (reduction > 0) {
                int reduced = Math.max(1, def.getObjectiveQuantity() - reduction);
                state.setOverrideQuantity(reduced);
            }
        }
        stateStore.setState(uuid, state);
        player.sendMessage(PREFIX + ChatColor.GREEN + "Commission accepted: "
                + def.getDisplayName() + " — " + def.getDescription());
        if (def.getType().equalsIgnoreCase("MAIL_SORTING") && mailRoomManager != null) {
            int rankNum = rankManager != null ? rankManager.getPlayerRank(uuid) : 1;
            mailRoomManager.assignMail(player, rankNum);
        }
        if (def.getType().equalsIgnoreCase("COOKING") && kitchenManager != null) {
            int rankNum = rankManager != null ? rankManager.getPlayerRank(uuid) : 1;
            kitchenManager.assignRecipe(player, rankNum);
        }
    }

    /** Cancels a player's active commission without reward. */
    public void cancelCommission(UUID uuid) {
        // If the cancelled commission is MAIL_SORTING, clear any mail items
        if (mailRoomManager != null) {
            PlayerCommissionState cs = stateStore.getState(uuid);
            if (cs != null && cs.getActiveCommissionId() != null) {
                CommissionDefinition def = registry.getById(cs.getActiveCommissionId());
                if (def != null && def.getType().equalsIgnoreCase("MAIL_SORTING")) {
                    org.bukkit.entity.Player player = org.bukkit.Bukkit.getPlayer(uuid);
                    if (player != null) mailRoomManager.clearMail(player);
                }
            }
        }
        if (kitchenManager != null) {
            PlayerCommissionState cs = stateStore.getState(uuid);
            if (cs != null && cs.getActiveCommissionId() != null) {
                CommissionDefinition def = registry.getById(cs.getActiveCommissionId());
                if (def != null && def.getType().equalsIgnoreCase("COOKING")) {
                    org.bukkit.entity.Player player = org.bukkit.Bukkit.getPlayer(uuid);
                    if (player != null) kitchenManager.clearCooking(player);
                }
            }
        }
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
        // Apply skill-based objective reduction
        if (skillManager != null) {
            int reduction = skillManager.getObjectiveReduction(uuid, def.getType());
            if (reduction > 0) {
                int reduced = Math.max(1, def.getObjectiveQuantity() - reduction);
                state.setOverrideQuantity(reduced);
            }
        }
        stateStore.setState(uuid, state);

        player.sendMessage(PREFIX + ChatColor.GREEN + "New commission assigned!");
        player.sendMessage(ChatColor.GOLD + "  Name:     " + ChatColor.YELLOW + def.getDisplayName());
        player.sendMessage(ChatColor.GOLD + "  Task:     " + ChatColor.YELLOW + def.getDescription());
        player.sendMessage(ChatColor.GOLD + "  Goal:     " + ChatColor.YELLOW
                + def.getObjectiveQuantity() + "x " + formatItem(def.getObjectiveItem()));
        player.sendMessage(ChatColor.GOLD + "  Reward:   " + ChatColor.YELLOW + formatCoins(def.getRewardAmount()));
        if (def.getType().equalsIgnoreCase("MAIL_SORTING") && mailRoomManager != null) {
            int rankNum = rankManager != null ? rankManager.getPlayerRank(uuid) : 1;
            mailRoomManager.assignMail(player, rankNum);
        }
        if (def.getType().equalsIgnoreCase("COOKING") && kitchenManager != null) {
            int rankNum = rankManager != null ? rankManager.getPlayerRank(uuid) : 1;
            kitchenManager.assignRecipe(player, rankNum);
        }
    }

    public void incrementProgress(UUID uuid, int amount) {
        PlayerCommissionState state = stateStore.getState(uuid);
        if (state == null || state.getActiveCommissionId() == null) return;
        CommissionDefinition def = registry.getById(state.getActiveCommissionId());
        if (def == null) return;

        int newProgress = state.getProgress() + amount;
        state.setProgress(newProgress);
        stateStore.setState(uuid, state);

        int effectiveQty = state.getEffectiveQuantity(def.getObjectiveQuantity());
        if (newProgress >= effectiveQty
                && !def.getType().equalsIgnoreCase("MAIL_SORTING")
                && !def.getType().equalsIgnoreCase("COOKING")) {
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

        int effectiveQty = state.getEffectiveQuantity(def.getObjectiveQuantity());
        if (newProgress >= effectiveQty
                && !def.getType().equalsIgnoreCase("MAIL_SORTING")
                && !def.getType().equalsIgnoreCase("COOKING")) {
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
        // MAIL_SORTING and COOKING have their own completion logic — skip the standard progress check
        if (def.getType().equalsIgnoreCase("MAIL_SORTING")) {
            completeMailSorting(player, uuid, def, state);
            return;
        }
        if (def.getType().equalsIgnoreCase("COOKING")) {
            completeCooking(player, uuid, def, state);
            return;
        }

        int effectiveQty = state.getEffectiveQuantity(def.getObjectiveQuantity());
        if (state.getProgress() < effectiveQty) {
            int remaining = effectiveQty - state.getProgress();
            player.sendMessage(PREFIX + ChatColor.RED + "Commission not yet complete. Progress: "
                    + ChatColor.YELLOW + state.getProgress() + "/" + effectiveQty
                    + ChatColor.RED + " (" + remaining + " remaining).");
            return;
        }

        // Use turn-in item for inventory check (may differ from objective block, e.g. COAL_ORE -> COAL)
        Material material = Material.matchMaterial(def.getTurnInItem());
        if (material == null) {
            player.sendMessage(PREFIX + ChatColor.RED + "Commission item is misconfigured. Contact an admin.");
            return;
        }
        int needed = effectiveQty;
        int inInventory = countItem(player, material);
        if (inInventory < needed) {
            player.sendMessage(PREFIX + ChatColor.RED + "You don't have the required items. Need "
                    + ChatColor.YELLOW + needed + "x " + formatItem(def.getTurnInItem())
                    + ChatColor.RED + " but only have " + inInventory + ".");
            return;
        }
        player.getInventory().removeItem(new ItemStack(material, needed));

        // Apply escape bonus to Gold Coins only (Shards are unaffected)
        double baseGold = def.getRewardAmount();
        if (escapeManager != null) {
            baseGold = escapeManager.applyBonus(uuid, baseGold);
        }

        // Apply skill bonuses
        double skillGoldBonus = 0;
        double skillShardsBonus = 0;
        if (skillManager != null) {
            skillGoldBonus = baseGold * skillManager.getGoldBonus(uuid, def.getType());
            skillShardsBonus = def.getShardsReward() * skillManager.getShardsBonus(uuid, def.getType());
        }

        double goldReward = baseGold + skillGoldBonus;
        double shardsReward = def.getShardsReward() + skillShardsBonus;

        currencyManager.addBalance(uuid, goldReward);
        if (shardsReward > 0) {
            currencyManager.addShards(uuid, shardsReward);
        }

        // Grant skill XP
        if (skillManager != null) {
            double xpAmount = skillManager.getXpForType(def.getType());
            if (xpAmount > 0) skillManager.grantXp(uuid, def.getType(), xpAmount);
        }

        stateStore.clearState(uuid);

        // Build reward message
        String rewardMsg = ChatColor.YELLOW + formatCoins(goldReward) + " Gold Coins";
        if (shardsReward > 0) {
            rewardMsg += ChatColor.GREEN + " and " + ChatColor.AQUA + formatCoins(shardsReward) + " Shards";
        }
        // Mention escape bonus if applicable
        if (escapeManager != null && escapeManager.getEscapeLevel(uuid) > 0) {
            int bonusPct = (int) ((escapeManager.getGoldCoinMultiplier(uuid) - 1.0) * 100);
            rewardMsg += ChatColor.GRAY + " (+" + bonusPct + "% escape bonus)";
        }
        player.sendMessage(PREFIX + ChatColor.GREEN + "Commission complete! You earned " + rewardMsg + ChatColor.GREEN + ".");
        player.sendMessage(ChatColor.GOLD + "  Gold Coins: " + ChatColor.YELLOW + formatCoins(currencyManager.getBalance(uuid)));
        if (shardsReward > 0) {
            player.sendMessage(ChatColor.AQUA + "  Shards:     " + ChatColor.WHITE + formatCoins(currencyManager.getShards(uuid)));
        }
    }

    private void completeMailSorting(Player player, UUID uuid, CommissionDefinition def,
                                     PlayerCommissionState state) {
        MailSortingState mailState = mailRoomManager != null ? mailRoomManager.getState(uuid) : null;
        if (mailState == null || !mailState.isComplete()) {
            player.sendMessage(PREFIX + ChatColor.RED + "Deliver all mail to the barrels first!");
            return;
        }

        int total = mailState.getTotalMail();
        int correct = mailState.getCorrect();
        double accuracy = total > 0 ? (correct * 100.0 / total) : 0.0;

        double maxGoldBonus = mailRoomManager.getMaxGoldBonus();
        double maxShardsBonus = mailRoomManager.getMaxShardsBonus();
        double goldBonus = maxGoldBonus * (accuracy / 100.0);
        double shardsBonus = maxShardsBonus * (accuracy / 100.0);

        double baseGold = def.getRewardAmount();
        if (escapeManager != null) {
            baseGold = escapeManager.applyBonus(uuid, baseGold);
        }

        // Apply skill bonuses
        double skillGoldBonus = 0;
        double skillShardsBonus = 0;
        if (skillManager != null) {
            skillGoldBonus = baseGold * skillManager.getGoldBonus(uuid, def.getType());
            skillShardsBonus = def.getShardsReward() * skillManager.getShardsBonus(uuid, def.getType());
        }

        double totalGold = baseGold + goldBonus + skillGoldBonus;
        double totalShards = def.getShardsReward() + shardsBonus + skillShardsBonus;

        currencyManager.addBalance(uuid, totalGold);
        if (totalShards > 0) {
            currencyManager.addShards(uuid, totalShards);
        }

        // Grant skill XP
        if (skillManager != null) {
            double xpAmount = skillManager.getXpForType(def.getType());
            if (xpAmount > 0) skillManager.grantXp(uuid, def.getType(), xpAmount);
        }

        if (mailRoomManager != null) mailRoomManager.clearMail(player);
        stateStore.clearState(uuid);

        String escapePart = "";
        if (escapeManager != null && escapeManager.getEscapeLevel(uuid) > 0) {
            int bonusPct = (int) ((escapeManager.getGoldCoinMultiplier(uuid) - 1.0) * 100);
            escapePart = ChatColor.GRAY + " (+" + bonusPct + "% escape bonus)";
        }

        player.sendMessage(PREFIX + ChatColor.GREEN + "Commission complete!");
        player.sendMessage(ChatColor.GOLD + "  Accuracy: " + ChatColor.YELLOW
                + String.format("%.0f%%", accuracy)
                + ChatColor.GRAY + " (" + correct + "/" + total + " correct)");
        player.sendMessage(ChatColor.GOLD + "  Base reward:  " + ChatColor.YELLOW
                + formatNumber(baseGold) + " Gold Coins"
                + (def.getShardsReward() > 0 ? ChatColor.GOLD + " + " + ChatColor.AQUA + formatNumber(def.getShardsReward()) + " Shards" : "")
                + escapePart);
        player.sendMessage(ChatColor.GOLD + "  Accuracy bonus: " + ChatColor.YELLOW
                + formatNumber(goldBonus) + " Gold Coins"
                + (shardsBonus > 0 ? ChatColor.GOLD + " + " + ChatColor.AQUA + formatNumber(shardsBonus) + " Shards" : ""));
        player.sendMessage(ChatColor.GOLD + "  Total earned: " + ChatColor.YELLOW
                + formatNumber(totalGold) + " Gold Coins"
                + (totalShards > 0 ? ChatColor.GOLD + " + " + ChatColor.AQUA + formatNumber(totalShards) + " Shards" : ""));
        player.sendMessage(ChatColor.GOLD + "  Gold Coins: " + ChatColor.YELLOW
                + formatNumber(currencyManager.getBalance(uuid)));
        if (totalShards > 0) {
            player.sendMessage(ChatColor.AQUA + "  Shards:     " + ChatColor.WHITE
                    + formatNumber(currencyManager.getShards(uuid)));
        }
    }

    private void completeCooking(Player player, UUID uuid, CommissionDefinition def,
                                 PlayerCommissionState state) {
        CookingState cookingState = kitchenManager != null ? kitchenManager.getState(uuid) : null;
        if (cookingState == null || !cookingState.isReadyToTurnIn()) {
            player.sendMessage(PREFIX + ChatColor.RED
                    + "Complete your dish in the Kitchen first! Use the cauldron to confirm your ingredients.");
            return;
        }

        CookingRecipe recipe = kitchenManager.getRecipe(cookingState.getRecipeId());
        if (recipe == null) {
            player.sendMessage(PREFIX + ChatColor.RED
                    + "Recipe not found. Commission cancelled.");
            if (kitchenManager != null) kitchenManager.clearCooking(player);
            stateStore.clearState(uuid);
            return;
        }

        // Calculate accuracy: positions that match the expected order
        java.util.List<Material> confirmed = cookingState.getConfirmedIngredients();
        java.util.List<Material> expected = recipe.getIngredients();
        int n = expected.size();
        int correct = 0;
        if (confirmed != null) {
            for (int i = 0; i < Math.min(confirmed.size(), n); i++) {
                if (confirmed.get(i) == expected.get(i)) correct++;
            }
        }
        double accuracy = n > 0 ? (correct * 100.0 / n) : 0.0;

        double maxGoldBonus = kitchenManager.getMaxGoldBonus();
        double maxShardsBonus = kitchenManager.getMaxShardsBonus();
        double goldBonus = maxGoldBonus * (accuracy / 100.0);
        double shardsBonus = maxShardsBonus * (accuracy / 100.0);

        double baseGold = def.getRewardAmount();
        if (escapeManager != null) {
            baseGold = escapeManager.applyBonus(uuid, baseGold);
        }

        // Apply skill bonuses
        double skillGoldBonus = 0;
        double skillShardsBonus = 0;
        if (skillManager != null) {
            skillGoldBonus = baseGold * skillManager.getGoldBonus(uuid, def.getType());
            skillShardsBonus = def.getShardsReward() * skillManager.getShardsBonus(uuid, def.getType());
        }

        double totalGold = baseGold + goldBonus + skillGoldBonus;
        double totalShards = def.getShardsReward() + shardsBonus + skillShardsBonus;

        currencyManager.addBalance(uuid, totalGold);
        if (totalShards > 0) {
            currencyManager.addShards(uuid, totalShards);
        }

        // Grant skill XP
        if (skillManager != null) {
            double xpAmount = skillManager.getXpForType(def.getType());
            if (xpAmount > 0) skillManager.grantXp(uuid, def.getType(), xpAmount);
        }

        kitchenManager.clearCooking(player);
        stateStore.clearState(uuid);

        String escapePart = "";
        if (escapeManager != null && escapeManager.getEscapeLevel(uuid) > 0) {
            int bonusPct = (int) ((escapeManager.getGoldCoinMultiplier(uuid) - 1.0) * 100);
            escapePart = ChatColor.GRAY + " (+" + bonusPct + "% escape bonus)";
        }

        player.sendMessage(PREFIX + ChatColor.GREEN + "Commission complete!");
        player.sendMessage(ChatColor.GOLD + "  Accuracy: " + ChatColor.YELLOW
                + String.format("%.0f%%", accuracy)
                + ChatColor.GRAY + " (" + correct + "/" + n + " correct)");
        player.sendMessage(ChatColor.GOLD + "  Base reward:  " + ChatColor.YELLOW
                + formatNumber(baseGold) + " Gold Coins"
                + (def.getShardsReward() > 0
                        ? ChatColor.GOLD + " + " + ChatColor.AQUA + formatNumber(def.getShardsReward()) + " Shards"
                        : "")
                + escapePart);
        player.sendMessage(ChatColor.GOLD + "  Accuracy bonus: " + ChatColor.YELLOW
                + formatNumber(goldBonus) + " Gold Coins"
                + (shardsBonus > 0
                        ? ChatColor.GOLD + " + " + ChatColor.AQUA + formatNumber(shardsBonus) + " Shards"
                        : ""));
        player.sendMessage(ChatColor.GOLD + "  Total earned: " + ChatColor.YELLOW
                + formatNumber(totalGold) + " Gold Coins"
                + (totalShards > 0
                        ? ChatColor.GOLD + " + " + ChatColor.AQUA + formatNumber(totalShards) + " Shards"
                        : ""));
        player.sendMessage(ChatColor.GOLD + "  Gold Coins: " + ChatColor.YELLOW
                + formatNumber(currencyManager.getBalance(uuid)));
        if (totalShards > 0) {
            player.sendMessage(ChatColor.AQUA + "  Shards:     " + ChatColor.WHITE
                    + formatNumber(currencyManager.getShards(uuid)));
        }
    }

    private String formatNumber(double amount) {
        if (amount == Math.floor(amount)) return String.valueOf((long) amount);
        return String.format("%.2f", amount);
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
