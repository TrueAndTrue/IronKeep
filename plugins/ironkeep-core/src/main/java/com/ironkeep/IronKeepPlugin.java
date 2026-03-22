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
    private CommissionBoardManager commissionBoardManager;
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

        commissionBoardManager = new CommissionBoardManager(this);
        commissionBoardManager.load();
        getServer().getPluginManager().registerEvents(new CommissionBoardListener(this, commissionBoardManager), this);
        // Place board blocks after world is ready
        getServer().getScheduler().runTaskLater(this, () -> commissionBoardManager.placeBoards(), 1L);

        StarterKitConfig kitConfig = new StarterKitConfig(this);
        kitConfig.load();
        starterKitManager = new StarterKitManager(this, kitConfig);
        starterKitManager.load();
        getServer().getPluginManager().registerEvents(new StarterKitListener(this, starterKitManager), this);
        getServer().getPluginManager().registerEvents(new WoodcuttingListener(this), this);
        getServer().getPluginManager().registerEvents(new MiningListener(this), this);

        wardenManager = new WardenManager(this);
        getServer().getPluginManager().registerEvents(new WardenListener(this, wardenManager), this);
        getServer().getScheduler().runTaskLater(this, wardenManager::spawnWarden, 20L);

        LifecycleEventManager<Plugin> manager = this.getLifecycleManager();
        manager.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands commands = event.registrar();
            new CommissionCommand(this).register(commands);
            new BalanceCommand(this).register(commands);
            new RemoveTargetCommand().register(commands);
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

    public CommissionBoardManager getCommissionBoardManager() {
        return commissionBoardManager;
    }
}
