package com.gatekeeper.velocity.gui;

/**
 * Holds the data collected from the /apply command arguments.
 */
public record ApplicationData(
    String realName,
    String discord,
    String inviter,
    String notes
) {
    public ApplicationData {
        // Normalize empty strings to null
        if (realName != null && realName.isBlank()) realName = null;
        if (discord != null && discord.isBlank()) discord = null;
        if (inviter != null && inviter.isBlank()) inviter = null;
        if (notes != null && notes.isBlank()) notes = null;
    }
}
