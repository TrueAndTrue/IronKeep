package com.ironkeep;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public class KitchenWandManager {

    private final NamespacedKey kitchenWandKey;

    public KitchenWandManager(IronKeepPlugin plugin) {
        this.kitchenWandKey = new NamespacedKey(plugin, "kitchen_wand");
    }

    public ItemStack createWand() {
        ItemStack wand = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = wand.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + "Kitchen Ingredient Wand");
            meta.setLore(java.util.List.of(ChatColor.GRAY + "Right-click an item frame to bind an ingredient"));
            meta.getPersistentDataContainer().set(kitchenWandKey, PersistentDataType.BOOLEAN, true);
            wand.setItemMeta(meta);
        }
        return wand;
    }

    public boolean isWand(ItemStack item) {
        if (item == null || item.getType() != Material.BLAZE_ROD) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(kitchenWandKey, PersistentDataType.BOOLEAN);
    }
}
