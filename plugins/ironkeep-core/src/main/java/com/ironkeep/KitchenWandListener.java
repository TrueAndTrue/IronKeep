package com.ironkeep;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class KitchenWandListener implements Listener {

    private final IronKeepPlugin plugin;

    public KitchenWandListener(IronKeepPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onItemFrameInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof ItemFrame frame)) return;
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!plugin.getKitchenWandManager().isWand(held)) return;
        if (!player.isOp()) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use the Kitchen Wand.");
            return;
        }
        event.setCancelled(true);
        new KitchenWandGUI(plugin).open(player, frame);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory inv = event.getInventory();
        if (!KitchenWandGUI.isKitchenWandGUI(inv)) return;
        event.setCancelled(true);
        if (event.getRawSlot() < 0 || event.getRawSlot() >= 27) return;

        ItemFrame frame = KitchenWandGUI.getFrame(inv);
        if (frame == null) return;

        int slot = event.getRawSlot();
        if (slot == 26) {
            // Remove binding
            plugin.getKitchenManager().unbindIngredientFrame(frame.getLocation());
            player.closeInventory();
            player.sendMessage(ChatColor.YELLOW + "Ingredient frame binding removed.");
            return;
        }

        ItemStack clicked = inv.getItem(slot);
        if (clicked == null || clicked.getType() == Material.AIR) return;

        Material selected = clicked.getType();
        KitchenManager km = plugin.getKitchenManager();

        // Register the binding
        km.bindIngredientFrame(frame.getLocation(), selected);

        // Update the frame entity visuals
        ItemStack frameItem = new ItemStack(selected);
        ItemMeta frameMeta = frameItem.getItemMeta();
        if (frameMeta != null) {
            frameMeta.setDisplayName(ChatColor.YELLOW + km.formatMaterial(selected));
            frameItem.setItemMeta(frameMeta);
        }
        frame.setItem(frameItem);
        frame.setFixed(true);
        frame.setVisible(false);

        player.closeInventory();
        player.sendMessage(ChatColor.GREEN + "Frame bound to: " + ChatColor.WHITE + km.formatMaterial(selected));
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        KitchenWandGUI.closeGUI(event.getInventory());
    }
}
