package com.ironkeep;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class StarterKitListener implements Listener {

    private final IronKeepPlugin plugin;
    private final StarterKitManager starterKitManager;

    public StarterKitListener(IronKeepPlugin plugin, StarterKitManager starterKitManager) {
        this.plugin = plugin;
        this.starterKitManager = starterKitManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        if (starterKitManager.hasReceivedKit(player.getUniqueId())) return;

        starterKitManager.grantKit(player);
        starterKitManager.markReceived(player.getUniqueId());
        player.sendMessage(Component.text("Welcome to IronKeep!").color(NamedTextColor.GOLD));

        ConfigurationSection spawnSection = plugin.getConfig().getConfigurationSection("new-player-spawn");
        if (spawnSection != null) {
            String worldName = spawnSection.getString("world", "world");
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                Location spawnLoc = new Location(
                        world,
                        spawnSection.getDouble("x", 0),
                        spawnSection.getDouble("y", 64),
                        spawnSection.getDouble("z", 0),
                        (float) spawnSection.getDouble("yaw", 0),
                        (float) spawnSection.getDouble("pitch", 0)
                );
                player.teleport(spawnLoc);
            }
        }
    }
}
