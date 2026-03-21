package com.ironkeep;

import org.bukkit.plugin.java.JavaPlugin;

public class IronKeepPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("IronKeep enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("IronKeep disabled.");
    }
}
