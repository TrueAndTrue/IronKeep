package com.ironkeep;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

/**
 * Tracks first-of-the-day commission completion per type and grants bonus rewards.
 *
 * Each distinct commission type (e.g. MINING_COAL, WOODCUTTING, COOKING) has its own
 * daily bonus. A player earns the bonus the first time they complete a commission of
 * that type each day. Resets at midnight UTC.
 *
 * Config: loaded from main config.yml under "daily-commission-bonus".
 * State: persisted in data/daily-commission-bonus.yml.
 */
public class DailyBonusManager {

    private final IronKeepPlugin plugin;
    private final File stateFile;

    private int goldBonus = 50;
    private int xpBonus = 50;

    // playerUUID -> set of commission types claimed today (with epoch timestamp)
    private final Map<UUID, Map<String, Long>> claimedBonuses = new HashMap<>();

    public DailyBonusManager(IronKeepPlugin plugin) {
        this.plugin = plugin;
        this.stateFile = new File(plugin.getDataFolder(), "data/daily-commission-bonus.yml");
    }

    public void load() {
        loadConfig();
        loadState();
    }

    private void loadConfig() {
        goldBonus = plugin.getConfig().getInt("daily-commission-bonus.gold-bonus", 50);
        xpBonus = plugin.getConfig().getInt("daily-commission-bonus.xp-bonus", 50);
        plugin.getLogger().info("DailyBonusManager: loaded config (gold=" + goldBonus + ", xp=" + xpBonus + ")");
    }

    private void loadState() {
        claimedBonuses.clear();
        if (!stateFile.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(stateFile);
        for (String uuidStr : yaml.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                ConfigurationSection section = yaml.getConfigurationSection(uuidStr);
                if (section == null) continue;
                Map<String, Long> types = new HashMap<>();
                for (String type : section.getKeys(false)) {
                    types.put(type.toUpperCase(), section.getLong(type));
                }
                claimedBonuses.put(uuid, types);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    private void saveState() {
        stateFile.getParentFile().mkdirs();
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, Map<String, Long>> entry : claimedBonuses.entrySet()) {
            String uuidStr = entry.getKey().toString();
            for (Map.Entry<String, Long> typeEntry : entry.getValue().entrySet()) {
                yaml.set(uuidStr + "." + typeEntry.getKey(), typeEntry.getValue());
            }
        }
        try {
            yaml.save(stateFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save daily-commission-bonus.yml: " + e.getMessage());
        }
    }

    private long currentCycleStart() {
        return LocalDate.now(ZoneOffset.UTC)
                .atStartOfDay(ZoneOffset.UTC)
                .toEpochSecond();
    }

    /**
     * Returns true if the player has NOT yet claimed the daily bonus for this commission type today.
     */
    public boolean isBonusAvailable(UUID uuid, String commissionType) {
        commissionType = commissionType.toUpperCase();
        Map<String, Long> claimed = claimedBonuses.get(uuid);
        if (claimed == null) return true;
        Long ts = claimed.get(commissionType);
        if (ts == null) return true;
        return ts < currentCycleStart(); // claimed before today's reset
    }

    /**
     * Marks the daily bonus as claimed for this commission type. Call after granting the bonus.
     */
    public void markClaimed(UUID uuid, String commissionType) {
        commissionType = commissionType.toUpperCase();
        claimedBonuses.computeIfAbsent(uuid, k -> new HashMap<>())
                .put(commissionType, Instant.now().getEpochSecond());
        saveState();
    }

    public int getGoldBonus() { return goldBonus; }
    public int getXpBonus() { return xpBonus; }
}
