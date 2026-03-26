package com.ironkeep;

import java.util.UUID;

public class PlayerCommissionState {

    private final UUID playerUUID;
    private String activeCommissionId;
    private int progress;
    private int overrideQuantity = -1; // -1 means use definition's objectiveQuantity

    public PlayerCommissionState(UUID playerUUID) {
        this.playerUUID = playerUUID;
    }

    public UUID getPlayerUUID() { return playerUUID; }

    public String getActiveCommissionId() { return activeCommissionId; }
    public void setActiveCommissionId(String activeCommissionId) { this.activeCommissionId = activeCommissionId; }

    public int getProgress() { return progress; }
    public void setProgress(int progress) { this.progress = progress; }

    /** Returns the effective objective quantity, applying skill-based reduction if set. */
    public int getEffectiveQuantity(int defaultQuantity) {
        return overrideQuantity > 0 ? overrideQuantity : defaultQuantity;
    }

    public int getOverrideQuantity() { return overrideQuantity; }
    public void setOverrideQuantity(int overrideQuantity) { this.overrideQuantity = overrideQuantity; }
}
