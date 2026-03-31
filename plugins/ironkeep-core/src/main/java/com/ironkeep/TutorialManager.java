package com.ironkeep;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Manages the new-player tutorial.
 *
 * Steps are loaded from tutorial.yml and are fully data-driven — add new steps
 * in YAML without touching Java code.
 *
 * Progress is persisted per-player in data/tutorial-progress.yml so it
 * survives server restarts. A value of -1 means the tutorial is complete.
 *
 * Steps with a dialogue list show those lines (with delays) when the trigger
 * fires; advancement happens after the last line.
 *
 * If a step has a guide-key, a floating TextDisplay label is shown at that
 * guide location (per-player, visible only during the tutorial step). The label
 * scales from MAX_SCALE (far away) down to MIN_SCALE (right next to it).
 * When the player arrives within 3 blocks, the guide stops; if the step also
 * has assign-commission, that commission is auto-assigned at arrival.
 */
public class TutorialManager {

    private static final int GUIDE_INTERVAL_TICKS = 100; // 5 seconds (unused, kept for reference)
    private static final double GUIDE_STOP_DISTANCE = 3.0; // blocks
    private static final int TUTORIAL_COMPLETE = -1;
    private static final int DIALOGUE_LINE_DELAY_TICKS = 40; // 2 seconds between lines

    // Guide label scaling constants
    private static final float MAX_SCALE = 5.0f;  // at MAX_DIST or farther
    private static final float MIN_SCALE = 0.6f;  // right next to the location
    private static final double MAX_DIST  = 40.0; // blocks at which max scale is reached
    private static final String GUIDE_LABEL_TAG_PREFIX = "ironkeep_guide_";

    private final IronKeepPlugin plugin;
    private final File tutorialFile;
    private final File progressFile;

    private final List<TutorialStep> steps = new ArrayList<>();
    private final Map<String, Location> guideLocations = new HashMap<>();

    /** Spawned floating TextDisplay entities, one per guide-location key. */
    private final Map<String, TextDisplay> guideLabels = new HashMap<>();

    /** UUID → current step index, or TUTORIAL_COMPLETE (-1) */
    private final Map<UUID, Integer> progress = new HashMap<>();

    /** Active Bukkit repeating task IDs for guide proximity tracking, one per player */
    private final Map<UUID, Integer> guideTasks = new HashMap<>();

    /** Players currently showing dialogue — prevents re-triggering */
    private final Set<UUID> dialogueInProgress = new HashSet<>();

    public TutorialManager(IronKeepPlugin plugin) {
        this.plugin = plugin;
        this.tutorialFile = new File(plugin.getDataFolder(), "tutorial.yml");
        this.progressFile = new File(plugin.getDataFolder(), "data/tutorial-progress.yml");
    }

    // -------------------------------------------------------------------------
    // Load
    // -------------------------------------------------------------------------

    public void load() {
        plugin.saveResource("tutorial.yml", true);
        loadSteps();
        loadProgress();
    }

    /**
     * Resolves guide locations against live World objects and spawns a floating
     * TextDisplay label at each one. Must be called after the world is loaded
     * (1-tick deferred from onEnable).
     */
    public void initGuideLocations() {
        // Remove any leftover guide label entities from a previous startup
        for (org.bukkit.World w : Bukkit.getWorlds()) {
            for (org.bukkit.entity.Entity entity : w.getEntities()) {
                if (entity.getScoreboardTags().stream()
                        .anyMatch(t -> t.startsWith(GUIDE_LABEL_TAG_PREFIX))) {
                    entity.remove();
                }
            }
        }
        guideLabels.clear();
        guideLocations.clear();

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(tutorialFile);
        ConfigurationSection locs = yaml.getConfigurationSection("guide-locations");
        if (locs == null) return;

        for (String key : locs.getKeys(false)) {
            ConfigurationSection l = locs.getConfigurationSection(key);
            if (l == null) continue;
            String worldName = l.getString("world", "world");
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                plugin.getLogger().warning("TutorialManager: guide-location '" + key
                        + "' references unknown world '" + worldName + "' — skipping.");
                continue;
            }
            Location loc = new Location(world,
                    l.getDouble("x"), l.getDouble("y"), l.getDouble("z"));
            guideLocations.put(key, loc);

            String labelText = l.getString("label", key);
            spawnGuideLabel(key, loc, labelText);
        }

