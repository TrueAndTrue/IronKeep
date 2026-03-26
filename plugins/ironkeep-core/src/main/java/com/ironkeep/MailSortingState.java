package com.ironkeep;

public class MailSortingState {

    private int totalMail;
    private int delivered;
    private int correct;

    public MailSortingState(int totalMail) {
        this.totalMail = totalMail;
        this.delivered = 0;
        this.correct = 0;
    }

    public int getTotalMail() { return totalMail; }
    public int getDelivered() { return delivered; }
    public int getCorrect() { return correct; }

    public void incrementDelivered() { delivered++; }
    public void incrementCorrect() { correct++; }

    /** Returns true when all mail has been delivered (correct or not). */
    public boolean isComplete() { return delivered >= totalMail; }
}
