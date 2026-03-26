package com.ironkeep;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@SuppressWarnings("UnstableApiUsage")
public class SkillCommand implements BasicCommand, Listener {

    private static final String GUI_TITLE_L1 = ChatColor.DARK_GRAY + "[ " + ChatColor.GOLD + "Skills" + ChatColor.DARK_GRAY + " ]";
    private static final String GUI_TITLE_L2_SUFFIX = ChatColor.DARK_GRAY + " Skills ]";

    private static final String[] SKILL_TYPES = {"MINING", "WOODCUTTING", "FARMING", "MAIL_SORTING", "COOKING"};
    private static final Material[] SKILL_MATERIALS = {
        Material.IRON_PICKAXE, Material.IRON_AXE, Material.WHEAT, Material.PAPER, Material.CAULDRON
    };

    private final IronKeepPlugin plugin;
    private final NamespacedKey skillTypeKey;

    public SkillCommand(IronKeepPlugin plugin) {
        this.plugin = plugin;
        this.skillTypeKey = new NamespacedKey(plugin, "skill_type");
    }

    public void register(Commands commands) {
        commands.register("skills", "View your commission skill levels", this);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public Collection<String> suggest(CommandSourceStack stack, String[] args) {
        return List.of();
    }

    @Override
    public void execute(CommandSourceStack stack, String[] args) {
        if (!(stack.getSender() instanceof Player player)) {
            stack.getSender().sendMessage(ChatColor.RED + "Only players can use this command.");
            return;
        }
        openLevel1(player);
    }

    private void openLevel1(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, GUI_TITLE_L1);
        SkillManager sm = plugin.getSkillManager();
        UUID uuid = player.getUniqueId();

        int[] skillSlots = {10, 12, 14, 11, 13};

        for (int i = 0; i < SKILL_TYPES.length; i++) {
            String type = SKILL_TYPES[i];
            Material mat = SKILL_MATERIALS[i];
            PlayerSkillData data = sm.getSkillData(uuid, type);
            int level = data.getLevel();
            double xp = data.getXp();
            double required = sm.xpRequired(level);

            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(ChatColor.GOLD + formatTypeName(type));

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Level " + ChatColor.YELLOW + level);
            lore.add(ChatColor.GRAY + "XP: " + ChatColor.YELLOW + formatNumber(xp) + "/" + formatNumber(required));
            lore.add(ChatColor.GRAY + "Click to view skill tree");
            meta.setLore(lore);

            meta.getPersistentDataContainer().set(skillTypeKey, PersistentDataType.STRING, type);
            item.setItemMeta(meta);
            inv.setItem(skillSlots[i], item);
        }

        // Filler panes
        ItemStack filler = createFiller();
        for (int slot = 0; slot < 27; slot++) {
            if (inv.getItem(slot) == null) {
                inv.setItem(slot, filler);
            }
        }

        player.openInventory(inv);
    }

