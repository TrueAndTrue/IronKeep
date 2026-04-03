package com.ironkeep;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Displays a persistent right-side sidebar scoreboard for each player showing:
 *   Player Name / Rank / Escape Level / Gold Coins / Shards
 *
 * Each player gets an individual Scoreboard so the values are player-specific.
 * The sidebar refreshes every second via a scheduler task.
 *
 * Line layout (top → bottom, score descending):
 *   Score 5  — Player Name
 *   Score 4  — Rank
 *   Score 3  — Escape Level
 *   Score 2  — (blank separator)
 *   Score 1  — Gold Coins
 *   Score 0  — Shards
 */
public class SidebarManager implements Listener {

    private static final String OBJECTIVE_NAME = "ik_sidebar";

    // Unique fake-player-name entries, one per line slot (6 lines + blank = 6 total)
    // These are invisible color codes used as placeholders; prefix holds the real text.
    private static final String[] ENTRIES = {
        "§0§r",   // score 5 — player name
        "§1§r",   // score 4 — rank
        "§2§r",   // score 3 — escape
        "§3§r",   // score 2 — blank
        "§4§r",   // score 1 — gold
        "§5§r",   // score 0 — shards
    };

    private final IronKeepPlugin plugin;
    private final Map<UUID, Scoreboard> playerBoards = new HashMap<>();
    // Tracks the threshold (Gold Coin cost) the player was last notified about,
    // so the milestone ping fires exactly once per threshold crossing.
    private final Map<UUID, Long> notifiedThreshold = new HashMap<>();

