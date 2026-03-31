package com.ironkeep;

import java.util.List;

/**
 * Represents a single step in the player tutorial.
 *
 * Each step has:
 *  - An optional chat message, title, and subtitle shown when the step starts.
 *  - A trigger type that determines what player action advances to the next step.
 *  - An optional guide key referencing a named location in tutorial.yml where
 *    a beacon firework will be fired to direct the player.
 *  - An optional dialogue list — lines shown with delays when the trigger fires
 *    (e.g. warden speech). Advancement happens after the last line.
 */
public class TutorialStep {

    public enum TriggerType {
        /** Advance immediately (no player action needed). */
        AUTO,
        /** Player right-clicks the Warden NPC. */
        INTERACT_WARDEN,
        /** Player opens the Commission Board GUI. */
        OPEN_BOARD,
        /** Player accepts any commission. */
        ACCEPT_COMMISSION,
        /** Player successfully completes any commission. */
        COMPLETE_COMMISSION,
        /** Player ranks up via the Warden GUI. */
        RANK_UP
    }

    private final String id;
    private final String message;          // nullable — chat message sent when step starts
    private final String title;            // nullable — title packet
    private final String subtitle;         // nullable — subtitle packet
    private final String objective;        // nullable — short text shown in the action bar while active
    private final TriggerType trigger;
    private final String guideKey;         // nullable — key into guide-locations map in tutorial.yml
    private final String assignCommission; // nullable — commission ID to assign when trigger fires
    private final List<String> dialogue;   // optional — lines shown with delays when trigger fires

    public TutorialStep(String id, String message, String title, String subtitle,
                        String objective, TriggerType trigger, String guideKey,
                        String assignCommission, List<String> dialogue) {
        this.id = id;
        this.message = message;
        this.title = title;
        this.subtitle = subtitle;
        this.objective = objective;
        this.trigger = trigger;
        this.guideKey = guideKey;
        this.assignCommission = assignCommission;
        this.dialogue = dialogue != null ? dialogue : List.of();
    }

    public String getId()               { return id; }
    public String getMessage()          { return message; }
    public String getTitle()            { return title; }
    public String getSubtitle()         { return subtitle; }
    public String getObjective()        { return objective; }
    public TriggerType getTrigger()     { return trigger; }
    public String getGuideKey()         { return guideKey; }
    public String getAssignCommission() { return assignCommission; }
    public List<String> getDialogue()   { return dialogue; }
}
