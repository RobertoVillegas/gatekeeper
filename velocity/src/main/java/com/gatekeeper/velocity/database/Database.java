package com.gatekeeper.velocity.database;

import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Database {
    private static final Pattern MIGRATION_PATTERN = Pattern.compile("V(\\d+)_(.+)\\.sql");

    private final Logger logger;
    private final Path databasePath;
    private Connection connection;

    public Database(Logger logger, Path dataDirectory, String relativePath) {
        this.logger = logger;
        this.databasePath = dataDirectory.resolve(relativePath);
    }

    public void initialize() throws SQLException, IOException {
        // Ensure parent directories exist
        Files.createDirectories(databasePath.getParent());

        // Explicitly load SQLite driver (relocated by shadow plugin)
        try {
            Class.forName("com.gatekeeper.libs.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new SQLException("SQLite JDBC driver not found", e);
        }

        // Connect to SQLite
        String url = "jdbc:sqlite:" + databasePath.toAbsolutePath();
        connection = DriverManager.getConnection(url);
        connection.setAutoCommit(true);

        logger.info("Database connected: {}", databasePath);

        // Run migrations
        runMigrations();
    }

    public Connection getConnection() {
        return connection;
    }

    public void close() {
        if (connection != null) {
            try {
                connection.close();
                logger.info("Database connection closed");
            } catch (SQLException e) {
                logger.error("Failed to close database connection", e);
            }
        }
    }

    private void runMigrations() throws SQLException, IOException {
        // Create schema_version table if not exists
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS schema_version (
                    version INTEGER PRIMARY KEY,
                    applied_at INTEGER NOT NULL
                )
            """);
        }

        int currentVersion = getCurrentVersion();
        logger.info("Current schema version: {}", currentVersion);

        List<Migration> migrations = loadMigrations();
        List<Migration> pending = migrations.stream()
            .filter(m -> m.version() > currentVersion)
            .sorted(Comparator.comparingInt(Migration::version))
            .toList();

        if (pending.isEmpty()) {
            logger.info("Database schema up to date");
            return;
        }

        logger.info("Applying {} pending migration(s)", pending.size());

        for (Migration migration : pending) {
            applyMigration(migration);
        }

        logger.info("Database schema updated to version {}", getCurrentVersion());
    }

    private int getCurrentVersion() throws SQLException {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT MAX(version) FROM schema_version")) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
        }
    }

    private List<Migration> loadMigrations() throws IOException {
        List<Migration> migrations = new ArrayList<>();

        // Load migration files from resources/db/
        ClassLoader classLoader = getClass().getClassLoader();

        // List files in db/ directory
        try (InputStream is = classLoader.getResourceAsStream("db");
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {

            String fileName;
            while ((fileName = reader.readLine()) != null) {
                Matcher matcher = MIGRATION_PATTERN.matcher(fileName);
                if (matcher.matches()) {
                    int version = Integer.parseInt(matcher.group(1));
                    String description = matcher.group(2);

                    String sql = loadResourceFile("db/" + fileName);
                    migrations.add(new Migration(version, description, sql));
                }
            }
        } catch (NullPointerException e) {
            // Fallback: try to load known migration files directly
            migrations.addAll(loadKnownMigrations());
        }

        return migrations;
    }

    private List<Migration> loadKnownMigrations() throws IOException {
        List<Migration> migrations = new ArrayList<>();

        // Try to load V001
        String v001 = loadResourceFile("db/V001_initial_schema.sql");
        if (v001 != null) {
            migrations.add(new Migration(1, "initial_schema", v001));
        }

        return migrations;
    }

    private String loadResourceFile(String path) throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                return null;
            }
            return new BufferedReader(new InputStreamReader(is))
                .lines()
                .collect(Collectors.joining("\n"));
        }
    }

    private void applyMigration(Migration migration) throws SQLException {
        logger.info("Applying migration V{}: {}", migration.version(), migration.description());

        try {
            connection.setAutoCommit(false);

            // Execute migration SQL (split by semicolons for multiple statements)
            try (Statement stmt = connection.createStatement()) {
                for (String sql : splitStatements(migration.sql())) {
                    if (!sql.isBlank()) {
                        stmt.execute(sql);
                    }
                }
            }

            // Record migration version
            try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO schema_version (version, applied_at) VALUES (?, ?)")) {
                ps.setInt(1, migration.version());
                ps.setLong(2, System.currentTimeMillis() / 1000);
                ps.executeUpdate();
            }

            connection.commit();
            logger.info("Migration V{} applied successfully", migration.version());

        } catch (SQLException e) {
            connection.rollback();
            logger.error("Migration V{} failed: {}", migration.version(), e.getMessage());
            throw e;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private List<String> splitStatements(String sql) {
        // Simple split by semicolons (doesn't handle semicolons in strings, but OK for our migrations)
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String line : sql.split("\n")) {
            String trimmed = line.trim();

            // Skip comments
            if (trimmed.startsWith("--")) {
                continue;
            }

            current.append(line).append("\n");

            if (trimmed.endsWith(";")) {
                statements.add(current.toString().trim());
                current = new StringBuilder();
            }
        }

        // Add any remaining SQL
        if (!current.toString().isBlank()) {
            statements.add(current.toString().trim());
        }

        return statements;
    }

    private record Migration(int version, String description, String sql) {}
}
