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
    private RankManager rankManager;
    private EscapeManager escapeManager;
    private DailyQuestManager dailyQuestManager;
    private ZoneManager zoneManager;
    private MailRoomManager mailRoomManager;
    private KitchenManager kitchenManager;
    private SkillManager skillManager;
    private BindingWandManager bindingWandManager;

    @SuppressWarnings("UnstableApiUsage")
    @Override
    public void onEnable() {
        saveDefaultConfig();

        commissionRegistry = new CommissionRegistry(this);
        commissionRegistry.load();

        rankManager = new RankManager(this);
        rankManager.load();

        escapeManager = new EscapeManager(this);
        escapeManager.load();

        skillManager = new SkillManager(this);
        skillManager.load();

        dailyQuestManager = new DailyQuestManager(this);
        dailyQuestManager.load();

        zoneManager = new ZoneManager(this);
        zoneManager.load();

        mailRoomManager = new MailRoomManager(this);
        mailRoomManager.load();

        bindingWandManager = new BindingWandManager(this);

        kitchenManager = new KitchenManager(this);
        kitchenManager.load();

        BlockRegenManager blockRegenManager = new BlockRegenManager(this);
        blockRegenManager.load();
        getServer().getPluginManager().registerEvents(blockRegenManager, this);

        currencyManager = new CurrencyManager(this);
        currencyManager.load();

        CommissionStateStore stateStore = new CommissionStateStore(this);
        stateStore.load();

        commissionManager = new CommissionManager(commissionRegistry, stateStore, currencyManager);
        commissionManager.setRankManager(rankManager);
        commissionManager.setEscapeManager(escapeManager);
        commissionManager.setMailRoomManager(mailRoomManager);
        commissionManager.setKitchenManager(kitchenManager);
        commissionManager.setSkillManager(skillManager);

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
        getServer().getPluginManager().registerEvents(new FarmingListener(this), this);
        getServer().getPluginManager().registerEvents(new MailSortingListener(this), this);
        getServer().getPluginManager().registerEvents(new KitchenListener(this), this);
        getServer().getPluginManager().registerEvents(new BindingWandListener(this), this);

        wardenManager = new WardenManager(this);
        getServer().getPluginManager().registerEvents(new WardenListener(this, wardenManager), this);
        getServer().getScheduler().runTaskLater(this, wardenManager::spawnWarden, 20L);


        LifecycleEventManager<Plugin> manager = this.getLifecycleManager();
        manager.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands commands = event.registrar();
            new CommissionCommand(this).register(commands);
            new BalanceCommand(this).register(commands);
            new PayCommand(this).register(commands);
            new RankUpCommand(this).register(commands);
            new RankCommand(this).register(commands);
            new EscapeCommand(this).register(commands);
            new MailRoomCommand(this).register(commands);
            new KitchenCommand(this).register(commands);
            new SkillCommand(this).register(commands);
            DailyQuestListener dql = new DailyQuestListener(this);
            getServer().getPluginManager().registerEvents(dql, this);
            dql.register(commands);
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

    public RankManager getRankManager() {
        return rankManager;
    }

    public EscapeManager getEscapeManager() {
        return escapeManager;
    }

    public DailyQuestManager getDailyQuestManager() {
        return dailyQuestManager;
    }

    public ZoneManager getZoneManager() {
        return zoneManager;
    }

    public MailRoomManager getMailRoomManager() {
        return mailRoomManager;
    }

    public KitchenManager getKitchenManager() {
        return kitchenManager;
    }

    public SkillManager getSkillManager() {
        return skillManager;
    }

    public BindingWandManager getBindingWandManager() {
        return bindingWandManager;
    }
}
