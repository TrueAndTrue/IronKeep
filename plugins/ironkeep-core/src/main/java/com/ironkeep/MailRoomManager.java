package com.ironkeep;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
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
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

public class MailRoomManager {

    private final IronKeepPlugin plugin;
    private final NamespacedKey mailDestinationKey;
    private final Random random = new Random();

    // Active mail sorting sessions per player
    private final Map<UUID, MailSortingState> activeStates = new HashMap<>();

    // Tracks which barrel key the player was last looking at, so clearTitle() is only
    // called once when they look away (avoids spamming the packet every 4 ticks).
    private final Map<UUID, String> lastLookedBarrel = new HashMap<>();
    // Per-player look-at task IDs
    private final Map<UUID, Integer> lookTasks = new HashMap<>();

    // Config values
    private double maxGoldBonus = 120.0;
    private double maxShardsBonus = 70.0;

    // Rank → mail-count
    private final Map<Integer, Integer> mailCounts = new HashMap<>();
    // Rank → list of destination names
    private final Map<Integer, List<String>> rankDestinations = new HashMap<>();

    // Barrel location string ("world,x,y,z") → static destination label (used for isBarrel checks)
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
        plugin.saveResource("mail-room.yml", true);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);

        // Accuracy bonus
        ConfigurationSection bonusSection = yaml.getConfigurationSection("accuracy-bonus");
        if (bonusSection != null) {
            maxGoldBonus = bonusSection.getDouble("max-gold-coins", 120.0);
            maxShardsBonus = bonusSection.getDouble("max-shards", 70.0);
        }

        // Difficulty per rank
        ConfigurationSection diffSection = yaml.getConfigurationSection("difficulty");
        if (diffSection != null) {
            for (String rankKey : diffSection.getKeys(false)) {
                ConfigurationSection rankEntry = diffSection.getConfigurationSection(rankKey);
                if (rankEntry == null) continue;
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
     * and creates a new MailSortingState with a freshly randomized barrel→destination mapping.
     * Barrel positions stay fixed in the world; which destination each barrel represents
     * is shuffled every commission so players can't memorise the layout.
     */
    public void assignMail(Player player, int rankNumber) {
        List<String> destinations = rankDestinations.getOrDefault(rankNumber,
                rankDestinations.getOrDefault(1, List.of("Armory", "Kitchen", "Warden Office", "Infirmary")));
        int mailCount = mailCounts.getOrDefault(rankNumber, mailCounts.getOrDefault(1, 4));

        // ---- Build per-session randomized barrel → destination mapping ----
        String playerWorld = player.getWorld().getName();
        org.bukkit.World world = player.getWorld();
        List<String> availableBarrelKeys = barrelDestinations.keySet().stream()
                .filter(k -> k.startsWith(playerWorld + ","))
                .filter(k -> {
                    // Skip stale config entries where no actual BARREL block exists in the world
                    String[] parts = k.split(",");
                    if (parts.length != 4) return false;
                    try {
                        int bx = Integer.parseInt(parts[1]);
                        int by = Integer.parseInt(parts[2]);
                        int bz = Integer.parseInt(parts[3]);
                        return world.getBlockAt(bx, by, bz).getType() == Material.BARREL;
                    } catch (NumberFormatException e) { return false; }
                })
                .collect(Collectors.toList());
        Collections.shuffle(availableBarrelKeys, random);

        List<String> shuffledDests = new ArrayList<>(destinations);
        Collections.shuffle(shuffledDests, random);

        int mappingCount = Math.min(availableBarrelKeys.size(), shuffledDests.size());
        if (mappingCount == 0) {
            player.sendMessage(ChatColor.RED + "[Mail Room] No barrels configured. Contact an admin.");
            return;
        }

        Map<String, String> sessionMapping = new HashMap<>();
        List<String> activeDests = new ArrayList<>();
        for (int i = 0; i < mappingCount; i++) {
            sessionMapping.put(availableBarrelKeys.get(i), shuffledDests.get(i));
            activeDests.add(shuffledDests.get(i));
        }

        // ---- Build mail items using the active destinations ----
        List<String> selected = new ArrayList<>();
        for (int i = 0; i < mailCount; i++) {
            selected.add(activeDests.get(i % activeDests.size()));
        }

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

        MailSortingState state = new MailSortingState(mailCount);
        state.setBarrelMapping(sessionMapping);
        activeStates.put(player.getUniqueId(), state);

        startLookTask(player);

        player.sendMessage(ChatColor.AQUA + "[Mail Room] " + ChatColor.WHITE
                + "You received " + mailCount + " mail item(s). "
                + "Look at each barrel to see its destination, then deliver the right mail.");
    }

    // -------------------------------------------------------------------------
    // Delivery handling
    // -------------------------------------------------------------------------

    /**
     * Called when a player right-clicks a barrel while holding a mail item.
     * Uses the player's per-session barrel mapping to determine the barrel's destination.
     */
    public void handleDelivery(Player player, Block barrel, ItemStack heldItem) {
        UUID uuid = player.getUniqueId();
        MailSortingState state = activeStates.get(uuid);
        if (state == null) return;

        String mailDest = getMailDestination(heldItem);
        String key = barrelKey(barrel.getWorld().getName(), barrel.getX(), barrel.getY(), barrel.getZ());
        String barrelDest = state.getBarrelDestination(key);
        if (mailDest == null || barrelDest == null) return;

        // Consume one mail item from the held slot
        if (heldItem.getAmount() > 1) {
            heldItem.setAmount(heldItem.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }
        // Force inventory resync to client (cancelled event may revert visual state otherwise)
        player.updateInventory();

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
            player.sendMessage(ChatColor.GOLD + "All mail delivered! Return to the Commission Board to collect your reward.");
        } else {
            player.sendMessage(ChatColor.GRAY + "" + remaining + " mail item(s) remaining.");
        }

        plugin.getCommissionManager().incrementProgress(uuid, 1);
    }

    // -------------------------------------------------------------------------
    // Look-at label system (subtitle-based)
    // -------------------------------------------------------------------------

    /**
     * Starts a repeating task (every 4 ticks) that ray-traces what the player is
     * looking at. While they hold a mail item and aim at a registered barrel, a
     * subtitle shows that barrel's session destination — like a hover tooltip.
     * The subtitle refreshes every 4 ticks (0.2 s) so it stays visible as long
     * as the player is looking. When they look away it clears immediately.
     */
    private void startLookTask(Player player) {
        stopLookTask(player.getUniqueId());

        Title.Times times = Title.Times.times(Duration.ZERO, Duration.ofMillis(400), Duration.ofMillis(200));

        int taskId = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()) { stopLookTask(player.getUniqueId()); return; }
            UUID uuid = player.getUniqueId();
            MailSortingState state = activeStates.get(uuid);
            if (state == null) { stopLookTask(uuid); return; }

            // Only show label when holding a mail item
            if (!isMailItem(player.getInventory().getItemInMainHand())) {
                if (lastLookedBarrel.remove(uuid) != null) player.clearTitle();
                return;
            }

            Block target = player.getTargetBlockExact(6);
            if (target != null && isBarrel(target)) {
                String key = barrelKey(target.getWorld().getName(),
                        target.getX(), target.getY(), target.getZ());
                String dest = state.getBarrelDestination(key);
                if (dest != null) {
                    // Send every tick while aimed at a barrel — short stay (400 ms) keeps it
                    // live continuously and fades naturally the moment they look away.
                    player.showTitle(Title.title(
                            Component.empty(),
                            Component.text("\u2709 " + dest, NamedTextColor.AQUA),
                            times));
                    lastLookedBarrel.put(uuid, key);
                    return;
                }
            }

            // Not aiming at a barrel — clear the title once
            if (lastLookedBarrel.remove(uuid) != null) player.clearTitle();
        }, 1L, 4L).getTaskId();
        lookTasks.put(player.getUniqueId(), taskId);
    }

    private void stopLookTask(UUID uuid) {
        Integer taskId = lookTasks.remove(uuid);
        if (taskId != null) plugin.getServer().getScheduler().cancelTask(taskId);
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
        String key = barrelKey(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
        return barrelDestinations.containsKey(key);
    }

    /** Returns the static config destination for a barrel (used for admin/wand tools). */
    public String getBarrelDestination(Block block) {
        if (block == null) return null;
        String key = barrelKey(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
        return barrelDestinations.get(key);
    }

    // -------------------------------------------------------------------------
    // Session management
    // -------------------------------------------------------------------------

    public MailSortingState getState(UUID uuid) {
        return activeStates.get(uuid);
    }

    /**
     * Removes all mail items from the player's inventory, stops their look task,
     * removes their barrel label entity, and clears their session state.
     */
    public void clearMail(Player player) {
        UUID uuid = player.getUniqueId();
        stopLookTask(uuid);
        if (lastLookedBarrel.remove(uuid) != null) player.clearTitle();
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
     * Called from /mailroom setup.
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
