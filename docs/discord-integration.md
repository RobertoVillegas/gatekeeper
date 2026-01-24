# Discord Integration Technical Design

**Component:** Discord Bot + Velocity Admin API
**Goal:** Real-time, low-friction admin UX for application review and access management

---

## 1. Design Philosophy

### Priorities (in order)

1. **Instant feedback** — Admins see applications immediately, players get notified instantly
2. **One-click actions** — No typing, no commands, no context switching
3. **Rich context** — All decision-relevant info visible without clicking
4. **Audit by default** — Every action is traceable in Discord history
5. **Graceful degradation** — System works if Discord is down (via fallback commands)

---

## 2. Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                     Docker Network                          │
│                                                             │
│  ┌──────────────────┐         ┌──────────────────┐         │
│  │     Velocity     │         │   Discord Bot    │         │
│  │                  │         │                  │         │
│  │  ┌────────────┐  │  push   │  ┌────────────┐  │         │
│  │  │ Webhook    │──┼────────►│  │ HTTP Server│  │         │
│  │  │ Client     │  │         │  │ (internal) │  │         │
│  │  └────────────┘  │         │  └────────────┘  │         │
│  │                  │         │        │         │         │
│  │  ┌────────────┐  │         │        ▼         │         │
│  │  │ Admin API  │◄─┼─────────┼─── Discord.js   │         │
│  │  │ (Javalin)  │  │ actions │        │         │         │
│  │  └────────────┘  │         │        │         │         │
│  │        │         │         │        ▼         │         │
│  │        ▼         │         │   Discord API   │         │
│  │     SQLite       │         │   (outbound)    │         │
│  └──────────────────┘         └──────────────────┘         │
│                                        │                    │
└────────────────────────────────────────┼────────────────────┘
                                         │
                                         ▼
                                   Discord Servers
```

### Communication Pattern

| Direction | Protocol | Purpose |
|-----------|----------|---------|
| Velocity → Bot | HTTP POST (push) | New applications, status changes |
| Bot → Velocity | HTTP POST (action) | Approve, deny, grant, revoke |
| Bot → Discord | WebSocket (gateway) | Send embeds, receive interactions |

**Why push instead of poll?**

- Instant delivery (< 1 second latency)
- No wasted requests
- Simpler state management
- Better UX for admins

---

## 3. Discord Bot Responsibilities

### What the bot does

- Receive application notifications from Velocity
- Post rich embeds to designated channel
- Handle button/menu interactions
- Forward admin actions to Velocity API
- Update embeds with decision status

### What the bot does NOT do

- Store any state (stateless)
- Make access decisions
- Validate permissions (Velocity does this)
- Query player data directly

---

## 4. Application Embed Design

### New Application Embed

```
┌─────────────────────────────────────────────────────────┐
│ 📋 New Access Request                                   │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  Player        Steve                                    │
│  UUID          550e8400-e29b-41d4-a716-446655440000    │
│  Platform      ☕ Java Edition                          │
│                                                         │
│  ─────────────────────────────────────────────────────  │
│                                                         │
│  Real Name     John Doe                                 │
│  Discord       @johndoe                                 │
│  Invited By    Alice                                    │
│  Notes         Friend from work, plays on weekends      │
│                                                         │
│  ─────────────────────────────────────────────────────  │
│                                                         │
│  Submitted     Jan 23, 2026 at 3:45 PM                 │
│  Status        🟡 Pending                               │
│                                                         │
├─────────────────────────────────────────────────────────┤
│  [✅ Approve]  [🎯 Approve + Select]  [❌ Deny]         │
└─────────────────────────────────────────────────────────┘
```

### Color Coding

| Status | Color | Hex |
|--------|-------|-----|
| Pending | Yellow | `#FEE75C` |
| Approved | Green | `#57F287` |
| Denied | Red | `#ED4245` |

---

## 5. Interaction Flows

### 5.1 Quick Approve (default servers)

```
Admin clicks [✅ Approve]
       │
       ▼
Bot sends: POST /applications/{id}/approve
           { servers: ["survival"], admin: "discord:123" }
       │
       ▼
Velocity: validates, creates entitlements, returns 200
       │
       ▼
Bot updates embed:
  - Color → Green
  - Status → "✅ Approved by @Admin"
  - Buttons → disabled
  - Adds field: "Access: survival"
       │
       ▼
Velocity: notifies player in-game (if online)
  "[Gatekeeper] Your application was approved! You now have access to: survival"
```

