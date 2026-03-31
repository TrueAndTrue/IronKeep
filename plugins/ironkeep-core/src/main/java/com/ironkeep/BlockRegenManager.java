package com.ironkeep;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.io.File;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Handles block regeneration in commission zones.
 *
 * Mining zones use a dynamic "living ore" system:
 *   - On startup, the mining zone is scanned and ore counts are tallied.
 *   - Any ore type below its target (70) is auto-populated using available stone blocks.
 *   - When a block is mined, it regenerates as a dynamically selected type based on
 *     current distribution, keeping each ore near its target of 70 (±5 variance).
 *   - Stone fills the remaining slots with a target of 81.
 *
 * Woodcutting zones use static restoration (block regens as exactly what was broken).
 * Farming zones restore fully-grown wheat after a delay.
 */
public class BlockRegenManager implements Listener {

    private final IronKeepPlugin plugin;

    // --- Woodcutting / Farming: static restoration ---
    /** Tracks pending regen: location → original BlockData to restore */
    private final Map<Location, BlockData> pendingRegen = new HashMap<>();
    /** Tracks farmland protected from trampling while crop regenerates */
    private final Set<Location> protectedFarmland = new HashSet<>();

    // --- Mining: dynamic ore system ---
    private static final Set<Material> TRACKED_ORES = Set.of(
            Material.COAL_ORE, Material.IRON_ORE, Material.GOLD_ORE, Material.DIAMOND_ORE
    );
    private static final int ORE_TARGET  = 70;
    private static final int STONE_TARGET = 81;
    private static final int ORE_VARIANCE = 5;

    /** Live ore counts, updated as blocks are mined and regenerated. */
    private final Map<Material, Integer> oreCounts = new HashMap<>();
    /** Locations waiting for mining regen (dynamic — no original block data needed). */
    private final Set<Location> pendingMiningRegen = new HashSet<>();

