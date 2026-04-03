package com.ironkeep;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Handles the FARMING commission type.
 *
 * The farming zone always contains farmland + fully-grown wheat as the physical world state.
 * Per-player crop overlays are sent via Player#sendBlockChange so each player sees the crop
 * type that matches their active commission (e.g. WHEAT or CARROTS). The real world blocks
 * never change — only the client-side visual.
 *
 * When a player harvests a crop:
 *   - Natural drops are suppressed.
 *   - The commission's turn-in item is given directly.
 *   - The real wheat block is restored after the regen delay, and the overlay is re-sent.
 *
 * Overlay lifecycle:
 *   - Sent when a player enters the farming zone with a FARMING commission active.
 *   - Also sent immediately if the commission is assigned while already in the zone.
 *   - Cleared on zone exit, commission completion, cancellation, skip, or player quit.
 */
public class FarmingListener implements Listener {

    /** Crop block materials the zone can contain. Extend this set when adding new crop types. */
    private static final Set<Material> CROP_BLOCKS = Set.of(Material.WHEAT, Material.CARROTS);

    /** The physical crop always planted in the world — acts as the harvestable substrate. */
    private static final Material BASE_CROP = Material.WHEAT;

    private final IronKeepPlugin plugin;

    /** The crop material each player is currently seeing via sendBlockChange. */
    private final Map<UUID, Material> activeOverlays = new HashMap<>();

    /** Crop locations currently regenerating (breaks on these are ignored). */
    private final Set<Location> regenLocations = new HashSet<>();

    public FarmingListener(IronKeepPlugin plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------
    // Block harvesting
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.NORMAL)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        CommissionManager cm = plugin.getCommissionManager();

        if (!cm.hasActiveCommission(player)) return;
        CommissionDefinition def = cm.getActiveCommission(player);
        if (def == null || !def.getType().equalsIgnoreCase("FARMING")) return;

        Block block = event.getBlock();
        Location loc = block.getLocation();

        // Must be in the farming zone
        ZoneManager zm = plugin.getZoneManager();
        if (zm == null || !zm.isInValidZone(loc, def.getType())) return;

        // Must be a tracked crop at full growth (real world block, not fake overlay)
        if (!CROP_BLOCKS.contains(block.getType())) return;
        if (!(block.getBlockData() instanceof Ageable ageable)) return;
        if (ageable.getAge() != ageable.getMaximumAge()) return;

        // Skip blocks currently regenerating
        if (regenLocations.contains(loc)) {
            event.setCancelled(true);
            return;
        }

        // Check the item cap before giving anything
        Material turnIn = Material.matchMaterial(def.getTurnInItem());
        PlayerCommissionState state = cm.getPlayerState(player);
        if (turnIn != null && state != null) {
            int effectiveQty = state.getEffectiveQuantity(def.getObjectiveQuantity());
            int currentCount = countItem(player, turnIn);
            if (currentCount >= effectiveQty) {
                event.setCancelled(true);
                player.sendMessage(org.bukkit.ChatColor.YELLOW
                        + "You already have enough for your commission. Use /commission complete.");
                return;
            }
        }

        // All checks passed — allow this break.
        event.setCancelled(false);

        // Suppress natural drops, give commission turn-in item instead
        event.setDropItems(false);
        if (turnIn != null) {
            player.getInventory().addItem(new ItemStack(turnIn, 1));
        }

