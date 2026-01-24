# In-Game Messages & Chat Interface Design

**Component:** Velocity Gatekeeper Plugin
**Goal:** Bedrock-compatible, clear, accessible chat UX for the application wizard

---

## 1. Bedrock Compatibility Constraints

### What works across Java AND Bedrock (via Geyser/Floodgate)

| Feature | Java | Bedrock | Notes |
|---------|------|---------|-------|
| Plain text chat | ✅ | ✅ | Universal |
| Color codes (`§c`, `&c`) | ✅ | ✅ | Works via Geyser translation |
| Bold, italic, underline | ✅ | ✅ | `§l`, `§o`, `§n` |
| Clickable text (click events) | ✅ | ❌ | **Not supported on Bedrock** |
| Hover text (hover events) | ✅ | ❌ | **Not supported on Bedrock** |
| Forms (Floodgate Forms API) | ❌ | ✅ | Bedrock-only native UI |
| Book UI | ✅ | ⚠️ | Partial support |
| Action bar | ✅ | ✅ | Works |
| Titles/subtitles | ✅ | ✅ | Works |

### Design Decision

**Use plain chat for both platforms.**

Rationale:
- Clickable text doesn't work on Bedrock
- Forms API only works on Bedrock (not Java)
- Chat is the universal common denominator
- Simple, predictable, testable

Future enhancement: Detect platform and use Forms for Bedrock, clickable chat for Java.

---

## 2. Color Palette

### Semantic Colors

| Purpose | Color | Code | Hex |
|---------|-------|------|-----|
| Plugin prefix | Gold | `§6` | `#FFAA00` |
| Bracket | Dark Gray | `§8` | `#555555` |
| Success | Green | `§a` | `#55FF55` |
| Error | Red | `§c` | `#FF5555` |
| Warning | Yellow | `§e` | `#FFFF55` |
| Info | Aqua | `§b` | `#55FFFF` |
| Prompt | White | `§f` | `#FFFFFF` |
| Input echo | Gray | `§7` | `#AAAAAA` |
| Emphasis | Gold | `§6` | `#FFAA00` |

### Prefix

All plugin messages use this prefix:

```
§8[§6Gatekeeper§8]§r
```

Rendered: `[Gatekeeper] `

---

## 3. Wizard Flow Messages

### 3.1 Starting the Wizard

**Command:** `/apply`

**Already has pending application:**
```
§8[§6Gatekeeper§8]§r §eYou already have a pending application.
§7Use §f/apply status §7to check its status.
```

**Already approved:**
```
§8[§6Gatekeeper§8]§r §aYou already have access to the server!
§7Servers: §fsurvival, creative
```

**Cooldown active (after denial):**
```
§8[§6Gatekeeper§8]§r §cYou cannot apply yet.
§7You may reapply in §f23 hours, 45 minutes§7.
```

**Starting wizard:**
```
§8[§6Gatekeeper§8]§r §aStarting access application...

§7Answer the following questions by typing in chat.
§7Type §fcancel §7at any time to abort.

§8─────────────────────────────────────
```

---

### 3.2 Question Prompts

Each question follows this format:

```
§6Question N of 4

§f<Question text>
§7<Hint or format guidance>

§8▶ §fType your answer:
```

---

**Question 1: Real Name (required)**

```
§6Question 1 of 4

§fWhat is your real name?
§7This helps us know who you are. First name is fine.

§8▶ §fType your answer:
```

**On empty input:**
```
§8[§6Gatekeeper§8]§r §cThis field is required. Please enter your name.
```

**On too long (>50 chars):**
```
§8[§6Gatekeeper§8]§r §cThat's too long. Please keep it under 50 characters.
```

**On valid input:**
```
§8[§6Gatekeeper§8]§r §7Real name: §fJohn Doe

```

---

**Question 2: Discord Tag (optional)**

```
§6Question 2 of 4

§fWhat is your Discord username?
§7Optional. Type §fskip §7to leave blank.

§8▶ §fType your answer:
```

**On skip:**
```
§8[§6Gatekeeper§8]§r §7Discord: §8(skipped)

```

---

**Question 3: Inviter (optional)**

```
§6Question 3 of 4

§fWho invited you to the server?
§7Optional. Type §fskip §7if no one.

§8▶ §fType your answer:
```

