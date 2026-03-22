package com.ironkeep;

import java.util.List;

/**
 * Represents a commission zone — a bounded region where specific commission types are tracked.
 */
public class Zone {

    private final String id;
    private final String world;
    private final int x1, z1, x2, z2;
    private final int yMin, yMax;
    private final List<String> commissionTypes; // type keys valid in this zone
    private int borderY = Integer.MIN_VALUE;    // Y level for Obsidian border ring (optional)

    public Zone(String id, String world, int x1, int z1, int x2, int z2,
                int yMin, int yMax, List<String> commissionTypes) {
        this.id = id;
        this.world = world;
        this.x1 = Math.min(x1, x2);
        this.z1 = Math.min(z1, z2);
        this.x2 = Math.max(x1, x2);
        this.z2 = Math.max(z1, z2);
        this.yMin = Math.min(yMin, yMax);
        this.yMax = Math.max(yMin, yMax);
        this.commissionTypes = commissionTypes;
    }

    public void setBorderY(int borderY) { this.borderY = borderY; }
    public int getBorderY() { return borderY; }

    public boolean contains(String worldName, int x, int y, int z) {
        if (!world.equals(worldName)) return false;
        return x >= x1 && x <= x2 && z >= z1 && z <= z2 && y >= yMin && y <= yMax;
    }

    public boolean supportsType(String type) {
        return commissionTypes.stream().anyMatch(t -> t.equalsIgnoreCase(type));
    }

    public String getId() { return id; }
    public String getWorld() { return world; }
    public int getX1() { return x1; }
    public int getX2() { return x2; }
    public int getZ1() { return z1; }
    public int getZ2() { return z2; }
    public List<String> getCommissionTypes() { return commissionTypes; }
}
