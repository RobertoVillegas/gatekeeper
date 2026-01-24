-- V001_initial_schema.sql
-- Initial Gatekeeper database schema

CREATE TABLE IF NOT EXISTS players (
    uuid TEXT PRIMARY KEY,
    last_username TEXT NOT NULL,
    platform TEXT NOT NULL DEFAULT 'JAVA',
    first_seen INTEGER NOT NULL,
    last_seen INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS applications (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    uuid TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'PENDING',
    real_name TEXT NOT NULL,
    discord_tag TEXT,
    inviter TEXT,
    notes TEXT,
    created_at INTEGER NOT NULL,
    decided_at INTEGER,
    decided_by TEXT,
    decision_note TEXT,
    discord_message_id TEXT,
    FOREIGN KEY (uuid) REFERENCES players(uuid)
);

CREATE TABLE IF NOT EXISTS entitlements (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    uuid TEXT NOT NULL,
    server_id TEXT NOT NULL,
    granted_at INTEGER NOT NULL,
    granted_by TEXT NOT NULL,
    revoked_at INTEGER,
    note TEXT,
    FOREIGN KEY (uuid) REFERENCES players(uuid)
);

CREATE TABLE IF NOT EXISTS audit_log (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    action TEXT NOT NULL,
    uuid TEXT,
    server_id TEXT,
    actor TEXT NOT NULL,
    at INTEGER NOT NULL,
    details TEXT
);

-- Indexes for common queries
CREATE INDEX IF NOT EXISTS idx_applications_status ON applications(status);
CREATE INDEX IF NOT EXISTS idx_applications_uuid ON applications(uuid);
CREATE INDEX IF NOT EXISTS idx_entitlements_uuid ON entitlements(uuid);
CREATE INDEX IF NOT EXISTS idx_entitlements_server ON entitlements(server_id);
CREATE INDEX IF NOT EXISTS idx_entitlements_active ON entitlements(uuid, server_id) WHERE revoked_at IS NULL;
