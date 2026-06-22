package com.gatekeeper.paper.api;

import com.gatekeeper.paper.config.PaperConfig;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Thin HTTP client for the Velocity Gatekeeper admin API.
 *
 * <p>All methods are blocking and MUST be called off the main server thread.
 */
public class GatekeeperApiClient {

    private static final Gson GSON = new Gson();

    private final PaperConfig config;
    private final Logger logger;
    private final HttpClient http;

    public GatekeeperApiClient(PaperConfig config, Logger logger) {
        this.config = config;
        this.logger = logger;
        this.http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(config.getTimeoutSeconds()))
            .build();
    }

    /**
     * Fetch the application context for a player. Returns null on any failure.
     */
    public ApplicationContext getContext(UUID uuid, String username) {
        try {
            String url = config.getApiBaseUrl() + "/api/players/" + uuid
                + "/application-context?username=" + url(username);
            HttpRequest request = baseRequest(url).GET().build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                logger.warning("application-context returned HTTP " + response.statusCode() + ": " + response.body());
                return null;
            }
            return GSON.fromJson(response.body(), ApplicationContext.class);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to fetch application context for " + username, e);
            return null;
        }
    }

    /**
     * Submit an application. Returns a structured result (never null).
     */
    public SubmitResult submit(UUID uuid, String username, boolean bedrock,
                               ApplicationData data, List<String> servers) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("uuid", uuid.toString());
            body.addProperty("username", username);
            body.addProperty("platform", bedrock ? "BEDROCK" : "JAVA");
            body.addProperty("realName", data.realName());
            body.addProperty("inviter", data.inviter());
            if (data.discord() != null) {
                body.addProperty("discord", data.discord());
            }
            if (data.notes() != null) {
                body.addProperty("notes", data.notes());
            }
            body.add("servers", GSON.toJsonTree(servers));

            HttpRequest request = baseRequest(config.getApiBaseUrl() + "/api/applications")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body), StandardCharsets.UTF_8))
                .header("Content-Type", "application/json")
                .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            JsonObject json = parseObject(response.body());

            if (response.statusCode() == 200 && json != null
                && json.has("success") && json.get("success").getAsBoolean()) {
                Long id = json.has("applicationId") && !json.get("applicationId").isJsonNull()
                    ? json.get("applicationId").getAsLong() : null;
                return SubmitResult.ok(id);
            }

            String error = json != null && json.has("error") && !json.get("error").isJsonNull()
                ? json.get("error").getAsString()
                : "Server returned HTTP " + response.statusCode();
            return SubmitResult.fail(error);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to submit application for " + username, e);
            return SubmitResult.fail("Could not reach the access server. Please try again later.");
        }
    }

    private HttpRequest.Builder baseRequest(String url) {
        return HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
            .header("X-Shared-Secret", config.getSharedSecret());
    }

    private static JsonObject parseObject(String body) {
        try {
            return GSON.fromJson(body, JsonObject.class);
        } catch (Exception e) {
            return null;
        }
    }

    private static String url(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
