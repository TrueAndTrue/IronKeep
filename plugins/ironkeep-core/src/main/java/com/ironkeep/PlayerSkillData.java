package com.ironkeep;

public class PlayerSkillData {

    private String commissionType;
    private int level;
    private double xp;

    public PlayerSkillData(String commissionType, int level, double xp) {
        this.commissionType = commissionType;
        this.level = level;
        this.xp = xp;
    }

    public String getCommissionType() { return commissionType; }
    public void setCommissionType(String commissionType) { this.commissionType = commissionType; }

    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }

    public double getXp() { return xp; }
    public void setXp(double xp) { this.xp = xp; }
}
