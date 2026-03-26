package com.ironkeep;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public class KitchenCommand implements BasicCommand {

    private final IronKeepPlugin plugin;

    public KitchenCommand(IronKeepPlugin plugin) {
        this.plugin = plugin;
    }

    public void register(Commands commands) {
        commands.register("kitchen", "Kitchen admin commands", this);
    }

    @Override
    public Collection<String> suggest(CommandSourceStack stack, String[] args) {
        if (args.length <= 1) {
            String partial = args.length == 1 ? args[0].toLowerCase() : "";
            return List.of("setup").stream().filter(s -> s.startsWith(partial)).toList();
        }
        return List.of();
    }

    @Override
    public void execute(CommandSourceStack stack, String[] args) {
        if (!(stack.getSender() instanceof Player player)) {
            stack.getSender().sendMessage(ChatColor.RED + "Only players can use this command.");
            return;
        }
        if (!player.isOp()) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return;
        }
        if (args.length == 0 || !args[0].equalsIgnoreCase("setup")) {
            player.sendMessage(ChatColor.GOLD + "Usage: " + ChatColor.YELLOW + "/kitchen setup");
            return;
        }

        World world = player.getWorld();
        plugin.getKitchenManager().setupKitchen(world);
        player.sendMessage(ChatColor.GREEN + "Kitchen set up in world '" + world.getName() + "'.");
    }
}
