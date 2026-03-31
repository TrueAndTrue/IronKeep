package com.ironkeep;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class KitchenWandGUI {

    static final String GUI_TITLE = "Kitchen: Select Ingredient";

    private static final Map<Inventory, ItemFrame> openGuis = new HashMap<>();

    private final IronKeepPlugin plugin;

    public KitchenWandGUI(IronKeepPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, ItemFrame frame) {
        Set<Material> allIngredients = plugin.getKitchenManager().getAllRecipeIngredients();
        Inventory inv = Bukkit.createInventory(null, 27, GUI_TITLE);

        int slot = 0;
        for (Material mat : allIngredients) {
            if (slot >= 26) break;
            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.WHITE + plugin.getKitchenManager().formatMaterial(mat));
                meta.setLore(List.of(ChatColor.GRAY + "Click to bind this frame"));
                item.setItemMeta(meta);
            }
            inv.setItem(slot++, item);
        }

        // Remove binding button
        ItemStack barrier = new ItemStack(Material.BARRIER);
        ItemMeta barrierMeta = barrier.getItemMeta();
        if (barrierMeta != null) {
            barrierMeta.setDisplayName(ChatColor.RED + "Remove Binding");
            barrier.setItemMeta(barrierMeta);
        }
        inv.setItem(26, barrier);

        openGuis.put(inv, frame);
        player.openInventory(inv);
    }

    static boolean isKitchenWandGUI(Inventory inv) { return openGuis.containsKey(inv); }
    static ItemFrame getFrame(Inventory inv) { return openGuis.get(inv); }
    static void closeGUI(Inventory inv) { openGuis.remove(inv); }
}