        plugin.getLogger().info("TutorialManager: resolved " + guideLocations.size()
                + " guide locations with floating labels.");

        // Scale update — every 10 ticks
        plugin.getServer().getScheduler().runTaskTimer(plugin,
                this::updateAllGuideScales, 30L, 10L);

        // Visibility refresh — re-send showEntity to all online players every 5 seconds.
        // Compensates for players whose clients haven't tracked the entity yet (e.g. chunk
        // not yet loaded when they first joined or when labels were spawned).
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                for (TextDisplay td : guideLabels.values()) {
                    if (td != null && td.isValid()) p.showEntity(plugin, td);
                }
            }
        }, 40L, 100L);
    }

    private void spawnGuideLabel(String key, Location location, String labelText) {
        Location labelLoc = location.clone().add(0, 2.8, 0);
        String tag = GUIDE_LABEL_TAG_PREFIX + key.toLowerCase();
        TextDisplay td = labelLoc.getWorld().spawn(labelLoc, TextDisplay.class, d -> {
            d.text(Component.text("\u2726 " + labelText, NamedTextColor.YELLOW));
            d.setBillboard(Display.Billboard.CENTER);
            d.setDefaultBackground(false);
            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            d.setSeeThrough(false);
            d.setVisibleByDefault(false); // shown to each player via showEntity on join
            d.setPersistent(false);
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(5);
            d.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),
                    new AxisAngle4f(0, 0, 0, 1),
                    new Vector3f(MAX_SCALE, MAX_SCALE, MAX_SCALE),
                    new AxisAngle4f(0, 0, 0, 1)));
            d.getScoreboardTags().add(tag);
        });
        guideLabels.put(key, td);
    }

    private void loadSteps() {
        steps.clear();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(tutorialFile);

        List<?> rawSteps = yaml.getList("tutorial.steps");
        if (rawSteps == null) {
            plugin.getLogger().warning("TutorialManager: tutorial.yml has no 'tutorial.steps' list.");
            return;
        }
        for (Object obj : rawSteps) {
            if (!(obj instanceof Map<?, ?> map)) continue;
            String id               = str(map, "id", "unknown");
            String message          = str(map, "message", null);
            String title            = str(map, "title", null);
            String subtitle         = str(map, "subtitle", null);
            String objective        = str(map, "objective", null);
            String guideKey         = str(map, "guide-key", null);
            String triggerStr       = str(map, "trigger", "AUTO");
            String assignCommission = str(map, "assign-commission", null);

            // Parse optional dialogue list
            List<String> dialogue = new ArrayList<>();
            Object rawDialogue = map.get("dialogue");
            if (rawDialogue instanceof List<?> dl) {
                for (Object line : dl) {
                    if (line != null) dialogue.add(translate(line.toString()));
                }
            }

            TutorialStep.TriggerType trigger;
            try {
                trigger = TutorialStep.TriggerType.valueOf(triggerStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("TutorialManager: unknown trigger '" + triggerStr
                        + "' on step '" + id + "' — defaulting to AUTO.");
                trigger = TutorialStep.TriggerType.AUTO;
            }
            steps.add(new TutorialStep(id, translate(message), translate(title),
                    translate(subtitle), objective, trigger, guideKey,
                    assignCommission, dialogue));
        }
        plugin.getLogger().info("TutorialManager: loaded " + steps.size() + " tutorial steps.");
    }

    private void loadProgress() {
        progress.clear();
        if (!progressFile.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(progressFile);
        for (String key : yaml.getKeys(false)) {
            try {
                progress.put(UUID.fromString(key), yaml.getInt(key));
            } catch (IllegalArgumentException ignored) {}
        }
    }

    private void saveProgress() {
        progressFile.getParentFile().mkdirs();
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, Integer> entry : progress.entrySet()) {
            yaml.set(entry.getKey().toString(), entry.getValue());
        }
        try {
            yaml.save(progressFile);
        } catch (IOException e) {
            plugin.getLogger().severe("TutorialManager: failed to save tutorial-progress.yml: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Public API — called by other systems
    // -------------------------------------------------------------------------

    /**
     * Called when a player joins. Restores their tutorial state (label visibility)
     * if they are still in the tutorial.
     */
    public void onPlayerJoin(Player player) {
        UUID uuid = player.getUniqueId();
        if (!progress.containsKey(uuid)) {
            progress.put(uuid, 0);
            saveProgress();
        }
        // Delay so client has fully loaded before we send entity visibility packets.
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            // Show all area labels — they are permanent and visible to everyone.
            for (TextDisplay td : guideLabels.values()) {
                if (td != null && td.isValid()) player.showEntity(plugin, td);
            }
            if (!isComplete(uuid)) showCurrentStep(player);
        }, 20L);
    }

    /** Called when a player disconnects — cleans up in-memory resources. */
    public void onPlayerQuit(Player player) {
        UUID uuid = player.getUniqueId();
        stopGuide(uuid);
        dialogueInProgress.remove(uuid);
    }

    /**
     * Fire a tutorial trigger for this player.
     * If the player's current step is waiting on this trigger, they advance.
     * Steps with a dialogue list show those lines with delays before advancing.
     */
    public void onTrigger(Player player, TutorialStep.TriggerType trigger) {
        UUID uuid = player.getUniqueId();
        if (isComplete(uuid)) return;
        if (dialogueInProgress.contains(uuid)) return;
        TutorialStep current = getCurrentStep(uuid);
        if (current == null) return;
        if (current.getTrigger() != trigger) return;

        List<String> dialogue = current.getDialogue();
        if (!dialogue.isEmpty()) {
            dialogueInProgress.add(uuid);
            int delay = 0;
            for (String line : dialogue) {
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) player.sendMessage(line);
                }, delay);
                delay += DIALOGUE_LINE_DELAY_TICKS;
            }
            final int finalDelay = delay;
            final TutorialStep stepSnapshot = current;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                dialogueInProgress.remove(uuid);
                if (!player.isOnline()) return;
                if (stepSnapshot.getAssignCommission() != null
                        && !plugin.getCommissionManager().hasActiveCommission(player)) {
                    plugin.getCommissionManager().assignCommission(player, stepSnapshot.getAssignCommission());
                }
                advanceStep(player);
            }, finalDelay);
        } else {
            if (current.getAssignCommission() != null
                    && !plugin.getCommissionManager().hasActiveCommission(player)) {
                plugin.getCommissionManager().assignCommission(player, current.getAssignCommission());
            }
            advanceStep(player);
        }
    }

    /** Resets a player's tutorial progress so they start from the beginning on next join. */
    public void resetTutorial(UUID uuid) {
        progress.remove(uuid);
        stopGuide(uuid);
        dialogueInProgress.remove(uuid);
        saveProgress();
    }

    /** OP admin command: immediately complete the tutorial for a player. */
    public void skipTutorial(Player player) {
        completeTutorial(player);
        player.sendMessage(ChatColor.GRAY + "[Tutorial] Tutorial skipped.");
    }

    public boolean isComplete(UUID uuid) {
        return progress.getOrDefault(uuid, 0) == TUTORIAL_COMPLETE;
    }

    public boolean isInTutorial(UUID uuid) {
        return progress.containsKey(uuid) && !isComplete(uuid);
    }

    /**
     * Returns true only when the player's current tutorial step is waiting for
     * an INTERACT_WARDEN trigger (i.e. the intro dialogue step). Used by
     * WardenListener to block the rank/escape GUI only during that specific step.
     */
    public boolean isWaitingForWardenInteract(UUID uuid) {
        if (isComplete(uuid)) return false;
        TutorialStep step = getCurrentStep(uuid);
        return step != null && step.getTrigger() == TutorialStep.TriggerType.INTERACT_WARDEN;
    }

    // -------------------------------------------------------------------------
    // Internal step logic
    // -------------------------------------------------------------------------

    private TutorialStep getCurrentStep(UUID uuid) {
        int index = progress.getOrDefault(uuid, 0);
        if (index < 0 || index >= steps.size()) return null;
        return steps.get(index);
    }

    private void showCurrentStep(Player player) {
        UUID uuid = player.getUniqueId();
        TutorialStep step = getCurrentStep(uuid);
        if (step == null) {
            completeTutorial(player);
            return;
        }

        // Chat message
        if (step.getMessage() != null) {
            player.sendMessage(step.getMessage());
        }

        // Title / subtitle
        if (step.getTitle() != null || step.getSubtitle() != null) {
            player.sendTitle(
                    step.getTitle() != null ? step.getTitle() : "",
                    step.getSubtitle() != null ? step.getSubtitle() : "",
                    10, 60, 20);
        }

        // Guide proximity tracker
        stopGuide(uuid);
        if (step.getGuideKey() != null) {
            Location guideLoc = guideLocations.get(step.getGuideKey());
            if (guideLoc != null) {
                startGuide(uuid, guideLoc);
            } else {
                plugin.getLogger().warning("TutorialManager: guide-key '" + step.getGuideKey()
                        + "' on step '" + step.getId() + "' has no matching guide-location entry.");
            }
        }

        // AUTO steps advance immediately (next tick so messages are delivered first)
        if (step.getTrigger() == TutorialStep.TriggerType.AUTO) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) advanceStep(player);
            }, 1L);
        }
    }

    private void advanceStep(Player player) {
        UUID uuid = player.getUniqueId();
        int current = progress.getOrDefault(uuid, 0);
        int next = current + 1;
        progress.put(uuid, next);
        saveProgress();
        showCurrentStep(player);
    }

    private void completeTutorial(Player player) {
        UUID uuid = player.getUniqueId();
        progress.put(uuid, TUTORIAL_COMPLETE);
        saveProgress();
        stopGuide(uuid);
        player.sendMessage(ChatColor.GOLD + "[Tutorial] " + ChatColor.GREEN
                + "Tutorial complete. Good luck, prisoner.");
    }

    // -------------------------------------------------------------------------
    // Guide label scaling
    // -------------------------------------------------------------------------

    /**
     * Updates the scale of each guide label based on the nearest player in the same world.
     * Large when far away, small when close, with smooth interpolation.
     * Labels are always visible to all players — scaling applies globally.
     * Called every 10 ticks by a task started in initGuideLocations().
     */
    private void updateAllGuideScales() {
        for (TextDisplay td : guideLabels.values()) {
            if (td == null || !td.isValid()) continue;

            double nearest = Double.MAX_VALUE;
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                if (player.getWorld().equals(td.getWorld())) {
                    nearest = Math.min(nearest, player.getLocation().distance(td.getLocation()));
                }
            }

            if (nearest != Double.MAX_VALUE) {
                float t = (float) Math.min(nearest / MAX_DIST, 1.0);
                float scale = MIN_SCALE + (MAX_SCALE - MIN_SCALE) * t;
                td.setTransformation(new Transformation(
                        new Vector3f(0, 0, 0),
                        new AxisAngle4f(0, 0, 0, 1),
                        new Vector3f(scale, scale, scale),
                        new AxisAngle4f(0, 0, 0, 1)));
            }
        }
    }

    // -------------------------------------------------------------------------
    // Guide (proximity tracking)
    // -------------------------------------------------------------------------

    /**
     * Starts a repeating task that checks whether the player has arrived at the
     * guide location. The distance is also surfaced via getActionBarHint() so the
     * clock action bar can display it in real time.
     */
    private void startGuide(UUID uuid, Location loc) {
        int taskId = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) return;

            TutorialStep step = getCurrentStep(uuid);
            if (step == null) return;

            boolean arrived;
            if (step.getTrigger() == TutorialStep.TriggerType.ACCEPT_COMMISSION
                    && step.getAssignCommission() != null) {
                // For commission-assignment steps, trigger on zone entry rather than
                // a fixed 3-block radius so the commission arrives the moment the
                // player steps into the actual work area.
                CommissionDefinition def = plugin.getCommissionRegistry()
                        .getById(step.getAssignCommission());
                arrived = def != null
                        && plugin.getZoneManager().isInValidZone(p.getLocation(), def.getType());
            } else {
                // For navigation-only steps (e.g. find_warden, mine_coal) keep the
                // original proximity check.
                arrived = loc.getWorld() != null
                        && p.getWorld().equals(loc.getWorld())
                        && p.getLocation().distance(loc) <= GUIDE_STOP_DISTANCE;
            }

            if (arrived) {
                stopGuide(uuid);
                onGuideProximityReached(p);
            }
        }, 10L, 20L).getTaskId(); // check every second
        guideTasks.put(uuid, taskId);
    }

    /**
     * Called when the player arrives within GUIDE_STOP_DISTANCE of the guide location.
     * If the current step has an assign-commission and is waiting for ACCEPT_COMMISSION,
     * the commission is auto-assigned here (which fires the ACCEPT_COMMISSION trigger
     * from within CommissionManager, advancing the tutorial).
     */
    private void onGuideProximityReached(Player player) {
        UUID uuid = player.getUniqueId();
        TutorialStep step = getCurrentStep(uuid);
        if (step == null) return;
        if (step.getAssignCommission() != null
                && step.getTrigger() == TutorialStep.TriggerType.ACCEPT_COMMISSION
                && !plugin.getCommissionManager().hasActiveCommission(player)) {
            plugin.getCommissionManager().assignCommission(player, step.getAssignCommission());
            // assignCommission fires onTrigger(ACCEPT_COMMISSION) internally, which advances the step
        }
    }

    private void stopGuide(UUID uuid) {
        Integer taskId = guideTasks.remove(uuid);
        if (taskId != null) {
            plugin.getServer().getScheduler().cancelTask(taskId);
        }
    }

    // -------------------------------------------------------------------------
    // Action bar integration
    // -------------------------------------------------------------------------

    /**
     * Returns a short string for the action bar while this player has an active
     * tutorial step. If the step has a guide location, the live distance is appended.
     * Returns null if the player has no active tutorial or no objective text.
     *
     * Called every second by IronKeepPlugin's clock task.
     */
    public String getActionBarHint(Player player) {
        UUID uuid = player.getUniqueId();
        if (isComplete(uuid)) return null;
        TutorialStep step = getCurrentStep(uuid);
        if (step == null || step.getObjective() == null) return null;

        String text = step.getObjective();

        // Append live distance when a guide location is active for this step
        if (guideTasks.containsKey(uuid) && step.getGuideKey() != null) {
            Location guideLoc = guideLocations.get(step.getGuideKey());
            if (guideLoc != null && guideLoc.getWorld() != null
                    && player.getWorld().equals(guideLoc.getWorld())) {
                double dist = player.getLocation().distance(guideLoc);
                text += String.format(" (%.1fm)", dist);
            }
        }

        return text;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String translate(String s) {
        if (s == null) return null;
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    private static String str(Map<?, ?> map, String key, String def) {
        Object val = map.get(key);
        return val != null ? val.toString() : def;
    }
}
