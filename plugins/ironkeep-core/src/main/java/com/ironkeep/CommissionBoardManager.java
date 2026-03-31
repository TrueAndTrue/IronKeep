package com.ironkeep;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ItemFrame;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CommissionBoardManager {

    private final IronKeepPlugin plugin;
    private Material boardType;
    private final List<Location> boardLocations = new ArrayList<>();

    /** Registered item frame boards, keyed as "world,bx,by,bz,face". */
    private final Set<String> frameBoardKeys = new HashSet<>();

    public CommissionBoardManager(IronKeepPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        boardLocations.clear();
        frameBoardKeys.clear();

        // --- Block-type boards (existing) ---
        String typeName = plugin.getConfig().getString("commission-board.block-type", "OAK_WALL_SIGN");
        boardType = Material.matchMaterial(typeName);
        if (boardType == null) {
            plugin.getLogger().warning("CommissionBoardManager: unknown block-type '" + typeName + "', defaulting to OAK_WALL_SIGN.");
            boardType = Material.OAK_WALL_SIGN;
        }

        List<?> list = plugin.getConfig().getList("commission-board.locations");
        if (list != null) {
            for (Object obj : list) {
                if (!(obj instanceof Map<?, ?> map)) continue;
                String worldName = String.valueOf(map.get("world"));
                World world = Bukkit.getWorld(worldName);
                if (world == null) {
                    plugin.getLogger().warning("CommissionBoardManager: world '" + worldName + "' not found, skipping board location.");
                    continue;
                }
                int x = ((Number) map.get("x")).intValue();
                int y = ((Number) map.get("y")).intValue();
                int z = ((Number) map.get("z")).intValue();
                boardLocations.add(new Location(world, x, y, z));
            }
        }

        // --- Item frame boards ---
        List<?> frameList = plugin.getConfig().getList("commission-board.frame-locations");
        if (frameList != null) {
            for (Object obj : frameList) {
                if (!(obj instanceof Map<?, ?> map)) continue;
                String world = String.valueOf(map.get("world"));
                int x = ((Number) map.get("x")).intValue();
                int y = ((Number) map.get("y")).intValue();
                int z = ((Number) map.get("z")).intValue();
                Object faceObj = map.get("face");
                String face = faceObj != null ? String.valueOf(faceObj) : "SOUTH";
                frameBoardKeys.add(frameKey(world, x, y, z, face));
            }
        }

        plugin.getLogger().info("CommissionBoardManager: loaded " + boardLocations.size()
                + " block board(s) and " + frameBoardKeys.size() + " frame board(s).");
    }

    public void placeBoards() {
        for (Location loc : boardLocations) {
            if (loc.getBlock().getType() != boardType) {
                loc.getBlock().setType(boardType);
                plugin.getLogger().info("CommissionBoardManager: placed " + boardType.name()
                        + " at " + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ());
            }
        }
        // Frame boards are entities — not auto-placed
    }

    public boolean isBoard(Block block) {
        if (block.getType() != boardType) return false;
        for (Location loc : boardLocations) {
            if (loc.getWorld() == block.getWorld()
                    && loc.getBlockX() == block.getX()
                    && loc.getBlockY() == block.getY()
                    && loc.getBlockZ() == block.getZ()) {
                return true;
            }
        }
        return false;
    }

    public boolean isFrameBoard(ItemFrame frame) {
        return frameBoardKeys.contains(frameKey(frame));
    }

    /** Registers an item frame as a commission board and saves to config. */
    public void addFrameBoard(ItemFrame frame) {
        frameBoardKeys.add(frameKey(frame));
        saveFrameBoards();
    }

    /** Unregisters an item frame as a commission board and saves to config. */
    public void removeFrameBoard(ItemFrame frame) {
        frameBoardKeys.remove(frameKey(frame));
        saveFrameBoards();
    }

    public List<Location> getBoards() {
        return Collections.unmodifiableList(boardLocations);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String frameKey(String world, int x, int y, int z, String face) {
        return world + "," + x + "," + y + "," + z + "," + face;
    }

    private String frameKey(ItemFrame frame) {
        Location loc = frame.getLocation();
        String world = loc.getWorld() != null ? loc.getWorld().getName() : "world";
        return frameKey(world, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(),
                frame.getAttachedFace().name());
    }

    private void saveFrameBoards() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (String key : frameBoardKeys) {
            String[] parts = key.split(",", 5);
            if (parts.length != 5) continue;
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("world", parts[0]);
            try {
                entry.put("x", Integer.parseInt(parts[1]));
                entry.put("y", Integer.parseInt(parts[2]));
                entry.put("z", Integer.parseInt(parts[3]));
            } catch (NumberFormatException ignored) { continue; }
            entry.put("face", parts[4]);
            list.add(entry);
        }
        plugin.getConfig().set("commission-board.frame-locations", list);
        plugin.saveConfig();
    }
}
