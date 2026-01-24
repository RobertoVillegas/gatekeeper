package com.gatekeeper.velocity.database;

import com.gatekeeper.velocity.model.GatekeeperPlayer;
import com.gatekeeper.velocity.model.Platform;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public class PlayerRepository {
    private final Database database;

    public PlayerRepository(Database database) {
        this.database = database;
    }

    public Optional<GatekeeperPlayer> findByUuid(UUID uuid) throws SQLException {
        String sql = "SELECT uuid, last_username, platform, first_seen, last_seen FROM players WHERE uuid = ?";

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

    public Optional<GatekeeperPlayer> findByUsername(String username) throws SQLException {
        String sql = "SELECT uuid, last_username, platform, first_seen, last_seen FROM players WHERE last_username = ? COLLATE NOCASE";

        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setString(1, username);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        }
    }

    public GatekeeperPlayer upsert(GatekeeperPlayer player) throws SQLException {
        String sql = """
            INSERT INTO players (uuid, last_username, platform, first_seen, last_seen)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(uuid) DO UPDATE SET
                last_username = excluded.last_username,
                last_seen = excluded.last_seen
        """;

        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setString(1, player.uuid().toString());
            ps.setString(2, player.lastUsername());
            ps.setString(3, player.platform().name());
            ps.setLong(4, player.firstSeen().getEpochSecond());
            ps.setLong(5, player.lastSeen().getEpochSecond());
            ps.executeUpdate();
        }

        return player;
    }

    public void updateLastSeen(UUID uuid, String username) throws SQLException {
        String sql = "UPDATE players SET last_username = ?, last_seen = ? WHERE uuid = ?";

        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setLong(2, Instant.now().getEpochSecond());
            ps.setString(3, uuid.toString());
            ps.executeUpdate();
        }
    }

    public int countAll() throws SQLException {
        String sql = "SELECT COUNT(*) FROM players";

        try (PreparedStatement ps = database.getConnection().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
        }
    }

    private GatekeeperPlayer mapRow(ResultSet rs) throws SQLException {
        return new GatekeeperPlayer(
            UUID.fromString(rs.getString("uuid")),
            rs.getString("last_username"),
            Platform.valueOf(rs.getString("platform")),
            Instant.ofEpochSecond(rs.getLong("first_seen")),
            Instant.ofEpochSecond(rs.getLong("last_seen"))
        );
    }
}