---

**Question 4: Notes (optional)**

```
§6Question 4 of 4

§fAnything else you'd like us to know?
§7Optional. Type §fskip §7to leave blank.

§8▶ §fType your answer:
```

---

### 3.3 Confirmation

```
§8─────────────────────────────────────

§6Review Your Application

§7Real name:    §fJohn Doe
§7Discord:      §fjohndoe
§7Invited by:   §fAlice
§7Notes:        §fFriend from work

§8─────────────────────────────────────

§aType §fconfirm §ato submit your application.
§cType §fcancel §cto abort.
```

---

### 3.4 Submission

**On confirm:**
```
§8[§6Gatekeeper§8]§r §aApplication submitted!

§7Your request is now pending review.
§7You'll be notified when an admin responds.
§7Use §f/apply status §7to check anytime.
```

**On cancel:**
```
§8[§6Gatekeeper§8]§r §7Application cancelled.
§7You can start again with §f/apply§7.
```

---

### 3.5 Timeout

If player doesn't respond within timeout (5 minutes):

```
§8[§6Gatekeeper§8]§r §cApplication wizard timed out.
§7You can start again with §f/apply§7.
```

---

### 3.6 Disconnect

If player disconnects during wizard:
- Wizard state is silently cleared
- No message sent (player is gone)
- On reconnect, player starts fresh

---

## 4. Status Messages

### `/apply status`

**No application:**
```
§8[§6Gatekeeper§8]§r §7You have no application on record.
§7Use §f/apply §7to request access.
```

**Pending:**
```
§8[§6Gatekeeper§8]§r §eYour application is pending review.

§7Submitted: §fJan 23, 2026 at 3:45 PM
§7Status:    §e⏳ Pending

§7An admin will review it soon.
```

**Approved:**
```
§8[§6Gatekeeper§8]§r §aYour application was approved!

§7Decided:   §fJan 23, 2026 at 5:12 PM
§7Status:    §a✓ Approved
§7Access:    §fsurvival, creative

§7You can now join these servers.
```

**Denied:**
```
§8[§6Gatekeeper§8]§r §cYour application was denied.

§7Decided:   §fJan 23, 2026 at 4:30 PM
§7Status:    §c✗ Denied
§7Reason:    §fIncomplete information

§7You may reapply in §f23 hours, 45 minutes§7.
```

---

## 5. Notification Messages

### Player Approved (sent when online)

```
§8─────────────────────────────────────

§8[§6Gatekeeper§8]§r §a§lGood news!

§7Your access request has been §aapproved§7!
§7You now have access to: §fsurvival, creative

§7To join, use §f/server survival

§8─────────────────────────────────────
```

Plus a title (visible overlay):

```
Title:    §a§lApproved!
Subtitle: §7You now have server access
```

### Player Denied (sent when online)

```
§8─────────────────────────────────────

§8[§6Gatekeeper§8]§r §cApplication Denied

§7Your access request was denied.
§7Reason: §fIncomplete information

§7You may reapply in §f24 hours§7.

§8─────────────────────────────────────
```

Plus a title:

```
Title:    §c§lDenied
Subtitle: §7Check chat for details
```

---

## 6. Access Enforcement Messages

### Attempting to join restricted server without access

```
§8[§6Gatekeeper§8]§r §cYou don't have access to that server.

§7Use §f/apply §7to request access.
```

Player remains in Lobby.

### Attempting to join while application pending

```
§8[§6Gatekeeper§8]§r §eYour application is still pending.

§7Please wait for an admin to review it.
```

---

## 7. Admin Command Messages

### `/access grant <player> <server>`

**Success:**
```
§8[§6Gatekeeper§8]§r §aGranted §fSteve §aaccess to §fsurvival§a.
```

**Player not found:**
```
§8[§6Gatekeeper§8]§r §cPlayer not found: §fStevee
```

**Already has access:**
```
§8[§6Gatekeeper§8]§r §eSteve §ealready has access to §fsurvival§e.
```

### `/access revoke <player> <server>`

**Success:**
```
§8[§6Gatekeeper§8]§r §cRevoked §fSteve§c's access to §fsurvival§c.
```

### `/access approve <player> [servers...]`

