package com.ironkeep;

import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Set;

public class WoodcuttingListener implements Listener {

    private static final Set<Material> AXES = Set.of(
            Material.WOODEN_AXE,
            Material.STONE_AXE,
            Material.IRON_AXE,
            Material.GOLDEN_AXE,
            Material.DIAMOND_AXE,
            Material.NETHERITE_AXE
    );

    private final IronKeepPlugin plugin;

    public WoodcuttingListener(IronKeepPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        CommissionManager commissionManager = plugin.getCommissionManager();

        // Check player has an active WOODCUTTING commission
        if (!commissionManager.hasActiveCommission(player)) return;
        CommissionDefinition def = commissionManager.getActiveCommission(player);
        if (def == null || !def.getType().equalsIgnoreCase("WOODCUTTING")) return;

        // The block broken must match this commission's objective item
        Material requiredMaterial;
        try {
            requiredMaterial = Material.valueOf(def.getObjectiveItem());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("WoodcuttingListener: invalid objective-item '"
                    + def.getObjectiveItem() + "' in commission " + def.getId());
            return;
        }

        if (event.getBlock().getType() != requiredMaterial) return;

        // Check axe requirement (configurable, default true)
        if (plugin.getConfig().getBoolean("woodcutting.require-axe", true)) {
            ItemStack mainHand = player.getInventory().getItemInMainHand();
            if (!AXES.contains(mainHand.getType())) return;
        }

        // Check optional region restriction
        if (plugin.getConfig().getBoolean("woodcutting.region-enabled", false)) {
            Location loc = event.getBlock().getLocation();
            double minX = plugin.getConfig().getDouble("woodcutting.region.min.x");
            double minY = plugin.getConfig().getDouble("woodcutting.region.min.y");
            double minZ = plugin.getConfig().getDouble("woodcutting.region.min.z");
            double maxX = plugin.getConfig().getDouble("woodcutting.region.max.x");
            double maxY = plugin.getConfig().getDouble("woodcutting.region.max.y");
            double maxZ = plugin.getConfig().getDouble("woodcutting.region.max.z");
            if (loc.getX() < minX || loc.getX() > maxX
                    || loc.getY() < minY || loc.getY() > maxY
                    || loc.getZ() < minZ || loc.getZ() > maxZ) return;
        }

        commissionManager.incrementProgress(player.getUniqueId(), 1);
    }
}
