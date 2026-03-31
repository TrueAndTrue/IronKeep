package com.ironkeep;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class TutorialListener implements Listener {

    private final TutorialManager tutorialManager;

    public TutorialListener(TutorialManager tutorialManager) {
        this.tutorialManager = tutorialManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        tutorialManager.onPlayerJoin(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        tutorialManager.onPlayerQuit(event.getPlayer());
    }
}
