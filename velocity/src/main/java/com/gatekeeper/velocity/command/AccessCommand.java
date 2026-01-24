package com.gatekeeper.velocity.command;

import com.gatekeeper.velocity.config.GatekeeperConfig;
import com.gatekeeper.velocity.database.ApplicationRepository;
import com.gatekeeper.velocity.database.PlayerRepository;
import com.gatekeeper.velocity.model.Application;
import com.gatekeeper.velocity.model.GatekeeperPlayer;
import com.gatekeeper.velocity.service.AccessService;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class AccessCommand implements SimpleCommand {
    private final Logger logger;
    private final GatekeeperConfig config;
    private final AccessService accessService;
    private final PlayerRepository playerRepository;
    private final ApplicationRepository applicationRepository;

    public AccessCommand(
        Logger logger,
        GatekeeperConfig config,
        AccessService accessService,
        PlayerRepository playerRepository,
        ApplicationRepository applicationRepository
    ) {
        this.logger = logger;
        this.config = config;
        this.accessService = accessService;
        this.playerRepository = playerRepository;
        this.applicationRepository = applicationRepository;
    }

    @Override
    public void execute(Invocation invocation) {
        String[] args = invocation.arguments();

        if (args.length < 1) {
            sendUsage(invocation.source());
            return;
        }

        switch (args[0].toLowerCase()) {
            case "approve" -> handleApprove(invocation, args);
            case "deny" -> handleDeny(invocation, args);
            case "grant" -> handleGrant(invocation, args);
            case "revoke" -> handleRevoke(invocation, args);
            default -> sendUsage(invocation.source());
        }
    }

    private void handleApprove(Invocation invocation, String[] args) {
        // /access approve <player> [servers...]
        if (args.length < 2) {
            sendMessage(invocation.source(), "&cUsage: /access approve <player> [servers...]");
            return;
        }

        String playerName = args[1];
        List<String> servers = args.length > 2
            ? Arrays.asList(Arrays.copyOfRange(args, 2, args.length))
            : config.getDefaultServers();

        try {
            Optional<GatekeeperPlayer> player = playerRepository.findByUsername(playerName);
            if (player.isEmpty()) {
                sendMessage(invocation.source(), "&cPlayer not found: &f" + playerName);
                return;
            }

            Optional<Application> pending = applicationRepository.findPendingByUuid(player.get().uuid());
            if (pending.isEmpty()) {
                sendMessage(invocation.source(), "&cNo pending application for &f" + playerName);
                return;
            }

            String admin = getAdminIdentifier(invocation);
            AccessService.ApprovalResult result = accessService.approve(pending.get().id(), servers, admin, null);

            if (result.ok()) {
                sendMessage(invocation.source(), "&aApproved &f" + playerName + "&a's application.");
                invocation.source().sendMessage(colorize("&7Granted access to: &f" + String.join(", ", servers)));
            } else {
                sendMessage(invocation.source(), "&c" + result.error());
            }

        } catch (SQLException e) {
            logger.error("Database error in /access approve", e);
            sendMessage(invocation.source(), "&cDatabase error. Check console.");
        }
    }

    private void handleDeny(Invocation invocation, String[] args) {
        // /access deny <player> [reason...]
        if (args.length < 2) {
            sendMessage(invocation.source(), "&cUsage: /access deny <player> [reason]");
            return;
        }

        String playerName = args[1];
        String reason = args.length > 2
            ? String.join(" ", Arrays.copyOfRange(args, 2, args.length))
            : null;

        try {
            Optional<GatekeeperPlayer> player = playerRepository.findByUsername(playerName);
            if (player.isEmpty()) {
                sendMessage(invocation.source(), "&cPlayer not found: &f" + playerName);
                return;
            }

            Optional<Application> pending = applicationRepository.findPendingByUuid(player.get().uuid());
            if (pending.isEmpty()) {
                sendMessage(invocation.source(), "&cNo pending application for &f" + playerName);
                return;
            }

            String admin = getAdminIdentifier(invocation);
            AccessService.DenialResult result = accessService.deny(pending.get().id(), admin, reason);

            if (result.ok()) {
                sendMessage(invocation.source(), "&cDenied &f" + playerName + "&c's application.");
                if (reason != null) {
                    invocation.source().sendMessage(colorize("&7Reason: &f" + reason));
                }
            } else {
                sendMessage(invocation.source(), "&c" + result.error());
            }

        } catch (SQLException e) {
            logger.error("Database error in /access deny", e);
            sendMessage(invocation.source(), "&cDatabase error. Check console.");
        }
    }

    private void handleGrant(Invocation invocation, String[] args) {
        // /access grant <player> <server>
        if (args.length < 3) {
            sendMessage(invocation.source(), "&cUsage: /access grant <player> <server>");
            return;
        }

        String playerName = args[1];
        String serverId = args[2];

        try {
            Optional<GatekeeperPlayer> player = playerRepository.findByUsername(playerName);
            if (player.isEmpty()) {
                sendMessage(invocation.source(), "&cPlayer not found: &f" + playerName);
                return;
            }

            String admin = getAdminIdentifier(invocation);
            AccessService.GrantResult result = accessService.grant(player.get().uuid(), serverId, admin, null);

            if (result.ok()) {
                sendMessage(invocation.source(), "&aGranted &f" + playerName + " &aaccess to &f" + serverId + "&a.");
            } else {
                sendMessage(invocation.source(), "&e" + playerName + " &ealready has access to &f" + serverId + "&e.");
            }

        } catch (SQLException e) {
            logger.error("Database error in /access grant", e);
            sendMessage(invocation.source(), "&cDatabase error. Check console.");
        }
    }

    private void handleRevoke(Invocation invocation, String[] args) {
        // /access revoke <player> <server>
        if (args.length < 3) {
            sendMessage(invocation.source(), "&cUsage: /access revoke <player> <server>");
            return;
        }

        String playerName = args[1];
        String serverId = args[2];

        try {
            Optional<GatekeeperPlayer> player = playerRepository.findByUsername(playerName);
            if (player.isEmpty()) {
                sendMessage(invocation.source(), "&cPlayer not found: &f" + playerName);
                return;
            }

            String admin = getAdminIdentifier(invocation);
            AccessService.RevokeResult result = accessService.revoke(player.get().uuid(), serverId, admin);

            if (result.ok()) {
                sendMessage(invocation.source(), "&cRevoked &f" + playerName + "&c's access to &f" + serverId + "&c.");
            } else {
                sendMessage(invocation.source(), "&e" + playerName + " &edoesn't have access to &f" + serverId + "&e.");
            }

        } catch (SQLException e) {
            logger.error("Database error in /access revoke", e);
            sendMessage(invocation.source(), "&cDatabase error. Check console.");
        }
    }

    private String getAdminIdentifier(Invocation invocation) {
        if (invocation.source() instanceof Player player) {
            return "player:" + player.getUniqueId();
        }
        return "console";
    }

    private void sendUsage(Object source) {
        if (source instanceof Player player) {
            sendMessage(player, "&7Staff commands:");
            player.sendMessage(colorize("&f/access approve <player> [servers] &7- Approve application"));
            player.sendMessage(colorize("&f/access deny <player> [reason] &7- Deny application"));
            player.sendMessage(colorize("&f/access grant <player> <server> &7- Grant access"));
            player.sendMessage(colorize("&f/access revoke <player> <server> &7- Revoke access"));
        } else {
            // Console
            ((com.velocitypowered.api.command.CommandSource) source).sendMessage(
                Component.text("Usage: /access <approve|deny|grant|revoke> ...")
            );
        }
    }

    private void sendMessage(Object source, String message) {
        if (source instanceof Player player) {
            player.sendMessage(colorize(config.getMessagePrefix() + message));
        } else {
            ((com.velocitypowered.api.command.CommandSource) source).sendMessage(colorize(message));
        }
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

    @Override
    public boolean hasPermission(Invocation invocation) {
        // Require gatekeeper.admin permission
        return invocation.source().hasPermission("gatekeeper.admin");
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();

        if (args.length <= 1) {
            return List.of("approve", "deny", "grant", "revoke");
        }

        if (args.length == 3 && (args[0].equalsIgnoreCase("grant") || args[0].equalsIgnoreCase("revoke"))) {
            return config.getRestrictedServers();
        }

        return List.of();
    }
}