    private void openLevel2(Player player, String type) {
        String formattedType = formatTypeName(type);
        String title = ChatColor.DARK_GRAY + "[ " + ChatColor.GOLD + formattedType + GUI_TITLE_L2_SUFFIX;
        Inventory inv = Bukkit.createInventory(null, 54, title);

        SkillManager sm = plugin.getSkillManager();
        UUID uuid = player.getUniqueId();
        PlayerSkillData data = sm.getSkillData(uuid, type);
        int level = data.getLevel();
        double xp = data.getXp();
        double required = sm.xpRequired(level);

        // Slot 4: Skill summary
        Material skillMat = getMaterialForType(type);
        ItemStack summary = new ItemStack(skillMat);
        ItemMeta summaryMeta = summary.getItemMeta();
        summaryMeta.setDisplayName(ChatColor.GOLD + formattedType + " - Level " + level);

        double goldBonus = sm.getGoldBonus(uuid, type) * 100;
        double shardsBonus = sm.getShardsBonus(uuid, type) * 100;
        int objReduction = sm.getObjectiveReduction(uuid, type);

        List<String> summaryLore = new ArrayList<>();
        summaryLore.add(ChatColor.GRAY + "Current XP: " + ChatColor.YELLOW + formatNumber(xp) + "/" + formatNumber(required));
        summaryLore.add(ChatColor.GRAY + "Progress: " + ChatColor.YELLOW + progressBar(xp, required));
        summaryLore.add("");
        summaryLore.add(ChatColor.GOLD + "Active Bonuses:");
        summaryLore.add(ChatColor.GREEN + "  +" + formatBonusPct(goldBonus) + "% Gold Coins per completion");
        summaryLore.add(ChatColor.AQUA + "  +" + formatBonusPct(shardsBonus) + "% Shards per completion");
        if (objReduction > 0) {
            summaryLore.add(ChatColor.YELLOW + "  -" + objReduction + " required items per commission");
        }
        summaryMeta.setLore(summaryLore);
        summary.setItemMeta(summaryMeta);
        inv.setItem(4, summary);

        // Slots 9-44: level milestones (levels 1-36 fit; we use 9+level-1 for levels 1-36)
        // For levels 1-50, place at slot 8+level but cap at slot 53
        int levelCap = sm.getLevelCap();
        for (int i = 1; i <= levelCap && (8 + i) < 54; i++) {
            int slot = 8 + i;
            boolean unlocked = i <= level;
            boolean isCurrent = i == level;

            ItemStack milestoneItem;
            ItemMeta milestoneMeta;

            if (isCurrent) {
                milestoneItem = new ItemStack(Material.GOLD_NUGGET);
                milestoneMeta = milestoneItem.getItemMeta();
                milestoneMeta.setDisplayName(ChatColor.GOLD + "Current: Level " + i);
            } else {
                Material glass = unlocked ? Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE;
                milestoneItem = new ItemStack(glass);
                milestoneMeta = milestoneItem.getItemMeta();
                ChatColor nameColor = unlocked ? ChatColor.GREEN : ChatColor.GRAY;
                milestoneMeta.setDisplayName(nameColor + "Level " + i);
            }

            List<String> milestoneLore = new ArrayList<>();
            if (i % 10 == 0) {
                milestoneLore.add(ChatColor.GRAY + "Milestone: " + ChatColor.YELLOW + "-1 required item");
                milestoneMeta.setLore(milestoneLore);
            } else {
                milestoneMeta.setLore(milestoneLore);
            }
            milestoneItem.setItemMeta(milestoneMeta);
            inv.setItem(slot, milestoneItem);
        }

        // Slot 49: Back button
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.setDisplayName(ChatColor.GRAY + "Back");
        back.setItemMeta(backMeta);
        inv.setItem(49, back);

        // Filler for remaining slots
        ItemStack filler = createFiller();
        for (int slot = 0; slot < 54; slot++) {
            if (inv.getItem(slot) == null) {
                inv.setItem(slot, filler);
            }
        }

        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String title = event.getView().getTitle();

        // Only handle clicks in Skills GUIs — do NOT cancel other inventories
        boolean isL1 = title.equals(GUI_TITLE_L1);
        boolean isL2 = title.startsWith(ChatColor.DARK_GRAY + "[ " + ChatColor.GOLD)
                && title.endsWith(GUI_TITLE_L2_SUFFIX);
        if (!isL1 && !isL2) return;

        event.setCancelled(true);

        if (isL1) {
            // Level 1 GUI: check for skill item click
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || !clicked.hasItemMeta()) return;
            ItemMeta meta = clicked.getItemMeta();
            String skillType = meta.getPersistentDataContainer().get(skillTypeKey, PersistentDataType.STRING);
            if (skillType != null) {
                openLevel2(player, skillType);
            }
        } else if (isL2) {
            // Level 2 GUI: check for back button
            if (event.getSlot() == 49) {
                openLevel1(player);
            }
        }
    }

    private ItemStack createFiller() {
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        meta.setDisplayName(" ");
        filler.setItemMeta(meta);
        return filler;
    }

    private String progressBar(double current, double max) {
        int filled = max > 0 ? (int) Math.round((current / max) * 10) : 0;
        filled = Math.max(0, Math.min(10, filled));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            if (i < filled) sb.append(ChatColor.GREEN).append('\u2588');
            else sb.append(ChatColor.GRAY).append('\u2591');
        }
        return sb.toString();
    }

    private String formatTypeName(String type) {
        String lower = type.replace('_', ' ').toLowerCase();
        StringBuilder sb = new StringBuilder();
        for (String word : lower.split(" ")) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(" ");
            }
        }
        return sb.toString().trim();
    }

    private String formatNumber(double val) {
        if (val == Math.floor(val)) return String.valueOf((long) val);
        return String.format("%.1f", val);
    }

    private String formatBonusPct(double val) {
        if (val == Math.floor(val)) return String.valueOf((long) val);
        return String.format("%.1f", val);
    }

    private Material getMaterialForType(String type) {
        for (int i = 0; i < SKILL_TYPES.length; i++) {
            if (SKILL_TYPES[i].equals(type)) return SKILL_MATERIALS[i];
        }
        return Material.NETHER_STAR;
    }
}
