package com.ironkeep;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Handles static block regeneration in commission zones.
 *
 * When a player breaks a commission-relevant block inside a zone:
 *  - Mining/Woodcutting: replaced with BEDROCK placeholder, regenerates after delay.
 *  - Farming: farmland stays, fully grown wheat reappears after delay.
 *
 * Each broken block has its own independent timer.
 * Config: zones.yml (reuses zone definitions) + regen-delay-ticks in zones.yml.
 */
public class BlockRegenManager implements Listener {

    private final IronKeepPlugin plugin;

    /** Tracks pending regen: location → original BlockData to restore */
    private final Map<Location, BlockData> pendingRegen = new HashMap<>();
    /** Tracks farmland that should be protected from trampling */
    private final Set<Location> protectedFarmland = new HashSet<>();

    private int regenDelayTicks = 60; // default 3 seconds (20 ticks/s)
    private Material miningPlaceholder = Material.BEDROCK;
    private Material woodcuttingPlaceholder = Material.BEDROCK;

    public BlockRegenManager(IronKeepPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File zonesFile = new File(plugin.getDataFolder(), "zones.yml");
        if (zonesFile.exists()) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(zonesFile);
            regenDelayTicks = yaml.getInt("regen-delay-ticks", 60);
            String miningPh = yaml.getString("placeholders.mining", "BEDROCK");
            String woodPh = yaml.getString("placeholders.woodcutting", "BEDROCK");
            try { miningPlaceholder = Material.valueOf(miningPh); } catch (IllegalArgumentException ignored) {}
            try { woodcuttingPlaceholder = Material.valueOf(woodPh); } catch (IllegalArgumentException ignored) {}
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Location loc = block.getLocation();

        ZoneManager zoneManager = plugin.getZoneManager();
        if (zoneManager == null) return;

        // Check if this location is inside any commission zone
        boolean inAnyZone = false;
        String matchedType = null;
        for (Zone zone : zoneManager.getAllZones()) {
            if (zone.contains(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ())) {
                inAnyZone = true;
                if (!zone.getCommissionTypes().isEmpty()) {
                    matchedType = zone.getCommissionTypes().get(0); // use first type to determine behavior
                }
                break;
            }
        }
        if (!inAnyZone) return;

        Material brokenType = block.getType();
        BlockData originalData = block.getBlockData().clone();

        if (matchedType != null && matchedType.startsWith("MINING")) {
            // Mining zones: replace with bedrock, restore ore after delay
            scheduleMiningRegen(loc, originalData);

        } else if (matchedType != null && matchedType.equals("WOODCUTTING")) {
            // Woodcutting zones: replace with bedrock, restore log after delay
            scheduleWoodRegen(loc, originalData);

        } else if (matchedType != null && matchedType.equals("FARMING")) {
            // Farming zones: only fully grown wheat triggers regen
            if (brokenType != Material.WHEAT) return;
            if (!(originalData instanceof Ageable ageable)) return;
            if (ageable.getAge() != ageable.getMaximumAge()) return;
            scheduleFarmingRegen(loc, originalData);
        }
    }

    private void scheduleMiningRegen(Location loc, BlockData originalData) {
        pendingRegen.put(loc, originalData);
        // Set placeholder immediately (block is already air after break event)
        plugin.getServer().getScheduler().runTask(plugin, () -> loc.getBlock().setType(miningPlaceholder));

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!pendingRegen.containsKey(loc)) return; // cancelled
            Block b = loc.getBlock();
            if (b.getType() != miningPlaceholder) {
                pendingRegen.remove(loc);
                return; // something else changed the block
            }
            b.setBlockData(originalData);
            pendingRegen.remove(loc);
        }, regenDelayTicks);
    }

    private void scheduleWoodRegen(Location loc, BlockData originalData) {
        pendingRegen.put(loc, originalData);
        plugin.getServer().getScheduler().runTask(plugin, () -> loc.getBlock().setType(woodcuttingPlaceholder));

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!pendingRegen.containsKey(loc)) return;
            Block b = loc.getBlock();
            if (b.getType() != woodcuttingPlaceholder) {
                pendingRegen.remove(loc);
                return;
            }
            b.setBlockData(originalData);
            pendingRegen.remove(loc);
        }, regenDelayTicks);
    }

    private void scheduleFarmingRegen(Location loc, BlockData originalData) {
        // Protect the farmland below from being trampled
        Location farmlandLoc = loc.clone().add(0, -1, 0);
        protectedFarmland.add(farmlandLoc);
        pendingRegen.put(loc, originalData);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!pendingRegen.containsKey(loc)) return;
            Block cropBlock = loc.getBlock();
            // Restore fully grown wheat
            cropBlock.setBlockData(originalData);
            pendingRegen.remove(loc);
            protectedFarmland.remove(farmlandLoc);
        }, regenDelayTicks);
    }

    /** Cancel regen if the placeholder itself is broken (e.g. by an op) */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlaceholderBreak(BlockBreakEvent event) {
        Location loc = event.getBlock().getLocation();
        if (pendingRegen.containsKey(loc) &&
                (event.getBlock().getType() == miningPlaceholder || event.getBlock().getType() == woodcuttingPlaceholder)) {
            pendingRegen.remove(loc);
        }
    }

    /** Prevent survival players from breaking bedrock placeholders */
    @EventHandler(priority = EventPriority.HIGH)
    public void onBedrockBreak(BlockBreakEvent event) {
        Location loc = event.getBlock().getLocation();
        if (pendingRegen.containsKey(loc) && !event.getPlayer().isOp()) {
            event.setCancelled(true);
        }
    }

    /** Protect farmland from trampling while crop is regenerating */
    @EventHandler
    public void onFarmlandTrample(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;
        Location loc = event.getClickedBlock().getLocation();
        if (protectedFarmland.contains(loc)) {
            event.setCancelled(true);
        }
    }
}
