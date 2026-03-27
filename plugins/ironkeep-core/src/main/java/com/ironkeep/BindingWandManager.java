package com.ironkeep;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public class BindingWandManager {

    private final NamespacedKey bindingWandKey;

    public BindingWandManager(IronKeepPlugin plugin) {
        this.bindingWandKey = new NamespacedKey(plugin, "binding_wand");
    }

    public ItemStack createWand() {
        ItemStack wand = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = wand.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "Mailroom Binding Wand");
            meta.getPersistentDataContainer().set(bindingWandKey, PersistentDataType.BOOLEAN, true);
            wand.setItemMeta(meta);
        }
        return wand;
    }

    public boolean isWand(ItemStack item) {
        if (item == null || item.getType() != Material.BLAZE_ROD) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(bindingWandKey, PersistentDataType.BOOLEAN);
    }
}
