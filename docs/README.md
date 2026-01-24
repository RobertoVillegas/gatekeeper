Below is a **technical design document**, written from zero assumptions.
This is **not implementation**; it is the mental and technical blueprint you can validate before any code exists.

---

> **📌 Final Architecture (v1)**
>
> The system consists of **two components only**:
> 1. **Velocity Gatekeeper Plugin** — Authority for all access decisions
> 2. **Discord Bot** — Admin UI for approvals/denials
>
> Paper whitelist sync was removed from scope. Velocity enforcement is sufficient.
>
> See also:
> - [`discord-integration.md`](./discord-integration.md) — Discord bot design
> - [`messages.md`](./messages.md) — In-game chat UX
> - [`operations.md`](./operations.md) — Logging and migrations

---

# Technical Design Document

**Project:** Velocity Gatekeeper (Access Manager)
**Goal:** Centralized access requests, approvals, and per-server entitlements with best UX and strong security

---

## 1. Scope and non-goals

### In scope

- In-game application flow (Java + Bedrock compatible)
- Centralized approval and entitlement logic
- Discord-based admin UX
- Velocity-enforced access control
- SQLite persistence

### Explicitly out of scope

- Web UI
- Multi-proxy clustering
- Complex permissions inheritance
- External auth (OAuth, SSO)
- ~~Paper whitelist mirroring~~ (removed — Velocity enforcement is sufficient)

---

## 2. Technology choices (community standards)

### Language

**Java (recommended for v1)**

Rationale:

- Velocity, Paper, Floodgate ecosystems are Java-first
- Most examples, docs, and community plugins are Java
- Fewer surprises with classloaders, shading, tooling
- Kotlin is fine later, but increases early cognitive load

> Kotlin is common but not “standard.” Java is the safest baseline.

### Build system

**Gradle (Kotlin DSL or Groovy DSL)**

Rationale:

- Official Velocity examples use Gradle
- Excellent dependency management and shading
- Standard in modern MC plugin dev

### Runtime

- Java 17 (Velocity standard)
- Runs inside existing Velocity Docker container

---

## 3. High-level architecture

```
+------------------+
| Discord Bot (UI) |
+--------+---------+
         |
         | HTTP (internal, authenticated)
         v
+---------------------------+
| Velocity Access Manager   |
| - SQLite (authoritative)  |
| - /apply wizard           |
| - Access enforcement      |
+-------------+-------------+
              |
              | private docker network
              v
+---------------------------+
| Paper Server(s)           |
| (optional WhitelistAgent) |
+---------------------------+
```

**Key principle:**
Velocity is the _only_ authority. Everything else is UI or a mirror.

---

## 4. Plugin boundaries

### 4.1 Velocity plugin (mandatory)

**Artifact:** `access-manager-velocity.jar`

Responsibilities:

- Owns SQLite database
- Collects applications
- Stores approval decisions
- Enforces access on server connect
- Exposes admin API for Discord bot
- Sends player notifications

Lives at:

```
/opt/minecraft/velocity/plugins/access-manager-velocity.jar
```

### 4.2 Paper plugin — REMOVED

~~The Paper whitelist agent was originally planned for defense-in-depth.~~

**Decision:** Removed from scope. Velocity enforcement is the single authority.
Backends remain untouched — no plugins required on Paper servers.

---

## 5. Data ownership and persistence

### Database choice

**SQLite (single file)**

Location:

```
/opt/minecraft/velocity/data/access-manager/access.db
```

Ownership:

- Only Velocity writes
- Paper agents read or receive pushed events

### Why SQLite is correct here

- Single writer (Velocity)
- Low write frequency
- No ops overhead
- Transaction-safe
- Easy backups

---

## 6. Domain model (mental model)

### Core concepts

#### Player

- Identified by UUID (Floodgate-safe)
- Has last known username
- Exists independently of approval

#### Application

- One active application per player
- Status: `PENDING | APPROVED | DENIED`
- Immutable submission data
- Mutable decision metadata

#### Entitlement

