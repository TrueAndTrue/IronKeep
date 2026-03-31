package com.ironkeep;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StarterKitConfig {

    private final IronKeepPlugin plugin;
    private List<StarterKitItem> items = new ArrayList<>();

    public StarterKitConfig(IronKeepPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "starter-kit.yml");
        plugin.saveResource("starter-kit.yml", true);

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        List<?> raw = yaml.getList("kit");

        if (raw == null || raw.isEmpty()) {
            plugin.getLogger().warning("starter-kit.yml: 'kit' list is empty — no items will be granted.");
            items = Collections.emptyList();
            return;
        }

        List<StarterKitItem> parsed = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            Object obj = raw.get(i);
            if (!(obj instanceof Map<?, ?> map)) {
                plugin.getLogger().warning("starter-kit.yml: entry " + i + " is not a map, skipping.");
                continue;
            }

            String material = getString(map, "item");
            if (material == null) {
                plugin.getLogger().warning("starter-kit.yml: entry " + i + " is missing 'item', skipping.");
                continue;
            }

            Object quantityObj = map.get("quantity");
            if (!(quantityObj instanceof Number)) {
                plugin.getLogger().warning("starter-kit.yml: entry " + i + " is missing or has invalid 'quantity', skipping.");
                continue;
            }
            int quantity = ((Number) quantityObj).intValue();

            String displayName = getString(map, "display-name");

            Map<String, Integer> enchantments = new HashMap<>();
            Object enchRaw = map.get("enchantments");
            if (enchRaw instanceof List<?> enchList) {
                for (Object enchObj : enchList) {
                    if (!(enchObj instanceof Map<?, ?> enchMap)) continue;
                    String enchKey = getString(enchMap, "enchantment");
                    Object levelObj = enchMap.get("level");
                    if (enchKey == null || !(levelObj instanceof Number)) {
                        plugin.getLogger().warning("starter-kit.yml: entry " + i + " has a malformed enchantment entry, skipping it.");
                        continue;
                    }
                    enchantments.put(enchKey.toLowerCase(), ((Number) levelObj).intValue());
                }
            }

            parsed.add(new StarterKitItem(material, quantity, enchantments, displayName));
        }

        items = parsed;
    }

    private static String getString(Map<?, ?> map, String key) {
        Object value = map.get(key);
        return value != null ? String.valueOf(value) : null;
    }

    public List<StarterKitItem> getItems() {
        return items;
    }
}
