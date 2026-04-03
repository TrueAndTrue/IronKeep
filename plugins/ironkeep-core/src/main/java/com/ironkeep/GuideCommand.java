package com.ironkeep;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * /guide — Opens a help GUI where players can browse topic explanations.
 *
 * Each topic sends a formatted block of chat messages on click.
 * No OP-only content is shown here.
 */
@SuppressWarnings("UnstableApiUsage")
public class GuideCommand implements BasicCommand, Listener {

    private static final String GUI_TITLE = ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "IronKeep Guide";
    private static final int GUI_SIZE = 27;

    private static final Material BORDER = Material.GRAY_STAINED_GLASS_PANE;

    // Slot positions for topic items (centre row of a 27-slot chest)
    private static final int[] TOPIC_SLOTS = { 10, 12, 14, 16, 19, 21, 23 };

    private static class GuideHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    private final IronKeepPlugin plugin;

    public GuideCommand(IronKeepPlugin plugin) {
        this.plugin = plugin;
    }

    public void register(Commands commands) {
        commands.register("guide", "Open the IronKeep player guide.", this);
    }

    @Override
    public void execute(CommandSourceStack stack, String[] args) {
        if (!(stack.getSender() instanceof Player player)) {
            stack.getSender().sendMessage(ChatColor.RED + "Only players can use this command.");
            return;
        }
        player.openInventory(buildGui(player));
    }

    // -------------------------------------------------------------------------
    // GUI construction
    // -------------------------------------------------------------------------

    private Inventory buildGui(Player player) {
        Inventory inv = Bukkit.createInventory(new GuideHolder(), GUI_SIZE, GUI_TITLE);

        // Border panes
        ItemStack border = borderPane();
        for (int i = 0; i < GUI_SIZE; i++) inv.setItem(i, border);

        // Topic items
        inv.setItem(TOPIC_SLOTS[0], topic(Material.BOOK,             ChatColor.YELLOW  + "Commissions",   "How the commission system works."));
        inv.setItem(TOPIC_SLOTS[1], topic(Material.GOLD_INGOT,       ChatColor.GOLD    + "Currency",      "Gold Coins & Shards explained."));
        inv.setItem(TOPIC_SLOTS[2], topic(Material.DIAMOND,          ChatColor.AQUA    + "Ranking Up",    "How to advance your rank."));
        inv.setItem(TOPIC_SLOTS[3], topic(Material.ENDER_PEARL,      ChatColor.LIGHT_PURPLE + "Escape",  "The prestige system."));
        inv.setItem(TOPIC_SLOTS[4], topic(Material.EXPERIENCE_BOTTLE,ChatColor.GREEN   + "Skills",        "The skill tree and bonuses."));
        inv.setItem(TOPIC_SLOTS[5], topic(Material.SUNFLOWER,        ChatColor.YELLOW  + "Daily Rewards", "Daily quest & first-completion bonuses."));
        inv.setItem(TOPIC_SLOTS[6], topic(Material.COMPASS,          ChatColor.WHITE   + "Commands",      "All player commands at a glance."));

        return inv;
    }

    private ItemStack borderPane() {
        ItemStack item = new ItemStack(BORDER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(" ");
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack topic(Material material, String name, String hint) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(List.of(ChatColor.GRAY + hint, "", ChatColor.DARK_GRAY + "Click to read"));
        item.setItemMeta(meta);
        return item;
    }

    // -------------------------------------------------------------------------
    // Click handling
    // -------------------------------------------------------------------------

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof GuideHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        String name = clicked.getItemMeta().getDisplayName();

        // Close first, then send — avoids the inventory flickering open while reading
        player.closeInventory();
        plugin.getServer().getScheduler().runTask(plugin, () -> sendTopic(player, name));
    }

    // -------------------------------------------------------------------------
    // Topic content
    // -------------------------------------------------------------------------

    private void sendTopic(Player player, String name) {
        String stripped = ChatColor.stripColor(name);
        switch (stripped) {
            case "Commissions"   -> sendCommissions(player);
            case "Currency"      -> sendCurrency(player);
            case "Ranking Up"    -> sendRankUp(player);
            case "Escape"        -> sendEscape(player);
            case "Skills"        -> sendSkills(player);
            case "Daily Rewards" -> sendDailyRewards(player);
            case "Commands"      -> sendCommands(player);
        }
    }

