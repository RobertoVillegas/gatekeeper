package com.gatekeeper.velocity.database;

import com.gatekeeper.velocity.model.Application;
import com.gatekeeper.velocity.model.ApplicationStatus;

import java.sql.Connection;
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

public class ApplicationRepository {
    private final Database database;

    public ApplicationRepository(Database database) {
        this.database = database;
    }

    public Optional<Application> findById(long id) throws SQLException {
        String sql = "SELECT * FROM applications WHERE id = ?";

        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setLong(1, id);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        }
    }

    public Optional<Application> findPendingByUuid(UUID uuid) throws SQLException {
        String sql = "SELECT * FROM applications WHERE uuid = ? AND status = 'PENDING' ORDER BY created_at DESC LIMIT 1";

        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setString(1, uuid.toString());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        }
    }

    public Optional<Application> findLatestByUuid(UUID uuid) throws SQLException {
        String sql = "SELECT * FROM applications WHERE uuid = ? ORDER BY created_at DESC LIMIT 1";

        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setString(1, uuid.toString());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        }
    }

    public List<Application> findByStatus(ApplicationStatus status) throws SQLException {
        String sql = "SELECT * FROM applications WHERE status = ? ORDER BY created_at ASC";

        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setString(1, status.name());

            try (ResultSet rs = ps.executeQuery()) {
                List<Application> results = new ArrayList<>();
                while (rs.next()) {
                    results.add(mapRow(rs));
                }
                return results;
            }
        }
    }

    public List<Application> findPending() throws SQLException {
        return findByStatus(ApplicationStatus.PENDING);
    }

    public Application insert(Application application) throws SQLException {
        String sql = """
            INSERT INTO applications (uuid, status, real_name, discord_tag, inviter, notes, created_at, discord_message_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """;

        try (PreparedStatement ps = database.getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, application.uuid().toString());
            ps.setString(2, application.status().name());
            ps.setString(3, application.realName());
            ps.setString(4, application.discordTag());
            ps.setString(5, application.inviter());
            ps.setString(6, application.notes());
            ps.setLong(7, application.createdAt().getEpochSecond());
            ps.setString(8, application.discordMessageId());

            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    long id = keys.getLong(1);
                    return new Application(
                        id,
                        application.uuid(),
                        application.status(),
                        application.realName(),
                        application.discordTag(),
                        application.inviter(),
                        application.notes(),
                        application.createdAt(),
                        application.decidedAt(),
                        application.decidedBy(),
                        application.decisionNote(),
                        application.discordMessageId()
                    );
                }
            }
        }

        return application;
    }

    public void updateDecision(Application application) throws SQLException {
        String sql = """
            UPDATE applications
            SET status = ?, decided_at = ?, decided_by = ?, decision_note = ?
            WHERE id = ?
        """;

        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setString(1, application.status().name());

            if (application.decidedAt() != null) {
                ps.setLong(2, application.decidedAt().getEpochSecond());
            } else {
                ps.setNull(2, Types.INTEGER);
            }

            ps.setString(3, application.decidedBy());
            ps.setString(4, application.decisionNote());
            ps.setLong(5, application.id());

            ps.executeUpdate();
        }
    }

    public void updateDiscordMessageId(long applicationId, String messageId) throws SQLException {
        String sql = "UPDATE applications SET discord_message_id = ? WHERE id = ?";

        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setString(1, messageId);
            ps.setLong(2, applicationId);
            ps.executeUpdate();
        }
    }

    public int countByStatus(ApplicationStatus status) throws SQLException {
        String sql = "SELECT COUNT(*) FROM applications WHERE status = ?";

        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setString(1, status.name());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
                return 0;
            }
        }
    }

    private Application mapRow(ResultSet rs) throws SQLException {
        Long decidedAt = rs.getLong("decided_at");
        if (rs.wasNull()) {
            decidedAt = null;
        }

        return new Application(
            rs.getLong("id"),
            UUID.fromString(rs.getString("uuid")),
            ApplicationStatus.valueOf(rs.getString("status")),
            rs.getString("real_name"),
            rs.getString("discord_tag"),
            rs.getString("inviter"),
            rs.getString("notes"),
            Instant.ofEpochSecond(rs.getLong("created_at")),
            decidedAt != null ? Instant.ofEpochSecond(decidedAt) : null,
            rs.getString("decided_by"),
            rs.getString("decision_note"),
            rs.getString("discord_message_id")
        );
    }
}
