package com.ironkeep;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

@SuppressWarnings("deprecation")
public class BlacksmithListener implements Listener {

    private static final String GUI_TITLE = ChatColor.DARK_GRAY + "Blacksmith";
    private static final int GUI_SIZE = 27;

    // Slot assignments
    private static final int SLOT_EFFICIENCY = 11;
    private static final int SLOT_FORTUNE     = 15;
    private static final int SLOT_BALANCE     = 22;

    private static final int MAX_EFFICIENCY = 5;
    private static final int MAX_FORTUNE    = 3;

    private static class BlacksmithHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    private final IronKeepPlugin plugin;
    private final BlacksmithManager blacksmithManager;

    public BlacksmithListener(IronKeepPlugin plugin, BlacksmithManager blacksmithManager) {
        this.plugin = plugin;
        this.blacksmithManager = blacksmithManager;
    }

    // -------------------------------------------------------------------------
    // NPC protection
    // -------------------------------------------------------------------------

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (blacksmithManager.isBlacksmith(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityTarget(EntityTargetEvent event) {
        if (event.getTarget() != null && blacksmithManager.isBlacksmith(event.getTarget())) {
            event.setCancelled(true);
        }
    }

    // -------------------------------------------------------------------------
    // NPC interaction → open GUI
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!blacksmithManager.isBlacksmith(event.getRightClicked())) return;
        event.setCancelled(true);
        openGui(event.getPlayer());
    }

    // -------------------------------------------------------------------------
    // GUI click handling
    // -------------------------------------------------------------------------

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof BlacksmithHolder)) return;
        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot == SLOT_EFFICIENCY) {
            handlePurchase(player, Enchantment.EFFICIENCY, MAX_EFFICIENCY, "Efficiency");
        } else if (slot == SLOT_FORTUNE) {
            handlePurchase(player, Enchantment.FORTUNE, MAX_FORTUNE, "Fortune");
        }
    }

    // -------------------------------------------------------------------------
    // Purchase logic
    // -------------------------------------------------------------------------

    private void handlePurchase(Player player, Enchantment enchantment, int maxLevel, String name) {
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!isValidTool(tool)) {
            player.sendMessage(ChatColor.RED + "Hold a pickaxe, axe, shovel, or hoe to enchant.");
            return;
        }

        int currentLevel = tool.getEnchantmentLevel(enchantment);
        if (currentLevel >= maxLevel) {
            player.sendMessage(ChatColor.YELLOW + name + " is already at max level!");
            return;
        }

        int nextLevel = currentLevel + 1;
        long cost = enchantmentCost(enchantment, nextLevel);

        CurrencyManager cm = plugin.getCurrencyManager();
        if (!cm.hasShards(player.getUniqueId(), cost)) {
            player.sendMessage(ChatColor.RED + "Not enough Shards! You need "
                    + cost + " Shards for " + name + " " + toRoman(nextLevel) + ".");
            return;
        }

        cm.removeShards(player.getUniqueId(), cost);
        tool.addUnsafeEnchantment(enchantment, nextLevel);

        player.sendMessage(ChatColor.GREEN + "Applied " + ChatColor.YELLOW + name + " " + toRoman(nextLevel)
                + ChatColor.GREEN + " to your " + formatMaterial(tool.getType()) + "!"
                + ChatColor.GRAY + " (-" + cost + " Shards)");

        // Refresh the GUI to show updated state
        openGui(player);
    }

    // -------------------------------------------------------------------------
    // GUI builder
    // -------------------------------------------------------------------------

    private void openGui(Player player) {
        Inventory gui = Bukkit.createInventory(new BlacksmithHolder(), GUI_SIZE, GUI_TITLE);

        ItemStack filler = makeFiller();
        for (int i = 0; i < GUI_SIZE; i++) gui.setItem(i, filler);

        ItemStack tool = player.getInventory().getItemInMainHand();
        boolean hasTool = isValidTool(tool);
        int effLevel  = hasTool ? tool.getEnchantmentLevel(Enchantment.EFFICIENCY) : 0;
        int fortLevel = hasTool ? tool.getEnchantmentLevel(Enchantment.FORTUNE) : 0;

        gui.setItem(SLOT_EFFICIENCY, buildEnchantSlot(
                Material.IRON_PICKAXE, "Efficiency", effLevel, MAX_EFFICIENCY,
                Enchantment.EFFICIENCY, hasTool));
        gui.setItem(SLOT_FORTUNE, buildEnchantSlot(
                Material.EMERALD, "Fortune", fortLevel, MAX_FORTUNE,
                Enchantment.FORTUNE, hasTool));

        // Shard balance display
        long shards = Math.round(plugin.getCurrencyManager().getShards(player.getUniqueId()));
        ItemStack balItem = new ItemStack(Material.AMETHYST_SHARD);
        ItemMeta balMeta = balItem.getItemMeta();
        balMeta.setDisplayName(ChatColor.LIGHT_PURPLE + "Your Shards");
        balMeta.setLore(List.of(ChatColor.AQUA + String.format("%,d", shards) + " Shards"));
        balItem.setItemMeta(balMeta);
        gui.setItem(SLOT_BALANCE, balItem);

        player.openInventory(gui);
    }

    private ItemStack buildEnchantSlot(Material icon, String enchName,
                                        int currentLevel, int maxLevel,
                                        Enchantment enchantment, boolean hasTool) {
        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();

        if (!hasTool) {
            meta.setDisplayName(ChatColor.YELLOW + enchName);
            meta.setLore(List.of(
                    ChatColor.GRAY + "Current level: None",
                    ChatColor.RED + "Hold a tool in your main hand"
            ));
        } else if (currentLevel >= maxLevel) {
            meta.setDisplayName(ChatColor.GREEN + enchName + " " + toRoman(currentLevel));
            meta.setLore(List.of(
                    ChatColor.GRAY + "Current level: " + toRoman(currentLevel),
                    ChatColor.GOLD + "Max level reached!"
            ));
        } else {
            int nextLevel = currentLevel + 1;
            long cost = enchantmentCost(enchantment, nextLevel);
            meta.setDisplayName(ChatColor.YELLOW + "Buy " + enchName + " " + toRoman(nextLevel));
            meta.setLore(List.of(
                    ChatColor.GRAY + "Current level: " + (currentLevel == 0 ? "None" : toRoman(currentLevel)),
                    ChatColor.WHITE + "Upgrade to: " + ChatColor.AQUA + toRoman(nextLevel),
                    ChatColor.LIGHT_PURPLE + "Cost: " + String.format("%,d", cost) + " Shards",
                    ChatColor.DARK_GRAY + "Click to purchase"
            ));
        }

        item.setItemMeta(meta);
        return item;
    }

    private long enchantmentCost(Enchantment enchantment, int level) {
        String key = enchantment == Enchantment.EFFICIENCY ? "efficiency-costs" : "fortune-costs";
        return plugin.getConfig().getLong("blacksmith." + key + "." + level, 100L);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private boolean isValidTool(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        String name = item.getType().name();
        return name.endsWith("_PICKAXE") || name.endsWith("_AXE")
                || name.endsWith("_SHOVEL") || name.endsWith("_HOE");
    }

    private ItemStack makeFiller() {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        meta.setDisplayName(ChatColor.RESET + "");
        pane.setItemMeta(meta);
        return pane;
    }

    private static String toRoman(int n) {
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(n);
        };
    }

    private static String formatMaterial(Material mat) {
        String raw = mat.name().replace('_', ' ').toLowerCase();
        return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
    }
}
