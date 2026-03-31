package com.ironkeep;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public class TutorialCommand implements BasicCommand {

    private final IronKeepPlugin plugin;

    public TutorialCommand(IronKeepPlugin plugin) {
        this.plugin = plugin;
    }

    public void register(Commands commands) {
        commands.register("tutorial", "[OP] Manage player tutorials", this);
    }

    @Override
    public Collection<String> suggest(CommandSourceStack stack, String[] args) {
        if (!stack.getSender().isOp()) return List.of();
        if (args.length <= 1) {
            String partial = args.length == 1 ? args[0].toLowerCase() : "";
            return List.of("skip", "reset").stream().filter(s -> s.startsWith(partial)).toList();
        }
        if (args.length == 2) {
            String partial = args[1].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(partial))
                    .toList();
        }
        return List.of();
    }

    @Override
    public void execute(CommandSourceStack stack, String[] args) {
        if (!stack.getSender().isOp()) {
            stack.getSender().sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return;
        }
        if (args.length < 2) {
            stack.getSender().sendMessage(ChatColor.GOLD + "Usage: " + ChatColor.YELLOW
                    + "/tutorial skip <player>" + ChatColor.GRAY + " — skip tutorial for a player");
            stack.getSender().sendMessage(ChatColor.GOLD + "       " + ChatColor.YELLOW
                    + "/tutorial reset <player>" + ChatColor.GRAY + " — restart tutorial from the beginning");
            return;
        }

        String sub = args[0].toLowerCase();
        String targetName = args[1];
        Player target = Bukkit.getPlayerExact(targetName);

        if (target == null) {
            stack.getSender().sendMessage(ChatColor.RED + "Player '" + targetName + "' is not online.");
            return;
        }

        TutorialManager tm = plugin.getTutorialManager();
        switch (sub) {
            case "skip" -> {
                tm.skipTutorial(target);
                stack.getSender().sendMessage(ChatColor.GREEN + "Skipped tutorial for " + ChatColor.YELLOW + target.getName() + ChatColor.GREEN + ".");
            }
            case "reset" -> {
                tm.resetTutorial(target.getUniqueId());
                tm.onPlayerJoin(target);
                stack.getSender().sendMessage(ChatColor.GREEN + "Reset tutorial for " + ChatColor.YELLOW + target.getName() + ChatColor.GREEN + ".");
            }
            default -> stack.getSender().sendMessage(ChatColor.RED + "Unknown subcommand. Use skip or reset.");
        }
    }
}
