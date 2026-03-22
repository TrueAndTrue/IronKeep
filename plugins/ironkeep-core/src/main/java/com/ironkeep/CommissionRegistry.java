package com.ironkeep;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class CommissionRegistry {

    private final IronKeepPlugin plugin;
    private final Map<String, CommissionDefinition> definitions = new LinkedHashMap<>();
    private final Random random = new Random();

    public CommissionRegistry(IronKeepPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        definitions.clear();
        File file = new File(plugin.getDataFolder(), "commissions.yml");
        if (!file.exists()) {
            plugin.getLogger().info("commissions.yml not found — extracting default from jar.");
            plugin.saveResource("commissions.yml", false);
        }
        if (!file.exists()) {
            plugin.getLogger().severe("commissions.yml could not be created! No commissions will be loaded.");
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("commissions");
        if (section == null) {
            plugin.getLogger().warning("commissions.yml has no 'commissions' section — no commissions loaded.");
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(id);
            if (entry == null) {
                plugin.getLogger().warning("Skipping malformed commission entry: '" + id + "'");
                continue;
            }
            String type = entry.getString("type");
            String displayName = entry.getString("display-name");
            String description = entry.getString("description");
            String objectiveItem = entry.getString("objective-item");
            String turnInItem = entry.getString("turn-in-item", null); // optional, falls back to objective-item
            int objectiveQuantity = entry.getInt("objective-quantity", -1);
            double rewardAmount = entry.getDouble("reward-amount", -1);
            if (type == null || displayName == null || description == null || objectiveItem == null
                    || objectiveQuantity < 1 || rewardAmount < 0) {
                plugin.getLogger().warning("Skipping commission '" + id + "': missing or invalid fields.");
                continue;
            }
            definitions.put(id, new CommissionDefinition(id, type, displayName, description,
                    objectiveItem, turnInItem, objectiveQuantity, rewardAmount));
        }
        plugin.getLogger().info("Loaded " + definitions.size() + " commission definitions.");
    }

    public Map<String, CommissionDefinition> getAll() {
        return Collections.unmodifiableMap(definitions);
    }

    public CommissionDefinition getById(String id) {
        return definitions.get(id);
    }

    public CommissionDefinition getRandom() {
        if (definitions.isEmpty()) return null;
        List<CommissionDefinition> list = new ArrayList<>(definitions.values());
        return list.get(random.nextInt(list.size()));
    }
}
