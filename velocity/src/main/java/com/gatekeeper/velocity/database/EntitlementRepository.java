package com.gatekeeper.velocity.database;

import com.gatekeeper.velocity.model.Entitlement;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class EntitlementRepository {
    private final Database database;

    public EntitlementRepository(Database database) {
        this.database = database;
    }

    public List<Entitlement> findActiveByUuid(UUID uuid) throws SQLException {
        String sql = "SELECT * FROM entitlements WHERE uuid = ? AND revoked_at IS NULL";

        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setString(1, uuid.toString());

            try (ResultSet rs = ps.executeQuery()) {
                List<Entitlement> results = new ArrayList<>();
                while (rs.next()) {
                    results.add(mapRow(rs));
                }
                return results;
            }
        }
    }

    public List<String> findActiveServerIdsByUuid(UUID uuid) throws SQLException {
        String sql = "SELECT server_id FROM entitlements WHERE uuid = ? AND revoked_at IS NULL";

        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setString(1, uuid.toString());

            try (ResultSet rs = ps.executeQuery()) {
                List<String> results = new ArrayList<>();
                while (rs.next()) {
                    results.add(rs.getString("server_id"));
                }
                return results;
            }
        }
    }

    public boolean hasActiveEntitlement(UUID uuid, String serverId) throws SQLException {
        String sql = "SELECT 1 FROM entitlements WHERE uuid = ? AND server_id = ? AND revoked_at IS NULL LIMIT 1";

        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, serverId);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public Optional<Entitlement> findActiveByUuidAndServer(UUID uuid, String serverId) throws SQLException {
        String sql = "SELECT * FROM entitlements WHERE uuid = ? AND server_id = ? AND revoked_at IS NULL LIMIT 1";

        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, serverId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        }
    }

    public Entitlement insert(Entitlement entitlement) throws SQLException {
        String sql = """
            INSERT INTO entitlements (uuid, server_id, granted_at, granted_by, note)
            VALUES (?, ?, ?, ?, ?)
        """;

        try (PreparedStatement ps = database.getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, entitlement.uuid().toString());
            ps.setString(2, entitlement.serverId());
            ps.setLong(3, entitlement.grantedAt().getEpochSecond());
            ps.setString(4, entitlement.grantedBy());
            ps.setString(5, entitlement.note());

            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    long id = keys.getLong(1);
                    return new Entitlement(
                        id,
                        entitlement.uuid(),
                        entitlement.serverId(),
                        entitlement.grantedAt(),
                        entitlement.grantedBy(),
                        entitlement.revokedAt(),
                        entitlement.note()
                    );
                }
            }
        }

        return entitlement;
    }

    public void revoke(UUID uuid, String serverId) throws SQLException {
        String sql = "UPDATE entitlements SET revoked_at = ? WHERE uuid = ? AND server_id = ? AND revoked_at IS NULL";

        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setLong(1, Instant.now().getEpochSecond());
            ps.setString(2, uuid.toString());
            ps.setString(3, serverId);
            ps.executeUpdate();
        }
    }

    public void revokeById(long id) throws SQLException {
        String sql = "UPDATE entitlements SET revoked_at = ? WHERE id = ? AND revoked_at IS NULL";

        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setLong(1, Instant.now().getEpochSecond());
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }

    public int countActive() throws SQLException {
        String sql = "SELECT COUNT(*) FROM entitlements WHERE revoked_at IS NULL";

        try (PreparedStatement ps = database.getConnection().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
        }
    }

    public List<Entitlement> findActiveByServerId(String serverId) throws SQLException {
        String sql = "SELECT * FROM entitlements WHERE server_id = ? AND revoked_at IS NULL";

        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setString(1, serverId);

            try (ResultSet rs = ps.executeQuery()) {
                List<Entitlement> results = new ArrayList<>();
                while (rs.next()) {
                    results.add(mapRow(rs));
                }
                return results;
            }
        }
    }

    private Entitlement mapRow(ResultSet rs) throws SQLException {
        Long revokedAt = rs.getLong("revoked_at");
        if (rs.wasNull()) {
            revokedAt = null;
        }

        return new Entitlement(
            rs.getLong("id"),
            UUID.fromString(rs.getString("uuid")),
            rs.getString("server_id"),
            Instant.ofEpochSecond(rs.getLong("granted_at")),
            rs.getString("granted_by"),
            revokedAt != null ? Instant.ofEpochSecond(revokedAt) : null,
            rs.getString("note")
        );
    }
}
