package com.ironkeep;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

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

        for (Entity entity : location.getWorld().getEntities()) {
            if (isWarden(entity)) {
                plugin.getLogger().info("WardenManager: existing warden found, skipping spawn.");
                return;
            }
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
        location.getWorld().spawn(location, Zombie.class, z -> {
            z.setInvulnerable(true);
            z.setSilent(true);
            z.setAI(false);
            z.setGravity(false);
            z.setPersistent(true);
            z.setBaby(false);
            z.customName(Component.text("Warden").color(NamedTextColor.GRAY));
            z.setCustomNameVisible(true);
            z.getScoreboardTags().add(WARDEN_TAG);

            EntityEquipment eq = z.getEquipment();
            if (eq != null) {
                eq.setHelmet(new ItemStack(Material.IRON_HELMET));
                eq.setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
                eq.setLeggings(new ItemStack(Material.IRON_LEGGINGS));
                eq.setBoots(new ItemStack(Material.IRON_BOOTS));
                eq.setItemInMainHand(new ItemStack(Material.IRON_SWORD));
                eq.setHelmetDropChance(0f);
                eq.setChestplateDropChance(0f);
                eq.setLeggingsDropChance(0f);
                eq.setBootsDropChance(0f);
                eq.setItemInMainHandDropChance(0f);
            }
        });
    }

    public boolean isWarden(Entity entity) {
        return entity.getScoreboardTags().contains(WARDEN_TAG);
    }
}
