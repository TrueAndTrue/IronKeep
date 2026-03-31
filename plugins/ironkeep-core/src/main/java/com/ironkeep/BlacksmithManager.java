package com.ironkeep;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;

public class BlacksmithManager {

    private static final String BLACKSMITH_TAG = "ironkeep_blacksmith";

    private final IronKeepPlugin plugin;

    public BlacksmithManager(IronKeepPlugin plugin) {
        this.plugin = plugin;
    }

    public void spawnBlacksmith() {
        Location location = getSpawnLocation();
        if (location == null || location.getWorld() == null) {
            plugin.getLogger().warning("BlacksmithManager: could not determine spawn location, skipping.");
            return;
        }

        int removed = 0;
        for (Entity entity : location.getWorld().getEntities()) {
            if (isBlacksmith(entity)) {
                entity.remove();
                removed++;
            }
        }
        if (removed > 0) {
            plugin.getLogger().info("BlacksmithManager: removed " + removed + " existing blacksmith(s).");
        }

        location.getWorld().spawn(location, Villager.class, v -> {
            v.setProfession(Villager.Profession.TOOLSMITH);
            v.setVillagerLevel(2);
            v.setInvulnerable(true);
            v.setSilent(true);
            v.setAI(false);
            v.setGravity(false);
            v.setPersistent(true);
            v.customName(Component.text("Blacksmith").color(NamedTextColor.GOLD));
            v.setCustomNameVisible(true);
            v.getScoreboardTags().add(BLACKSMITH_TAG);
        });
        plugin.getLogger().info("BlacksmithManager: blacksmith spawned at " + location);
    }

    private Location getSpawnLocation() {
        var cfg = plugin.getConfig();
        String worldName = cfg.getString("blacksmith.spawn.world", "world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("BlacksmithManager: world '" + worldName + "' not found.");
            return null;
        }
        double x   = cfg.getDouble("blacksmith.spawn.x", 128.0);
        double y   = cfg.getDouble("blacksmith.spawn.y", 1.0);
        double z   = cfg.getDouble("blacksmith.spawn.z", -51.0);
        float  yaw   = (float) cfg.getDouble("blacksmith.spawn.yaw", 0.0);
        float  pitch = (float) cfg.getDouble("blacksmith.spawn.pitch", 0.0);
        return new Location(world, x, y, z, yaw, pitch);
    }

    public boolean isBlacksmith(Entity entity) {
        return entity.getScoreboardTags().contains(BLACKSMITH_TAG);
    }
}