- `(player, server)`
- Active or revoked
- Independent of application lifecycle

---

## 7. Runtime behavior (conceptual)

### Player join flow

1. Player connects → Velocity → Lobby
2. Velocity checks:
   - Has entitlements?
     - Yes → allow server switch
     - No → remain in Lobby

3. Lobby UI prompts `/apply`

### Application flow

1. Player runs `/apply`
2. Velocity starts chat wizard
3. Data stored as `PENDING`
4. Discord notified

### Approval flow

1. Admin approves in Discord
2. Discord bot calls Velocity API
3. Velocity:
   - Updates application
   - Creates entitlements
   - Notifies player

4. (Optional) Paper agents mirror whitelist

---

## 8. Access enforcement model

### Primary enforcement (authoritative)

**Velocity `ServerPreConnectEvent`**

Rules:

- Lobby: always allowed
- Other servers:
  - Require active entitlement
  - Otherwise redirect to Lobby with message

### Secondary enforcement (optional)

**Paper vanilla whitelist**

Purpose:

- Defense in depth
- Familiar admin safety net
- Not relied upon for correctness

---

## 9. Floodgate / Bedrock compatibility

Rules:

- Always use `player.getUniqueId()`
- Store `player.getUsername()` verbatim
- Never perform Mojang UUID lookups
- Treat Bedrock and Java identically

This design is Floodgate-safe by construction.

---

## 10. Configuration model

### Velocity config (conceptual)

```yaml
database:
  type: sqlite
  path: data/access-manager/access.db

servers:
  crafty_lobby: lobby
  crafty_survival: survival
  crafty_smp2: smp2

discord:
  api_enabled: true
  shared_secret: "..."

application:
  cooldown_hours: 24
  default_servers: [survival]
```

### Paper agent config (conceptual)

```yaml
server_id: survival
database:
  mode: read_only
```

---

## 11. Security model

### Trust boundaries

- Internet → Traefik → Velocity (trusted)
- Velocity → Paper (trusted, private network)
- Discord bot → Velocity API (authenticated)

### Security guarantees

- Backends unreachable externally
- Single decision-maker
- UUID-based identity
- Auditability of approvals
- No RCON exposure

---

## 12. Build artifacts and lifecycle

### Development

- Gradle project
- Produces shaded `.jar`

### Deployment

- Copy `.jar` into plugins folder
- Restart Velocity / Paper servers

### Rollback

- Remove `.jar`
- Restart
- No schema corruption risk (SQLite)

---

## 13. What “creating the .jar” means (mentally)

You are **not** hand-writing bytecode.

You will:

1. Create a Gradle project
2. Write Java classes
3. Run `./gradlew build`
4. Obtain:

   ```
   build/libs/access-manager-velocity.jar
   ```

5. Drop it into `velocity/plugins`

That is the entire lifecycle.

---

## 14. Design invariants (do not break these)

- Velocity is the only authority
- UUIDs, never names, define identity
- Lobby is always accessible
- Entitlements ≠ whitelist entries
- Discord is UI, not logic

If these remain true, the system stays correct.

---

## 15. Next logical step (when you are ready)

Next documents should be:

1. **Repository & project structure**
2. **Plugin lifecycle and event hooks**
3. **Command & wizard state machine**
4. **SQLite schema**
5. **Discord API contract**

Below is the **continuation of the technical design documentation**, picking up exactly where we left off.
This remains **design-level**, not implementation.

---

# Technical Design Documentation (continued)

## 16. Repository and project structure

You will end up with **two independent Gradle projects** (two jars).
They can live in the same Git repository or separate ones; mentally, treat them as separate artifacts.

### Recommended repository layout (single repo)

```
access-manager/
├─ velocity/
│  ├─ build.gradle
│  ├─ settings.gradle
│  └─ src/main/java/...
│
├─ paper-whitelist-agent/
│  ├─ build.gradle
│  ├─ settings.gradle
│  └─ src/main/java/...
│
└─ docs/
   └─ design.md
```

