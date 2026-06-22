package com.gatekeeper.velocity.api;

import com.gatekeeper.velocity.config.GatekeeperConfig;
import com.gatekeeper.velocity.database.ApplicationRepository;
import com.gatekeeper.velocity.database.EntitlementRepository;
import com.gatekeeper.velocity.database.PlayerRepository;
import com.gatekeeper.velocity.model.Application;
import com.gatekeeper.velocity.model.ApplicationStatus;
import com.gatekeeper.velocity.model.Entitlement;
import com.gatekeeper.velocity.model.GatekeeperPlayer;
import com.gatekeeper.velocity.model.Platform;
import com.gatekeeper.velocity.service.AccessService;
import com.gatekeeper.velocity.service.DiscordNotifier;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import org.slf4j.Logger;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class AdminApiServer {
    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .create();

    private final Logger logger;
    private final GatekeeperConfig config;
    private final AccessService accessService;
    private final PlayerRepository playerRepository;
    private final ApplicationRepository applicationRepository;
    private final EntitlementRepository entitlementRepository;
    private final DiscordNotifier discordNotifier;

    private Javalin app;

    public AdminApiServer(
        Logger logger,
        GatekeeperConfig config,
        AccessService accessService,
        PlayerRepository playerRepository,
        ApplicationRepository applicationRepository,
        EntitlementRepository entitlementRepository,
        DiscordNotifier discordNotifier
    ) {
        this.logger = logger;
        this.config = config;
        this.accessService = accessService;
        this.playerRepository = playerRepository;
        this.applicationRepository = applicationRepository;
        this.entitlementRepository = entitlementRepository;
        this.discordNotifier = discordNotifier;
    }

    public void start() {
        app = Javalin.create(cfg -> {
            cfg.showJavalinBanner = false;
        });

        // Authentication middleware
        app.before("/api/*", this::authenticate);

        // Routes
        app.get("/api/health", this::handleHealth);
        app.get("/api/applications", this::handleGetApplications);
        app.get("/api/applications/{id}", this::handleGetApplication);
        app.post("/api/applications/{id}/approve", this::handleApprove);
        app.post("/api/applications/{id}/deny", this::handleDeny);
        app.get("/api/players/{uuid}/entitlements", this::handleGetPlayerEntitlements);
        app.post("/api/entitlements/grant", this::handleGrant);
        app.post("/api/entitlements/revoke", this::handleRevoke);

        // Player-facing endpoints (used by the Paper lobby GUI)
        app.get("/api/players/{uuid}/application-context", this::handleApplicationContext);
        app.post("/api/applications", this::handleSubmitApplication);

        app.start(config.getApiBindAddress(), config.getApiPort());

        logger.info("Admin API listening on {}:{}", config.getApiBindAddress(), config.getApiPort());
    }

    public void stop() {
        if (app != null) {
            app.stop();
            logger.info("Admin API stopped");
        }
    }

    private void authenticate(Context ctx) {
        String secret = ctx.header("X-Shared-Secret");

        if (secret == null || !secret.equals(config.getSharedSecret())) {
            logger.warn("Unauthorized API request from {}", ctx.ip());
            ctx.status(HttpStatus.UNAUTHORIZED);
            ctx.json(error("Unauthorized"));
            ctx.skipRemainingHandlers();
        }
    }

    private void handleHealth(Context ctx) {
        try {
            int pendingApps = applicationRepository.countByStatus(ApplicationStatus.PENDING);
            int totalPlayers = playerRepository.countAll();
            int totalEntitlements = entitlementRepository.countActive();

            Map<String, Object> response = new HashMap<>();
            response.put("status", "healthy");
            response.put("version", "1.0.0");
            response.put("stats", Map.of(
                "pendingApplications", pendingApps,
                "totalPlayers", totalPlayers,
                "totalEntitlements", totalEntitlements
            ));

            ctx.json(response);
        } catch (SQLException e) {
            logger.error("Health check failed", e);
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
            ctx.json(error("Database error"));
        }
    }

    private void handleGetApplications(Context ctx) {
        try {
            String statusParam = ctx.queryParam("status");
            List<Application> applications;

            if (statusParam != null) {
                ApplicationStatus status = ApplicationStatus.valueOf(statusParam.toUpperCase());
                applications = applicationRepository.findByStatus(status);
            } else {
                applications = applicationRepository.findPending();
            }

            // Enrich with player data
            List<Map<String, Object>> enriched = applications.stream()
                .map(this::enrichApplication)
                .toList();

            ctx.json(Map.of("applications", enriched));
        } catch (SQLException e) {
            logger.error("Failed to get applications", e);
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
            ctx.json(error("Database error"));
        }
    }

    private void handleGetApplication(Context ctx) {
        try {
            long id = Long.parseLong(ctx.pathParam("id"));
            Optional<Application> app = applicationRepository.findById(id);

            if (app.isEmpty()) {
                ctx.status(HttpStatus.NOT_FOUND);
                ctx.json(error("Application not found"));
                return;
            }

            ctx.json(enrichApplication(app.get()));
        } catch (SQLException e) {
            logger.error("Failed to get application", e);
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
            ctx.json(error("Database error"));
        }
    }

    private void handleApprove(Context ctx) {
        try {
            long id = Long.parseLong(ctx.pathParam("id"));
            ApproveRequest request = GSON.fromJson(ctx.body(), ApproveRequest.class);

            if (request.servers == null || request.servers.isEmpty()) {
                request.servers = config.getDefaultServers();
            }

            AccessService.ApprovalResult result = accessService.approve(
                id,
                request.servers,
                request.admin,
                request.note
            );

            if (result.ok()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("entitlements", result.entitlements().stream()
                    .map(e -> Map.of(
                        "serverId", e.serverId(),
                        "grantedAt", e.grantedAt().getEpochSecond()
                    ))
                    .toList());

                ctx.json(response);
            } else {
                ctx.status(HttpStatus.CONFLICT);
                ctx.json(Map.of(
                    "success", false,
                    "error", result.error()
                ));
            }
        } catch (SQLException e) {
            logger.error("Failed to approve application", e);
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
            ctx.json(error("Database error"));
        }
    }

    private void handleDeny(Context ctx) {
        try {
            long id = Long.parseLong(ctx.pathParam("id"));
            DenyRequest request = GSON.fromJson(ctx.body(), DenyRequest.class);

            AccessService.DenialResult result = accessService.deny(
                id,
                request.admin,
                request.reason
            );

            if (result.ok()) {
                ctx.json(Map.of("success", true));
            } else {
                ctx.status(HttpStatus.CONFLICT);
                ctx.json(Map.of(
                    "success", false,
                    "error", result.error()
                ));
            }
        } catch (SQLException e) {
            logger.error("Failed to deny application", e);
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
            ctx.json(error("Database error"));
        }
    }

    private void handleGetPlayerEntitlements(Context ctx) {
        try {
            UUID uuid = UUID.fromString(ctx.pathParam("uuid"));
            List<Entitlement> entitlements = entitlementRepository.findActiveByUuid(uuid);

            ctx.json(Map.of(
                "uuid", uuid.toString(),
                "entitlements", entitlements.stream()
                    .map(e -> Map.of(
                        "serverId", e.serverId(),
                        "grantedAt", e.grantedAt().getEpochSecond(),
                        "grantedBy", e.grantedBy()
                    ))
                    .toList()
            ));
        } catch (SQLException e) {
            logger.error("Failed to get player entitlements", e);
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
            ctx.json(error("Database error"));
        }
    }

    private void handleGrant(Context ctx) {
        try {
            GrantRequest request = GSON.fromJson(ctx.body(), GrantRequest.class);
            UUID uuid = UUID.fromString(request.uuid);

            AccessService.GrantResult result = accessService.grant(
                uuid,
                request.serverId,
                request.admin,
                request.note
            );

            if (result.ok()) {
                ctx.json(Map.of(
                    "success", true,
                    "entitlement", Map.of(
                        "serverId", result.entitlement().serverId(),
                        "grantedAt", result.entitlement().grantedAt().getEpochSecond()
                    )
                ));
            } else {
                ctx.status(HttpStatus.CONFLICT);
                ctx.json(Map.of(
                    "success", false,
                    "error", result.error()
                ));
            }
        } catch (SQLException e) {
            logger.error("Failed to grant entitlement", e);
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
            ctx.json(error("Database error"));
        }
    }

    private void handleRevoke(Context ctx) {
        try {
            RevokeRequest request = GSON.fromJson(ctx.body(), RevokeRequest.class);
            UUID uuid = UUID.fromString(request.uuid);

            AccessService.RevokeResult result = accessService.revoke(
                uuid,
                request.serverId,
                request.admin
            );

            if (result.ok()) {
                ctx.json(Map.of("success", true));
            } else {
                ctx.status(HttpStatus.CONFLICT);
                ctx.json(Map.of(
                    "success", false,
                    "error", result.error()
                ));
            }
        } catch (SQLException e) {
            logger.error("Failed to revoke entitlement", e);
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
            ctx.json(error("Database error"));
        }
    }

    /**
     * Returns everything the lobby GUI needs to render the application flow for a player:
     * which servers are restricted, which the player already has, which remain available,
     * and the status of their latest application (for /apply status).
     */
    private void handleApplicationContext(Context ctx) {
        try {
            UUID uuid = UUID.fromString(ctx.pathParam("uuid"));

            List<String> restricted = config.getRestrictedServers();
            List<String> currentAccess = entitlementRepository.findActiveServerIdsByUuid(uuid);
            List<String> available = restricted.stream()
                .filter(server -> !currentAccess.contains(server))
                .toList();

            Optional<Application> pending = applicationRepository.findPendingByUuid(uuid);
            Optional<Application> latest = applicationRepository.findLatestByUuid(uuid);

            Map<String, Object> response = new HashMap<>();
            response.put("uuid", uuid.toString());
            response.put("restrictedServers", restricted);
            response.put("currentAccess", currentAccess);
            response.put("availableServers", available);
            response.put("defaultServers", config.getDefaultServers());
            response.put("hasPending", pending.isPresent());
            response.put("latest", latest.map(this::enrichApplication).orElse(null));

            ctx.json(response);
        } catch (IllegalArgumentException e) {
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(error("Invalid uuid"));
        } catch (SQLException e) {
            logger.error("Failed to build application context", e);
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
            ctx.json(error("Database error"));
        }
    }

    /**
     * Creates a pending application on behalf of a player (submitted from the lobby GUI).
     * Mirrors the logic that previously lived in the Velocity-side GuiManager.
     */
    private void handleSubmitApplication(Context ctx) {
        try {
            SubmitApplicationRequest request = GSON.fromJson(ctx.body(), SubmitApplicationRequest.class);

            if (request == null || request.uuid == null || request.username == null
                || request.realName == null || request.inviter == null) {
                ctx.status(HttpStatus.BAD_REQUEST);
                ctx.json(error("Missing required fields: uuid, username, realName, inviter"));
                return;
            }
            if (request.servers == null || request.servers.isEmpty()) {
                ctx.status(HttpStatus.BAD_REQUEST);
                ctx.json(error("At least one server must be requested"));
                return;
            }

            UUID uuid = UUID.fromString(request.uuid);

            // Reject if there is already a pending application
            if (applicationRepository.findPendingByUuid(uuid).isPresent()) {
                ctx.status(HttpStatus.CONFLICT);
                ctx.json(Map.of("success", false, "error", "You already have a pending application."));
                return;
            }

            Platform platform = Platform.BEDROCK.name().equalsIgnoreCase(request.platform)
                ? Platform.BEDROCK
                : Platform.JAVA;
            GatekeeperPlayer gkPlayer = playerRepository.upsert(
                GatekeeperPlayer.create(uuid, request.username, platform));

            String serversNote = "Requested servers: " + String.join(", ", request.servers);
            String fullNotes = (request.notes != null && !request.notes.isBlank())
                ? request.notes + " | " + serversNote
                : serversNote;

            Application application = applicationRepository.insert(Application.createPending(
                uuid,
                request.realName,
                (request.discord != null && request.discord.isBlank()) ? null : request.discord,
                request.inviter,
                fullNotes
            ));

            logger.info("Player {} ({}) submitted application #{} via lobby GUI for servers: {}",
                request.username, uuid, application.id(), request.servers);

            if (discordNotifier != null) {
                discordNotifier.notifyNewApplication(application, gkPlayer);
            }

            ctx.json(Map.of("success", true, "applicationId", application.id()));
        } catch (IllegalArgumentException e) {
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(error("Invalid request: " + e.getMessage()));
        } catch (SQLException e) {
            logger.error("Failed to submit application", e);
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
            ctx.json(error("Database error"));
        }
    }

    private Map<String, Object> enrichApplication(Application app) {
        Map<String, Object> result = new HashMap<>();
        result.put("id", app.id());
        result.put("status", app.status().name());
        result.put("realName", app.realName());
        result.put("discordTag", app.discordTag());
        result.put("inviter", app.inviter());
        result.put("notes", app.notes());
        result.put("createdAt", app.createdAt().getEpochSecond());

        if (app.decidedAt() != null) {
            result.put("decidedAt", app.decidedAt().getEpochSecond());
            result.put("decidedBy", app.decidedBy());
            result.put("decisionNote", app.decisionNote());
        }

        // Add player info
        try {
            Optional<GatekeeperPlayer> player = playerRepository.findByUuid(app.uuid());
            if (player.isPresent()) {
                result.put("player", Map.of(
                    "uuid", player.get().uuid().toString(),
                    "username", player.get().lastUsername(),
                    "platform", player.get().platform().name()
                ));
            }
        } catch (SQLException e) {
            logger.debug("Failed to enrich application with player data", e);
        }

        return result;
    }

    private Map<String, Object> error(String message) {
        return Map.of("error", message);
    }

    // Request DTOs
    private static class ApproveRequest {
        List<String> servers;
        String admin;
        String note;
    }

    private static class DenyRequest {
        String admin;
        String reason;
    }

    private static class GrantRequest {
        String uuid;
        String serverId;
        String admin;
        String note;
    }

    private static class RevokeRequest {
        String uuid;
        String serverId;
        String admin;
    }

    private static class SubmitApplicationRequest {
        String uuid;
        String username;
        String platform;
        String realName;
        String inviter;
        String discord;
        String notes;
        List<String> servers;
    }
}