**Success:**
```
§8[§6Gatekeeper§8]§r §aApproved §fSteve§a's application.
§7Granted access to: §fsurvival, creative
```

**No pending application:**
```
§8[§6Gatekeeper§8]§r §cNo pending application for §fSteve§c.
```

### `/access deny <player> [reason]`

**Success:**
```
§8[§6Gatekeeper§8]§r §cDenied §fSteve§c's application.
§7Reason: §fIncomplete information
```

---

## 8. Error Messages

### Generic error

```
§8[§6Gatekeeper§8]§r §cSomething went wrong. Please try again.
```

### Database error

```
§8[§6Gatekeeper§8]§r §cCould not save your application. Please try again later.
```

### No permission (for admin commands)

```
§8[§6Gatekeeper§8]§r §cYou don't have permission to do that.
```

---

## 9. Input Handling

### Chat Listener Behavior

During active wizard:
1. Intercept all chat messages from that player
2. Do NOT broadcast to other players
3. Process as wizard input
4. Cancel the chat event

### Input Sanitization

- Strip color codes from input (`§` sequences)
- Trim whitespace
- Enforce max length per field (50 chars for name, 100 for notes)
- Reject empty strings for required fields

### Special Keywords

These are case-insensitive:

| Keyword | Action |
|---------|--------|
| `cancel` | Abort wizard |
| `skip` | Skip optional field |
| `confirm` | Submit application (confirmation step only) |

---

## 10. Configuration (messages.yml)

All messages are configurable:

```yaml
prefix: "&8[&6Gatekeeper&8]&r "

wizard:
  start: |
    &aStarting access application...

    &7Answer the following questions by typing in chat.
    &7Type &fcancel &7at any time to abort.

  question-header: "&6Question {current} of {total}"

  questions:
    real-name:
      prompt: "&fWhat is your real name?"
      hint: "&7This helps us know who you are. First name is fine."
    discord:
      prompt: "&fWhat is your Discord username?"
      hint: "&7Optional. Type &fskip &7to leave blank."
    inviter:
      prompt: "&fWho invited you to the server?"
      hint: "&7Optional. Type &fskip &7if no one."
    notes:
      prompt: "&fAnything else you'd like us to know?"
      hint: "&7Optional. Type &fskip &7to leave blank."

  confirm:
    header: "&6Review Your Application"
    submit-hint: "&aType &fconfirm &ato submit your application."
    cancel-hint: "&cType &fcancel &cto abort."

  submitted: |
    &aApplication submitted!

    &7Your request is now pending review.
    &7You'll be notified when an admin responds.

  cancelled: "&7Application cancelled."
  timeout: "&cApplication wizard timed out."

errors:
  required-field: "&cThis field is required."
  too-long: "&cThat's too long. Please keep it under {max} characters."
  generic: "&cSomething went wrong. Please try again."
  no-permission: "&cYou don't have permission to do that."

# ... etc
```

---

## 11. Future Enhancement: Platform-Specific UX

When Geyser/Floodgate is detected, we could offer enhanced UX:

### Bedrock: Floodgate Forms

```java
if (FloodgateApi.getInstance().isFloodgatePlayer(player.getUniqueId())) {
    // Show native Bedrock form
    CustomForm form = CustomForm.builder()
        .title("Access Application")
        .input("Real Name", "John Doe")
        .input("Discord", "optional")
        .input("Who invited you?", "optional")
        .input("Notes", "optional")
        .build();

    FloodgateApi.getInstance().sendForm(player.getUniqueId(), form);
}
```

### Java: Clickable Chat

```java
// For Java players, add click-to-skip functionality
Component skipButton = Component.text("[Skip]")
    .color(NamedTextColor.GRAY)
    .clickEvent(ClickEvent.runCommand("skip"))
    .hoverEvent(HoverEvent.showText(Component.text("Click to skip")));
```

**Deferred to v2** — plain chat works universally for v1.

---

## Summary

- All messages use **plain colored chat** (works on Java + Bedrock)
- No clickable text, hover events, or forms in v1
- Wizard intercepts chat input, doesn't broadcast
- All messages configurable via `messages.yml`
- Clear visual hierarchy: gold for prompts, gray for hints, green/red for results