**Why separate projects**

- Different APIs (Velocity vs Paper)
- Different lifecycle models
- Clear responsibility separation
- Easier long-term maintenance

---

## 17. Velocity plugin lifecycle (mental model)

Velocity plugins have **three relevant phases**:

1. **Load**
   - Plugin class is constructed
   - Configuration is read
   - Database initialized

2. **Enable**
   - Event listeners registered
   - Commands registered
   - Discord notifier/API enabled

3. **Runtime**
   - Players join
   - Applications processed
   - Access enforced

Nothing runs before Velocity is fully up.

---

## 18. Core subsystems inside the Velocity plugin

Think of the Velocity plugin as **five internal subsystems**.

```
AccessManager (root)
 ├─ Configuration
 ├─ Persistence (SQLite)
 ├─ Application Flow
 ├─ Access Enforcement
 └─ Admin API / Notifications
```

Each subsystem is independent and testable.

---

## 19. Application flow subsystem

### Responsibilities

- Own the `/apply` command
- Track wizard state per player
- Persist submissions
- Enforce cooldowns and reapplication rules

### Wizard model (conceptual)

Each player can be in **one wizard state** at a time:

```
IDLE
 ↓
ASK_REAL_NAME
 ↓
ASK_DISCORD
 ↓
ASK_INVITER
 ↓
ASK_NOTES
 ↓
CONFIRM
 ↓
SUBMITTED
```

Rules:

- Wizard state is **ephemeral** (in-memory)
- Only final submission is persisted
- Disconnect cancels wizard safely
- `/apply cancel` resets state

---

## 20. Access enforcement subsystem

### Responsibility

Decide whether a player may connect to a given backend server.

### Decision inputs

- Player UUID
- Target server ID
- Active entitlements

### Decision outputs

- ALLOW
- DENY → redirect to Lobby with reason

### Important invariant

Access enforcement **never consults Paper servers or whitelist state**.

Velocity does not trust downstream state.

---

## 21. Server identity abstraction

Velocity server names and ports are **not business identifiers**.

Introduce an abstraction:

```
Velocity Server Name → Logical Server ID
```

Example:

```
crafty:25500 → lobby
crafty:25501 → survival
crafty:25502 → smp2
```

All entitlements, approvals, and UX refer to **logical server IDs**, never ports.

---

## 22. Admin control plane (Discord integration)

### Responsibility

- Surface pending applications
- Allow approval, denial, and toggling
- Never store authority state

### Trust model

- Discord bot is **not trusted**
- Velocity validates every admin action

### Admin actions (conceptual)

- approve(application_id, servers[])
- deny(application_id, reason)
- grant(uuid, server)
- revoke(uuid, server)

---

## 23. Velocity Admin API (conceptual)

The Velocity plugin exposes a **small internal API**.

Conceptually:

```
POST /applications/{id}/approve
POST /applications/{id}/deny
POST /entitlements/{uuid}/grant
POST /entitlements/{uuid}/revoke
GET  /applications/pending
```

Characteristics:

- Internal-only
- Shared-secret authenticated
- Idempotent
- No business logic in Discord bot

---

## 24. Persistence layer (SQLite mental model)

### Ownership

- Velocity is the **only writer**
- Reads are local and synchronous

### Transaction rules

- Application submission = single transaction
- Approval = single transaction
- Entitlement changes = atomic

### Failure model

- SQLite locked → reject request gracefully
- DB corruption → system fails closed (no access)

---

## 25. Paper whitelist agent (optional subsystem)

### Purpose

Mirror Velocity decisions into vanilla whitelist.

### Non-responsibilities

- Does not decide access
- Does not accept admin input
- Does not trust player commands

### Operation model

- Identifies itself as `server_id`
- Periodically syncs entitlements for that server
- Applies whitelist diffs deterministically

If removed entirely, the system still works.

---

## 26. Failure scenarios (designed behavior)

### Velocity plugin crashes

- No access granted to restricted servers
- Lobby still reachable
- Fail-closed

