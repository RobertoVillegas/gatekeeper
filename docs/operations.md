# Operations: Logging & Database Migrations

**Component:** Velocity Gatekeeper Plugin
**Goal:** Observable, maintainable plugin with safe schema evolution

---

## 1. Logging Strategy

### 1.1 Log Levels

| Level | Purpose | Examples |
|-------|---------|----------|
| `ERROR` | Something failed that shouldn't | DB write failed, API unreachable |
| `WARN` | Recoverable issues | Discord push failed (will retry), invalid input rejected |
| `INFO` | Significant events | Plugin started, application submitted, player approved |
| `DEBUG` | Detailed flow | Wizard state transitions, SQL queries, API requests |

### 1.2 What Gets Logged

#### Startup/Shutdown (INFO)

```
[INFO] Gatekeeper v1.0.0 starting...
[INFO] Database initialized: data/access-manager/access.db
[INFO] Admin API listening on 0.0.0.0:8080
[INFO] Discord webhook configured: http://discord-bot:3000/events
[INFO] Loaded 3 restricted servers: [survival, smp2, creative]
[INFO] Gatekeeper started successfully
```

```
[INFO] Gatekeeper shutting down...
[INFO] Admin API stopped
[INFO] Database connections closed
[INFO] Gatekeeper shutdown complete
```

#### Application Events (INFO)

```
[INFO] Player Steve (550e8400-...) started application wizard
[INFO] Player Steve (550e8400-...) submitted application #42
[INFO] Application #42 approved by discord:123456789 - servers: [survival, creative]
[INFO] Application #42 denied by discord:123456789 - reason: "Incomplete info"
```

#### Access Events (INFO)

```
[INFO] Granted Steve (550e8400-...) access to survival by discord:123456789
[INFO] Revoked Steve (550e8400-...) access to smp2 by console
[INFO] Steve (550e8400-...) blocked from survival - no entitlement
[INFO] Steve (550e8400-...) allowed to survival - has entitlement
```

#### Warnings (WARN)

```
[WARN] Discord webhook push failed (will retry): connection timeout
[WARN] Player Steve (550e8400-...) application rejected: cooldown active (23h remaining)
[WARN] Invalid API request from 10.0.0.5: missing X-Shared-Secret header
[WARN] Application #42 approve failed: already decided
```

#### Errors (ERROR)

```
[ERROR] Database write failed: disk full
[ERROR] Failed to load config.yml: invalid YAML syntax at line 23
[ERROR] Admin API failed to start: port 8080 already in use
```

#### Debug (DEBUG)

```
[DEBUG] Wizard state for 550e8400-...: ASK_REAL_NAME -> ASK_DISCORD
[DEBUG] SQL: INSERT INTO applications (uuid, status, ...) VALUES (?, ?, ...)
[DEBUG] API request: POST /applications/42/approve from discord-bot
[DEBUG] Checking entitlement: player=550e8400-..., server=survival, result=ALLOW
```

### 1.3 Logging Framework

Use **SLF4J** (provided by Velocity):

```java
import org.slf4j.Logger;

public class GatekeeperPlugin {
    private final Logger logger;

    @Inject
    public GatekeeperPlugin(Logger logger) {
        this.logger = logger;
    }

    public void onApplicationSubmit(Application app) {
        logger.info("Player {} ({}) submitted application #{}",
            app.getPlayerName(),
            app.getUuid(),
            app.getId());
    }
}
```

### 1.4 Log Configuration

Velocity uses Log4j2. Users can adjust log levels in:

```
/opt/minecraft/velocity/log4j2.xml
```

Add to show debug logs:

```xml
<Logger name="com.gatekeeper" level="DEBUG" />
```

### 1.5 Sensitive Data

**Never log:**
- Full shared secrets (log first 4 chars max: `abc1****`)
- Player IP addresses (unless needed for security investigation)

**Always log:**
- UUIDs (not sensitive, needed for debugging)
- Admin identifiers (for audit)
- Application IDs

---

## 2. Database Migrations

### 2.1 Approach: Versioned Schema

No ORM. Use a simple version-based migration system.

**Schema version stored in database:**

```sql
CREATE TABLE IF NOT EXISTS schema_version (
    version INTEGER PRIMARY KEY,
    applied_at INTEGER NOT NULL
);
```

