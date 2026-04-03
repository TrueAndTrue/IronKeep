package com.ironkeep;

import java.util.Map;

public class MailSortingState {

    private int totalMail;
    private int delivered;
    private int correct;

    /**
     * Per-session randomized mapping of barrelKey → destination label.
     * Shuffled fresh each commission so barrel positions never mean the same
     * destination twice in a row.
     */
    private Map<String, String> barrelMapping;

    public MailSortingState(int totalMail) {
        this.totalMail = totalMail;
        this.delivered = 0;
        this.correct = 0;
    }

    public int getTotalMail() { return totalMail; }
    public int getDelivered() { return delivered; }
    public int getCorrect() { return correct; }

    public Map<String, String> getBarrelMapping() { return barrelMapping; }
    public void setBarrelMapping(Map<String, String> mapping) { this.barrelMapping = mapping; }

    /** Returns the destination assigned to this barrel for this session, or null if unused. */
    public String getBarrelDestination(String barrelKey) {
        return barrelMapping != null ? barrelMapping.get(barrelKey) : null;
    }

    public void incrementDelivered() { delivered++; }
    public void incrementCorrect() { correct++; }

    /** Returns true when all mail has been delivered (correct or not). */
    public boolean isComplete() { return delivered >= totalMail; }
}
