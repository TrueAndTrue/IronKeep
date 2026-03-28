package com.ironkeep;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeSet;
import java.util.UUID;

public class MailRoomManager {

    private final IronKeepPlugin plugin;
    private final NamespacedKey mailDestinationKey;

    // Active mail sorting sessions per player
    private final Map<UUID, MailSortingState> activeStates = new HashMap<>();

    // Config values
    private double maxGoldBonus = 40.0;
    private double maxShardsBonus = 40.0;

    // Rank → mail-count
    private final Map<Integer, Integer> mailCounts = new HashMap<>();
    // Rank → list of destination names
    private final Map<Integer, List<String>> rankDestinations = new HashMap<>();

    // Barrel location string ("world,x,y,z") → destination label
    private final Map<String, String> barrelDestinations = new HashMap<>();
    // Barrel location string ("world,x,y,z") → facing direction (e.g. "NORTH", "SOUTH")
    private final Map<String, String> barrelFacings = new HashMap<>();

    public MailRoomManager(IronKeepPlugin plugin) {
        this.plugin = plugin;
        this.mailDestinationKey = new NamespacedKey(plugin, "mail_destination");
    }

    public void load() {
        mailCounts.clear();
        rankDestinations.clear();
        barrelDestinations.clear();
        barrelFacings.clear();

        File file = new File(plugin.getDataFolder(), "mail-room.yml");
        if (!file.exists()) plugin.saveResource("mail-room.yml", false);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);

        // Accuracy bonus
        ConfigurationSection bonusSection = yaml.getConfigurationSection("accuracy-bonus");
        if (bonusSection != null) {
            maxGoldBonus = bonusSection.getDouble("max-gold-coins", 40.0);
            maxShardsBonus = bonusSection.getDouble("max-shards", 40.0);
        }

        // Difficulty per rank
        ConfigurationSection diffSection = yaml.getConfigurationSection("difficulty");
        if (diffSection != null) {
            for (String rankKey : diffSection.getKeys(false)) {
                ConfigurationSection rankEntry = diffSection.getConfigurationSection(rankKey);
                if (rankEntry == null) continue;
                // Parse rank number from "rank1", "rank2", etc.
                String numStr = rankKey.replaceAll("[^0-9]", "");
                if (numStr.isEmpty()) continue;
                int rankNum = Integer.parseInt(numStr);
                int count = rankEntry.getInt("mail-count", 4);
                List<String> dests = rankEntry.getStringList("destinations");
                mailCounts.put(rankNum, count);
                rankDestinations.put(rankNum, new ArrayList<>(dests));
            }
        }

        // Barrel locations from mail-room.yml
        loadBarrelList(yaml.getList("barrels"));

        // Barrel bindings from barrel-bindings.yml (overrides/adds to mail-room.yml entries)
        File bindingsFile = new File(plugin.getDataFolder(), "barrel-bindings.yml");
        if (bindingsFile.exists()) {
            YamlConfiguration bindingsYaml = YamlConfiguration.loadConfiguration(bindingsFile);
            loadBarrelList(bindingsYaml.getList("barrels"));
        }

