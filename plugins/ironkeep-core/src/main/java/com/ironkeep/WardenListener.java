package com.ironkeep;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class WardenListener implements Listener {

    private static final List<String> DIALOGUE = List.of(
        "Welcome to my prison.",
        "You are my prisoner now.",
        "You will complete the tasks I tell you to complete."
    );

    private final IronKeepPlugin plugin;
    private final WardenManager wardenManager;
    private final File seenFile;
    private final Set<UUID> seen = new HashSet<>();

    public WardenListener(IronKeepPlugin plugin, WardenManager wardenManager) {
        this.plugin = plugin;
        this.wardenManager = wardenManager;
        this.seenFile = new File(plugin.getDataFolder(), "warden-seen.yml");
        load();
    }

    private void load() {
        seen.clear();
        if (!seenFile.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(seenFile);
        List<?> uuids = yaml.getList("seen");
        if (uuids == null) return;
        for (Object obj : uuids) {
            try {
                seen.add(UUID.fromString(String.valueOf(obj)));
            } catch (IllegalArgumentException ignored) {}
        }
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("seen", seen.stream().map(UUID::toString).toList());
        try {
            yaml.save(seenFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save warden-seen.yml: " + e.getMessage());
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (seen.contains(uuid)) return;

        var scheduler = plugin.getServer().getScheduler();
        for (int i = 0; i < DIALOGUE.size(); i++) {
            String line = DIALOGUE.get(i);
            long delay = 1 + (long) i * 40; // line 1 at ~0s, line 2 at 2s, line 3 at 4s
            boolean isLast = (i == DIALOGUE.size() - 1);
            scheduler.runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;
                player.sendMessage(
                    Component.text(line)
                        .color(NamedTextColor.GOLD)
                        .decorate(TextDecoration.ITALIC)
                );
                if (isLast) {
                    seen.add(uuid);
                    save();
                }
            }, delay);
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (wardenManager.isWarden(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityTarget(EntityTargetEvent event) {
        if (event.getTarget() != null && wardenManager.isWarden(event.getTarget())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (wardenManager.isWarden(event.getRightClicked())) {
            event.setCancelled(true);
        }
    }
}
