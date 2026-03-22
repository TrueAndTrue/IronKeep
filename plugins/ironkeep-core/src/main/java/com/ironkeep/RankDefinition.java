package com.ironkeep;

import java.util.List;

/**
 * Defines a player rank: its number, display name, Gold Coin cost to reach,
 * and the list of commission type keys unlocked at this rank.
 */
public class RankDefinition {

    private final int rank;
    private final String displayName;
    private final double cost;           // Gold Coins required to rank up TO this rank
    private final List<String> unlockedTypes; // Commission type keys (e.g. MINING, WOODCUTTING)

    public RankDefinition(int rank, String displayName, double cost, List<String> unlockedTypes) {
        this.rank = rank;
        this.displayName = displayName;
        this.cost = cost;
        this.unlockedTypes = unlockedTypes;
    }

    public int getRank() { return rank; }
    public String getDisplayName() { return displayName; }
    public double getCost() { return cost; }
    public List<String> getUnlockedTypes() { return unlockedTypes; }

    public boolean allowsType(String type) {
        return unlockedTypes.stream().anyMatch(t -> t.equalsIgnoreCase(type));
    }
}
