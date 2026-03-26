package com.ironkeep;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@SuppressWarnings("UnstableApiUsage")
public class CommissionCommand implements BasicCommand {

    private static final String PREFIX = ChatColor.GOLD + "[Commission] " + ChatColor.RESET;

    private final IronKeepPlugin plugin;

    public CommissionCommand(IronKeepPlugin plugin) {
        this.plugin = plugin;
    }

    public void register(Commands commands) {
        commands.register("commission", "Manage your commissions", this);
    }

    @Override
    public Collection<String> suggest(CommandSourceStack stack, String[] args) {
        if (args.length <= 1) {
            String partial = args.length == 1 ? args[0].toLowerCase() : "";
            return List.of("new", "status", "complete", "list", "skip").stream()
                    .filter(s -> s.startsWith(partial))
                    .toList();
        }
        return List.of();
    }

    @Override
    public void execute(CommandSourceStack stack, String[] args) {
        if (!(stack.getSender() instanceof Player player)) {
            stack.getSender().sendMessage(ChatColor.RED + "Only players can use this command.");
            return;
        }

        if (args.length == 0) {
            sendUsage(player);
            return;
        }

        switch (args[0].toLowerCase()) {
            case "new" -> plugin.getCommissionManager().assignCommission(player);
            case "status" -> handleStatus(player);
            case "complete" -> plugin.getCommissionManager().completeCommission(player);
            case "list" -> handleList(player);
            case "choose" -> handleChoose(player, args);
            case "skip" -> handleSkip(player);
            default -> sendUsage(player);
        }
    }

    private void handleStatus(Player player) {
        CommissionManager manager = plugin.getCommissionManager();
        if (!manager.hasActiveCommission(player)) {
            player.sendMessage(PREFIX + ChatColor.YELLOW + "You have no active commission. Use "
                    + ChatColor.GOLD + "/commission new" + ChatColor.YELLOW + " to get one.");
            return;
        }
        CommissionDefinition def = manager.getActiveCommission(player);
        PlayerCommissionState state = manager.getPlayerState(player);
        player.sendMessage(PREFIX + ChatColor.GOLD + "Active Commission:");
        player.sendMessage(ChatColor.GOLD + "  Name:     " + ChatColor.YELLOW + def.getDisplayName());
        player.sendMessage(ChatColor.GOLD + "  Task:     " + ChatColor.YELLOW + def.getDescription());
        player.sendMessage(ChatColor.GOLD + "  Progress: " + ChatColor.YELLOW
                + state.getProgress() + "/" + def.getObjectiveQuantity());
        player.sendMessage(ChatColor.GOLD + "  Reward:   " + ChatColor.YELLOW + Math.round(def.getRewardAmount()) + " Gold Coins"
                + (def.getShardsReward() > 0 ? ChatColor.GOLD + " + " + ChatColor.AQUA + Math.round(def.getShardsReward()) + " Shards" : ""));
    }

    private void handleList(Player player) {
        Map<String, CommissionDefinition> all = plugin.getCommissionRegistry().getAll();
        if (all.isEmpty()) {
            player.sendMessage(PREFIX + ChatColor.YELLOW + "No commissions are currently available.");
            return;
        }
        player.sendMessage(ChatColor.GOLD + "--- Available Commissions ---");
        for (CommissionDefinition def : all.values()) {
            player.sendMessage(ChatColor.YELLOW + def.getId()
                    + ChatColor.GRAY + " — " + def.getDisplayName()
                    + ChatColor.GRAY + " (" + def.getDescription() + ")");
        }
    }

    private void handleSkip(Player player) {
        if (!player.isOp()) {
            player.sendMessage(PREFIX + ChatColor.RED + "You don't have permission to skip commissions.");
            return;
        }
        CommissionManager manager = plugin.getCommissionManager();
        if (!manager.hasActiveCommission(player)) {
            player.sendMessage(PREFIX + ChatColor.YELLOW + "You have no active commission to skip.");
            return;
        }
        manager.skipCommission(player);
        player.sendMessage(PREFIX + ChatColor.YELLOW + "Commission skipped.");
    }

    private void handleChoose(Player player, String[] args) {
        if (!player.isOp()) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return;
        }
        if (args.length < 2) {
            player.sendMessage(PREFIX + ChatColor.RED + "Usage: /commission choose <type>");
            player.sendMessage(ChatColor.GRAY + "Types: mining, woodcutting, farming, mail, kitchen");
            return;
        }

        String requestedType = args[1].toLowerCase();
        String commissionType = switch (requestedType) {
            case "mining" -> "MINING";
            case "woodcutting" -> "WOODCUTTING";
            case "farming" -> "FARMING";
            case "mail" -> "MAIL_SORTING";
            case "kitchen", "cooking" -> "COOKING";
            default -> null;
        };

        if (commissionType == null) {
            player.sendMessage(PREFIX + ChatColor.RED + "Unknown type: " + args[1]);
            player.sendMessage(ChatColor.GRAY + "Valid types: mining, woodcutting, farming, mail, kitchen");
            return;
        }

        // Cancel existing commission if any
        if (plugin.getCommissionManager().hasActiveCommission(player)) {
            plugin.getCommissionManager().cancelCommission(player.getUniqueId());
            player.sendMessage(PREFIX + ChatColor.YELLOW + "Previous commission cancelled.");
        }

        // Pick a random commission of the requested type
        String finalType = commissionType;
        java.util.List<CommissionDefinition> matching = plugin.getCommissionRegistry().getAll().values()
                .stream()
                .filter(d -> d.getType().equalsIgnoreCase(finalType))
                .collect(java.util.stream.Collectors.toList());

        if (matching.isEmpty()) {
            player.sendMessage(PREFIX + ChatColor.RED + "No commissions found for type: " + args[1]);
            return;
        }

        CommissionDefinition chosen = matching.get(new java.util.Random().nextInt(matching.size()));
        plugin.getCommissionManager().assignCommission(player, chosen.getId());
    }

    private void sendUsage(Player player) {
        player.sendMessage(ChatColor.GOLD + "--- Commission Commands ---");
        player.sendMessage(ChatColor.YELLOW + "/commission new"
                + ChatColor.GRAY + " — Get a new commission");
        player.sendMessage(ChatColor.YELLOW + "/commission status"
                + ChatColor.GRAY + " — View active commission and progress");
        player.sendMessage(ChatColor.YELLOW + "/commission complete"
                + ChatColor.GRAY + " — Turn in your commission");
        player.sendMessage(ChatColor.YELLOW + "/commission list"
                + ChatColor.GRAY + " — List all available commissions");
        if (player.isOp()) {
            player.sendMessage(ChatColor.YELLOW + "/commission choose <type>"
                    + ChatColor.GRAY + " — [OP] Force a commission type (mining/woodcutting/farming/mail/kitchen)");
        }
    }

}
