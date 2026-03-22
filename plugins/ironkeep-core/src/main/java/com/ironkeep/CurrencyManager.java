package com.ironkeep;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages both virtual currencies for Iron Keep:
 *
 *  - Gold Coins: NON-TRADEABLE. Flow only between server and player (e.g. commission rewards).
 *                Stored in balances.yml under key "gold-coins.<uuid>".
 *
 *  - Shards: TRADEABLE. Can flow between players (e.g. /pay) as well as server↔player.
 *            Stored in balances.yml under key "shards.<uuid>".
 *
 * Enforcement of the tradeable/non-tradeable distinction happens at the command layer, not here.
 */
public class CurrencyManager {

    private final IronKeepPlugin plugin;
    private final File balancesFile;

    // Gold Coins — non-tradeable (server↔player only)
    private final Map<UUID, Double> goldCoins = new HashMap<>();

    // Shards — tradeable (player↔player and server↔player)
    private final Map<UUID, Double> shards = new HashMap<>();

    public CurrencyManager(IronKeepPlugin plugin) {
        this.plugin = plugin;
        this.balancesFile = new File(plugin.getDataFolder(), "balances.yml");
    }

    public void load() {
        goldCoins.clear();
        shards.clear();
        if (!balancesFile.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(balancesFile);

        // Load Gold Coins — supports both old flat format (uuid: value) and new nested format
        if (yaml.isConfigurationSection("gold-coins")) {
            for (String key : yaml.getConfigurationSection("gold-coins").getKeys(false)) {
                try {
                    goldCoins.put(UUID.fromString(key),
                            yaml.getDouble("gold-coins." + key, 0.0));
                } catch (IllegalArgumentException ignored) {}
            }
        } else {
            // Backward-compat: old balances.yml had flat uuid -> double
            for (String key : yaml.getKeys(false)) {
                if (key.equals("gold-coins") || key.equals("shards")) continue;
                try {
                    goldCoins.put(UUID.fromString(key), yaml.getDouble(key, 0.0));
                } catch (IllegalArgumentException ignored) {}
            }
        }

        // Load Shards (new field — defaults to 0 if absent)
        if (yaml.isConfigurationSection("shards")) {
            for (String key : yaml.getConfigurationSection("shards").getKeys(false)) {
                try {
                    shards.put(UUID.fromString(key),
                            yaml.getDouble("shards." + key, 0.0));
                } catch (IllegalArgumentException ignored) {}
            }
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, Double> entry : goldCoins.entrySet()) {
            yaml.set("gold-coins." + entry.getKey().toString(), entry.getValue());
        }
        for (Map.Entry<UUID, Double> entry : shards.entrySet()) {
            yaml.set("shards." + entry.getKey().toString(), entry.getValue());
        }
        try {
            yaml.save(balancesFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save balances.yml: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Gold Coins API (non-tradeable)
    // -------------------------------------------------------------------------

    public double getBalance(UUID uuid) {
        return goldCoins.getOrDefault(uuid, 0.0);
    }

    public void addBalance(UUID uuid, double amount) {
        goldCoins.put(uuid, getBalance(uuid) + amount);
        save();
    }

    /** Sets Gold Coins balance to an exact value (minimum 0). */
    public void setBalance(UUID uuid, double amount) {
        goldCoins.put(uuid, Math.max(0.0, amount));
        save();
    }

    // -------------------------------------------------------------------------
    // Shards API (tradeable)
    // -------------------------------------------------------------------------

    /** Returns the player's current Shards balance (defaults to 0). */
    public double getShards(UUID uuid) {
        return shards.getOrDefault(uuid, 0.0);
    }

    /** Adds the specified amount to the player's Shards balance. */
    public void addShards(UUID uuid, double amount) {
        shards.put(uuid, getShards(uuid) + amount);
        save();
    }

    /**
     * Removes the specified amount from the player's Shards balance.
     * Returns true on success, false if the player has insufficient Shards.
     * Never allows a negative balance.
     */
    public boolean removeShards(UUID uuid, double amount) {
        double current = getShards(uuid);
        if (current < amount) return false;
        shards.put(uuid, current - amount);
        save();
        return true;
    }

    /** Sets the player's Shards balance to an exact value. */
    public void setShards(UUID uuid, double amount) {
        shards.put(uuid, Math.max(0.0, amount));
        save();
    }

    /** Returns true if the player has at least the specified amount of Shards. */
    public boolean hasShards(UUID uuid, double amount) {
        return getShards(uuid) >= amount;
    }
}