### 5.2 Approve with Server Selection

```
Admin clicks [🎯 Approve + Select]
       │
       ▼
Bot shows ephemeral select menu:
  ┌─────────────────────────────────┐
  │ Select servers to grant access  │
  │                                 │
  │ ☑ survival                      │
  │ ☐ smp2                          │
  │ ☐ creative                      │
  │                                 │
  │ [Confirm]  [Cancel]             │
  └─────────────────────────────────┘
       │
       ▼
Admin selects servers, clicks [Confirm]
       │
       ▼
Bot sends: POST /applications/{id}/approve
           { servers: ["survival", "creative"], admin: "discord:123" }
       │
       ▼
(same flow as quick approve)
```

### 5.3 Deny with Reason

```
Admin clicks [❌ Deny]
       │
       ▼
Bot shows modal:
  ┌─────────────────────────────────┐
  │ Deny Application                │
  │                                 │
  │ Reason (optional):              │
  │ ┌─────────────────────────────┐ │
  │ │ Incomplete information      │ │
  │ └─────────────────────────────┘ │
  │                                 │
  │ [Confirm Denial]  [Cancel]      │
  └─────────────────────────────────┘
       │
       ▼
Bot sends: POST /applications/{id}/deny
           { reason: "Incomplete information", admin: "discord:123" }
       │
       ▼
Velocity: updates application, returns 200
       │
       ▼
Bot updates embed:
  - Color → Red
  - Status → "❌ Denied by @Admin"
  - Adds field: "Reason: Incomplete information"
       │
       ▼
Velocity: notifies player in-game (if online)
  "[Gatekeeper] Your application was denied. Reason: Incomplete information"
  "You may reapply in 24 hours."
```

### 5.4 Post-Approval Access Management

After approval, the embed shows a management button:

```
┌─────────────────────────────────────────────────────────┐
│ 📋 Access Request                                       │
├─────────────────────────────────────────────────────────┤
│  Player        Steve                                    │
│  Status        ✅ Approved by @Admin                    │
│  Access        survival, creative                       │
│  Decided       Jan 23, 2026 at 4:02 PM                 │
├─────────────────────────────────────────────────────────┤
│  [🔧 Modify Access]                                     │
└─────────────────────────────────────────────────────────┘
```

Clicking [🔧 Modify Access] shows:

```
┌─────────────────────────────────┐
│ Modify Access for Steve         │
│                                 │
│ ☑ survival    [Revoke]          │
│ ☑ creative    [Revoke]          │
│ ☐ smp2        [Grant]           │
│                                 │
│ [Done]                          │
└─────────────────────────────────┘
```

---

## 6. Slash Commands (Secondary UX)

For actions not tied to a specific application embed:

| Command | Purpose |
|---------|---------|
| `/access player:<name>` | View player's current access |
| `/access grant player:<name> server:<server>` | Grant access directly |
| `/access revoke player:<name> server:<server>` | Revoke access |
| `/applications pending` | List pending applications |
| `/applications search player:<name>` | Find player's applications |

These are escape hatches, not primary UX.

---

## 7. Velocity → Bot Push Events

### 7.1 New Application

```http
POST http://discord-bot:3000/events/application
X-Shared-Secret: <secret>
Content-Type: application/json

{
  "type": "NEW_APPLICATION",
  "application": {
    "id": 42,
    "player": {
      "uuid": "550e8400-e29b-41d4-a716-446655440000",
      "username": "Steve",
      "platform": "JAVA"
    },
    "realName": "John Doe",
    "discordTag": "johndoe",
    "inviter": "Alice",
    "notes": "Friend from work",
    "submittedAt": 1737654321
  },
  "defaultServers": ["survival"],
  "availableServers": ["survival", "smp2", "creative"]
}
```

### 7.2 Application Decided (external trigger)

If approved via in-game command, notify Discord to update any existing embed:

```http
POST http://discord-bot:3000/events/application
X-Shared-Secret: <secret>

{
  "type": "APPLICATION_DECIDED",
  "applicationId": 42,
  "status": "APPROVED",
  "decidedBy": "console",
  "servers": ["survival"],
  "reason": null
}
```

### 7.3 Player Online Status (optional enhancement)

```http
POST http://discord-bot:3000/events/player

{
  "type": "PLAYER_ONLINE",
  "uuid": "550e8400-e29b-41d4-a716-446655440000",
  "username": "Steve"
}
```