### 2.2 Migration System

#### File Structure

```
velocity/src/main/resources/
├── config.yml
└── db/
    ├── V001_initial_schema.sql
    ├── V002_add_discord_message_id.sql
    └── V003_add_platform_column.sql
```

#### Migration Format

Each file is a plain SQL script:

```sql
-- V001_initial_schema.sql
-- Initial database schema

CREATE TABLE players (
    uuid TEXT PRIMARY KEY,
    last_username TEXT NOT NULL,
    first_seen INTEGER NOT NULL,
    last_seen INTEGER NOT NULL
);

CREATE TABLE applications (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    uuid TEXT NOT NULL REFERENCES players(uuid),
    status TEXT NOT NULL DEFAULT 'PENDING',
    real_name TEXT NOT NULL,
    discord_tag TEXT,
    inviter TEXT,
    notes TEXT,
    created_at INTEGER NOT NULL,
    decided_at INTEGER,
    decided_by TEXT,
    decision_note TEXT
);

CREATE TABLE entitlements (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    uuid TEXT NOT NULL,
    server_id TEXT NOT NULL,
    granted_at INTEGER NOT NULL,
    granted_by TEXT NOT NULL,
    revoked_at INTEGER,
    note TEXT
);

CREATE TABLE audit_log (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    action TEXT NOT NULL,
    uuid TEXT,
    server_id TEXT,
    actor TEXT NOT NULL,
    at INTEGER NOT NULL,
    details TEXT
);

CREATE INDEX idx_applications_status ON applications(status);
CREATE INDEX idx_applications_uuid ON applications(uuid);
CREATE INDEX idx_entitlements_uuid ON entitlements(uuid);
CREATE INDEX idx_entitlements_server ON entitlements(server_id);
```

```sql
-- V002_add_discord_message_id.sql
-- Track Discord message ID for embed updates

ALTER TABLE applications ADD COLUMN discord_message_id TEXT;
```

```sql
-- V003_add_platform_column.sql
-- Track player platform (JAVA/BEDROCK)

ALTER TABLE players ADD COLUMN platform TEXT DEFAULT 'JAVA';
```

### 2.3 Migration Runner

```java
public class DatabaseMigrator {
    private final Logger logger;
    private final Connection connection;

    public void migrate() throws SQLException {
        int currentVersion = getCurrentVersion();
        List<Migration> pending = getMigrationsAfter(currentVersion);

        for (Migration migration : pending) {
            logger.info("Applying migration V{}: {}",
                migration.version(),
                migration.description());

            try {
                connection.setAutoCommit(false);
                executeSql(migration.sql());
                recordVersion(migration.version());
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
    }

    private int getCurrentVersion() throws SQLException {
        // Create schema_version table if not exists
        execute("""
            CREATE TABLE IF NOT EXISTS schema_version (
                version INTEGER PRIMARY KEY,
                applied_at INTEGER NOT NULL
            )
        """);

        try (var stmt = connection.createStatement();
             var rs = stmt.executeQuery("SELECT MAX(version) FROM schema_version")) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
        }
    }

    private List<Migration> getMigrationsAfter(int version) {
        // Load SQL files from resources/db/V*.sql
        // Parse version number from filename
        // Return sorted list of migrations > version
    }
}
```

### 2.4 Startup Behavior

```
[INFO] Gatekeeper v1.0.0 starting...
[INFO] Checking database schema...
[INFO] Current schema version: 1
[INFO] Available migrations: [V002, V003]
[INFO] Applying migration V002: add_discord_message_id
[INFO] Migration V002 applied successfully
[INFO] Applying migration V003: add_platform_column
[INFO] Migration V003 applied successfully
[INFO] Database schema up to date (version 3)
```

### 2.5 Migration Rules

1. **Migrations are immutable** — Once released, never modify a migration file
2. **Migrations are additive** — Prefer `ALTER TABLE ADD` over destructive changes
3. **Migrations are transactional** — Each migration runs in a transaction; failure rolls back
4. **Migrations are idempotent** — Running twice has no effect (version check prevents re-run)

### 2.6 Rollback Strategy

SQLite doesn't support `ALTER TABLE DROP COLUMN` easily.

