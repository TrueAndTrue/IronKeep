package com.ironkeep;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class StarterKitListener implements Listener {

    private final StarterKitManager starterKitManager;

    public StarterKitListener(StarterKitManager starterKitManager) {
        this.starterKitManager = starterKitManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        if (starterKitManager.hasReceivedKit(player.getUniqueId())) return;

        starterKitManager.grantKit(player);
        starterKitManager.markReceived(player.getUniqueId());
        player.sendMessage(Component.text("Welcome to IronKeep!").color(NamedTextColor.GOLD));
    }
}
