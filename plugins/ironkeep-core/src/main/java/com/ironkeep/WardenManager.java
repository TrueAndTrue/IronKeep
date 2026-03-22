package com.ironkeep;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;

public class WardenManager {

    private static final String WARDEN_TAG = "ironkeep_warden";

    private final IronKeepPlugin plugin;

    public WardenManager(IronKeepPlugin plugin) {
        this.plugin = plugin;
    }

    public void spawnWarden() {
        Location location = getSpawnLocation();
        if (location == null || location.getWorld() == null) {
            plugin.getLogger().warning("WardenManager: could not determine spawn location, skipping warden spawn.");
            return;
        }

        int removed = 0;
        for (Entity entity : location.getWorld().getEntities()) {
            if (isWarden(entity)) {
                entity.remove();
                removed++;
            }
        }
        if (removed > 0) {
            plugin.getLogger().info("WardenManager: removed " + removed + " existing warden(s).");
        }

        spawnNpc(location);
        plugin.getLogger().info("WardenManager: warden spawned at " + location);
    }

    private Location getSpawnLocation() {
        var config = plugin.getConfig();
        String worldName = config.getString("warden.spawn.world");

        World world;
        if (worldName == null) {
            world = Bukkit.getWorlds().get(0);
            return world.getSpawnLocation();
        }

        world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("WardenManager: configured warden world '" + worldName + "' not found, using default.");
            world = Bukkit.getWorlds().get(0);
            return world.getSpawnLocation();
        }

        Location def = world.getSpawnLocation();
        double x = config.getDouble("warden.spawn.x", def.getX());
        double y = config.getDouble("warden.spawn.y", def.getY());
        double z = config.getDouble("warden.spawn.z", def.getZ());
        float yaw = (float) config.getDouble("warden.spawn.yaw", 0.0);
        float pitch = (float) config.getDouble("warden.spawn.pitch", 0.0);
        return new Location(world, x, y, z, yaw, pitch);
    }

    private void spawnNpc(Location location) {
        location.getWorld().spawn(location, Villager.class, v -> {
            v.setInvulnerable(true);
            v.setSilent(true);
            v.setAI(false);
            v.setGravity(false);
            v.setPersistent(true);
            v.customName(Component.text("Warden").color(NamedTextColor.GRAY));
            v.setCustomNameVisible(true);
            v.getScoreboardTags().add(WARDEN_TAG);
        });
    }

    public boolean isWarden(Entity entity) {
        return entity.getScoreboardTags().contains(WARDEN_TAG);
    }
}
