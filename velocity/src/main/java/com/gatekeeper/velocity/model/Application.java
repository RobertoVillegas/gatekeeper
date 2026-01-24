package com.gatekeeper.velocity.model;

import java.time.Instant;
import java.util.UUID;

public record Application(
    Long id,
    UUID uuid,
    ApplicationStatus status,
    String realName,
    String discordTag,
    String inviter,
    String notes,
    Instant createdAt,
    Instant decidedAt,
    String decidedBy,
    String decisionNote,
    String discordMessageId
) {
    public static Application createPending(
        UUID uuid,
        String realName,
        String discordTag,
        String inviter,
        String notes
    ) {
        return new Application(
            null,
            uuid,
            ApplicationStatus.PENDING,
            realName,
            discordTag,
            inviter,
            notes,
            Instant.now(),
            null,
            null,
            null,
            null
        );
    }

    public Application approve(String decidedBy, String note) {
        return new Application(
            id, uuid, ApplicationStatus.APPROVED, realName, discordTag,
            inviter, notes, createdAt, Instant.now(), decidedBy, note, discordMessageId
        );
    }

    public Application deny(String decidedBy, String reason) {
        return new Application(
            id, uuid, ApplicationStatus.DENIED, realName, discordTag,
            inviter, notes, createdAt, Instant.now(), decidedBy, reason, discordMessageId
        );
    }

    public Application withDiscordMessageId(String messageId) {
        return new Application(
            id, uuid, status, realName, discordTag, inviter, notes,
            createdAt, decidedAt, decidedBy, decisionNote, messageId
        );
    }

    public boolean isPending() {
        return status == ApplicationStatus.PENDING;
    }
}
