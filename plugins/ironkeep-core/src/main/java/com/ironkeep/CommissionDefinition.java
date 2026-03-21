package com.ironkeep;

public class CommissionDefinition {

    private final String id;
    private final String type;
    private final String displayName;
    private final String description;
    private final String objectiveItem;
    private final int objectiveQuantity;
    private final double rewardAmount;

    public CommissionDefinition(String id, String type, String displayName, String description,
                                String objectiveItem, int objectiveQuantity, double rewardAmount) {
        this.id = id;
        this.type = type;
        this.displayName = displayName;
        this.description = description;
        this.objectiveItem = objectiveItem;
        this.objectiveQuantity = objectiveQuantity;
        this.rewardAmount = rewardAmount;
    }

    public String getId() { return id; }
    public String getType() { return type; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public String getObjectiveItem() { return objectiveItem; }
    public int getObjectiveQuantity() { return objectiveQuantity; }
    public double getRewardAmount() { return rewardAmount; }
}