**Strategy: Don't roll back schema. Roll back code.**

- If a new column causes issues, deploy older plugin version
- Old code ignores new columns (forward compatible)
- New code handles missing columns gracefully (backward compatible)

For breaking changes, use a new table:

```sql
-- V004_new_applications_table.sql
-- Rename old table, create new one, migrate data

ALTER TABLE applications RENAME TO applications_v1;

CREATE TABLE applications (
    -- new schema
);

INSERT INTO applications (...)
SELECT ... FROM applications_v1;

-- Keep old table for safety, drop later in V005
```

### 2.7 Backup Before Migration

The plugin logs a reminder:

```
[INFO] Database backup recommended before migration
[INFO] Current database: data/access-manager/access.db
[INFO] Proceeding with migration in 3 seconds...
```

Users can copy the SQLite file to back up:

```bash
cp data/access-manager/access.db data/access-manager/access.db.bak
```

---

## 3. Configuration Validation

### 3.1 Startup Validation

On startup, validate config and fail fast:

```java
public void validateConfig(Config config) throws ConfigException {
    // Required fields
    if (config.getServers().isEmpty()) {
        throw new ConfigException("At least one server must be configured");
    }

    if (config.getDiscord().isEnabled() &&
        config.getDiscord().getSharedSecret().equals("CHANGE_ME")) {
        throw new ConfigException("Discord shared secret must be changed from default");
    }

    // Logical validation
    for (String server : config.getServers().getRestricted()) {
        if (!config.getServers().getMapping().containsValue(server)) {
            logger.warn("Restricted server '{}' not found in server mappings", server);
        }
    }
}
```

### 3.2 Startup Failure Logs

```
[ERROR] Failed to start Gatekeeper: Discord shared secret must be changed from default
[ERROR] Please update config.yml and restart Velocity
```

---

## 4. Health Indicators

### 4.1 Simple Health Check

Exposed via Admin API:

```http
GET http://velocity:8080/api/health
```

Response:

```json
{
  "status": "healthy",
  "version": "1.0.0",
  "database": "connected",
  "uptime": 3600,
  "stats": {
    "pendingApplications": 5,
    "totalPlayers": 42,
    "totalEntitlements": 38
  }
}
```

### 4.2 Logged Periodically (every 5 minutes)

```
[INFO] Gatekeeper health: 5 pending applications, 42 players, 38 entitlements
```

---

## 5. File Locations Summary

| File | Location | Purpose |
|------|----------|---------|
| Plugin JAR | `/opt/minecraft/velocity/plugins/gatekeeper.jar` | Plugin artifact |
| Config | `/opt/minecraft/velocity/plugins/gatekeeper/config.yml` | Configuration |
| Database | `/opt/minecraft/velocity/plugins/gatekeeper/data/access.db` | SQLite database |
| Logs | `/opt/minecraft/velocity/logs/latest.log` | Velocity logs (includes plugin logs) |

---

## 6. Operational Runbook

### Check pending applications

```sql
sqlite3 data/access.db "SELECT * FROM applications WHERE status = 'PENDING';"
```

### View recent approvals

```sql
sqlite3 data/access.db "
  SELECT a.id, p.last_username, a.decided_at, a.decided_by
  FROM applications a
  JOIN players p ON a.uuid = p.uuid
  WHERE a.status = 'APPROVED'
  ORDER BY a.decided_at DESC
  LIMIT 10;
"
```

### Check player entitlements

```sql
sqlite3 data/access.db "
  SELECT e.server_id, e.granted_at, e.granted_by
  FROM entitlements e
  JOIN players p ON e.uuid = p.uuid
  WHERE p.last_username = 'Steve'
  AND e.revoked_at IS NULL;
"
```

### Manual backup

```bash
cp /opt/minecraft/velocity/plugins/gatekeeper/data/access.db \
   /backups/gatekeeper-$(date +%Y%m%d-%H%M%S).db
```

---

## Summary

- **Logging:** SLF4J with INFO for events, DEBUG for details, ERROR for failures
- **Migrations:** Versioned SQL files, applied automatically on startup
- **Rollback:** Code rollback, not schema rollback
- **Health:** Simple `/api/health` endpoint for monitoring
