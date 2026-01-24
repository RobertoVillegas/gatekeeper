package com.gatekeeper.velocity.database;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

public class AuditLogRepository {
    private final Database database;

    public AuditLogRepository(Database database) {
        this.database = database;
    }

    public void log(String action, UUID uuid, String serverId, String actor, String details) throws SQLException {
        String sql = """
            INSERT INTO audit_log (action, uuid, server_id, actor, at, details)
            VALUES (?, ?, ?, ?, ?, ?)
        """;

        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setString(1, action);
            ps.setString(2, uuid != null ? uuid.toString() : null);
            ps.setString(3, serverId);
            ps.setString(4, actor);
            ps.setLong(5, Instant.now().getEpochSecond());
            ps.setString(6, details);
            ps.executeUpdate();
        }
    }

    public void logApprove(UUID uuid, String actor, String servers) throws SQLException {
        log("APPROVE", uuid, null, actor, "Servers: " + servers);
    }

    public void logDeny(UUID uuid, String actor, String reason) throws SQLException {
        log("DENY", uuid, null, actor, reason);
    }

    public void logGrant(UUID uuid, String serverId, String actor) throws SQLException {
        log("GRANT", uuid, serverId, actor, null);
    }

    public void logRevoke(UUID uuid, String serverId, String actor) throws SQLException {
        log("REVOKE", uuid, serverId, actor, null);
    }
}
