package com.gatekeeper.velocity.model;

import java.time.Instant;
import java.util.UUID;

public record GatekeeperPlayer(
    UUID uuid,
    String lastUsername,
    Platform platform,
    Instant firstSeen,
    Instant lastSeen
) {
    public static GatekeeperPlayer create(UUID uuid, String username, Platform platform) {
        Instant now = Instant.now();
        return new GatekeeperPlayer(uuid, username, platform, now, now);
    }

    public GatekeeperPlayer withLastSeen(Instant lastSeen) {
        return new GatekeeperPlayer(uuid, lastUsername, platform, firstSeen, lastSeen);
    }

    public GatekeeperPlayer withUsername(String username) {
        return new GatekeeperPlayer(uuid, username, platform, firstSeen, lastSeen);
    }
}
