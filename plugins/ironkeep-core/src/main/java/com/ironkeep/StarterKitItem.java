package com.ironkeep;

import java.util.Map;

public class StarterKitItem {

    private final String material;
    private final int quantity;
    private final Map<String, Integer> enchantments;
    private final String displayName;

    public StarterKitItem(String material, int quantity, Map<String, Integer> enchantments, String displayName) {
        this.material = material;
        this.quantity = quantity;
        this.enchantments = enchantments;
        this.displayName = displayName;
    }

    public String getMaterial() {
        return material;
    }

    public int getQuantity() {
        return quantity;
    }

    public Map<String, Integer> getEnchantments() {
        return enchantments;
    }

    /** May be null if no custom display name was configured. */
    public String getDisplayName() {
        return displayName;
    }
}
