package com.ironkeep;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

public class FarmingListener implements Listener {

    private final IronKeepPlugin plugin;

    public FarmingListener(IronKeepPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        CommissionManager commissionManager = plugin.getCommissionManager();

        // Check player has an active FARMING commission
        if (!commissionManager.hasActiveCommission(player)) return;
        CommissionDefinition def = commissionManager.getActiveCommission(player);
        if (def == null || !def.getType().equalsIgnoreCase("FARMING")) return;

        // Check broken block type matches configured crop-type
        String cropTypeName = plugin.getConfig().getString("farming.crop-type", "WHEAT");
        Material cropMaterial;
        try {
            cropMaterial = Material.valueOf(cropTypeName);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("FarmingListener: invalid crop-type '" + cropTypeName + "' in config");
            return;
        }

        if (event.getBlock().getType() != cropMaterial) return;

        // Check block is fully grown (age == max-age)
        if (!(event.getBlock().getBlockData() instanceof Ageable ageable)) return;
        int maxAge = plugin.getConfig().getInt("farming.max-age", 7);
        if (ageable.getAge() != maxAge) return;

        // Zone check — use ZoneManager if available, fall back to legacy region config
        if (plugin.getZoneManager() != null) {
            if (!plugin.getZoneManager().isInValidZone(event.getBlock().getLocation(), def.getType())) return;
        } else if (plugin.getConfig().getBoolean("farming.region.enabled", false)) {
            Location loc = event.getBlock().getLocation();
            double minX = plugin.getConfig().getDouble("farming.region.min.x");
            double minY = plugin.getConfig().getDouble("farming.region.min.y");
            double minZ = plugin.getConfig().getDouble("farming.region.min.z");
            double maxX = plugin.getConfig().getDouble("farming.region.max.x");
            double maxY = plugin.getConfig().getDouble("farming.region.max.y");
            double maxZ = plugin.getConfig().getDouble("farming.region.max.z");
            if (loc.getX() < minX || loc.getX() > maxX
                    || loc.getY() < minY || loc.getY() > maxY
                    || loc.getZ() < minZ || loc.getZ() > maxZ) return;
        }

        commissionManager.incrementProgress(player.getUniqueId(), 1);
    }
}
