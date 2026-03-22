package com.ironkeep;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class CommissionBoardManager {

    private final IronKeepPlugin plugin;
    private Material boardType;
    private final List<Location> boardLocations = new ArrayList<>();

    public CommissionBoardManager(IronKeepPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        boardLocations.clear();

        String typeName = plugin.getConfig().getString("commission-board.block-type", "OAK_WALL_SIGN");
        boardType = Material.matchMaterial(typeName);
        if (boardType == null) {
            plugin.getLogger().warning("CommissionBoardManager: unknown block-type '" + typeName + "', defaulting to OAK_WALL_SIGN.");
            boardType = Material.OAK_WALL_SIGN;
        }

        List<?> list = plugin.getConfig().getList("commission-board.locations");
        if (list == null) {
            plugin.getLogger().info("CommissionBoardManager: no board locations configured.");
            return;
        }

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

        plugin.getLogger().info("CommissionBoardManager: loaded " + boardLocations.size() + " board location(s).");
    }

    public void placeBoards() {
        for (Location loc : boardLocations) {
            if (loc.getBlock().getType() != boardType) {
                loc.getBlock().setType(boardType);
                plugin.getLogger().info("CommissionBoardManager: placed " + boardType.name()
                        + " at " + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ());
            }
        }
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

    public List<Location> getBoards() {
        return Collections.unmodifiableList(boardLocations);
    }
}