        plugin.getLogger().info("MailRoomManager: loaded "
                + rankDestinations.size() + " rank difficulties, "
                + barrelDestinations.size() + " barrel(s).");
    }

    private void loadBarrelList(List<?> barrelList) {
        if (barrelList == null) return;
        for (Object obj : barrelList) {
            if (!(obj instanceof Map<?, ?> map)) continue;
            String destination = String.valueOf(map.get("destination"));
            String world = String.valueOf(map.get("world"));
            int x = toInt(map.get("x"));
            int y = toInt(map.get("y"));
            int z = toInt(map.get("z"));
            String key = barrelKey(world, x, y, z);
            barrelDestinations.put(key, destination);
            if (map.containsKey("facing")) {
                barrelFacings.put(key, String.valueOf(map.get("facing")).toUpperCase());
            }
        }
    }

    private int toInt(Object obj) {
        if (obj instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(obj)); } catch (NumberFormatException e) { return 0; }
    }

    private String barrelKey(String world, int x, int y, int z) {
        return world + "," + x + "," + y + "," + z;
    }

    // -------------------------------------------------------------------------
    // Mail assignment
    // -------------------------------------------------------------------------

    /**
     * Generates mail items for the player based on their rank, gives them the items,
     * and creates a new MailSortingState session.
     */
    public void assignMail(Player player, int rankNumber) {
        List<String> destinations = rankDestinations.getOrDefault(rankNumber,
                rankDestinations.getOrDefault(1, List.of("Armory", "Kitchen", "Warden Office", "Infirmary")));
        int mailCount = mailCounts.getOrDefault(rankNumber, mailCounts.getOrDefault(1, 4));

        // Build a random selection of destinations (shuffled pool, may repeat if mail-count > dest count)
        List<String> pool = new ArrayList<>(destinations);
        Collections.shuffle(pool, new Random());
        List<String> selected = new ArrayList<>();
        for (int i = 0; i < mailCount; i++) {
            selected.add(pool.get(i % pool.size()));
        }

        // Give mail items to the player
        for (String dest : selected) {
            ItemStack mail = new ItemStack(Material.PAPER);
            ItemMeta meta = mail.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.AQUA + "Mail: " + dest);
                meta.getPersistentDataContainer().set(mailDestinationKey, PersistentDataType.STRING, dest);
                mail.setItemMeta(meta);
            }
            player.getInventory().addItem(mail);
        }

        activeStates.put(player.getUniqueId(), new MailSortingState(mailCount));

        player.sendMessage(ChatColor.AQUA + "[Mail Room] " + ChatColor.WHITE
                + "You received " + mailCount + " mail item(s). Deliver each to the correct labeled barrel.");
    }

    // -------------------------------------------------------------------------
    // Delivery handling
    // -------------------------------------------------------------------------

    /**
     * Called when a player right-clicks a barrel while holding a mail item.
     * Checks whether the mail matches the barrel destination, consumes the item,
     * and updates the session state.
     */
    public void handleDelivery(Player player, Block barrel, ItemStack heldItem) {
        UUID uuid = player.getUniqueId();
        MailSortingState state = activeStates.get(uuid);
        if (state == null) return;

        String mailDest = getMailDestination(heldItem);
        String barrelDest = getBarrelDestination(barrel);
        if (mailDest == null || barrelDest == null) return;

        // Consume one mail item from the held slot
        if (heldItem.getAmount() > 1) {
            heldItem.setAmount(heldItem.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }

        state.incrementDelivered();

        if (mailDest.equalsIgnoreCase(barrelDest)) {
            state.incrementCorrect();
            player.sendMessage(ChatColor.GREEN + "✓ Correctly delivered to " + barrelDest + "!");
        } else {
            player.sendMessage(ChatColor.RED + "✗ Wrong barrel! That mail was for " + mailDest
                    + " but you delivered to " + barrelDest + ".");
        }

        int remaining = state.getTotalMail() - state.getDelivered();
        if (state.isComplete()) {
            player.sendMessage(ChatColor.GOLD + "All mail delivered! Use "
                    + ChatColor.YELLOW + "/commission complete"
                    + ChatColor.GOLD + " to collect your reward.");
        } else {
            player.sendMessage(ChatColor.GRAY + "" + remaining + " mail item(s) remaining.");
        }

        // Also increment commission manager progress so the standard check works
        plugin.getCommissionManager().incrementProgress(uuid, 1);
    }

    // -------------------------------------------------------------------------
    // Item / block helpers
    // -------------------------------------------------------------------------

    public boolean isMailItem(ItemStack item) {
        if (item == null || item.getType() != Material.PAPER) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(mailDestinationKey, PersistentDataType.STRING);
    }

    public String getMailDestination(ItemStack item) {
        if (item == null) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(mailDestinationKey, PersistentDataType.STRING);
    }

    public boolean isBarrel(Block block) {
        if (block == null || block.getType() != Material.BARREL) return false;
        String worldName = block.getWorld().getName();
        String key = barrelKey(worldName, block.getX(), block.getY(), block.getZ());
        return barrelDestinations.containsKey(key);
    }

    public String getBarrelDestination(Block block) {
        if (block == null) return null;
        String worldName = block.getWorld().getName();
        String key = barrelKey(worldName, block.getX(), block.getY(), block.getZ());
        return barrelDestinations.get(key);
    }

    // -------------------------------------------------------------------------
    // Session management
    // -------------------------------------------------------------------------

    public MailSortingState getState(UUID uuid) {
        return activeStates.get(uuid);
    }

    /**
     * Removes all mail items from the player's inventory and clears their session state.
     */
    public void clearMail(Player player) {
        UUID uuid = player.getUniqueId();
        // Remove mail items from inventory
        player.getInventory().setContents(
                java.util.Arrays.stream(player.getInventory().getContents())
                        .map(item -> isMailItem(item) ? null : item)
                        .toArray(ItemStack[]::new)
        );
        activeStates.remove(uuid);
    }

    // -------------------------------------------------------------------------
    // Bonus config accessors (used by CommissionManager)
    // -------------------------------------------------------------------------

    public double getMaxGoldBonus() { return maxGoldBonus; }
    public double getMaxShardsBonus() { return maxShardsBonus; }

    // -------------------------------------------------------------------------
    // Barrel binding (wand)
    // -------------------------------------------------------------------------

    public List<String> getAvailableDestinations() {
        TreeSet<String> set = new TreeSet<>();
        for (List<String> dests : rankDestinations.values()) {
            set.addAll(dests);
        }
        return new ArrayList<>(set);
    }

    public void bindBarrel(Block block, String destination) {
        String key = barrelKey(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
        barrelDestinations.put(key, destination);
        persistBarrels();
    }

    public void unbindBarrel(Block block) {
        String key = barrelKey(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
        barrelDestinations.remove(key);
        barrelFacings.remove(key);
        persistBarrels();
    }

    private void persistBarrels() {
        File file = new File(plugin.getDataFolder(), "barrel-bindings.yml");
        YamlConfiguration yaml = new YamlConfiguration();
        List<Map<String, Object>> barrelList = new ArrayList<>();
        for (Map.Entry<String, String> entry : barrelDestinations.entrySet()) {
            String[] parts = entry.getKey().split(",");
            if (parts.length != 4) continue;
            Map<String, Object> map = new HashMap<>();
            map.put("world", parts[0]);
            map.put("x", Integer.parseInt(parts[1]));
            map.put("y", Integer.parseInt(parts[2]));
            map.put("z", Integer.parseInt(parts[3]));
            map.put("destination", entry.getValue());
            String facing = barrelFacings.get(entry.getKey());
            if (facing != null) map.put("facing", facing);
            barrelList.add(map);
        }
        yaml.set("barrels", barrelList);
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("MailRoomManager: failed to save barrel-bindings.yml: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Barrel setup
    // -------------------------------------------------------------------------

    /**
     * Places BARREL blocks at all configured locations in the given world.
     * Requires OP permission — called from /mailroom setup.
     */
    public void setupBarrels(World world) {
        int placed = 0;
        for (Map.Entry<String, String> entry : barrelDestinations.entrySet()) {
            String[] parts = entry.getKey().split(",");
            if (parts.length != 4) continue;
            String worldName = parts[0];
            if (!world.getName().equals(worldName)) continue;
            try {
                int x = Integer.parseInt(parts[1]);
                int y = Integer.parseInt(parts[2]);
                int z = Integer.parseInt(parts[3]);
                org.bukkit.block.Block barrelBlock = world.getBlockAt(x, y, z);
                barrelBlock.setType(Material.BARREL);
                // Apply facing if configured
                String facingStr = barrelFacings.get(entry.getKey());
                if (facingStr != null) {
                    try {
                        org.bukkit.block.data.type.Barrel barrelData =
                                (org.bukkit.block.data.type.Barrel) barrelBlock.getBlockData();
                        barrelData.setFacing(org.bukkit.block.BlockFace.valueOf(facingStr));
                        barrelBlock.setBlockData(barrelData);
                    } catch (Exception ignored) {}
                }
                placed++;
            } catch (NumberFormatException ignored) {}
        }
        plugin.getLogger().info("MailRoomManager: placed " + placed + " barrel(s) in world '" + world.getName() + "'.");
    }
}
