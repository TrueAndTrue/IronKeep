package com.ironkeep;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CommissionStateStore {

    private final IronKeepPlugin plugin;
    private final File stateFile;
    private final Map<UUID, PlayerCommissionState> states = new HashMap<>();

    public CommissionStateStore(IronKeepPlugin plugin) {
        this.plugin = plugin;
        this.stateFile = new File(plugin.getDataFolder(), "player-commissions.yml");
    }

    public void load() {
        states.clear();
        if (!stateFile.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(stateFile);
        for (String key : yaml.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                ConfigurationSection section = yaml.getConfigurationSection(key);
                if (section == null) continue;
                PlayerCommissionState state = new PlayerCommissionState(uuid);
                state.setActiveCommissionId(section.getString("commission-id"));
                state.setProgress(section.getInt("progress", 0));
                states.put(uuid, state);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, PlayerCommissionState> entry : states.entrySet()) {
            PlayerCommissionState state = entry.getValue();
            if (state.getActiveCommissionId() == null) continue;
            String key = entry.getKey().toString();
            yaml.set(key + ".commission-id", state.getActiveCommissionId());
            yaml.set(key + ".progress", state.getProgress());
        }
        try {
            yaml.save(stateFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save player-commissions.yml: " + e.getMessage());
        }
    }

    public PlayerCommissionState getState(UUID uuid) {
        return states.get(uuid);
    }

    public void setState(UUID uuid, PlayerCommissionState state) {
        states.put(uuid, state);
        save();
    }

    public void clearState(UUID uuid) {
        states.remove(uuid);
        save();
    }
}
