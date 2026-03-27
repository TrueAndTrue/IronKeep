package com.ironkeep;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BindingWandGUI {

    static final String GUI_TITLE = "Bind Barrel: Select Destination";

    private static final Material[] WOOL_COLORS = {
        Material.RED_WOOL,
        Material.ORANGE_WOOL,
        Material.YELLOW_WOOL,
        Material.LIME_WOOL,
        Material.CYAN_WOOL,
        Material.LIGHT_BLUE_WOOL,
        Material.PURPLE_WOOL,
        Material.PINK_WOOL
    };

    // Maps open inventory instances to the barrel block they represent
    private static final Map<Inventory, Block> openGuis = new HashMap<>();

    private final IronKeepPlugin plugin;

    public BindingWandGUI(IronKeepPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, Block block) {
        Inventory inv = Bukkit.createInventory(null, 27, GUI_TITLE);

        List<String> destinations = plugin.getMailRoomManager().getAvailableDestinations();
        for (int i = 0; i < destinations.size() && i < 25; i++) {
            String dest = destinations.get(i);
            Material wool = WOOL_COLORS[i % WOOL_COLORS.length];
            ItemStack item = new ItemStack(wool);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.WHITE + dest);
                item.setItemMeta(meta);
            }
            inv.setItem(i, item);
        }

        ItemStack barrier = new ItemStack(Material.BARRIER);
        ItemMeta barrierMeta = barrier.getItemMeta();
        if (barrierMeta != null) {
            barrierMeta.setDisplayName(ChatColor.RED + "Remove Binding");
            barrier.setItemMeta(barrierMeta);
        }
        inv.setItem(26, barrier);

        openGuis.put(inv, block);
        player.openInventory(inv);
    }

    static boolean isBindingGUI(Inventory inv) {
        return openGuis.containsKey(inv);
    }

    static Block getBlock(Inventory inv) {
        return openGuis.get(inv);
    }

    static void closeGUI(Inventory inv) {
        openGuis.remove(inv);
    }
}
