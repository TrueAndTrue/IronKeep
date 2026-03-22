package com.ironkeep;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages the Escape (Prestige) system.
 *
 * Players who reach Rank 4 can /escape to reset rank to 1 in exchange for a
 * permanent Gold Coin income bonus. Shards are NOT affected by escaping.
 *
 * Escape levels are defined in escapes.yml.
 * Player escape data persists in data/player-escapes.yml.
 */
public class EscapeManager {

    private final IronKeepPlugin plugin;
    private final File escapesFile;
    private final File playerEscapesFile;

    /** escape level → bonus multiplier (e.g. 1.10 for 10% bonus) */
    private final Map<Integer, Double> bonusMultipliers = new HashMap<>();
    /** escape level → cost in Gold Coins */
    private final Map<Integer, Double> escapeCosts = new HashMap<>();

    private final Map<UUID, Integer> playerEscapes = new HashMap<>();
    private int maxEscapeLevel = 0;

    public EscapeManager(IronKeepPlugin plugin) {
        this.plugin = plugin;
        this.escapesFile = new File(plugin.getDataFolder(), "escapes.yml");
        this.playerEscapesFile = new File(plugin.getDataFolder(), "data/player-escapes.yml");
    }

    public void load() {
        loadDefinitions();
        loadPlayerEscapes();
    }

    private void loadDefinitions() {
        bonusMultipliers.clear();
        escapeCosts.clear();
        if (!escapesFile.exists()) plugin.saveResource("escapes.yml", false);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(escapesFile);
        ConfigurationSection section = yaml.getConfigurationSection("escapes");
        if (section == null) {
            plugin.getLogger().severe("escapes.yml has no 'escapes' section!");
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(key);
            if (entry == null) continue;
            int level = entry.getInt("level");
            double cost = entry.getDouble("cost");
            double bonusPct = entry.getDouble("bonus-percent", 0);
            bonusMultipliers.put(level, 1.0 + bonusPct / 100.0);
            escapeCosts.put(level, cost);
            if (level > maxEscapeLevel) maxEscapeLevel = level;
        }
        plugin.getLogger().info("EscapeManager: loaded " + bonusMultipliers.size() + " escape levels.");
    }

    private void loadPlayerEscapes() {
        playerEscapes.clear();
        if (!playerEscapesFile.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(playerEscapesFile);
        for (String key : yaml.getKeys(false)) {
            try {
                playerEscapes.put(UUID.fromString(key), yaml.getInt(key, 0));
            } catch (IllegalArgumentException ignored) {}
        }
    }

    public void savePlayerEscapes() {
        playerEscapesFile.getParentFile().mkdirs();
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, Integer> entry : playerEscapes.entrySet()) {
            yaml.set(entry.getKey().toString(), entry.getValue());
        }
        try {
            yaml.save(playerEscapesFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save player-escapes.yml: " + e.getMessage());
        }
    }

    public int getEscapeLevel(UUID uuid) {
        return playerEscapes.getOrDefault(uuid, 0);
    }

    public void setEscapeLevel(UUID uuid, int level) {
        playerEscapes.put(uuid, level);
        savePlayerEscapes();
    }

    public int getMaxEscapeLevel() { return maxEscapeLevel; }

    public double getEscapeCost(int escapeLevel) {
        return escapeCosts.getOrDefault(escapeLevel, 0.0);
    }

    /**
     * Returns the Gold Coin multiplier for the given escape level.
     * E.g. Escape 1 = 1.10 (10% bonus). Escape 0 = 1.0 (no bonus).
     */
    public double getGoldCoinMultiplier(UUID uuid) {
        int level = getEscapeLevel(uuid);
        return bonusMultipliers.getOrDefault(level, 1.0);
    }

    /**
     * Applies the escape bonus to a Gold Coin amount.
     * Shards are never modified by this method.
     */
    public double applyBonus(UUID uuid, double goldCoins) {
        return goldCoins * getGoldCoinMultiplier(uuid);
    }
}
