package com.ironkeep;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class CommissionManager {

    private final IronKeepPlugin plugin;
    private final File commissionsFile;
    private final File balancesFile;

    // uuid -> active commission
    private final Map<UUID, Commission> activeCommissions = new HashMap<>();
    // uuid -> balance
    private final Map<UUID, Double> balances = new HashMap<>();

    private final List<Commission> pool = new ArrayList<>();
    private final Random random = new Random();

    public CommissionManager(IronKeepPlugin plugin) {
        this.plugin = plugin;
        this.commissionsFile = new File(plugin.getDataFolder(), "commissions.yml");
        this.balancesFile = new File(plugin.getDataFolder(), "balances.yml");
    }

    public void load() {
        loadPool();
        loadCommissions();
        loadBalances();
    }

    public void save() {
        saveCommissions();
        saveBalances();
    }

    private void loadPool() {
        pool.clear();
        List<?> list = plugin.getConfig().getList("commissions");
        if (list == null) return;
        for (Object obj : list) {
            if (!(obj instanceof Map<?, ?> map)) continue;
            String item = String.valueOf(map.get("item"));
            int quantity = ((Number) map.get("quantity")).intValue();
            double reward = ((Number) map.get("reward")).doubleValue();
            pool.add(new Commission(item, quantity, reward));
        }
    }

    private void loadCommissions() {
        activeCommissions.clear();
        if (!commissionsFile.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(commissionsFile);
        for (String key : yaml.getKeys(false)) {
            String value = yaml.getString(key);
            if (value == null) continue;
            String[] parts = value.split(":");
            if (parts.length != 2) continue;
            try {
                UUID uuid = UUID.fromString(key);
                String item = parts[0];
                int quantity = Integer.parseInt(parts[1]);
                // Find matching commission from pool to get reward
                double reward = 0.0;
                for (Commission c : pool) {
                    if (c.getItem().equals(item) && c.getQuantity() == quantity) {
                        reward = c.getReward();
                        break;
                    }
                }
                activeCommissions.put(uuid, new Commission(item, quantity, reward));
            } catch (IllegalArgumentException ignored) {}
        }
    }

    private void loadBalances() {
        balances.clear();
        if (!balancesFile.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(balancesFile);
        for (String key : yaml.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                balances.put(uuid, yaml.getDouble(key));
            } catch (IllegalArgumentException ignored) {}
        }
    }

    private void saveCommissions() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, Commission> entry : activeCommissions.entrySet()) {
            Commission c = entry.getValue();
            yaml.set(entry.getKey().toString(), c.getItem() + ":" + c.getQuantity());
        }
        try {
            yaml.save(commissionsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save commissions.yml: " + e.getMessage());
        }
    }

    private void saveBalances() {
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

    public boolean hasActiveCommission(UUID uuid) {
        return activeCommissions.containsKey(uuid);
    }

    public Commission getActiveCommission(UUID uuid) {
        return activeCommissions.get(uuid);
    }

    /**
     * Assigns a random commission from the pool. Returns null if pool is empty.
     */
    public Commission assignCommission(UUID uuid) {
        if (pool.isEmpty()) return null;
        Commission commission = pool.get(random.nextInt(pool.size()));
        activeCommissions.put(uuid, commission);
        saveCommissions();
        return commission;
    }

    public void clearCommission(UUID uuid) {
        activeCommissions.remove(uuid);
        saveCommissions();
    }

    public double getBalance(UUID uuid) {
        return balances.getOrDefault(uuid, 0.0);
    }

    public void addBalance(UUID uuid, double amount) {
        balances.put(uuid, getBalance(uuid) + amount);
        saveBalances();
    }
}
