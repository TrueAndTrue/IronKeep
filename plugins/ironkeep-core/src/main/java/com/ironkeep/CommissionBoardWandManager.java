package com.ironkeep;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public class CommissionBoardWandManager {

    private final NamespacedKey boardWandKey;

    public CommissionBoardWandManager(IronKeepPlugin plugin) {
        this.boardWandKey = new NamespacedKey(plugin, "commission_board_wand");
    }

    public ItemStack createWand() {
        ItemStack wand = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = wand.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.DARK_RED + "Commission Board Wand");
            meta.setLore(java.util.List.of(
                    ChatColor.GRAY + "Right-click an item frame to register it",
                    ChatColor.GRAY + "as a commission board.",
                    ChatColor.YELLOW + "Right-click again to unregister."
            ));
            meta.getPersistentDataContainer().set(boardWandKey, PersistentDataType.BOOLEAN, true);
            wand.setItemMeta(meta);
        }
        return wand;
    }

    public boolean isWand(ItemStack item) {
        if (item == null || item.getType() != Material.BLAZE_ROD) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(boardWandKey, PersistentDataType.BOOLEAN);
    }
}
