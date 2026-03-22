package com.ironkeep;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages the Daily Quest system.
 *
 * The default quest: type "Hi" in chat once per reset cycle.
 * Reward: 50 Gold Coins (with escape bonus applied) + 50 Shards.
 *
 * Reset schedule: midnight UTC daily (configurable).
 * State persists in data/daily-quest.yml (UUID → last completion epoch seconds).
 */
public class DailyQuestManager {

    private final IronKeepPlugin plugin;
    private final File stateFile;
    private final Map<UUID, Long> lastCompletion = new HashMap<>(); // UUID → epoch seconds of last completion

    // Config values (loaded from daily-quests.yml)
    private double goldReward = 50.0;
    private double shardsReward = 50.0;
    private String questObjective = "Say \"Hi\" in chat";
    private String resetSchedule = "MIDNIGHT_UTC"; // only supported value for now

    public DailyQuestManager(IronKeepPlugin plugin) {
        this.plugin = plugin;
        this.stateFile = new File(plugin.getDataFolder(), "data/daily-quest.yml");
    }

    public void load() {
        loadConfig();
        loadState();
    }

    private void loadConfig() {
        File configFile = new File(plugin.getDataFolder(), "daily-quests.yml");
        if (!configFile.exists()) plugin.saveResource("daily-quests.yml", false);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(configFile);
        goldReward = yaml.getDouble("quest.gold-reward", 50.0);
        shardsReward = yaml.getDouble("quest.shards-reward", 50.0);
        questObjective = yaml.getString("quest.objective", "Say \"Hi\" in chat");
        resetSchedule = yaml.getString("reset-schedule", "MIDNIGHT_UTC");
    }

    private void loadState() {
        lastCompletion.clear();
        if (!stateFile.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(stateFile);
        for (String key : yaml.getKeys(false)) {
            try {
                lastCompletion.put(UUID.fromString(key), yaml.getLong(key));
            } catch (IllegalArgumentException ignored) {}
        }
    }

    public void saveState() {
        stateFile.getParentFile().mkdirs();
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, Long> entry : lastCompletion.entrySet()) {
            yaml.set(entry.getKey().toString(), entry.getValue());
        }
        try {
            yaml.save(stateFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save daily-quest.yml: " + e.getMessage());
        }
    }

    /**
     * Returns the epoch second of the current reset cycle's start (last midnight UTC).
     */
    private long currentCycleStart() {
        return LocalDate.now(ZoneOffset.UTC)
                .atStartOfDay(ZoneOffset.UTC)
                .toEpochSecond();
    }

    /** Returns true if the player has already completed the quest this cycle. */
    public boolean hasCompleted(UUID uuid) {
        Long ts = lastCompletion.get(uuid);
        if (ts == null) return false;
        return ts >= currentCycleStart();
    }

    /** Marks the quest as completed for this cycle. */
    public void markCompleted(UUID uuid) {
        lastCompletion.put(uuid, Instant.now().getEpochSecond());
        saveState();
    }

    /** Returns the next reset time as an epoch second (next midnight UTC). */
    public long nextResetEpoch() {
        return LocalDate.now(ZoneOffset.UTC)
                .plusDays(1)
                .atStartOfDay(ZoneOffset.UTC)
                .toEpochSecond();
    }

    public double getGoldReward() { return goldReward; }
    public double getShardsReward() { return shardsReward; }
    public String getQuestObjective() { return questObjective; }
}
