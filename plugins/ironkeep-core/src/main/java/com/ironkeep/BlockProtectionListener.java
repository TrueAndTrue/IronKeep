package com.ironkeep;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

/**
 * Cancels all block breaks by non-OP players by default.
 * Commission listeners run at NORMAL priority and un-cancel valid commission breaks.
 */
public class BlockProtectionListener implements Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!(event.getPlayer().isOp())) {
            event.setCancelled(true);
        }
    }
}
