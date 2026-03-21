package com.ironkeep;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class IronKeepPlugin extends JavaPlugin {

    private CommissionManager commissionManager;

    @SuppressWarnings("UnstableApiUsage")
    @Override
    public void onEnable() {
        saveDefaultConfig();

        commissionManager = new CommissionManager(this);
        commissionManager.load();

        // Paper plugins register commands via the lifecycle manager, not paper-plugin.yml
        LifecycleEventManager<Plugin> manager = this.getLifecycleManager();
        manager.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands commands = event.registrar();
            new CommissionCommand(this).register(commands);
            new BalanceCommand(this).register(commands);
        });

        getLogger().info("IronKeep enabled.");
    }

    @Override
    public void onDisable() {
        if (commissionManager != null) {
            commissionManager.save();
        }
        getLogger().info("IronKeep disabled.");
    }

    public CommissionManager getCommissionManager() {
        return commissionManager;
    }
}