        scheduleCropRegen(loc);
        cm.incrementProgress(uuid, 1);
    }

    // -------------------------------------------------------------------------
    // Crop regeneration
    // -------------------------------------------------------------------------

    private void scheduleCropRegen(Location loc) {
        regenLocations.add(loc);

        Location farmlandLoc = loc.clone().add(0, -1, 0);
        BlockRegenManager brm = plugin.getBlockRegenManager();
        brm.protectFarmland(farmlandLoc);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            regenLocations.remove(loc);
            brm.unprotectFarmland(farmlandLoc);

            // Always restore the physical block as BASE_CROP (world state is always wheat).
            // Per-player overlays handle the visual appearance of the crop type.
            BlockData fullyGrownBase = createFullyGrown(BASE_CROP);
            if (fullyGrownBase != null) {
                loc.getBlock().setBlockData(fullyGrownBase);
            } else {
                loc.getBlock().setType(BASE_CROP);
            }

            // Defer overlay resend by one tick so the real block update packet
            // reaches clients before the per-player overlay packets
            plugin.getServer().getScheduler().runTask(plugin, () -> resendOverlayAt(loc));
        }, brm.getRegenDelayTicks());
    }

    /** Sends the per-player overlay for a single location to all players with an active overlay. */
    private void resendOverlayAt(Location loc) {
        for (Map.Entry<UUID, Material> entry : activeOverlays.entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p != null && p.isOnline()) {
                BlockData overlay = createFullyGrown(entry.getValue());
                if (overlay != null) p.sendBlockChange(loc, overlay);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Per-player crop overlay
    // -------------------------------------------------------------------------

    /**
     * Sends a full-zone crop overlay to the player so they see their commission's
     * crop type instead of the real wheat. No-ops if cropType == BASE_CROP since the
     * real world already shows wheat.
     */
    public void sendOverlay(Player player, Material cropType) {
        activeOverlays.put(player.getUniqueId(), cropType);

        ZoneManager zm = plugin.getZoneManager();
        if (zm == null) return;
        List<Zone> zones = zm.getZonesForType("FARMING");
        if (zones.isEmpty()) return;
        Zone zone = zones.get(0);

        World world = Bukkit.getWorld(zone.getWorld());
        if (world == null) return;

        BlockData overlayData = createFullyGrown(cropType);
        if (overlayData == null) return;

        int cropY = zone.getYMax();
        int minX = Math.min(zone.getX1(), zone.getX2());
        int maxX = Math.max(zone.getX1(), zone.getX2());
        int minZ = Math.min(zone.getZ1(), zone.getZ2());
        int maxZ = Math.max(zone.getZ1(), zone.getZ2());

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                player.sendBlockChange(new Location(world, x, cropY, z), overlayData);
            }
        }
    }

    /** Clears the player's crop overlay, reverting to the real block visuals. */
    public void clearOverlay(Player player) {
        Material previous = activeOverlays.remove(player.getUniqueId());
        if (previous == null || previous == BASE_CROP) return; // nothing to undo

        ZoneManager zm = plugin.getZoneManager();
        if (zm == null) return;
        List<Zone> zones = zm.getZonesForType("FARMING");
        if (zones.isEmpty()) return;
        Zone zone = zones.get(0);

        World world = Bukkit.getWorld(zone.getWorld());
        if (world == null) return;

        int cropY = zone.getYMax();
        int minX = Math.min(zone.getX1(), zone.getX2());
        int maxX = Math.max(zone.getX1(), zone.getX2());
        int minZ = Math.min(zone.getZ1(), zone.getZ2());
        int maxZ = Math.max(zone.getZ1(), zone.getZ2());

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                Location loc = new Location(world, x, cropY, z);
                player.sendBlockChange(loc, loc.getBlock().getBlockData());
            }
        }
    }

    // -------------------------------------------------------------------------
    // Farmland protection
    // -------------------------------------------------------------------------

    /** Prevents farmland in the farming zone from drying out (no water source required). */
    @EventHandler
    public void onFarmlandFade(BlockFadeEvent event) {
        if (event.getBlock().getType() != Material.FARMLAND) return;
        ZoneManager zm = plugin.getZoneManager();
        if (zm != null && zm.isInValidZone(event.getBlock().getLocation(), "FARMING")) {
            event.setCancelled(true);
        }
    }

    /** Prevents players from trampling farmland anywhere in the farming zone. */
    @EventHandler
    public void onFarmlandTrample(PlayerInteractEvent event) {
        if (event.getAction() != Action.PHYSICAL) return;
        if (event.getClickedBlock() == null) return;
        if (event.getClickedBlock().getType() != Material.FARMLAND) return;

        ZoneManager zm = plugin.getZoneManager();
        if (zm == null) return;
        if (zm.isInValidZone(event.getClickedBlock().getLocation(), "FARMING")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        activeOverlays.remove(event.getPlayer().getUniqueId());
    }

    // -------------------------------------------------------------------------
    // Commission lifecycle hooks (called by CommissionManager)
    // -------------------------------------------------------------------------

    /** Called when a FARMING commission is assigned to a player. */
    public void onCommissionAssigned(Player player) {
        applyOverlayIfActive(player);
    }

    /**
     * Called when a FARMING commission ends (complete, cancel, or skip).
     * The overlay is intentionally kept so the player continues seeing their
     * crop type until a new commission is assigned.
     */
    public void onCommissionEnded(Player player) {
        // Overlay persists — cleared when next commission is assigned
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void applyOverlayIfActive(Player player) {
        CommissionManager cm = plugin.getCommissionManager();
        if (!cm.hasActiveCommission(player)) return;
        CommissionDefinition def = cm.getActiveCommission(player);
        if (def == null || !def.getType().equalsIgnoreCase("FARMING")) return;

        Material cropMaterial = Material.matchMaterial(def.getObjectiveItem());
        if (cropMaterial == null) cropMaterial = BASE_CROP;
        sendOverlay(player, cropMaterial);
    }

    private int countItem(Player player, Material material) {
        int count = 0;
        for (org.bukkit.inventory.ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getType() == material) count += stack.getAmount();
        }
        return count;
    }

    private static BlockData createFullyGrown(Material cropMaterial) {
        try {
            BlockData data = Bukkit.createBlockData(cropMaterial);
            if (data instanceof Ageable ageable) {
                ageable.setAge(ageable.getMaximumAge());
            }
            return data;
        } catch (Exception e) {
            return null;
        }
    }
}
