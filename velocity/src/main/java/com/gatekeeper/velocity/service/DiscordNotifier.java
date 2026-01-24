package com.gatekeeper.velocity.service;

import com.gatekeeper.velocity.config.GatekeeperConfig;
import com.gatekeeper.velocity.model.Application;
import com.gatekeeper.velocity.model.GatekeeperPlayer;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

public class DiscordNotifier {
    private static final Gson GSON = new GsonBuilder().create();

    private final Logger logger;
    private final GatekeeperConfig config;
    private final HttpClient httpClient;
    private final ExecutorService executor;

    public DiscordNotifier(Logger logger, GatekeeperConfig config, ExecutorService executor) {
        this.logger = logger;
        this.config = config;
        this.executor = executor;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .executor(executor)
            .build();
    }

    public void notifyNewApplication(Application application, GatekeeperPlayer player) {
        if (!config.isDiscordEnabled() || config.getDiscordWebhookUrl().isEmpty()) {
            logger.debug("Discord notifications disabled, skipping");
            return;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "NEW_APPLICATION");

        Map<String, Object> appData = new HashMap<>();
        appData.put("id", application.id());

        Map<String, Object> playerData = new HashMap<>();
        playerData.put("uuid", player.uuid().toString());
        playerData.put("username", player.lastUsername());
        playerData.put("platform", player.platform().name());
        appData.put("player", playerData);

        appData.put("realName", application.realName());
        appData.put("discordTag", application.discordTag());
        appData.put("inviter", application.inviter());
        appData.put("notes", application.notes());
        appData.put("submittedAt", application.createdAt().getEpochSecond());

        payload.put("application", appData);
        payload.put("defaultServers", config.getDefaultServers());
        payload.put("availableServers", config.getRestrictedServers());

        sendWebhook(payload)
            .thenAccept(success -> {
                if (success) {
                    logger.debug("Discord notification sent for application #{}", application.id());
                } else {
                    logger.warn("Failed to send Discord notification for application #{}", application.id());
                }
            });
    }

    public void notifyApplicationDecided(
        Application application,
        String playerUsername,
        List<String> grantedServers
    ) {
        if (!config.isDiscordEnabled() || config.getDiscordWebhookUrl().isEmpty()) {
            return;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "APPLICATION_DECIDED");
        payload.put("applicationId", application.id());
        payload.put("playerUsername", playerUsername);
        payload.put("status", application.status().name());
        payload.put("decidedBy", application.decidedBy());
        payload.put("servers", grantedServers);
        payload.put("reason", application.decisionNote());

        sendWebhook(payload);
    }

    private CompletableFuture<Boolean> sendWebhook(Map<String, Object> payload) {
        String json = GSON.toJson(payload);
        String url = config.getDiscordWebhookUrl();

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .header("X-Shared-Secret", config.getSharedSecret())
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .timeout(Duration.ofSeconds(10))
            .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(response -> {
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return true;
                } else {
                    logger.warn("Discord webhook returned {}: {}", response.statusCode(), response.body());
                    return false;
                }
            })
            .exceptionally(e -> {
                logger.warn("Discord webhook failed: {}", e.getMessage());
                return false;
            });
    }
}