    public SidebarManager(IronKeepPlugin plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    public void start() {
        // Assign scoreboards to any players already online (e.g. plugin reload)
        for (Player player : Bukkit.getOnlinePlayers()) {
            setup(player);
        }

        // Refresh all sidebars every second
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                update(player);
            }
        }, 20L, 20L);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Slight delay so other managers (rank, currency) have loaded the player's data
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> setup(player), 2L);
        // Commission reminder — wait a bit longer so CommissionStateStore is fully loaded
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> sendCommissionReminder(player), 20L);
    }

    private void sendCommissionReminder(Player player) {
        if (!player.isOnline()) return;
        CommissionManager mgr = plugin.getCommissionManager();
        if (mgr == null || !mgr.hasActiveCommission(player)) return;
        CommissionDefinition def = mgr.getActiveCommission(player);
        PlayerCommissionState state = mgr.getPlayerState(player);
        int progress = state != null ? state.getProgress() : 0;
        int required = state != null ? state.getEffectiveQuantity(def.getObjectiveQuantity()) : def.getObjectiveQuantity();
        player.sendMessage(ChatColor.GOLD + "[Commission] " + ChatColor.YELLOW + "Active: "
                + ChatColor.WHITE + def.getDisplayName()
                + ChatColor.GRAY + " — " + progress + "/" + required + " complete.");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        playerBoards.remove(event.getPlayer().getUniqueId());
    }

    // -------------------------------------------------------------------------
    // Setup / Update
    // -------------------------------------------------------------------------

    private void setup(Player player) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();

        Objective obj = board.registerNewObjective(
                OBJECTIVE_NAME,
                Criteria.DUMMY,
                ChatColor.GOLD + "" + ChatColor.BOLD + "IronKeep");
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        // Register a team per line — the team prefix holds the visible text
        for (int i = 0; i < ENTRIES.length; i++) {
            Team team = board.registerNewTeam("ik_line" + i);
            team.addEntry(ENTRIES[i]);
        }

        playerBoards.put(player.getUniqueId(), board);
        player.setScoreboard(board);
        update(player);
    }

    /** Refreshes all line text for the given player. Safe to call every second. */
    public void update(Player player) {
        Scoreboard board = playerBoards.get(player.getUniqueId());
        if (board == null) return;
        Objective obj = board.getObjective(OBJECTIVE_NAME);
        if (obj == null) return;

        UUID uuid = player.getUniqueId();
        RankManager rankManager     = plugin.getRankManager();
        EscapeManager escapeManager = plugin.getEscapeManager();
        CurrencyManager currency    = plugin.getCurrencyManager();

        int rankNum         = rankManager.getPlayerRank(uuid);
        RankDefinition rank = rankManager.getDefinition(rankNum);
        String rankName     = rank != null ? rank.getDisplayName() : "Rank " + rankNum;
        int escapeLevel     = escapeManager.getEscapeLevel(uuid);
        long gold           = Math.round(currency.getBalance(uuid));
        long shards         = Math.round(currency.getShards(uuid));

        // Set line text via team prefix (entry string is invisible)
        setPrefix(board, 0, ChatColor.YELLOW + player.getName());
        setPrefix(board, 1, ChatColor.GRAY   + "Rank: "    + ChatColor.GREEN + rankName);
        setPrefix(board, 2, ChatColor.GRAY   + "Escape: "  + ChatColor.AQUA  + "Lv." + escapeLevel);
        setPrefix(board, 3, "");  // blank separator
        setPrefix(board, 4, ChatColor.GRAY   + "Gold: "    + ChatColor.GOLD  + String.format("%,d", gold));
        setPrefix(board, 5, ChatColor.GRAY   + "Shards: "  + ChatColor.LIGHT_PURPLE + String.format("%,d", shards));

        checkMilestonePing(player, uuid, rankNum, escapeLevel, gold);

        // Register scores (idempotent — Bukkit creates/updates in place)
        obj.getScore(ENTRIES[0]).setScore(5);
        obj.getScore(ENTRIES[1]).setScore(4);
        obj.getScore(ENTRIES[2]).setScore(3);
        obj.getScore(ENTRIES[3]).setScore(2);
        obj.getScore(ENTRIES[4]).setScore(1);
        obj.getScore(ENTRIES[5]).setScore(0);
    }

    // -------------------------------------------------------------------------
    // Milestone ping
    // -------------------------------------------------------------------------

    /**
     * Sends a one-time chat notification when the player's Gold Coin balance first
     * reaches the cost of their next rank-up, or (at Rank 4 only) their next escape.
     * Resets automatically if their balance drops below the threshold again.
     */
    private void checkMilestonePing(Player player, UUID uuid, int rankNum, int escapeLevel, long gold) {
        RankManager rankManager     = plugin.getRankManager();
        EscapeManager escapeManager = plugin.getEscapeManager();

        long threshold;
        String message;

        int maxRank = rankManager.getMaxRank();
        if (rankNum >= maxRank) {
            // Only show escape ping at max rank
            int maxEscape = escapeManager.getMaxEscapeLevel();
            if (escapeLevel >= maxEscape) {
                notifiedThreshold.remove(uuid);
                return;
            }
            int nextEscape = escapeLevel + 1;
            threshold = Math.round(escapeManager.getEscapeCost(nextEscape));
            message = ChatColor.LIGHT_PURPLE + "✦ You can now Escape! Talk to the Warden. ("
                    + ChatColor.YELLOW + String.format("%,d", threshold) + " GP"
                    + ChatColor.LIGHT_PURPLE + ")";
        } else {
            int nextRank = rankNum + 1;
            RankDefinition nextDef = rankManager.getDefinition(nextRank);
            if (nextDef == null) return;
            threshold = Math.round(nextDef.getCost());
            message = ChatColor.GOLD + "✦ You can now rank up to "
                    + ChatColor.GREEN + nextDef.getDisplayName()
                    + ChatColor.GOLD + "! Talk to the Warden. ("
                    + ChatColor.YELLOW + String.format("%,d", threshold) + " GP"
                    + ChatColor.GOLD + ")";
        }

        Long last = notifiedThreshold.get(uuid);
        if (gold >= threshold) {
            if (last == null || last != threshold) {
                player.sendMessage(message);
                notifiedThreshold.put(uuid, threshold);
            }
        } else if (last != null && last == threshold) {
            // Balance dropped below threshold (e.g. after escape resets gold) — allow re-ping
            notifiedThreshold.remove(uuid);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void setPrefix(Scoreboard board, int lineIndex, String text) {
        Team team = board.getTeam("ik_line" + lineIndex);
        if (team != null) {
            team.setPrefix(text);
        }
    }
}
