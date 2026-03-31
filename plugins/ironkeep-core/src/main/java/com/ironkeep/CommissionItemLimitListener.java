package com.ironkeep;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Prevents players from picking up commission turn-in items once they already
 * have as many as their current commission requires.
 *
 * MAIL_SORTING and COOKING are excluded — they manage their own item inventories.
 * FARMING items are given directly in FarmingListener and capped there.
 */
public class CommissionItemLimitListener implements Listener {

    private final IronKeepPlugin plugin;

    public CommissionItemLimitListener(IronKeepPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        CommissionManager cm = plugin.getCommissionManager();
        if (!cm.hasActiveCommission(player)) return;

        CommissionDefinition def = cm.getActiveCommission(player);
        if (def == null) return;

        // MAIL_SORTING and COOKING have their own item management
        String type = def.getType().toUpperCase();
        if (type.equals("MAIL_SORTING") || type.equals("COOKING") || type.equals("FARMING")) return;

        Material turnIn = Material.matchMaterial(def.getTurnInItem());
        if (turnIn == null || event.getItem().getItemStack().getType() != turnIn) return;

        PlayerCommissionState state = cm.getPlayerState(player);
        if (state == null) return;

        int effectiveQty = state.getEffectiveQuantity(def.getObjectiveQuantity());
        int currentCount = countItem(player, turnIn);

        if (currentCount >= effectiveQty) {
            event.setCancelled(true);
            return;
        }

        // Partial cap: if the stack would push them over the limit, trim it
        int pickupAmount = event.getItem().getItemStack().getAmount();
        int allowed = effectiveQty - currentCount;
        if (pickupAmount > allowed) {
            event.setCancelled(true);
            player.getInventory().addItem(new ItemStack(turnIn, allowed));
            event.getItem().setItemStack(new ItemStack(turnIn, pickupAmount - allowed));
        }
    }

    private int countItem(Player player, Material material) {
        int count = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getType() == material) count += stack.getAmount();
        }
        return count;
    }
}