Bot can then add 🟢 indicator to pending applications.

---

## 8. Bot → Velocity Admin API

### 8.1 Approve Application

```http
POST http://velocity:8080/api/applications/{id}/approve
X-Shared-Secret: <secret>
Content-Type: application/json

{
  "servers": ["survival", "creative"],
  "admin": "discord:123456789012345678",
  "note": "Vouched by Alice"
}
```

**Response (success):**

```json
{
  "success": true,
  "application": {
    "id": 42,
    "status": "APPROVED",
    "decidedAt": 1737654999
  },
  "entitlements": [
    { "serverId": "survival", "grantedAt": 1737654999 },
    { "serverId": "creative", "grantedAt": 1737654999 }
  ],
  "playerNotified": true
}
```

**Response (error):**

```json
{
  "success": false,
  "error": "APPLICATION_ALREADY_DECIDED",
  "message": "This application was already approved by discord:987654321"
}
```

### 8.2 Deny Application

```http
POST http://velocity:8080/api/applications/{id}/deny
X-Shared-Secret: <secret>

{
  "reason": "Incomplete information",
  "admin": "discord:123456789012345678"
}
```

### 8.3 Grant Entitlement

```http
POST http://velocity:8080/api/entitlements/grant
X-Shared-Secret: <secret>

{
  "uuid": "550e8400-e29b-41d4-a716-446655440000",
  "serverId": "smp2",
  "admin": "discord:123456789012345678",
  "note": "Added per request"
}
```

### 8.4 Revoke Entitlement

```http
POST http://velocity:8080/api/entitlements/revoke
X-Shared-Secret: <secret>

{
  "uuid": "550e8400-e29b-41d4-a716-446655440000",
  "serverId": "creative",
  "admin": "discord:123456789012345678",
  "note": "Temporary access expired"
}
```

### 8.5 Get Pending Applications

```http
GET http://velocity:8080/api/applications?status=PENDING
X-Shared-Secret: <secret>
```

### 8.6 Get Player Access

```http
GET http://velocity:8080/api/players/{uuid}/entitlements
X-Shared-Secret: <secret>
```

---

## 9. Error Handling

### Bot-side errors

| Scenario | Bot Behavior |
|----------|--------------|
| Velocity unreachable | Ephemeral: "⚠️ Could not reach game server. Try again." |
| Invalid secret | Log error, do not expose to user |
| Application already decided | Ephemeral: "This application was already handled." + refresh embed |
| Unknown application | Ephemeral: "Application not found." |

### Velocity-side errors

| Scenario | API Response | Bot Behavior |
|----------|--------------|--------------|
| Invalid JSON | 400 Bad Request | Log, show generic error |
| Missing secret | 401 Unauthorized | Log security event |
| App not found | 404 Not Found | "Application not found" |
| Already decided | 409 Conflict | Show who decided, update embed |
| DB error | 500 Internal | "Server error, try again" |

---

## 10. Discord Bot Configuration

```yaml
# discord-bot/config.yaml

discord:
  token: ${DISCORD_TOKEN}

  # Channel where application embeds are posted
  applicationChannelId: "1234567890123456789"

  # Role required to approve/deny (optional)
  adminRoleId: "9876543210987654321"

velocity:
  apiUrl: "http://velocity:8080/api"
  sharedSecret: ${VELOCITY_API_SECRET}

# Webhook server (receives events from Velocity)
server:
  port: 3000
  host: "0.0.0.0"
```

---

## 11. Velocity Plugin Configuration

```yaml
# velocity/plugins/gatekeeper/config.yml

discord:
  # Enable Discord integration
  enabled: true

  # Bot webhook endpoint (for push events)
  botWebhookUrl: "http://discord-bot:3000/events"

  # Shared secret for API authentication
  sharedSecret: ${API_SECRET}

api:
  # Admin API server
  enabled: true
  port: 8080
  bindAddress: "0.0.0.0"
```

---

## 12. Message Tracking

To update embeds when applications are decided:

### Option A: Store Discord message ID in SQLite

```sql
ALTER TABLE applications ADD COLUMN discord_message_id TEXT;
```

When posting embed, Velocity stores the message ID. When updating, bot uses this to edit the correct message.

### Option B: Bot maintains ephemeral mapping

Bot keeps in-memory map: `applicationId → messageId`

