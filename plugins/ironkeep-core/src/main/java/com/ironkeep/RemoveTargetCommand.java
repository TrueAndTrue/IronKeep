package com.ironkeep;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

@SuppressWarnings("UnstableApiUsage")
public class RemoveTargetCommand implements BasicCommand {

    public void register(Commands commands) {
        commands.register("removetarget", "Remove the entity you are looking at", this);
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

        Entity target = player.getTargetEntity(10);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "No entity found within 10 blocks.");
            return;
        }

        String name = target.customName() != null
                ? net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(target.customName())
                : target.getType().name();
        target.remove();
        player.sendMessage(ChatColor.GREEN + "Removed: " + ChatColor.YELLOW + name
                + ChatColor.GRAY + " (" + target.getType().name() + ")");
    }
}