    private int regenDelayTicks = 60;
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
            String woodPh   = yaml.getString("placeholders.woodcutting", "BEDROCK");
            try { miningPlaceholder    = Material.valueOf(miningPh); } catch (IllegalArgumentException ignored) {}
            try { woodcuttingPlaceholder = Material.valueOf(woodPh); } catch (IllegalArgumentException ignored) {}
        }
    }

    /**
     * Called one tick after startup (world is loaded).
     * Scans the mining zone to tally existing ores, then auto-populates any
     * ore type that is below its target by converting stone blocks.
     */
    public void initMiningOreCounts() {
        ZoneManager zm = plugin.getZoneManager();
        if (zm == null) return;

        List<Zone> miningZones = zm.getZonesForType("MINING_COAL");
        if (miningZones.isEmpty()) return;
        Zone zone = miningZones.get(0);

        World world = Bukkit.getWorld(zone.getWorld());
        if (world == null) {
            plugin.getLogger().warning("BlockRegenManager: world '" + zone.getWorld() + "' not found for ore init.");
            return;
        }

        // Reset counts
        for (Material ore : TRACKED_ORES) oreCounts.put(ore, 0);
        oreCounts.put(Material.STONE, 0);

        // Scan zone
        for (int y = zone.getYMin(); y <= zone.getYMax(); y++) {
            for (int x = zone.getX1(); x <= zone.getX2(); x++) {
                for (int z = zone.getZ1(); z <= zone.getZ2(); z++) {
                    Material type = world.getBlockAt(x, y, z).getType();
                    if (TRACKED_ORES.contains(type)) {
                        oreCounts.merge(type, 1, Integer::sum);
                    } else if (type == Material.STONE) {
                        oreCounts.merge(Material.STONE, 1, Integer::sum);
                    }
                }
            }
        }

        plugin.getLogger().info("BlockRegenManager: ore scan complete — " + oreCounts);

        // Auto-populate ores that are below target
        placeMissingOres(world, zone);
    }

    /**
     * Fills ore types below their target by converting stone blocks in the zone.
     * Stone blocks are shuffled before selection so placement is spread out.
     */
    private void placeMissingOres(World world, Zone zone) {
        // Collect available stone locations
        List<Location> stoneLocs = new ArrayList<>();
        for (int y = zone.getYMin(); y <= zone.getYMax(); y++) {
            for (int x = zone.getX1(); x <= zone.getX2(); x++) {
                for (int z = zone.getZ1(); z <= zone.getZ2(); z++) {
                    if (world.getBlockAt(x, y, z).getType() == Material.STONE) {
                        stoneLocs.add(new Location(world, x, y, z));
                    }
                }
            }
        }
        Collections.shuffle(stoneLocs);

        Iterator<Location> iter = stoneLocs.iterator();
        for (Material ore : TRACKED_ORES) {
            int needed = ORE_TARGET - oreCounts.getOrDefault(ore, 0);
            for (int i = 0; i < needed && iter.hasNext(); i++) {
                Location loc = iter.next();
                loc.getBlock().setType(ore);
                oreCounts.merge(ore, 1, Integer::sum);
                oreCounts.merge(Material.STONE, -1, Integer::sum);
            }
        }
    }

    /**
     * Selects the block type a mined ore slot should regenerate as.
     *
     * Each type's weight = max(0, target + variance - currentCount).
     * Types at or above (target + variance) have weight 0 and won't be chosen.
     * Types below target are proportionally favoured, guiding the mine back toward balance.
     */
    private Material selectRegenMaterial() {
        Map<Material, Integer> weights = new LinkedHashMap<>();

        for (Material ore : TRACKED_ORES) {
            int count  = oreCounts.getOrDefault(ore, 0);
            int weight = Math.max(0, ORE_TARGET + ORE_VARIANCE - count);
            if (weight > 0) weights.put(ore, weight);
        }

        int stoneCount  = oreCounts.getOrDefault(Material.STONE, 0);
        int stoneWeight = Math.max(0, STONE_TARGET + ORE_VARIANCE - stoneCount);
        if (stoneWeight > 0) weights.put(Material.STONE, stoneWeight);

        if (weights.isEmpty()) return Material.STONE;

        int total = weights.values().stream().mapToInt(Integer::intValue).sum();
        int rand  = ThreadLocalRandom.current().nextInt(total);
        int cumulative = 0;
        for (Map.Entry<Material, Integer> entry : weights.entrySet()) {
            cumulative += entry.getValue();
            if (rand < cumulative) return entry.getKey();
        }
        return Material.STONE;
    }

    // -------------------------------------------------------------------------
    // Event handlers
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Location loc = block.getLocation();

        ZoneManager zoneManager = plugin.getZoneManager();
        if (zoneManager == null) return;

        boolean inAnyZone = false;
        String matchedType = null;
        for (Zone zone : zoneManager.getAllZones()) {
            if (zone.contains(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ())) {
                inAnyZone = true;
                if (!zone.getCommissionTypes().isEmpty()) {
                    matchedType = zone.getCommissionTypes().get(0);
                }
                break;
            }
        }
        if (!inAnyZone) return;

        Material brokenType  = block.getType();
        BlockData originalData = block.getBlockData().clone();

        if (matchedType != null && matchedType.startsWith("MINING")) {
            if (!TRACKED_ORES.contains(brokenType) && brokenType != Material.STONE) return;
            scheduleMiningRegen(loc, brokenType);

        } else if (matchedType != null && matchedType.equals("WOODCUTTING")) {
            scheduleWoodRegen(loc, originalData);

        }
    }

    private void scheduleMiningRegen(Location loc, Material brokenType) {
        // Decrement count for what was just broken
        if (TRACKED_ORES.contains(brokenType) || brokenType == Material.STONE) {
            oreCounts.merge(brokenType, -1, Integer::sum);
        }

        pendingMiningRegen.add(loc);
        plugin.getServer().getScheduler().runTask(plugin, () -> loc.getBlock().setType(miningPlaceholder));

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!pendingMiningRegen.contains(loc)) return;
            Block b = loc.getBlock();
            if (b.getType() != miningPlaceholder) {
                pendingMiningRegen.remove(loc);
                return;
            }
            Material regenType = selectRegenMaterial();
            b.setType(regenType);
            oreCounts.merge(regenType, 1, Integer::sum);
            pendingMiningRegen.remove(loc);
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

    // -------------------------------------------------------------------------
    // Farming zone support (regen handled by FarmingListener)
    // -------------------------------------------------------------------------

    public int getRegenDelayTicks() { return regenDelayTicks; }

    public void protectFarmland(Location loc)   { protectedFarmland.add(loc); }
    public void unprotectFarmland(Location loc) { protectedFarmland.remove(loc); }

    /**
     * Called one tick after startup. Ensures the farming zone has farmland at Y=yMin
     * and fully-grown wheat at Y=yMax. Any missing crops or bare dirt are filled in.
     */
    public void initFarmingZone() {
        ZoneManager zm = plugin.getZoneManager();
        if (zm == null) return;

        List<Zone> farmingZones = zm.getZonesForType("FARMING");
        if (farmingZones.isEmpty()) return;
        Zone zone = farmingZones.get(0);

        World world = org.bukkit.Bukkit.getWorld(zone.getWorld());
        if (world == null) {
            plugin.getLogger().warning("BlockRegenManager: world '" + zone.getWorld() + "' not found for farming zone init.");
            return;
        }

        int minX = Math.min(zone.getX1(), zone.getX2());
        int maxX = Math.max(zone.getX1(), zone.getX2());
        int minZ = Math.min(zone.getZ1(), zone.getZ2());
        int maxZ = Math.max(zone.getZ1(), zone.getZ2());
        int farmlandY = zone.getYMin();
        int cropY     = zone.getYMax();

        BlockData fullyGrownWheat = org.bukkit.Bukkit.createBlockData(Material.WHEAT,
                d -> ((Ageable) d).setAge(((Ageable) d).getMaximumAge()));

        int farmlandPlaced = 0;
        int cropsPlaced    = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (world.getBlockAt(x, farmlandY, z).getType() != Material.FARMLAND) {
                    world.getBlockAt(x, farmlandY, z).setType(Material.FARMLAND);
                    farmlandPlaced++;
                }
                if (world.getBlockAt(x, cropY, z).getType() != Material.WHEAT) {
                    world.getBlockAt(x, cropY, z).setBlockData(fullyGrownWheat);
                    cropsPlaced++;
                }
            }
        }
        plugin.getLogger().info("BlockRegenManager: farming zone initialised — "
                + farmlandPlaced + " farmland placed, " + cropsPlaced + " crops placed.");
    }

    /** Cancel regen if a placeholder is broken (e.g. by an OP clearing the mine). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlaceholderBreak(BlockBreakEvent event) {
        Location loc = event.getBlock().getLocation();
        Material type = event.getBlock().getType();
        if (type == miningPlaceholder || type == woodcuttingPlaceholder) {
            pendingMiningRegen.remove(loc);
            pendingRegen.remove(loc);
        }
    }

    /** Prevent survival players from breaking placeholder blocks. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onBedrockBreak(BlockBreakEvent event) {
        Location loc = event.getBlock().getLocation();
        boolean pending = pendingMiningRegen.contains(loc) || pendingRegen.containsKey(loc);
        if (pending && !event.getPlayer().isOp()) {
            event.setCancelled(true);
        }
    }

    /** Protect farmland from trampling while a crop is regenerating. */
    @EventHandler
    public void onFarmlandTrample(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;
        if (protectedFarmland.contains(event.getClickedBlock().getLocation())) {
            event.setCancelled(true);
        }
    }
}
