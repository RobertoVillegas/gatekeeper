# Gatekeeper

Access management system for Minecraft servers running on Velocity proxy.

Players apply for server access in-game, admins approve/deny via Discord.

## Components

| Component | Description |
|-----------|-------------|
| **Velocity Plugin** | Handles applications, enforces access, exposes admin API |
| **Discord Bot** | Admin UI for approving/denying applications |

## Quick Start

### 1. Velocity Plugin

Download the latest JAR from [Releases](https://github.com/RobertoVillegas/gatekeeper/releases).

```bash
# Install plugin
cp access-manager-velocity-*.jar /path/to/velocity/plugins/

# Install Protocolize (required for GUI, optional for text-only mode)
# Download from: https://ci.exceptionflug.de/job/Protocolize2/lastSuccessfulBuild/artifact/protocolize-velocity/target/protocolize-velocity.jar
```

Restart Velocity. Edit `plugins/gatekeeper/config.yml`:

```yaml
restricted-servers:
  - survival
  - creative

default-servers:
  - survival

discord:
  enabled: true
  webhook-url: "https://discord.com/api/webhooks/..."

api:
  enabled: true
  port: 8080
  shared-secret: "your-secret-here"
```

### 2. Discord Bot

```bash
docker pull ghcr.io/robertovillegas/gatekeeper/discord-bot:latest

docker run -d \
  -e DISCORD_TOKEN=your-bot-token \
  -e DISCORD_CHANNEL_ID=your-channel-id \
  -e VELOCITY_API_URL=http://velocity:8080 \
  -e VELOCITY_SHARED_SECRET=your-secret-here \
  ghcr.io/robertovillegas/gatekeeper/discord-bot:latest
```

## Usage

### Players

```
/apply "YourName" "WhoInvitedYou"
/apply "YourName" "WhoInvitedYou" "discord_username"
/apply "YourName" "WhoInvitedYou" "discord_username" "Optional notes"
/apply status
```

If the player's client supports it, a GUI opens to select servers. Otherwise, text mode is used.

### Admins (Discord)

When a player applies, a message appears in Discord with:
- **Quick Approve** - Grants access to the requested servers
- **Select Servers** - Choose which servers to grant
- **Deny** - Deny with optional reason

### Admins (In-game)

```
/access list pending
/access approve <id> [servers...]
/access deny <id> [reason]
/access grant <player> <server>
/access revoke <player> <server>
```

## How It Works

1. New players join and land in lobby
2. Trying to join a restricted server shows a message to `/apply`
3. Player runs `/apply` with their info
4. Application appears in Discord for admin review
5. Admin approves/denies
6. Player gets notified and can access approved servers

## Requirements

- Velocity 3.3.0+
- Java 17+
- Protocolize (optional, for GUI mode)

## Bedrock Support

Works with Geyser/Floodgate. Bedrock players use the same commands. The GUI is translated to Bedrock forms automatically.

## License

MIT
