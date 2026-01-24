package com.gatekeeper.velocity.wizard;

import java.time.Instant;
import java.util.UUID;

public class WizardSession {
    private final UUID playerUuid;
    private final Instant startedAt;

    private WizardState state;
    private Instant lastActivity;

    // Collected data
    private String realName;
    private String discordTag;
    private String inviter;
    private String notes;

    public WizardSession(UUID playerUuid) {
        this.playerUuid = playerUuid;
        this.startedAt = Instant.now();
        this.lastActivity = Instant.now();
        this.state = WizardState.ASK_REAL_NAME;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public WizardState getState() {
        return state;
    }

    public void setState(WizardState state) {
        this.state = state;
        this.lastActivity = Instant.now();
    }

    public Instant getLastActivity() {
        return lastActivity;
    }

    public void touch() {
        this.lastActivity = Instant.now();
    }

    public boolean isTimedOut(int timeoutSeconds) {
        return Instant.now().isAfter(lastActivity.plusSeconds(timeoutSeconds));
    }

    // Data getters/setters
    public String getRealName() { return realName; }
    public void setRealName(String realName) { this.realName = realName; }

    public String getDiscordTag() { return discordTag; }
    public void setDiscordTag(String discordTag) { this.discordTag = discordTag; }

    public String getInviter() { return inviter; }
    public void setInviter(String inviter) { this.inviter = inviter; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    /**
     * Advance to the next state after processing input.
     */
    public void advance() {
        switch (state) {
            case ASK_REAL_NAME -> setState(WizardState.ASK_DISCORD);
            case ASK_DISCORD -> setState(WizardState.ASK_INVITER);
            case ASK_INVITER -> setState(WizardState.ASK_NOTES);
            case ASK_NOTES -> setState(WizardState.CONFIRM);
            case CONFIRM -> setState(WizardState.SUBMITTED);
            default -> {}
        }
    }

    /**
     * Get the current question number (1-4).
     */
    public int getQuestionNumber() {
        return switch (state) {
            case ASK_REAL_NAME -> 1;
            case ASK_DISCORD -> 2;
            case ASK_INVITER -> 3;
            case ASK_NOTES -> 4;
            default -> 0;
        };
    }
}
