package com.ironkeep;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class WardenListener implements Listener {

    private static final String GUI_TITLE = ChatColor.DARK_GRAY + "The Warden";
    private static final int GUI_SIZE = 27;
    private static final int SLOT_RANKUP  = 11;
    private static final int SLOT_ESCAPE  = 15;
    private static final int SLOT_BALANCE = 22;

    private static class WardenHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    private final IronKeepPlugin plugin;
    private final WardenManager wardenManager;

    public WardenListener(IronKeepPlugin plugin, WardenManager wardenManager) {
        this.plugin = plugin;
        this.wardenManager = wardenManager;
    }

    // -------------------------------------------------------------------------
    // NPC protection + GUI open
    // -------------------------------------------------------------------------

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (wardenManager.isWarden(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityTarget(EntityTargetEvent event) {
        if (event.getTarget() != null && wardenManager.isWarden(event.getTarget())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!wardenManager.isWarden(event.getRightClicked())) return;
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player p = event.getPlayer();
        TutorialManager tm = plugin.getTutorialManager();
        if (tm != null) {
            tm.onTrigger(p, TutorialStep.TriggerType.INTERACT_WARDEN);
            // Only block the GUI while the player is on a step that expects
            // INTERACT_WARDEN (i.e. the intro dialogue). All other tutorial
            // steps (including free_commissions/rank-up) should open the GUI.
            if (tm.isWaitingForWardenInteract(p.getUniqueId())) return;
        }
        openGui(p);
    }

    // -------------------------------------------------------------------------
    // GUI
    // -------------------------------------------------------------------------

    private void openGui(Player player) {
        Inventory inv = Bukkit.createInventory(new WardenHolder(), GUI_SIZE, GUI_TITLE);

        UUID uuid = player.getUniqueId();
        RankManager rm = plugin.getRankManager();
        EscapeManager em = plugin.getEscapeManager();
        CurrencyManager cm = plugin.getCurrencyManager();

        int currentRank = rm.getPlayerRank(uuid);
        int maxRank = rm.getMaxRank();
        int currentEscape = em.getEscapeLevel(uuid);
        int maxEscape = em.getMaxEscapeLevel();
        double balance = cm.getBalance(uuid);

        inv.setItem(SLOT_RANKUP, buildRankUpItem(currentRank, maxRank, rm, balance));
        inv.setItem(SLOT_ESCAPE, buildEscapeItem(currentRank, maxRank, currentEscape, maxEscape, em, balance));
        inv.setItem(SLOT_BALANCE, buildBalanceItem(balance));

        player.openInventory(inv);
    }

    @SuppressWarnings("deprecation")
    private ItemStack buildRankUpItem(int currentRank, int maxRank, RankManager rm, double balance) {
        if (currentRank >= maxRank) {
            ItemStack item = new ItemStack(Material.GOLD_BLOCK);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(ChatColor.GOLD + "Max Rank Reached");
            meta.setLore(List.of(
                ChatColor.GRAY + "You have reached the highest rank:",
                ChatColor.YELLOW + rm.getDefinition(maxRank).getDisplayName()
            ));
            item.setItemMeta(meta);
            return item;
        }

        int nextRank = currentRank + 1;
        RankDefinition nextDef = rm.getDefinition(nextRank);
        double cost = nextDef.getCost();
        boolean canAfford = balance >= cost;

        ItemStack item = new ItemStack(canAfford ? Material.DIAMOND : Material.COAL);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "Rank Up");

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Current rank: " + ChatColor.YELLOW + rm.getDefinition(currentRank).getDisplayName());
        lore.add(ChatColor.GRAY + "Next rank:    " + ChatColor.YELLOW + nextDef.getDisplayName());
        lore.add("");
        lore.add(ChatColor.GRAY + "Cost: " + (canAfford ? ChatColor.GREEN : ChatColor.RED) + format(cost) + " Gold Coins");
        lore.add("");
        lore.add(ChatColor.GRAY + "Unlocks: " + ChatColor.AQUA + String.join(", ", nextDef.getUnlockedTypes()));
        if (!canAfford) {
            lore.add("");
            lore.add(ChatColor.RED + "You cannot afford this.");
        } else {
            lore.add("");
            lore.add(ChatColor.GREEN + "Click to rank up!");
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    @SuppressWarnings("deprecation")
    private ItemStack buildEscapeItem(int currentRank, int maxRank, int currentEscape, int maxEscape,
                                      EscapeManager em, double balance) {
        if (currentEscape >= maxEscape) {
            ItemStack item = new ItemStack(Material.NETHER_STAR);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(ChatColor.LIGHT_PURPLE + "Max Escape Reached");
            meta.setLore(List.of(ChatColor.GRAY + "You have fully escaped the prison."));
            item.setItemMeta(meta);
            return item;
        }

        boolean atMaxRank = currentRank >= maxRank;
        int nextEscape = currentEscape + 1;
        double cost = em.getEscapeCost(nextEscape);
        boolean canAfford = balance >= cost;
        boolean eligible = atMaxRank && canAfford;

        double bonusDisplay = nextEscape * 10.0;

        ItemStack item = new ItemStack(eligible ? Material.ENDER_PEARL : Material.ENDER_EYE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.LIGHT_PURPLE + "Escape");

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Current escape level: " + ChatColor.YELLOW + currentEscape);
        lore.add(ChatColor.GRAY + "Next escape level:    " + ChatColor.YELLOW + nextEscape);
        lore.add("");
        lore.add(ChatColor.GRAY + "Reward: +" + ChatColor.GREEN + (int) bonusDisplay + "% Gold Coin income");
        lore.add(ChatColor.GRAY + "Rank resets to 1. Shards kept.");
        lore.add("");
        lore.add(ChatColor.GRAY + "Requires: " + (atMaxRank ? ChatColor.GREEN + "Rank 4 ✔" : ChatColor.RED + "Rank 4 ✘"));
        lore.add(ChatColor.GRAY + "Cost: " + (canAfford ? ChatColor.GREEN : ChatColor.RED) + format(cost) + " Gold Coins");
        if (!eligible) {
            lore.add("");
            if (!atMaxRank) lore.add(ChatColor.RED + "Reach Rank 4 first.");
            if (!canAfford) lore.add(ChatColor.RED + "You cannot afford this.");
        } else {
            lore.add("");
            lore.add(ChatColor.GREEN + "Click to escape!");
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    @SuppressWarnings("deprecation")
    private ItemStack buildBalanceItem(double balance) {
        ItemStack item = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "Your Balance");
        meta.setLore(List.of(ChatColor.YELLOW + format(balance) + " Gold Coins"));
        item.setItemMeta(meta);
        return item;
    }

    // -------------------------------------------------------------------------
    // GUI clicks
    // -------------------------------------------------------------------------

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof WardenHolder)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();

        if (slot == SLOT_RANKUP) {
            handleRankUp(player);
        } else if (slot == SLOT_ESCAPE) {
            handleEscape(player);
        }
    }

    @SuppressWarnings("deprecation")
    private void handleRankUp(Player player) {
        UUID uuid = player.getUniqueId();
        RankManager rm = plugin.getRankManager();
        CurrencyManager cm = plugin.getCurrencyManager();

        int currentRank = rm.getPlayerRank(uuid);
        int maxRank = rm.getMaxRank();

        if (currentRank >= maxRank) {
            player.sendMessage(ChatColor.RED + "You are already at the highest rank.");
            return;
        }

        int nextRank = currentRank + 1;
        RankDefinition nextDef = rm.getDefinition(nextRank);
        if (nextDef == null) return;

        double cost = nextDef.getCost();
        if (cm.getBalance(uuid) < cost) {
            player.sendMessage(ChatColor.RED + "Insufficient Gold Coins. You need "
                    + ChatColor.YELLOW + format(cost) + ChatColor.RED + " Gold Coins.");
            openGui(player);
            return;
        }

        cm.addBalance(uuid, -cost);
        rm.setPlayerRank(uuid, nextRank);

        CommissionManager commManager = plugin.getCommissionManager();
        if (commManager.hasActiveCommission(player)) {
            CommissionDefinition active = commManager.getActiveCommission(player);
            if (active != null && !rm.canAccept(uuid, active.getType())) {
                commManager.cancelCommission(uuid);
                player.sendMessage(ChatColor.YELLOW + "Your active commission was cancelled due to ranking up.");
            }
        }

        player.sendMessage(ChatColor.GREEN + "You ranked up to " + ChatColor.GOLD + nextDef.getDisplayName() + ChatColor.GREEN + "!");
        player.sendMessage(ChatColor.YELLOW + "Unlocked: " + String.join(", ", nextDef.getUnlockedTypes()));
        if (plugin.getTutorialManager() != null) {
            plugin.getTutorialManager().onTrigger(player, TutorialStep.TriggerType.RANK_UP);
        }
        openGui(player);
    }

    @SuppressWarnings("deprecation")
    private void handleEscape(Player player) {
        UUID uuid = player.getUniqueId();
        RankManager rm = plugin.getRankManager();
        EscapeManager em = plugin.getEscapeManager();
        CurrencyManager cm = plugin.getCurrencyManager();

        int currentRank = rm.getPlayerRank(uuid);
        if (currentRank < rm.getMaxRank()) {
            player.sendMessage(ChatColor.RED + "You must reach Rank 4 before you can escape.");
            return;
        }

        int currentEscape = em.getEscapeLevel(uuid);
        int maxEscape = em.getMaxEscapeLevel();
        if (currentEscape >= maxEscape) {
            player.sendMessage(ChatColor.RED + "You have already reached the maximum escape level.");
            return;
        }

        int nextEscape = currentEscape + 1;
        double cost = em.getEscapeCost(nextEscape);
        if (cm.getBalance(uuid) < cost) {
            player.sendMessage(ChatColor.RED + "Insufficient Gold Coins. You need "
                    + ChatColor.YELLOW + format(cost) + ChatColor.RED + " Gold Coins.");
            openGui(player);
            return;
        }

        cm.addBalance(uuid, -cost);
        cm.setBalance(uuid, 0);
        rm.setPlayerRank(uuid, 1);
        plugin.getCommissionManager().cancelCommission(uuid);
        em.setEscapeLevel(uuid, nextEscape);

        double bonusPct = (em.getGoldCoinMultiplier(uuid) - 1.0) * 100;

        player.sendMessage(ChatColor.LIGHT_PURPLE + "You have escaped the prison!");
        player.sendMessage(ChatColor.GOLD + "Escape Level: " + ChatColor.YELLOW + nextEscape);
        player.sendMessage(ChatColor.GOLD + "Bonus: " + ChatColor.YELLOW + (int) bonusPct + "% Gold Coin income");
        player.sendMessage(ChatColor.GOLD + "Rank reset to: " + ChatColor.YELLOW + rm.getDefinition(1).getDisplayName());
        player.sendMessage(ChatColor.GRAY + "(Shards balance untouched)");
        openGui(player);
    }

    private String format(double amount) {
        return String.format("%,d", Math.round(amount));
    }
}
