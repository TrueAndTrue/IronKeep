package com.ironkeep;

public class Commission {

    private final String item;
    private final int quantity;
    private final double reward;

    public Commission(String item, int quantity, double reward) {
        this.item = item;
        this.quantity = quantity;
        this.reward = reward;
    }

    public String getItem() {
        return item;
    }

    public int getQuantity() {
        return quantity;
    }

    public double getReward() {
        return reward;
    }
}