    private void sendCommissions(Player player) {
        header(player, "Commissions");
        player.sendMessage(ChatColor.GRAY + "Commissions are your primary source of income in IronKeep.");
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "How it works:");
        player.sendMessage(ChatColor.WHITE + "  1. " + ChatColor.GRAY + "Find a " + ChatColor.YELLOW + "Commission Board" + ChatColor.GRAY + " in the prison and right-click it.");
        player.sendMessage(ChatColor.WHITE + "  2. " + ChatColor.GRAY + "Pick a commission from the board. Your rank determines which are available.");
        player.sendMessage(ChatColor.WHITE + "  3. " + ChatColor.GRAY + "Complete the objective (mine, chop, farm, sort mail, or cook).");
        player.sendMessage(ChatColor.WHITE + "  4. " + ChatColor.GRAY + "Return to the Commission Board to collect your reward.");
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "Commission types:");
        player.sendMessage(ChatColor.WHITE + "  Mining   " + ChatColor.GRAY + "— Break ore blocks in the Mining Area.");
        player.sendMessage(ChatColor.WHITE + "  Woodcutting " + ChatColor.GRAY + "— Chop logs in the Woodcutting Area.");
        player.sendMessage(ChatColor.WHITE + "  Farming  " + ChatColor.GRAY + "— Harvest fully-grown crops in the Farming Area.");
        player.sendMessage(ChatColor.WHITE + "  Mail     " + ChatColor.GRAY + "— Sort mail into the correct barrels (hover a barrel to see its label).");
        player.sendMessage(ChatColor.WHITE + "  Cooking  " + ChatColor.GRAY + "— Arrange ingredients in the correct order at the cauldron.");
        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "Mail and Cooking have an " + ChatColor.GREEN + "accuracy bonus" + ChatColor.GRAY + " — the more correct deliveries, the bigger the reward.");
        footer(player);
    }

    private void sendCurrency(Player player) {
        header(player, "Currency");
        player.sendMessage(ChatColor.GRAY + "IronKeep has two currencies:");
        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "Gold Coins " + ChatColor.GRAY + "(earned from commissions & daily rewards)");
        player.sendMessage(ChatColor.GRAY + "  • Used to rank up and escape.");
        player.sendMessage(ChatColor.GRAY + "  • Cannot be traded between players.");
        player.sendMessage(ChatColor.GRAY + "  • Boosted by your Escape level (permanent multiplier).");
        player.sendMessage("");
        player.sendMessage(ChatColor.LIGHT_PURPLE + "Shards " + ChatColor.GRAY + "(also earned from commissions)");
        player.sendMessage(ChatColor.GRAY + "  • Can be sent to other players with " + ChatColor.YELLOW + "/pay <player> <amount>" + ChatColor.GRAY + ".");
        player.sendMessage(ChatColor.GRAY + "  • Never lost on rank reset or escape.");
        player.sendMessage(ChatColor.GRAY + "  • Check your balance with " + ChatColor.YELLOW + "/balance" + ChatColor.GRAY + ".");
        footer(player);
    }

    private void sendRankUp(Player player) {
        header(player, "Ranking Up");

        RankManager rm = plugin.getRankManager();
        int maxRank = rm.getMaxRank();

        player.sendMessage(ChatColor.GRAY + "Ranks unlock new commission types and areas of the prison.");
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "How to rank up:");
        player.sendMessage(ChatColor.GRAY + "  Earn enough Gold Coins, then talk to the " + ChatColor.AQUA + "Warden" + ChatColor.GRAY + " and click Rank Up.");
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "Ranks:");
        for (int r = 1; r <= maxRank; r++) {
            RankDefinition def = rm.getDefinition(r);
            if (def == null) continue;
            String cost = r == 1 ? ChatColor.GREEN + "Free (starting rank)" : ChatColor.GOLD + String.format("%,d", Math.round(def.getCost())) + " Gold Coins";
            player.sendMessage(ChatColor.WHITE + "  " + r + ". " + ChatColor.AQUA + def.getDisplayName()
                    + ChatColor.GRAY + " — " + cost);
            if (!def.getUnlockedTypes().isEmpty()) {
                player.sendMessage(ChatColor.DARK_GRAY + "       Unlocks: " + String.join(", ", def.getUnlockedTypes()));
            }
        }
        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "You'll receive a notification when you have enough Gold Coins to rank up.");
        footer(player);
    }

    private void sendEscape(Player player) {
        header(player, "Escape (Prestige)");

        EscapeManager em = plugin.getEscapeManager();
        int maxEscape = em.getMaxEscapeLevel();

        player.sendMessage(ChatColor.GRAY + "Escaping is the prestige system of IronKeep.");
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "Requirements:");
        player.sendMessage(ChatColor.GRAY + "  • Reach " + ChatColor.AQUA + "Rank 4 (Trustee)" + ChatColor.GRAY + ".");
        player.sendMessage(ChatColor.GRAY + "  • Have enough Gold Coins for the escape cost.");
        player.sendMessage(ChatColor.GRAY + "  • Talk to the " + ChatColor.AQUA + "Warden" + ChatColor.GRAY + " and click Escape.");
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "What happens:");
        player.sendMessage(ChatColor.GRAY + "  • Your rank resets to 1.");
        player.sendMessage(ChatColor.GRAY + "  • Your Gold Coins are spent (balance resets to 0).");
        player.sendMessage(ChatColor.GRAY + "  • Your " + ChatColor.LIGHT_PURPLE + "Shards are kept" + ChatColor.GRAY + ".");
        player.sendMessage(ChatColor.GRAY + "  • You earn a permanent " + ChatColor.GREEN + "Gold Coin income bonus" + ChatColor.GRAY + ".");
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "Escape levels:");
        for (int e = 1; e <= maxEscape; e++) {
            double cost = em.getEscapeCost(e);
            int bonus = e * 10;
            player.sendMessage(ChatColor.WHITE + "  Lv." + e + " " + ChatColor.GRAY + "— "
                    + ChatColor.GOLD + String.format("%,d", Math.round(cost)) + " Gold Coins"
                    + ChatColor.GRAY + " → +" + ChatColor.GREEN + bonus + "% income");
        }
        footer(player);
    }

    private void sendSkills(Player player) {
        header(player, "Skills");
        player.sendMessage(ChatColor.GRAY + "Each commission type has its own skill that levels up as you work.");
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "Skill bonuses (per level):");
        player.sendMessage(ChatColor.WHITE + "  Gold Coins " + ChatColor.GRAY + "— Each level adds a % bonus to Gold Coin rewards.");
        player.sendMessage(ChatColor.WHITE + "  Shards     " + ChatColor.GRAY + "— Each level adds a % bonus to Shard rewards.");
        player.sendMessage(ChatColor.WHITE + "  Efficiency " + ChatColor.GRAY + "— Higher levels reduce the number of items required.");
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "Skills:");
        player.sendMessage(ChatColor.GRAY + "  Mining · Woodcutting · Farming · Mail Sorting · Cooking");
        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "Open the skill tree with " + ChatColor.YELLOW + "/skills"
                + ChatColor.GRAY + " to see your progress and current bonuses.");
        footer(player);
    }

    private void sendDailyRewards(Player player) {
        header(player, "Daily Rewards");
        player.sendMessage(ChatColor.GRAY + "Two daily bonus systems reward consistent play.");
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "Daily Quest:");
        player.sendMessage(ChatColor.GRAY + "  Type " + ChatColor.GREEN + "\"hi\"" + ChatColor.GRAY + " in chat once per day to earn bonus Gold Coins and Shards.");
        player.sendMessage(ChatColor.GRAY + "  Check your status with " + ChatColor.YELLOW + "/daily" + ChatColor.GRAY + ".");
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "First Completion Bonus:");
        player.sendMessage(ChatColor.GRAY + "  The first time you complete each commission " + ChatColor.ITALIC + "type" + ChatColor.RESET + ChatColor.GRAY + " each day,");
        player.sendMessage(ChatColor.GRAY + "  you earn extra Gold Coins and Skill XP.");
        player.sendMessage(ChatColor.GRAY + "  Resets daily at midnight UTC.");
        footer(player);
    }

    private void sendCommands(Player player) {
        header(player, "Commands");
        cmd(player, "/guide",             "Open this help menu.");
        cmd(player, "/balance",           "Check your Gold Coins and Shards.");
        cmd(player, "/pay <player> <amt>","Send Shards to another player.");
        cmd(player, "/rank",              "View your current rank and next rank cost.");
        cmd(player, "/skills",            "Open the skill tree GUI.");
        cmd(player, "/daily",             "Check your daily quest status.");
        footer(player);
    }

    // -------------------------------------------------------------------------
    // Formatting helpers
    // -------------------------------------------------------------------------

    private void header(Player player, String topic) {
        player.sendMessage("");
        player.sendMessage(ChatColor.DARK_AQUA + ChatColor.BOLD.toString()
                + "━━━━━━━━━━ " + ChatColor.AQUA + ChatColor.BOLD + topic
                + ChatColor.DARK_AQUA + ChatColor.BOLD + " ━━━━━━━━━━");
    }

    private void footer(Player player) {
        player.sendMessage(ChatColor.DARK_AQUA + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("");
    }

    private void cmd(Player player, String command, String description) {
        player.sendMessage(ChatColor.YELLOW + command + ChatColor.GRAY + " — " + description);
    }
}
