package com.ironkeep;

import org.bukkit.Material;

import java.util.List;

public class CookingState {

    private final String recipeId;
    private List<Material> confirmedIngredients;
    private boolean confirmedOrder;

    public CookingState(String recipeId) {
        this.recipeId = recipeId;
        this.confirmedIngredients = null;
        this.confirmedOrder = false;
    }

    public String getRecipeId() { return recipeId; }

    public List<Material> getConfirmedIngredients() { return confirmedIngredients; }
    public void setConfirmedIngredients(List<Material> confirmedIngredients) {
        this.confirmedIngredients = confirmedIngredients;
    }

    public boolean isConfirmedOrder() { return confirmedOrder; }
    public void setConfirmedOrder(boolean confirmedOrder) {
        this.confirmedOrder = confirmedOrder;
    }

    /** Returns true when the player has confirmed their ingredient order. */
    public boolean isReadyToTurnIn() { return confirmedOrder; }
}
