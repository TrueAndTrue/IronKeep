package com.ironkeep;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads zone definitions and provides location-based zone lookup.
 *
 * Zones are defined in zones.yml. Multiple zones per commission type are supported.
 * If no zone is configured for a commission type, the default behavior (from config) applies.
 */
public class ZoneManager {

    private final IronKeepPlugin plugin;
    private final List<Zone> zones = new ArrayList<>();

    /** If true (default), block progress when no zone is configured for a type. */
    private boolean blockWithoutZone = true;

    public ZoneManager(IronKeepPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        zones.clear();
        File zonesFile = new File(plugin.getDataFolder(), "zones.yml");
        if (!zonesFile.exists()) plugin.saveResource("zones.yml", false);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(zonesFile);

        blockWithoutZone = yaml.getBoolean("block-without-zone", true);

        ConfigurationSection section = yaml.getConfigurationSection("zones");
        if (section == null) {
            plugin.getLogger().warning("zones.yml has no 'zones' section.");
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(key);
            if (entry == null) continue;
            String world = entry.getString("world", "world");
            int x1 = entry.getInt("x1"), z1 = entry.getInt("z1");
            int x2 = entry.getInt("x2"), z2 = entry.getInt("z2");
            int yMin = entry.getInt("y-min", -64), yMax = entry.getInt("y-max", 320);
            List<String> types = entry.getStringList("commission-types");
            zones.add(new Zone(key, world, x1, z1, x2, z2, yMin, yMax, types));
        }
        plugin.getLogger().info("ZoneManager: loaded " + zones.size() + " zone(s).");
    }

    /**
     * Returns true if the given location is in a valid zone for the given commission type.
     * If no zone is configured for the type: returns !blockWithoutZone (default: false).
     */
    public boolean isInValidZone(Location loc, String commissionType) {
        if (loc.getWorld() == null) return false;
        String worldName = loc.getWorld().getName();
        int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();

        boolean anyZoneForType = false;
        for (Zone zone : zones) {
            if (zone.supportsType(commissionType)) {
                anyZoneForType = true;
                if (zone.contains(worldName, x, y, z)) return true;
            }
        }
        // No zone at this location — fall back to config setting
        if (!anyZoneForType) return !blockWithoutZone;
        return false;
    }

    /** Returns all zones that apply to a given commission type. */
    public List<Zone> getZonesForType(String commissionType) {
        List<Zone> result = new ArrayList<>();
        for (Zone zone : zones) {
            if (zone.supportsType(commissionType)) result.add(zone);
        }
        return result;
    }

    public List<Zone> getAllZones() { return zones; }
}
