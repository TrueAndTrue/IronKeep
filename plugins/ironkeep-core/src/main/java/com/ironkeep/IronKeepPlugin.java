package com.ironkeep;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.entity.Player;
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
    private DailyBonusManager dailyBonusManager;

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

        dailyBonusManager = new DailyBonusManager(this);
        dailyBonusManager.load();

        dailyQuestManager = new DailyQuestManager(this);
        dailyQuestManager.load();

        zoneManager = new ZoneManager(this);
        zoneManager.load();

        mailRoomManager = new MailRoomManager(this);
        mailRoomManager.load();

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
        commissionManager.setDailyBonusManager(dailyBonusManager);

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

        // Enable daylight cycle, lock weather to clear
        getServer().getScheduler().runTaskLater(this, () -> {
            for (World world : getServer().getWorlds()) {
                @SuppressWarnings("removal")
                boolean unusedTime = world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, true);
                @SuppressWarnings("removal")
                boolean unusedWeather = world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
                world.setStorm(false);
                world.setThundering(false);
            }
        }, 1L);

        // Clock action bar — update every 20 ticks (1 second) to stay visible
        getServer().getScheduler().runTaskTimer(this, () -> {
            World world = getServer().getWorlds().isEmpty() ? null : getServer().getWorlds().get(0);
            if (world == null) return;
            Component clockText = buildClockText(world.getTime());
            for (Player player : getServer().getOnlinePlayers()) {
                player.sendActionBar(clockText);
            }
        }, 20L, 20L);

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

    public DailyBonusManager getDailyBonusManager() {
        return dailyBonusManager;
    }

    /**
     * Converts Minecraft world ticks to a 12-hour clock string, rounded to the nearest
     * 30-minute interval. Tick 0 = 6:00 AM, 6000 = 12:00 PM, 18000 = 12:00 AM.
     */
    private Component buildClockText(long ticks) {
        // Convert ticks to total minutes past midnight (tick 0 = 6:00 AM)
        int totalMinutes = (int) ((ticks / 1000.0 * 60 + 360) % 1440);

        // Round to nearest 30 minutes
        int rounded = ((totalMinutes + 15) / 30) * 30;
        if (rounded >= 1440) rounded = 0;

        int hour24 = rounded / 60;
        int minute = rounded % 60;

        // 12-hour format
        String period = hour24 < 12 ? "AM" : "PM";
        int hour12 = hour24 % 12;
        if (hour12 == 0) hour12 = 12;
        String time = String.format("%d:%02d %s", hour12, minute, period);

        // Day (6 AM - 6 PM) = gold sun, Night = gray moon
        boolean isDay = hour24 >= 6 && hour24 < 18;
        String icon = isDay ? "\u2600" : "\u263E"; // ☀ or ☾
        TextColor color = isDay ? NamedTextColor.GOLD : NamedTextColor.GRAY;

        return Component.text(icon + " " + time, color);
    }
}
