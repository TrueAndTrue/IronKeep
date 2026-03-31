package com.ironkeep;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SkillManager {

    private final IronKeepPlugin plugin;
    private final File skillsConfigFile;
    private final File dataFile;

    private int levelCap = 50;
    private double xpCurveBase = 200.0;
    private double goldBonusPerLevel = 2.0;
    private double shardBonusPerLevel = 1.0;
    private int reductionEveryLevels = 10;

    private final Map<String, Double> xpPerCompletion = new HashMap<>();

    // playerUUID -> (commissionType -> PlayerSkillData)
    private final Map<UUID, Map<String, PlayerSkillData>> skillData = new HashMap<>();

    public SkillManager(IronKeepPlugin plugin) {
        this.plugin = plugin;
        this.skillsConfigFile = new File(plugin.getDataFolder(), "skills.yml");
        this.dataFile = new File(plugin.getDataFolder(), "data/skill-levels.yml");
    }

    public void load() {
        loadConfig();
        loadData();
    }

    private void loadConfig() {
        plugin.saveResource("skills.yml", true);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(skillsConfigFile);
        levelCap = yaml.getInt("level-cap", 50);
        xpCurveBase = yaml.getDouble("xp-curve-base", 200.0);
        goldBonusPerLevel = yaml.getDouble("gold-bonus-per-level", 2.0);
        shardBonusPerLevel = yaml.getDouble("shard-bonus-per-level", 1.0);
        reductionEveryLevels = yaml.getInt("objective-reduction-every-levels", 10);

        xpPerCompletion.clear();
        ConfigurationSection xpSection = yaml.getConfigurationSection("xp-per-completion");
        if (xpSection != null) {
            for (String key : xpSection.getKeys(false)) {
                xpPerCompletion.put(key.toUpperCase(), xpSection.getDouble(key));
            }
        }
        plugin.getLogger().info("SkillManager: loaded config (cap=" + levelCap + ", types=" + xpPerCompletion.size() + ")");
    }

    private void loadData() {
        skillData.clear();
        if (!dataFile.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dataFile);
        for (String uuidStr : yaml.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                ConfigurationSection playerSection = yaml.getConfigurationSection(uuidStr);
                if (playerSection == null) continue;
                Map<String, PlayerSkillData> playerSkills = new HashMap<>();
                for (String type : playerSection.getKeys(false)) {
                    ConfigurationSection typeSection = playerSection.getConfigurationSection(type);
                    if (typeSection == null) continue;
                    int level = typeSection.getInt("level", 1);
                    double xp = typeSection.getDouble("xp", 0.0);
                    playerSkills.put(type.toUpperCase(), new PlayerSkillData(type.toUpperCase(), level, xp));
                }
                skillData.put(uuid, playerSkills);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    public void save() {
        dataFile.getParentFile().mkdirs();
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, Map<String, PlayerSkillData>> playerEntry : skillData.entrySet()) {
            String uuidStr = playerEntry.getKey().toString();
            for (Map.Entry<String, PlayerSkillData> skillEntry : playerEntry.getValue().entrySet()) {
                String type = skillEntry.getKey();
                PlayerSkillData data = skillEntry.getValue();
                yaml.set(uuidStr + "." + type + ".level", data.getLevel());
                yaml.set(uuidStr + "." + type + ".xp", data.getXp());
            }
        }
        try {
            yaml.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save skill-levels.yml: " + e.getMessage());
        }
    }

    public void resetSkills(UUID uuid) {
        skillData.remove(uuid);
        save();
    }

    public PlayerSkillData getSkillData(UUID uuid, String type) {
        type = type.toUpperCase();
        skillData.computeIfAbsent(uuid, k -> new HashMap<>());
        return skillData.get(uuid).computeIfAbsent(type, t -> new PlayerSkillData(t, 1, 0.0));
    }

    public Map<String, PlayerSkillData> getAllSkillData(UUID uuid) {
        return skillData.computeIfAbsent(uuid, k -> new HashMap<>());
    }

    /**
     * Grants XP to a player for a commission type.
     * @return true if the player leveled up
     */
    public boolean grantXp(UUID uuid, String type, double amount) {
        type = type.toUpperCase();
        PlayerSkillData data = getSkillData(uuid, type);
        if (data.getLevel() >= levelCap) {
            return false; // already max level, no more XP
        }

        data.setXp(data.getXp() + amount);
        boolean leveledUp = false;

        while (data.getLevel() < levelCap && data.getXp() >= xpRequired(data.getLevel())) {
            double required = xpRequired(data.getLevel());
            data.setXp(data.getXp() - required);
            data.setLevel(data.getLevel() + 1);
            leveledUp = true;

            // Send level-up message if player is online
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                String formattedType = formatTypeName(type);
                player.sendMessage(
                    ChatColor.GOLD + "[Skills] " +
                    ChatColor.GREEN + "Your " + formattedType + " skill reached level " + data.getLevel() + "!"
                );
            }
        }

        // Cap XP at max level
        if (data.getLevel() >= levelCap) {
            data.setXp(0.0);
        }

        save();
        return leveledUp;
    }

    public double getXpForType(String type) {
        return xpPerCompletion.getOrDefault(type.toUpperCase(), 0.0);
    }

    public double xpRequired(int level) {
        return xpCurveBase * Math.pow(level, 1.5);
    }

    /** Returns fractional gold bonus (e.g. 0.04 for +4%). */
    public double getGoldBonus(UUID uuid, String type) {
        PlayerSkillData data = getSkillData(uuid, type);
        return (data.getLevel() - 1) * goldBonusPerLevel / 100.0;
    }

    /** Returns fractional shards bonus (e.g. 0.02 for +2%). */
    public double getShardsBonus(UUID uuid, String type) {
        PlayerSkillData data = getSkillData(uuid, type);
        return (data.getLevel() - 1) * shardBonusPerLevel / 100.0;
    }

    /** Returns objective quantity reduction (integer). */
    public int getObjectiveReduction(UUID uuid, String type) {
        PlayerSkillData data = getSkillData(uuid, type);
        return (data.getLevel() - 1) / reductionEveryLevels;
    }

    public int getLevelCap() { return levelCap; }
    public double getXpCurveBase() { return xpCurveBase; }
    public double getGoldBonusPerLevel() { return goldBonusPerLevel; }
    public double getShardBonusPerLevel() { return shardBonusPerLevel; }
    public int getReductionEveryLevels() { return reductionEveryLevels; }

    private String formatTypeName(String type) {
        String lower = type.replace('_', ' ').toLowerCase();
        StringBuilder sb = new StringBuilder();
        for (String word : lower.split(" ")) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(" ");
            }
        }
        return sb.toString().trim();
    }
}
