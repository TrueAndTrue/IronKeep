package com.ironkeep;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Manages player ranks and rank definitions.
 *
 * Ranks are defined in ranks.yml. Player rank data persists in data/player-ranks.yml.
 */
public class RankManager {

    private final IronKeepPlugin plugin;
    private final File ranksFile;
    private final File playerRanksFile;

    private final Map<Integer, RankDefinition> rankDefinitions = new LinkedHashMap<>();
    private final Map<UUID, Integer> playerRanks = new HashMap<>();

    private int maxRank = 1;

    public RankManager(IronKeepPlugin plugin) {
        this.plugin = plugin;
        this.ranksFile = new File(plugin.getDataFolder(), "ranks.yml");
        this.playerRanksFile = new File(plugin.getDataFolder(), "data/player-ranks.yml");
    }

    public void load() {
        loadDefinitions();
        loadPlayerRanks();
    }

    private void loadDefinitions() {
        rankDefinitions.clear();
        if (!ranksFile.exists()) {
            plugin.saveResource("ranks.yml", false);
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(ranksFile);
        ConfigurationSection section = yaml.getConfigurationSection("ranks");
        if (section == null) {
            plugin.getLogger().severe("ranks.yml has no 'ranks' section!");
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(key);
            if (entry == null) continue;
            int rank = entry.getInt("rank");
            String displayName = entry.getString("display-name", "Rank " + rank);
            double cost = entry.getDouble("cost", 0);
            List<String> types = entry.getStringList("unlocked-types");
            rankDefinitions.put(rank, new RankDefinition(rank, displayName, cost, types));
        }
        maxRank = rankDefinitions.keySet().stream().mapToInt(i -> i).max().orElse(1);
        plugin.getLogger().info("RankManager: loaded " + rankDefinitions.size() + " rank definitions (max: " + maxRank + ")");
    }

    private void loadPlayerRanks() {
        playerRanks.clear();
        if (!playerRanksFile.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(playerRanksFile);
        for (String key : yaml.getKeys(false)) {
            try {
                playerRanks.put(UUID.fromString(key), yaml.getInt(key, 1));
            } catch (IllegalArgumentException ignored) {}
        }
    }

    public void savePlayerRanks() {
        playerRanksFile.getParentFile().mkdirs();
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, Integer> entry : playerRanks.entrySet()) {
            yaml.set(entry.getKey().toString(), entry.getValue());
        }
        try {
            yaml.save(playerRanksFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save player-ranks.yml: " + e.getMessage());
        }
    }

    /** Returns the player's current rank number (defaults to 1). */
    public int getPlayerRank(UUID uuid) {
        return playerRanks.getOrDefault(uuid, 1);
    }

    /** Sets and persists the player's rank. */
    public void setPlayerRank(UUID uuid, int rank) {
        playerRanks.put(uuid, rank);
        savePlayerRanks();
    }

    /** Returns the RankDefinition for the given rank number, or null. */
    public RankDefinition getDefinition(int rank) {
        return rankDefinitions.get(rank);
    }

    /** Returns the RankDefinition for the given player's current rank. */
    public RankDefinition getPlayerRankDefinition(UUID uuid) {
        return getDefinition(getPlayerRank(uuid));
    }

    public int getMaxRank() { return maxRank; }

    /**
     * Returns all commission types unlocked at or below the player's current rank (cumulative).
     * e.g. Rank 2 unlocks WOODCUTTING but Rank 1 unlocked MINING, so Rank 2 players get both.
     */
    public Set<String> getCumulativeUnlockedTypes(UUID uuid) {
        int playerRank = getPlayerRank(uuid);
        Set<String> types = new HashSet<>();
        for (int r = 1; r <= playerRank; r++) {
            RankDefinition def = getDefinition(r);
            if (def != null) types.addAll(def.getUnlockedTypes());
        }
        return types;
    }

    /** Returns true if the player's rank (cumulatively) allows them to take a commission of this type. */
    public boolean canAccept(UUID uuid, String commissionType) {
        return getCumulativeUnlockedTypes(uuid).stream()
                .anyMatch(t -> t.equalsIgnoreCase(commissionType));
    }

    /** Returns a list of commissions the player can see based on their cumulative rank unlocks. */
    public List<CommissionDefinition> getAccessibleCommissions(UUID uuid) {
        Set<String> unlocked = getCumulativeUnlockedTypes(uuid);
        List<CommissionDefinition> accessible = new ArrayList<>();
        for (CommissionDefinition comm : plugin.getCommissionRegistry().getAll().values()) {
            if (unlocked.stream().anyMatch(t -> t.equalsIgnoreCase(comm.getType()))) {
                accessible.add(comm);
            }
        }
        return accessible;
    }
}
