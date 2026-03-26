package com.ironkeep;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * /pay <player> <amount>
 *
 * Transfers Shards from the sender to another online player.
 * Gold Coins are non-tradeable and cannot be transferred — this command is Shards-only.
 */
@SuppressWarnings("UnstableApiUsage")
public class PayCommand implements BasicCommand {

    private static final String PREFIX = ChatColor.AQUA + "[Pay] " + ChatColor.RESET;

    private final IronKeepPlugin plugin;

    public PayCommand(IronKeepPlugin plugin) {
        this.plugin = plugin;
    }

    public void register(Commands commands) {
        commands.register("pay", "Send Shards to another player. Usage: /pay <player> <amount>", this);
    }

    @Override
    public void execute(CommandSourceStack stack, String[] args) {
        if (!(stack.getSender() instanceof Player sender)) {
            stack.getSender().sendMessage(ChatColor.RED + "Only players can use this command.");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Usage: /pay <player> <amount>");
            return;
        }

        // Parse amount as whole number (all currency values are integers)
        long amount;
        try {
            amount = Long.parseLong(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Amount must be a whole number.");
            return;
        }

        if (amount <= 0) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Amount must be greater than 0.");
            return;
        }

        // Resolve target player
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Player \"" + args[0] + "\" is not online.");
            return;
        }

        // Cannot pay yourself
        if (target.getUniqueId().equals(sender.getUniqueId())) {
            sender.sendMessage(PREFIX + ChatColor.RED + "You cannot pay yourself.");
            return;
        }

        UUID senderUuid = sender.getUniqueId();
        UUID targetUuid = target.getUniqueId();
        CurrencyManager currency = plugin.getCurrencyManager();

        // Check sufficient balance
        if (!currency.hasShards(senderUuid, amount)) {
            double have = currency.getShards(senderUuid);
            sender.sendMessage(PREFIX + ChatColor.RED + "Insufficient Shards. You have "
                    + ChatColor.WHITE + format(have) + ChatColor.RED + " Shards.");
            return;
        }

        // Perform transfer
        currency.removeShards(senderUuid, amount);
        currency.addShards(targetUuid, amount);

        // Confirm to sender
        sender.sendMessage(PREFIX + ChatColor.WHITE + "You sent "
                + ChatColor.AQUA + format(amount) + " Shards"
                + ChatColor.WHITE + " to " + ChatColor.YELLOW + target.getName() + ChatColor.WHITE + "."
                + ChatColor.GRAY + " (Balance: " + format(currency.getShards(senderUuid)) + " Shards)");

        // Notify receiver
        target.sendMessage(PREFIX + ChatColor.YELLOW + sender.getName()
                + ChatColor.WHITE + " sent you "
                + ChatColor.AQUA + format(amount) + " Shards" + ChatColor.WHITE + "."
                + ChatColor.GRAY + " (Balance: " + format(currency.getShards(targetUuid)) + " Shards)");
    }

    private String format(double amount) {
        return String.format("%,d", Math.round(amount));
    }
}
