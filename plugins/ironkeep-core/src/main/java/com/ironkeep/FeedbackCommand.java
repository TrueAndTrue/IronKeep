package com.ironkeep;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * /feedback <message>         — Submit feedback (all players)
 * /feedback list [page]       — Read submitted feedback (OP only)
 * /feedback clear             — Delete all feedback (OP only)
 */
@SuppressWarnings("UnstableApiUsage")
public class FeedbackCommand implements BasicCommand {

    private static final int PAGE_SIZE = 5;
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC);

    private final IronKeepPlugin plugin;
    private final File feedbackFile;

    public FeedbackCommand(IronKeepPlugin plugin) {
        this.plugin = plugin;
        this.feedbackFile = new File(plugin.getDataFolder(), "data/feedback.yml");
    }

    public void register(Commands commands) {
        commands.register("feedback", "Submit feedback or read submissions (OP).", this);
    }

    @Override
    public void execute(CommandSourceStack stack, String[] args) {
        CommandSender sender = stack.getSender();

        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /feedback <message>");
            if (sender.isOp()) {
                sender.sendMessage(ChatColor.YELLOW + "       /feedback list [page]");
                sender.sendMessage(ChatColor.YELLOW + "       /feedback clear");
            }
            return;
        }

        switch (args[0].toLowerCase()) {
            case "list" -> {
                if (!sender.isOp()) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission to do that.");
                    return;
                }
                int page = 1;
                if (args.length >= 2) {
                    try { page = Integer.parseInt(args[1]); } catch (NumberFormatException ignored) {}
                }
                listFeedback(sender, page);
            }
            case "clear" -> {
                if (!sender.isOp()) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission to do that.");
                    return;
                }
                clearFeedback(sender);
            }
            default -> {
                // Treat everything as the message (supports spaces)
                String message = String.join(" ", args);
                submitFeedback(sender, message);
            }
        }
    }

    @Override
    public Collection<String> suggest(CommandSourceStack stack, String[] args) {
        if (args.length == 1 && stack.getSender().isOp()) {
            return List.of("list", "clear");
        }
        return List.of();
    }

    // -------------------------------------------------------------------------

    private void submitFeedback(CommandSender sender, String message) {
        if (message.isBlank()) {
            sender.sendMessage(ChatColor.RED + "Feedback cannot be empty.");
            return;
        }

        YamlConfiguration config = loadFile();
        List<java.util.Map<?, ?>> entries = config.getMapList("entries");

        java.util.Map<String, String> entry = new java.util.LinkedHashMap<>();
        entry.put("player", sender instanceof Player p ? p.getName() : "Console");
        entry.put("uuid",   sender instanceof Player p ? p.getUniqueId().toString() : "console");
        entry.put("time",   FORMATTER.format(Instant.now()) + " UTC");
        entry.put("message", message);

        List<Object> updated = new ArrayList<>(entries);
        updated.add(entry);
        config.set("entries", updated);
        saveFile(config);

        sender.sendMessage(ChatColor.GREEN + "Thank you! Your feedback has been recorded.");
    }

    private void listFeedback(CommandSender sender, int page) {
        YamlConfiguration config = loadFile();
        List<java.util.Map<?, ?>> entries = config.getMapList("entries");

        if (entries.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "No feedback submissions yet.");
            return;
        }

        int total = entries.size();
        int totalPages = (int) Math.ceil((double) total / PAGE_SIZE);
        page = Math.max(1, Math.min(page, totalPages));
        int start = (page - 1) * PAGE_SIZE;
        int end   = Math.min(start + PAGE_SIZE, total);

        sender.sendMessage(ChatColor.GOLD + "--- Feedback (" + page + "/" + totalPages
                + ", " + total + " total) ---");
        for (int i = start; i < end; i++) {
            java.util.Map<?, ?> e = entries.get(i);
            sender.sendMessage(ChatColor.YELLOW + "[" + (i + 1) + "] "
                    + ChatColor.AQUA  + e.get("player")
                    + ChatColor.GRAY  + " · " + e.get("time"));
            sender.sendMessage(ChatColor.WHITE + "    " + e.get("message"));
        }
        if (page < totalPages) {
            sender.sendMessage(ChatColor.GRAY + "  Use /feedback list " + (page + 1) + " for more.");
        }
    }

    private void clearFeedback(CommandSender sender) {
        YamlConfiguration config = loadFile();
        int count = config.getMapList("entries").size();
        config.set("entries", new ArrayList<>());
        saveFile(config);
        sender.sendMessage(ChatColor.GREEN + "Cleared " + count + " feedback entry/entries.");
    }

    // -------------------------------------------------------------------------

    private YamlConfiguration loadFile() {
        if (!feedbackFile.exists()) {
            feedbackFile.getParentFile().mkdirs();
        }
        return YamlConfiguration.loadConfiguration(feedbackFile);
    }

    private void saveFile(YamlConfiguration config) {
        try {
            config.save(feedbackFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save feedback.yml: " + e.getMessage());
        }
    }
}
