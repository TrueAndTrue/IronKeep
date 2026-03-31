package com.ironkeep;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class CommissionBoardListener implements Listener {

    private static final String GUI_TITLE = ChatColor.DARK_RED + "Commission Board";
    private static final int GUI_SIZE = 27;

    private static class BoardHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    private final IronKeepPlugin plugin;
    private final CommissionBoardManager boardManager;
    private final NamespacedKey commissionIdKey;

    public CommissionBoardListener(IronKeepPlugin plugin, CommissionBoardManager boardManager) {
        this.plugin = plugin;
        this.boardManager = boardManager;
        this.commissionIdKey = new NamespacedKey(plugin, "commission_id");
    }

    // -------------------------------------------------------------------------
    // Block-type board interaction (existing)
    // -------------------------------------------------------------------------

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null || !boardManager.isBoard(block)) return;
        event.setCancelled(true);
        openCommissionGui(event.getPlayer());
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!boardManager.isBoard(event.getBlock())) return;
        if (event.getPlayer().isOp()) return;
        event.setCancelled(true);
    }

    // -------------------------------------------------------------------------
    // Item frame board interaction
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onItemFrameInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!(event.getRightClicked() instanceof ItemFrame frame)) return;

        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();

        // Wand interaction — register/unregister the frame
        if (plugin.getCommissionBoardWandManager().isWand(held)) {
            if (!player.isOp()) {
                player.sendMessage(ChatColor.RED + "You don't have permission to use the Commission Board Wand.");
                return;
            }
            event.setCancelled(true);
            if (boardManager.isFrameBoard(frame)) {
                boardManager.removeFrameBoard(frame);
                player.sendMessage(ChatColor.YELLOW + "Item frame unregistered as commission board.");
            } else {
                boardManager.addFrameBoard(frame);
                player.sendMessage(ChatColor.GREEN + "Item frame registered as commission board.");
            }
            return;
        }

        // Normal right-click — open commission GUI if this frame is a registered board
        if (!boardManager.isFrameBoard(frame)) return;
        event.setCancelled(true);
        openCommissionGui(player);
    }

    /** Prevent non-OP players from breaking registered item frame boards. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        if (!(event.getEntity() instanceof ItemFrame frame)) return;
        if (!boardManager.isFrameBoard(frame)) return;
        if (event.getRemover() instanceof Player player && player.isOp()) return;
        event.setCancelled(true);
    }

    // -------------------------------------------------------------------------
    // GUI click handling (shared by both board types)
    // -------------------------------------------------------------------------

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof BoardHolder)) return;
        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        // Check for commission ID in persistent data — this is a commission selection click
        String commissionId = clicked.getItemMeta().getPersistentDataContainer()
                .get(commissionIdKey, PersistentDataType.STRING);

        if (commissionId != null) {
            player.closeInventory();
            plugin.getCommissionManager().assignCommission(player, commissionId);
            return;
        }

        // Check for action buttons by display name
        String displayName = ChatColor.stripColor(clicked.getItemMeta().hasDisplayName()
                ? clicked.getItemMeta().getDisplayName() : "");
        if (displayName.equals("Complete Commission")) {
            player.closeInventory();
            plugin.getCommissionManager().completeCommission(player);
        }
    }

    // -------------------------------------------------------------------------
    // GUI builder
    // -------------------------------------------------------------------------

    private void openCommissionGui(Player player) {
        if (plugin.getTutorialManager() != null) {
            plugin.getTutorialManager().onTrigger(player, TutorialStep.TriggerType.OPEN_BOARD);
        }
        CommissionManager mgr = plugin.getCommissionManager();
        CurrencyManager currency = plugin.getCurrencyManager();

        Inventory gui = Bukkit.createInventory(new BoardHolder(), GUI_SIZE, GUI_TITLE);

        // Fill with gray glass pane
        ItemStack filler = makeFiller();
        for (int i = 0; i < GUI_SIZE; i++) gui.setItem(i, filler);

        if (mgr.hasActiveCommission(player)) {
            // Show active commission in center
            CommissionDefinition def = mgr.getActiveCommission(player);
            PlayerCommissionState state = mgr.getPlayerState(player);
            int progress = state != null ? state.getProgress() : 0;

            Material mat = iconForType(def.getType());
            ItemStack commItem = new ItemStack(mat);
            ItemMeta commMeta = commItem.getItemMeta();
            commMeta.setDisplayName(ChatColor.GOLD + def.getDisplayName());
            commMeta.setLore(Arrays.asList(
                    ChatColor.GRAY + def.getDescription(),
                    ChatColor.YELLOW + "Progress: " + progress + "/" + def.getObjectiveQuantity(),
                    ChatColor.YELLOW + "Reward: " + Math.round(def.getRewardAmount()) + " Gold Coins",
                    ChatColor.DARK_GRAY + "Use /commission complete to turn in"
            ));
            commItem.setItemMeta(commMeta);
            gui.setItem(11, commItem);

            // Complete button
            ItemStack completeBtn = new ItemStack(Material.CHEST);
            ItemMeta completeMeta = completeBtn.getItemMeta();
            completeMeta.setDisplayName(ChatColor.GREEN + "Complete Commission");
            completeMeta.setLore(List.of(ChatColor.GRAY + "Turn in your completed commission."));
            completeBtn.setItemMeta(completeMeta);
            gui.setItem(15, completeBtn);

        } else {
            // Show commissions filtered by player's rank
            java.util.List<CommissionDefinition> rankFiltered = plugin.getRankManager() != null
                    ? plugin.getRankManager().getAccessibleCommissions(player.getUniqueId())
                    : new java.util.ArrayList<>(plugin.getCommissionRegistry().getAll().values());
            Map<String, CommissionDefinition> all = new java.util.LinkedHashMap<>();
            for (CommissionDefinition c : rankFiltered) all.put(c.getId(), c);
            int slot = 10;
            for (CommissionDefinition def : all.values()) {
                if (slot >= GUI_SIZE - 4) break;
                Material mat = iconForType(def.getType());
                ItemStack item = new ItemStack(mat);
                ItemMeta meta = item.getItemMeta();
                meta.setDisplayName(ChatColor.GOLD + def.getDisplayName());
                String rewardLine = ChatColor.YELLOW + "Reward: " + Math.round(def.getRewardAmount()) + " Gold Coins"
                        + (def.getShardsReward() > 0 ? ChatColor.YELLOW + " + " + ChatColor.AQUA + Math.round(def.getShardsReward()) + " Shards" : "");
                meta.setLore(Arrays.asList(
                        ChatColor.GRAY + def.getDescription(),
                        rewardLine,
                        ChatColor.GREEN + "Click to accept"
                ));
                meta.getPersistentDataContainer().set(commissionIdKey, PersistentDataType.STRING, def.getId());
                item.setItemMeta(meta);
                gui.setItem(slot, item);
                slot++;
                if (slot == 17) slot = 19; // skip border
            }
        }

        // Balance display
        double balance = currency.getBalance(player.getUniqueId());
        ItemStack balItem = new ItemStack(Material.GOLD_INGOT);
        ItemMeta balMeta = balItem.getItemMeta();
        balMeta.setDisplayName(ChatColor.GOLD + "Your Balance");
        balMeta.setLore(List.of(ChatColor.YELLOW + "" + Math.round(balance) + " Gold Coins"));
        balItem.setItemMeta(balMeta);
        gui.setItem(22, balItem);

        player.openInventory(gui);
    }

    private Material iconForType(String type) {
        if (type == null) return Material.PAPER;
        return switch (type.toUpperCase()) {
            case "MINING", "MINING_COAL", "MINING_IRON", "MINING_GOLD", "MINING_DIAMOND" -> Material.IRON_PICKAXE;
            case "WOODCUTTING" -> Material.IRON_AXE;
            case "FARMING" -> Material.WHEAT;
            case "MAIL_SORTING" -> Material.PAPER;
            case "COOKING" -> Material.BOWL;
            default -> Material.PAPER;
        };
    }

    private ItemStack makeFiller() {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        meta.setDisplayName(ChatColor.RESET + "");
        pane.setItemMeta(meta);
        return pane;
    }
}
