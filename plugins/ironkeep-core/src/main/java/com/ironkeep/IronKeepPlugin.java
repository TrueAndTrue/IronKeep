package com.ironkeep;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class IronKeepPlugin extends JavaPlugin {

    private CommissionRegistry commissionRegistry;
    private CurrencyManager currencyManager;
    private CommissionManager commissionManager;
    private StarterKitManager starterKitManager;
    private WardenManager wardenManager;

    @SuppressWarnings("UnstableApiUsage")
    @Override
    public void onEnable() {
        saveDefaultConfig();

        commissionRegistry = new CommissionRegistry(this);
        commissionRegistry.load();

        currencyManager = new CurrencyManager(this);
        currencyManager.load();

        CommissionStateStore stateStore = new CommissionStateStore(this);
        stateStore.load();

        commissionManager = new CommissionManager(commissionRegistry, stateStore, currencyManager);

        StarterKitConfig kitConfig = new StarterKitConfig(this);
        kitConfig.load();
        starterKitManager = new StarterKitManager(this, kitConfig);
        starterKitManager.load();
        getServer().getPluginManager().registerEvents(new StarterKitListener(starterKitManager), this);

        wardenManager = new WardenManager(this);
        getServer().getPluginManager().registerEvents(new WardenListener(this, wardenManager), this);
        getServer().getScheduler().runTaskLater(this, wardenManager::spawnWarden, 1L);

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
        getLogger().info("IronKeep disabled.");
    }

    public CommissionRegistry getCommissionRegistry() {
        return commissionRegistry;
    }

    public CurrencyManager getCurrencyManager() {
        return currencyManager;
    }

    public CommissionManager getCommissionManager() {
        return commissionManager;
    }
}
