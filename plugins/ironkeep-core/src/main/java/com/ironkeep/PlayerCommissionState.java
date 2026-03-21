package com.ironkeep;

import java.util.UUID;

public class PlayerCommissionState {

    private final UUID playerUUID;
    private String activeCommissionId;
    private int progress;

    public PlayerCommissionState(UUID playerUUID) {
        this.playerUUID = playerUUID;
    }

    public UUID getPlayerUUID() { return playerUUID; }

    public String getActiveCommissionId() { return activeCommissionId; }
    public void setActiveCommissionId(String activeCommissionId) { this.activeCommissionId = activeCommissionId; }

    public int getProgress() { return progress; }
    public void setProgress(int progress) { this.progress = progress; }
}
