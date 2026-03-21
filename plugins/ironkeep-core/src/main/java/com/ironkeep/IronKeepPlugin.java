package com.ironkeep;

import org.bukkit.plugin.java.JavaPlugin;

public class IronKeepPlugin extends JavaPlugin {

    private CommissionManager commissionManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        commissionManager = new CommissionManager(this);
        commissionManager.load();

        getCommand("commission").setExecutor(new CommissionCommand(this));
        getCommand("balance").setExecutor(new BalanceCommand(this));

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
