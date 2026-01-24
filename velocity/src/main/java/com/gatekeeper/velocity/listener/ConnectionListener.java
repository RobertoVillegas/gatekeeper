package com.gatekeeper.velocity.listener;

import com.gatekeeper.velocity.config.GatekeeperConfig;
import com.gatekeeper.velocity.database.EntitlementRepository;
import com.gatekeeper.velocity.database.PlayerRepository;
import com.gatekeeper.velocity.gui.GuiManager;
import com.gatekeeper.velocity.model.GatekeeperPlayer;
import com.gatekeeper.velocity.model.Platform;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.sql.SQLException;

public class ConnectionListener {
    private final Logger logger;
    private final GatekeeperConfig config;
    private final PlayerRepository playerRepository;
    private final EntitlementRepository entitlementRepository;
    private final GuiManager guiManager;

    public ConnectionListener(
        Logger logger,
        GatekeeperConfig config,
        PlayerRepository playerRepository,
        EntitlementRepository entitlementRepository,
        GuiManager guiManager
    ) {
        this.logger = logger;
        this.config = config;
        this.playerRepository = playerRepository;
        this.entitlementRepository = entitlementRepository;
        this.guiManager = guiManager;
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        Player player = event.getPlayer();

        try {
            // Upsert player record
            Platform platform = isBedrockPlayer(player) ? Platform.BEDROCK : Platform.JAVA;
            GatekeeperPlayer gkPlayer = GatekeeperPlayer.create(
                player.getUniqueId(),
                player.getUsername(),
                platform
            );
            playerRepository.upsert(gkPlayer);

            logger.debug("Player {} ({}) logged in ({})",
                player.getUsername(), player.getUniqueId(), platform);

        } catch (SQLException e) {
            logger.error("Failed to upsert player {} on login", player.getUsername(), e);
        }
    }

    @Subscribe
    public void onServerPreConnect(ServerPreConnectEvent event) {
        Player player = event.getPlayer();
        RegisteredServer target = event.getOriginalServer();

        // Get the logical server ID
        String velocityServerName = target.getServerInfo().getName();
        String logicalServerId = config.getLogicalServerId(velocityServerName);

        // Check if this server is restricted
        if (!config.isRestricted(logicalServerId)) {
            // Not restricted, allow connection
            logger.debug("Player {} connecting to unrestricted server {}",
                player.getUsername(), logicalServerId);
            return;
        }

        // Server is restricted - check entitlement
        try {
            boolean hasAccess = entitlementRepository.hasActiveEntitlement(
                player.getUniqueId(),
                logicalServerId
            );

            if (hasAccess) {
                logger.info("Player {} allowed to {} (has entitlement)",
                    player.getUsername(), logicalServerId);
                return;
            }

            // No access - block and notify
            logger.info("Player {} blocked from {} (no entitlement)",
                player.getUsername(), logicalServerId);

            event.setResult(ServerPreConnectEvent.ServerResult.denied());

            // Send message to player
            player.sendMessage(Component.empty());
            player.sendMessage(colorize(config.getMessagePrefix() + "&cYou don't have access to that server."));
            player.sendMessage(Component.empty());
            player.sendMessage(colorize("&7Use &f/apply &7to request access."));

        } catch (SQLException e) {
            logger.error("Database error checking entitlement for {} to {}",
                player.getUsername(), logicalServerId, e);

            // Fail closed - deny access on error
            event.setResult(ServerPreConnectEvent.ServerResult.denied());
            player.sendMessage(colorize(config.getMessagePrefix() + "&cCould not verify access. Please try again."));
        }
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();

        // Clean up GUI if any
        guiManager.handleDisconnect(player.getUniqueId());

        // Update last seen
        try {
            playerRepository.updateLastSeen(player.getUniqueId(), player.getUsername());
        } catch (SQLException e) {
            logger.debug("Failed to update last seen for {}", player.getUsername());
        }
    }

    private boolean isBedrockPlayer(Player player) {
        // Floodgate prefixes Bedrock UUIDs with zeros
        String uuid = player.getUniqueId().toString();
        return uuid.startsWith("00000000-0000-0000");
    }

    private Component colorize(String message) {
        String translated = message
            .replace("&0", "§0").replace("&1", "§1").replace("&2", "§2")
            .replace("&3", "§3").replace("&4", "§4").replace("&5", "§5")
            .replace("&6", "§6").replace("&7", "§7").replace("&8", "§8")
            .replace("&9", "§9").replace("&a", "§a").replace("&b", "§b")
            .replace("&c", "§c").replace("&d", "§d").replace("&e", "§e")
            .replace("&f", "§f").replace("&r", "§r");
        return Component.text(translated);
    }
}
