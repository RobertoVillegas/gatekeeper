#!/usr/bin/env bash
set -euo pipefail

# Paths
VELOCITY_PLUGINS="/opt/minecraft/velocity/plugins"
CRAFTY_SERVERS="/opt/minecraft/crafty/servers"
BACKUP_DIR="/opt/minecraft/backups/plugins-$(date +%Y%m%d-%H%M%S)"

# Download URLs
GEYSER_VELOCITY="https://download.geysermc.org/v2/projects/geyser/versions/latest/builds/latest/downloads/velocity"
FLOODGATE_VELOCITY="https://download.geysermc.org/v2/projects/floodgate/versions/latest/builds/latest/downloads/velocity"
FLOODGATE_SPIGOT="https://download.geysermc.org/v2/projects/floodgate/versions/latest/builds/latest/downloads/spigot"

echo "=== Geyser & Floodgate Updater ==="
echo ""

# --- Backup ---
echo "[1/3] Backing up current JARs to $BACKUP_DIR"
mkdir -p "$BACKUP_DIR/velocity"
cp "$VELOCITY_PLUGINS/Geyser-Velocity.jar" "$BACKUP_DIR/velocity/"
cp "$VELOCITY_PLUGINS/Floodgate-Velocity.jar" "$BACKUP_DIR/velocity/"

for server_dir in "$CRAFTY_SERVERS"/*/plugins/; do
  if [ -f "$server_dir/floodgate-spigot.jar" ]; then
    server_id=$(basename "$(dirname "$server_dir")")
    mkdir -p "$BACKUP_DIR/servers/$server_id"
    cp "$server_dir/floodgate-spigot.jar" "$BACKUP_DIR/servers/$server_id/"
    echo "  Backed up floodgate-spigot from server $server_id"
  fi
done
echo "  Backup complete."
echo ""

# --- Download ---
echo "[2/3] Downloading latest versions..."

echo "  Geyser-Velocity..."
curl -sfL -o "$VELOCITY_PLUGINS/Geyser-Velocity.jar" "$GEYSER_VELOCITY"
echo "  Floodgate-Velocity..."
curl -sfL -o "$VELOCITY_PLUGINS/Floodgate-Velocity.jar" "$FLOODGATE_VELOCITY"

for server_dir in "$CRAFTY_SERVERS"/*/plugins/; do
  if [ -f "$server_dir/floodgate-spigot.jar" ]; then
    server_id=$(basename "$(dirname "$server_dir")")
    echo "  Floodgate-Spigot for server $server_id..."
    curl -sfL -o "$server_dir/floodgate-spigot.jar" "$FLOODGATE_SPIGOT"
  fi
done
echo "  Downloads complete."
echo ""

# --- Done ---
echo "[3/3] Done!"
echo ""
echo "Next steps:"
echo "  1. Restart the backend Minecraft servers from Crafty panel"
echo "  2. Restart Velocity: docker restart games-crafty-tvc67g-velocity-1"
echo ""
echo "To rollback if something breaks:"
echo "  cp $BACKUP_DIR/velocity/* $VELOCITY_PLUGINS/"
echo "  # Then for each server with floodgate:"
echo "  # cp $BACKUP_DIR/servers/<server-id>/floodgate-spigot.jar <server-plugins-dir>/"
