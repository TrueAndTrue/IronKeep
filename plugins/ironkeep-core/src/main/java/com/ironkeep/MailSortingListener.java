package com.ironkeep;

import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class MailSortingListener implements Listener {

    private final IronKeepPlugin plugin;

    public MailSortingListener(IronKeepPlugin plugin) {
        this.plugin = plugin;
    }

    /** Handle right-clicking a barrel while holding a mail item. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;

        Player player = event.getPlayer();
        MailRoomManager mailManager = plugin.getMailRoomManager();

        if (!mailManager.isBarrel(block)) return;

        CommissionManager commissionManager = plugin.getCommissionManager();
        CommissionDefinition activeDef = commissionManager.getActiveCommission(player);
        if (activeDef == null || !activeDef.getType().equalsIgnoreCase("MAIL_SORTING")) return;

        ItemStack heldItem = player.getInventory().getItemInMainHand();
        if (!mailManager.isMailItem(heldItem)) {
            player.sendMessage(ChatColor.GRAY + "Hold a mail item to deliver it here.");
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);
        mailManager.handleDelivery(player, block, heldItem);
    }

    /** Prevent players from dropping mail items. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        ItemStack item = event.getItemDrop().getItemStack();
        if (plugin.getMailRoomManager().isMailItem(item)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "You cannot drop mail items.");
        }
    }

    /** Prevent players from moving mail items into external inventories. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Check both the cursor item and the clicked item for mail
        ItemStack cursor = event.getCursor();
        ItemStack clicked = event.getCurrentItem();
        MailRoomManager mailManager = plugin.getMailRoomManager();

        boolean cursorIsMail = mailManager.isMailItem(cursor);
        boolean clickedIsMail = mailManager.isMailItem(clicked);

        if (!cursorIsMail && !clickedIsMail) return;

        // Allow if no external inventory is open (just player's own inventory)
        if (event.getView().getTopInventory().getType() == org.bukkit.event.inventory.InventoryType.CRAFTING
                || event.getView().getTopInventory() == player.getInventory()) {
            return;
        }

        // Block moving mail items to or from external inventories
        event.setCancelled(true);
        player.sendMessage(ChatColor.RED + "You cannot move mail items into other inventories.");
    }

    /** Clear mail items when a player quits while on a MAIL_SORTING commission. */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        CommissionDefinition activeDef = plugin.getCommissionManager().getActiveCommission(player);
        if (activeDef != null && activeDef.getType().equalsIgnoreCase("MAIL_SORTING")) {
            plugin.getMailRoomManager().clearMail(player);
        }
    }
}
