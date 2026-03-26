package com.ironkeep;

import org.bukkit.Material;

import java.util.List;

public class CookingRecipe {

    private final String id;
    private final String displayName;
    private final int rankTier;
    private final List<Material> ingredients;

    public CookingRecipe(String id, String displayName, int rankTier, List<Material> ingredients) {
        this.id = id;
        this.displayName = displayName;
        this.rankTier = rankTier;
        this.ingredients = ingredients;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public int getRankTier() { return rankTier; }
    public List<Material> getIngredients() { return ingredients; }
}