### Paper agent crashes

- Velocity still blocks unauthorized access
- Whitelist may lag
- No security breach

### Discord bot offline

- Players can still apply
- Admins can still approve via fallback Velocity commands
- UX degraded, not broken

---

## 27. Audit and traceability

Every irreversible action should be traceable:

- Who approved
- When
- Which servers
- Optional note

Audit data is append-only.

---

## 28. Operational practices

### Deployment

- Drop `.jar`
- Restart Velocity
- Zero schema migration risk

### Backup

- Back up SQLite file
- Back up Velocity config

### Rollback

- Remove plugin
- Restart
- No residual side effects

---

## 29. Design constraints recap (non-negotiable)

- Velocity is the authority
- UUIDs define identity
- Lobby is always accessible
- Discord is UI, not logic
- Paper is optional defense-in-depth

If these hold, the system remains correct.

---

## 30. Where we go next (choose one)

The next document should be **one** of the following:

1. **Plugin bootstrap & Gradle setup**
   (how a `.jar` actually comes into existence)

2. **Command & wizard state machine spec**
   (exact `/apply` UX behavior)

3. **SQLite schema design**
   (tables, constraints, indexes)

4. **Discord interaction contract**
   (what the bot sends/receives)

Say **which one you want next**, and I will continue in that order.

Below are **documents 2, 3, and 4**, continuing the design documentation.
This is still **architecture and specification**, not code.

---

# 2. Command & Wizard State Machine Specification

## 2.1 Goals

- Zero-mod, zero-web, Bedrock-safe UX
- Clear progress, no ambiguity
- Fully resumable and cancelable
- No accidental partial submissions

---

## 2.2 Command surface (Velocity)

### Player commands

| Command         | Purpose                         |
| --------------- | ------------------------------- |
| `/apply`        | Start application wizard        |
| `/apply status` | View current application status |
| `/apply cancel` | Cancel active wizard            |
| `/apply edit`   | Restart wizard if pending       |

### Staff fallback commands (optional)

| Command                            | Purpose            |
| ---------------------------------- | ------------------ |
| `/access approve <uuid> <servers>` | Manual approval    |
| `/access deny <uuid> [reason]`     | Manual denial      |
| `/access grant <uuid> <server>`    | Add entitlement    |
| `/access revoke <uuid> <server>`   | Remove entitlement |

Discord is preferred; these are escape hatches.

---

## 2.3 Wizard state machine

### States (per-player, in-memory)

```
IDLE
 ↓ /apply
ASK_REAL_NAME
 ↓
ASK_DISCORD
 ↓
ASK_INVITER
 ↓
ASK_NOTES
 ↓
CONFIRM
 ↓
SUBMITTED
```

### State rules

- Only one active wizard per UUID
- Wizard state is cleared on:
  - `/apply cancel`
  - Disconnect
  - Successful submission

- Wizard input times out after configurable idle period (e.g. 5 minutes)

---

## 2.4 Field definitions

| Field       | Required | Notes                     |
| ----------- | -------- | ------------------------- |
| Real name   | Yes      | Free text, length-limited |
| Discord tag | Optional | Free text                 |
| Inviter     | Optional | Free text                 |
| Notes       | Optional | Free text                 |

**Validation rules**

- Strip formatting codes
- Enforce max length (prevents spam/abuse)
- Reject empty required fields

---

## 2.5 Confirmation step

Before submission, the player sees:

```
Real name: John Doe
Discord: johndoe#1234
Inviter: Alice
Notes: —
Type CONFIRM to submit or CANCEL to abort
```

No submission occurs without explicit confirmation.

---

## 2.6 Status UX

### Pending

```
Your access request is pending review.
Submitted: <timestamp>
```

### Approved

```
Approved!
Access granted to: survival, smp2
```

### Denied

```
Denied.
Reason: <optional>
You may reapply in <cooldown>.
```

---

## 3. SQLite Schema Design

## 3.1 Design principles

- UUID-first
- Immutable submissions
- Append-only audit trail
- Simple indexes
- No cascading deletes

