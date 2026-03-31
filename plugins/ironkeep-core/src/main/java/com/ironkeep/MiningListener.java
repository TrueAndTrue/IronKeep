package com.ironkeep;

import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;

public class MiningListener implements Listener {

    private final IronKeepPlugin plugin;

    public MiningListener(IronKeepPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        CommissionManager commissionManager = plugin.getCommissionManager();

        // Check player has an active MINING commission (any mining type key)
        if (!commissionManager.hasActiveCommission(player)) return;
        CommissionDefinition def = commissionManager.getActiveCommission(player);
        if (def == null || !def.getType().toUpperCase().startsWith("MINING")) return;

        // Resolve the objective material and its deepslate variant
        Material requiredMaterial;
        try {
            requiredMaterial = Material.valueOf(def.getObjectiveItem());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("MiningListener: invalid objective-item '"
                    + def.getObjectiveItem() + "' in commission " + def.getId());
            return;
        }

        Material brokenBlock = event.getBlock().getType();

        // Accept both normal and deepslate variants of the same ore
        if (!matches(brokenBlock, requiredMaterial)) return;

        // Zone check — use ZoneManager if available, fall back to legacy region config
        if (plugin.getZoneManager() != null) {
            if (!plugin.getZoneManager().isInValidZone(event.getBlock().getLocation(), def.getType())) return;
        } else if (plugin.getConfig().getBoolean("mining.region.enabled", false)) {
            Location loc = event.getBlock().getLocation();
            double minX = plugin.getConfig().getDouble("mining.region.min.x");
            double minY = plugin.getConfig().getDouble("mining.region.min.y");
            double minZ = plugin.getConfig().getDouble("mining.region.min.z");
            double maxX = plugin.getConfig().getDouble("mining.region.max.x");
            double maxY = plugin.getConfig().getDouble("mining.region.max.y");
            double maxZ = plugin.getConfig().getDouble("mining.region.max.z");
            if (loc.getX() < minX || loc.getX() > maxX
                    || loc.getY() < minY || loc.getY() > maxY
                    || loc.getZ() < minZ || loc.getZ() > maxZ) return;
        }

        // All checks passed — allow this break.
        event.setCancelled(false);

        // Suppress natural drops and give the turn-in item directly to inventory.
        // Uses block.getDrops(tool) so fortune enchantments are respected.
        event.setDropItems(false);
        Material turnIn = Material.matchMaterial(def.getTurnInItem());
        if (turnIn != null) {
            ItemStack tool = player.getInventory().getItemInMainHand();
            Collection<ItemStack> drops = event.getBlock().getDrops(tool);
            int fortuneAmount = drops.stream()
                    .filter(s -> s.getType() == turnIn)
                    .mapToInt(ItemStack::getAmount)
                    .sum();
            if (fortuneAmount == 0) fortuneAmount = 1; // fallback

            // Apply commission item cap
            PlayerCommissionState state = commissionManager.getPlayerState(player);
            int effectiveQty = state != null
                    ? state.getEffectiveQuantity(def.getObjectiveQuantity())
                    : def.getObjectiveQuantity();
            int currentCount = countItem(player, turnIn);
            int toGive = Math.min(fortuneAmount, Math.max(0, effectiveQty - currentCount));

            if (toGive > 0) {
                player.getInventory().addItem(new ItemStack(turnIn, toGive));
            }
        }

        commissionManager.incrementProgress(player.getUniqueId(), 1);
    }

    private int countItem(Player player, Material material) {
        int count = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getType() == material) count += stack.getAmount();
        }
        return count;
    }

    /**
     * Returns true if the broken block matches the required material,
     * including deepslate ore variants (e.g. COAL_ORE matches DEEPSLATE_COAL_ORE).
     */
    private boolean matches(Material broken, Material required) {
        if (broken == required) return true;
        // Build deepslate variant name and check
        String reqName = required.name();
        String deepslateVariant = "DEEPSLATE_" + reqName;
        try {
            Material deepslate = Material.valueOf(deepslateVariant);
            return broken == deepslate;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
