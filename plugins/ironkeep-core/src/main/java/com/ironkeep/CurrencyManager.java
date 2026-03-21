package com.ironkeep;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CurrencyManager {

    private final IronKeepPlugin plugin;
    private final File balancesFile;
    private final Map<UUID, Double> balances = new HashMap<>();

    public CurrencyManager(IronKeepPlugin plugin) {
        this.plugin = plugin;
        this.balancesFile = new File(plugin.getDataFolder(), "balances.yml");
    }

    public void load() {
        balances.clear();
        if (!balancesFile.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(balancesFile);
        for (String key : yaml.getKeys(false)) {
            try {
                balances.put(UUID.fromString(key), yaml.getDouble(key));
            } catch (IllegalArgumentException ignored) {}
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, Double> entry : balances.entrySet()) {
            yaml.set(entry.getKey().toString(), entry.getValue());
        }
        try {
            yaml.save(balancesFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save balances.yml: " + e.getMessage());
        }
    }

    public double getBalance(UUID uuid) {
        return balances.getOrDefault(uuid, 0.0);
    }

    public void addBalance(UUID uuid, double amount) {
        balances.put(uuid, getBalance(uuid) + amount);
        save();
    }
}