Simpler, but loses mapping on bot restart. Acceptable since:
- Pending applications are rare
- Admins can use `/applications pending` to re-fetch

**Recommendation:** Option A (persist in SQLite) for reliability.

---

## 13. Rate Limiting

### Discord limits

- 50 requests per second per bot
- 5 messages per 5 seconds per channel

### Mitigations

- Batch multiple quick approvals into single embed update
- Queue outgoing messages
- Velocity debounces rapid application submissions (anti-spam)

---

## 14. Audit Trail in Discord

Every decision is visible in Discord history:

```
#access-requests channel

[Embed] New Access Request - Steve - 🟡 Pending
        ↓ 2 hours later
[Embed] Access Request - Steve - ✅ Approved by @Admin
        Access: survival, creative

[Embed] New Access Request - Alex - 🟡 Pending
        ↓ 30 minutes later
[Embed] Access Request - Alex - ❌ Denied by @Mod
        Reason: Unknown player
```

This provides natural audit log visible to all admins.

---

## 15. Graceful Degradation

| Failure | Impact | Mitigation |
|---------|--------|------------|
| Discord API down | Embeds not posted | Applications still saved; use `/apply status` in-game |
| Bot crashed | No new embeds, buttons don't work | Use in-game `/access approve` commands |
| Velocity API down | Bot can't process actions | Ephemeral error; retry later |
| Network partition | Push events fail | Bot can poll `/applications/pending` as fallback |

The system never loses data. Discord is convenience, not requirement.

---

## 16. Implementation Order

### Phase 1: Core API (Velocity)
1. Javalin HTTP server setup
2. Authentication middleware
3. `/api/applications/{id}/approve` endpoint
4. `/api/applications/{id}/deny` endpoint
5. `/api/applications` list endpoint

### Phase 2: Push Events (Velocity)
1. HTTP client for bot webhook
2. Event dispatch on application submit
3. Event dispatch on decision (for external updates)

### Phase 3: Discord Bot
1. Basic bot skeleton (discord.js / JDA)
2. Webhook receiver server
3. Embed posting
4. Button handlers
5. Select menu for server selection
6. Modal for deny reason

### Phase 4: Polish
1. Message ID tracking
2. Embed updates on decision
3. Slash commands
4. Error handling refinement

---

## 17. Technology Choice: Discord Bot

### Recommended: Node.js + discord.js

**Pros:**
- Most Discord bot examples and docs
- Excellent discord.js library
- Fast iteration
- Small footprint

**Alternative: Java + JDA**

**Pros:**
- Same language as plugins
- Can share models/DTOs
- Familiar tooling

**Recommendation:** Node.js for faster development, unless you prefer Java for consistency.

---

## 18. Security Checklist

- [ ] Shared secret is cryptographically random (32+ bytes)
- [ ] Bot webhook only listens on internal network
- [ ] Velocity API only binds to internal network
- [ ] Admin role check in Discord (defense in depth)
- [ ] All admin actions logged with actor identity
- [ ] Rate limiting on API endpoints
- [ ] Input validation on all endpoints

---

## 19. File Structure

```
discord-bot/
├── src/
│   ├── index.ts              # Entry point
│   ├── config.ts             # Configuration loading
│   ├── discord/
│   │   ├── client.ts         # Discord.js client setup
│   │   ├── embeds.ts         # Embed builders
│   │   └── handlers/
│   │       ├── approve.ts    # Approve button handler
│   │       ├── deny.ts       # Deny button/modal handler
│   │       └── modify.ts     # Modify access handler
│   ├── velocity/
│   │   └── api.ts            # Velocity API client
│   └── webhook/
│       └── server.ts         # HTTP server for Velocity events
├── package.json
├── tsconfig.json
└── Dockerfile
```

---

## 20. Open Questions

1. **Multi-channel support?** — Should different servers post to different channels?
2. **Notification preferences?** — Should players opt-in to Discord DM notifications?
3. **Bulk actions?** — Approve/deny multiple applications at once?
4. **Application expiry?** — Auto-deny after X days of no decision?

These can be deferred to v2.

---

## Summary

This design provides:

- **< 1 second** latency from apply to Discord notification
- **One-click** approval for common case
- **Full context** visible without leaving Discord
- **Graceful fallback** to in-game commands
- **Complete audit trail** in Discord history
- **Zero state** in Discord bot (stateless, restartable)

Next step: Implement Phase 1 (Velocity Admin API).
