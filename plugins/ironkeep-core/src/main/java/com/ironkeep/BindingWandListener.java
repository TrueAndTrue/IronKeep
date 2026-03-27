package com.ironkeep;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class BindingWandListener implements Listener {

    private final IronKeepPlugin plugin;

    public BindingWandListener(IronKeepPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (!plugin.getBindingWandManager().isWand(item)) return;

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.BARREL) return;

        if (!player.hasPermission("ironkeep.admin")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use the Binding Wand.");
            return;
        }

        event.setCancelled(true);
        new BindingWandGUI(plugin).open(player, block);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Inventory inv = event.getInventory();
        if (!BindingWandGUI.isBindingGUI(inv)) return;

        event.setCancelled(true);

        // Only handle clicks in the top GUI (not the player inventory area)
        if (event.getRawSlot() < 0 || event.getRawSlot() >= 27) return;

        Block block = BindingWandGUI.getBlock(inv);
        if (block == null) return;

        int slot = event.getRawSlot();

        if (slot == 26) {
            plugin.getMailRoomManager().unbindBarrel(block);
            player.closeInventory();
            player.sendMessage(ChatColor.YELLOW + "Barrel binding removed.");
            return;
        }

        ItemStack clicked = inv.getItem(slot);
        if (clicked == null || clicked.getType() == Material.AIR) return;

        ItemMeta meta = clicked.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return;

        String dest = ChatColor.stripColor(meta.getDisplayName());
        plugin.getMailRoomManager().bindBarrel(block, dest);
        player.closeInventory();
        player.sendMessage(ChatColor.GREEN + "Barrel bound to: " + ChatColor.WHITE + dest);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        BindingWandGUI.closeGUI(event.getInventory());
    }
}