---

## 3.2 Tables

### `players`

Stores identity metadata.

| Column        | Type      | Notes          |
| ------------- | --------- | -------------- |
| uuid          | TEXT (PK) | Floodgate-safe |
| last_username | TEXT      | Display only   |
| first_seen    | INTEGER   | Unix timestamp |
| last_seen     | INTEGER   | Unix timestamp |

---

### `applications`

Stores application submissions.

| Column        | Type         | Notes                       |
| ------------- | ------------ | --------------------------- |
| id            | INTEGER (PK) | Auto-increment              |
| uuid          | TEXT (FK)    | → players                   |
| status        | TEXT         | PENDING / APPROVED / DENIED |
| real_name     | TEXT         |                             |
| discord_tag   | TEXT         |                             |
| inviter       | TEXT         |                             |
| notes         | TEXT         |                             |
| created_at    | INTEGER      |                             |
| decided_at    | INTEGER      | Nullable                    |
| decided_by    | TEXT         | Admin identifier            |
| decision_note | TEXT         | Optional                    |

**Constraints**

- One active application per UUID
- Historical rows preserved

---

### `entitlements`

Defines server access.

| Column     | Type    | Notes          |
| ---------- | ------- | -------------- |
| uuid       | TEXT    |                |
| server_id  | TEXT    | Logical server |
| granted_at | INTEGER |                |
| granted_by | TEXT    |                |
| revoked_at | INTEGER | Nullable       |
| note       | TEXT    | Optional       |

**Constraints**

- `(uuid, server_id, revoked_at IS NULL)` unique

---

### `audit_log` (optional but recommended)

| Column    | Type         | Notes                           |
| --------- | ------------ | ------------------------------- |
| id        | INTEGER (PK) |                                 |
| action    | TEXT         | APPROVE / DENY / GRANT / REVOKE |
| uuid      | TEXT         |                                 |
| server_id | TEXT         | Nullable                        |
| actor     | TEXT         | Discord ID / staff              |
| at        | INTEGER      |                                 |
| details   | TEXT         |                                 |

---

## 3.3 Indexes

- `applications(status)`
- `entitlements(uuid)`
- `entitlements(server_id)`

---

## 3.4 Failure behavior

- Any DB error → fail closed
- Partial writes are impossible (transactions)

---

## 4. Discord Interaction Contract

## 4.1 Philosophy

- Discord is **presentation only**
- All authority lives in Velocity
- Bot actions must be idempotent

---

## 4.2 Discord message (application)

### Embed content

- Player name
- UUID
- Real name
- Discord tag
- Inviter
- Notes
- Timestamp
- Source (Java / Bedrock, optional)

---

## 4.3 Admin actions (UI)

### Buttons / menus

- ✅ Approve (default servers)
- 🛠 Approve (select servers)
- ❌ Deny
- 🔄 Modify access

---

## 4.4 API calls (conceptual)

### Approve

```
POST /applications/{id}/approve
{
  "servers": ["survival", "smp2"],
  "admin": "discord:123456789"
}
```

### Deny

```
POST /applications/{id}/deny
{
  "reason": "Incomplete info",
  "admin": "discord:123456789"
}
```

### Grant / revoke

```
POST /entitlements/{uuid}/grant
POST /entitlements/{uuid}/revoke
```

---

## 4.5 Authentication

- Shared secret header
- Internal network only
- Reject unsigned requests

---

## 4.6 Failure UX

- If API fails:
  - Discord shows ephemeral error
  - No partial state change

- Velocity remains consistent

---

## 5. Consistency guarantees

- Approve = atomic (application + entitlements)
- Revoke = immediate effect
- Discord retries are safe
- Paper whitelist mirrors eventually

---

## 6. What we have now

You now have:

- Full UX flow
- Data model
- Admin contract
- Clear authority boundaries

The **next logical step** is now **implementation planning**.

When ready, say **“continue with project bootstrap and Gradle setup”** and we will move from design to executable reality.
