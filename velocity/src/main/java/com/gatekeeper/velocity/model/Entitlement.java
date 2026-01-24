package com.gatekeeper.velocity.model;

import java.time.Instant;
import java.util.UUID;

public record Entitlement(
    Long id,
    UUID uuid,
    String serverId,
    Instant grantedAt,
    String grantedBy,
    Instant revokedAt,
    String note
) {
    public static Entitlement grant(UUID uuid, String serverId, String grantedBy, String note) {
        return new Entitlement(
            null,
            uuid,
            serverId,
            Instant.now(),
            grantedBy,
            null,
            note
        );
    }

    public boolean isActive() {
        return revokedAt == null;
    }

    public Entitlement revoke() {
        return new Entitlement(id, uuid, serverId, grantedAt, grantedBy, Instant.now(), note);
    }
}
