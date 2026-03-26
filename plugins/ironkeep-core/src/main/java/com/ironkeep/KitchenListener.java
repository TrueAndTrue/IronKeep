package com.ironkeep;

import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class KitchenListener implements Listener {

    private final IronKeepPlugin plugin;

    public KitchenListener(IronKeepPlugin plugin) {
        this.plugin = plugin;
    }

    /** Right-clicking the configured cauldron opens the cooking GUI. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onCauldronInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;

        KitchenManager kitchenManager = plugin.getKitchenManager();
        if (!kitchenManager.isCauldron(block)) return;

        Player player = event.getPlayer();
        CommissionDefinition activeDef = plugin.getCommissionManager().getActiveCommission(player);
        if (activeDef == null || !activeDef.getType().equalsIgnoreCase("COOKING")) return;

        // Must be in kitchen zone
        ZoneManager zoneManager = plugin.getZoneManager();
        if (!zoneManager.isInValidZone(player.getLocation(), "COOKING")) return;

        event.setCancelled(true);
        kitchenManager.openCauldronGui(player);
    }

    /** Right-clicking an item frame in the kitchen gives the player that ingredient. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onItemFrameInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof ItemFrame frame)) return;

        Player player = event.getPlayer();
        CommissionDefinition activeDef = plugin.getCommissionManager().getActiveCommission(player);
        if (activeDef == null || !activeDef.getType().equalsIgnoreCase("COOKING")) return;

        KitchenManager kitchenManager = plugin.getKitchenManager();
        if (!kitchenManager.isIngredientFrame(frame)) return;

        event.setCancelled(true);
        kitchenManager.handleItemFrameClick(player, frame);
    }

    /** Handle clicks inside the Kitchen Cauldron GUI. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().contains("Kitchen")) return;

        plugin.getKitchenManager().handleCauldronClick(player, event);
    }

    /** Prevent dragging items in the cooking GUI. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (!event.getView().getTitle().contains("Kitchen")) return;
        boolean affectsGui = event.getRawSlots().stream().anyMatch(s -> s < 27);
        if (affectsGui) event.setCancelled(true);
    }

    /**
     * When the cooking GUI closes, return any cooking ingredients that were placed in slots
     * back to the player's inventory (so they remain available for clearCooking on completion).
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!event.getView().getTitle().contains("Kitchen")) return;

        KitchenManager kitchenManager = plugin.getKitchenManager();
        for (ItemStack item : event.getInventory().getContents()) {
            if (kitchenManager.isCookingIngredient(item)) {
                java.util.Map<Integer, ItemStack> leftover = player.getInventory().addItem(item.clone());
                for (ItemStack overflow : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), overflow);
                }
            }
        }
    }

    /** Prevent players from dropping cooking ingredients. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        ItemStack item = event.getItemDrop().getItemStack();
        if (plugin.getKitchenManager().isCookingIngredient(item)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "You cannot drop cooking ingredients.");
        }
    }

    /** Clear cooking state when player quits while on a COOKING commission. */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        CommissionDefinition activeDef = plugin.getCommissionManager().getActiveCommission(player);
        if (activeDef != null && activeDef.getType().equalsIgnoreCase("COOKING")) {
            plugin.getKitchenManager().clearCooking(player);
        }
    }

    /** Cancel commission and clear cooking state when player leaves the kitchen zone. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();
        CommissionManager commissionManager = plugin.getCommissionManager();
        CommissionDefinition activeDef = commissionManager.getActiveCommission(player);
        if (activeDef == null || !activeDef.getType().equalsIgnoreCase("COOKING")) return;

        ZoneManager zoneManager = plugin.getZoneManager();
        if (!zoneManager.isInValidZone(event.getTo(), "COOKING")) {
            UUID uuid = player.getUniqueId();
            plugin.getKitchenManager().clearCooking(player);
            commissionManager.cancelCommission(uuid);
            player.sendMessage(ChatColor.RED + "You left the Kitchen — your commission has been cancelled.");
        }
    }
}
