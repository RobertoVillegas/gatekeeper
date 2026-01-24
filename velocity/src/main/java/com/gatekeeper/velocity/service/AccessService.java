package com.gatekeeper.velocity.service;

import com.gatekeeper.velocity.config.GatekeeperConfig;
import com.gatekeeper.velocity.database.ApplicationRepository;
import com.gatekeeper.velocity.database.AuditLogRepository;
import com.gatekeeper.velocity.database.EntitlementRepository;
import com.gatekeeper.velocity.database.PlayerRepository;
import com.gatekeeper.velocity.model.Application;
import com.gatekeeper.velocity.model.ApplicationStatus;
import com.gatekeeper.velocity.model.Entitlement;
import com.gatekeeper.velocity.model.GatekeeperPlayer;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.slf4j.Logger;

import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class AccessService {
    private final Logger logger;
    private final GatekeeperConfig config;
    private final ProxyServer server;
    private final PlayerRepository playerRepository;
    private final ApplicationRepository applicationRepository;
    private final EntitlementRepository entitlementRepository;
    private final AuditLogRepository auditLogRepository;
    private final DiscordNotifier discordNotifier;

    public AccessService(
        Logger logger,
        GatekeeperConfig config,
        ProxyServer server,
        PlayerRepository playerRepository,
        ApplicationRepository applicationRepository,
        EntitlementRepository entitlementRepository,
        AuditLogRepository auditLogRepository,
        DiscordNotifier discordNotifier
    ) {
        this.logger = logger;
        this.config = config;
        this.server = server;
        this.playerRepository = playerRepository;
        this.applicationRepository = applicationRepository;
        this.entitlementRepository = entitlementRepository;
        this.auditLogRepository = auditLogRepository;
        this.discordNotifier = discordNotifier;
    }

    /**
     * Check if a player has an active entitlement for a server.
     */
    public boolean hasAccess(UUID uuid, String serverId) throws SQLException {
        return entitlementRepository.hasActiveEntitlement(uuid, serverId);
    }

    /**
     * Get all active server IDs a player has access to.
     */
    public List<String> getPlayerServers(UUID uuid) throws SQLException {
        return entitlementRepository.findActiveServerIdsByUuid(uuid);
    }

    /**
     * Approve an application and grant entitlements.
     */
    public ApprovalResult approve(long applicationId, List<String> servers, String admin, String note) throws SQLException {
        Optional<Application> optApp = applicationRepository.findById(applicationId);
        if (optApp.isEmpty()) {
            return ApprovalResult.notFound();
        }

        Application application = optApp.get();
        if (!application.isPending()) {
            return ApprovalResult.alreadyDecided(application.decidedBy());
        }

        // Update application
        Application updated = application.approve(admin, note);
        applicationRepository.updateDecision(updated);

        // Grant entitlements
        List<Entitlement> granted = new ArrayList<>();
        for (String serverId : servers) {
            // Check if already has entitlement
            if (!entitlementRepository.hasActiveEntitlement(application.uuid(), serverId)) {
                Entitlement entitlement = Entitlement.grant(application.uuid(), serverId, admin, "Application #" + applicationId);
                entitlement = entitlementRepository.insert(entitlement);
                granted.add(entitlement);
            }
        }

        // Audit log
        auditLogRepository.logApprove(application.uuid(), admin, String.join(", ", servers));

        logger.info("Application #{} approved by {} - servers: {}",
            applicationId, admin, servers);

        // Notify player if online
        Optional<GatekeeperPlayer> player = playerRepository.findByUuid(application.uuid());
        String username = player.map(GatekeeperPlayer::lastUsername).orElse("Unknown");

        notifyPlayerApproved(application.uuid(), servers);

        // Notify Discord
        if (discordNotifier != null) {
            discordNotifier.notifyApplicationDecided(updated, username, servers);
        }

        return ApprovalResult.success(granted);
    }

    /**
     * Deny an application.
     */
    public DenialResult deny(long applicationId, String admin, String reason) throws SQLException {
        Optional<Application> optApp = applicationRepository.findById(applicationId);
        if (optApp.isEmpty()) {
            return DenialResult.notFound();
        }

        Application application = optApp.get();
        if (!application.isPending()) {
            return DenialResult.alreadyDecided(application.decidedBy());
        }

        // Update application
        Application updated = application.deny(admin, reason);
        applicationRepository.updateDecision(updated);

        // Audit log
        auditLogRepository.logDeny(application.uuid(), admin, reason);

        logger.info("Application #{} denied by {} - reason: {}",
            applicationId, admin, reason);

        // Notify player if online
        Optional<GatekeeperPlayer> player = playerRepository.findByUuid(application.uuid());
        String username = player.map(GatekeeperPlayer::lastUsername).orElse("Unknown");

        notifyPlayerDenied(application.uuid(), reason);

        // Notify Discord
        if (discordNotifier != null) {
            discordNotifier.notifyApplicationDecided(updated, username, List.of());
        }

        return DenialResult.success();
    }

    /**
     * Grant access to a server for a player.
     */
    public GrantResult grant(UUID uuid, String serverId, String admin, String note) throws SQLException {
        // Check if already has access
        if (entitlementRepository.hasActiveEntitlement(uuid, serverId)) {
            return GrantResult.alreadyHasAccess();
        }

        Entitlement entitlement = Entitlement.grant(uuid, serverId, admin, note);
        entitlement = entitlementRepository.insert(entitlement);

        auditLogRepository.logGrant(uuid, serverId, admin);

        logger.info("Granted {} access to {} by {}", uuid, serverId, admin);

        return GrantResult.success(entitlement);
    }

    /**
     * Revoke access to a server for a player.
     */
    public RevokeResult revoke(UUID uuid, String serverId, String admin) throws SQLException {
        Optional<Entitlement> existing = entitlementRepository.findActiveByUuidAndServer(uuid, serverId);
        if (existing.isEmpty()) {
            return RevokeResult.noAccess();
        }

        entitlementRepository.revoke(uuid, serverId);
        auditLogRepository.logRevoke(uuid, serverId, admin);

        logger.info("Revoked {} access to {} by {}", uuid, serverId, admin);

        return RevokeResult.success();
    }

    private void notifyPlayerApproved(UUID uuid, List<String> servers) {
        server.getPlayer(uuid).ifPresent(player -> {
            player.sendMessage(Component.empty());
            player.sendMessage(colorize("&8─────────────────────────────────────"));
            player.sendMessage(Component.empty());
            player.sendMessage(colorize(config.getMessagePrefix() + "&a&lGood news!"));
            player.sendMessage(Component.empty());
            player.sendMessage(colorize("&7Your access request has been &aapproved&7!"));
            player.sendMessage(colorize("&7You now have access to: &f" + String.join(", ", servers)));
            player.sendMessage(Component.empty());
            player.sendMessage(colorize("&7To join, use &f/server <name>"));
            player.sendMessage(Component.empty());
            player.sendMessage(colorize("&8─────────────────────────────────────"));

            // Show title
            player.showTitle(Title.title(
                colorize("&a&lApproved!"),
                colorize("&7You now have server access"),
                Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(500))
            ));
        });
    }

    private void notifyPlayerDenied(UUID uuid, String reason) {
        server.getPlayer(uuid).ifPresent(player -> {
            player.sendMessage(Component.empty());
            player.sendMessage(colorize("&8─────────────────────────────────────"));
            player.sendMessage(Component.empty());
            player.sendMessage(colorize(config.getMessagePrefix() + "&cApplication Denied"));
            player.sendMessage(Component.empty());
            player.sendMessage(colorize("&7Your access request was denied."));
            if (reason != null && !reason.isEmpty()) {
                player.sendMessage(colorize("&7Reason: &f" + reason));
            }
            player.sendMessage(Component.empty());
            player.sendMessage(colorize("&8─────────────────────────────────────"));

            // Show title
            player.showTitle(Title.title(
                colorize("&c&lDenied"),
                colorize("&7Check chat for details"),
                Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(500))
            ));
        });
    }

    private Component colorize(String message) {
        String translated = message
            .replace("&0", "§0").replace("&1", "§1").replace("&2", "§2")
            .replace("&3", "§3").replace("&4", "§4").replace("&5", "§5")
            .replace("&6", "§6").replace("&7", "§7").replace("&8", "§8")
            .replace("&9", "§9").replace("&a", "§a").replace("&b", "§b")
            .replace("&c", "§c").replace("&d", "§d").replace("&e", "§e")
            .replace("&f", "§f").replace("&r", "§r").replace("&l", "§l");
        return Component.text(translated);
    }

    // Result types
    public record ApprovalResult(boolean ok, String error, List<Entitlement> entitlements) {
        public static ApprovalResult success(List<Entitlement> entitlements) {
            return new ApprovalResult(true, null, entitlements);
        }
        public static ApprovalResult notFound() {
            return new ApprovalResult(false, "Application not found", null);
        }
        public static ApprovalResult alreadyDecided(String by) {
            return new ApprovalResult(false, "Already decided by " + by, null);
        }
    }

    public record DenialResult(boolean ok, String error) {
        public static DenialResult success() { return new DenialResult(true, null); }
        public static DenialResult notFound() { return new DenialResult(false, "Application not found"); }
        public static DenialResult alreadyDecided(String by) { return new DenialResult(false, "Already decided by " + by); }
    }

    public record GrantResult(boolean ok, String error, Entitlement entitlement) {
        public static GrantResult success(Entitlement e) { return new GrantResult(true, null, e); }
        public static GrantResult alreadyHasAccess() { return new GrantResult(false, "Already has access", null); }
    }

    public record RevokeResult(boolean ok, String error) {
        public static RevokeResult success() { return new RevokeResult(true, null); }
        public static RevokeResult noAccess() { return new RevokeResult(false, "No active access to revoke"); }
    }
}
