package com.gatekeeper.velocity.config;

import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GatekeeperConfig {
    private final String databasePath;
    private final Map<String, String> serverMapping;
    private final List<String> restrictedServers;
    private final List<String> defaultServers;
    private final boolean discordEnabled;
    private final String discordWebhookUrl;
    private final String sharedSecret;
    private final boolean apiEnabled;
    private final int apiPort;
    private final String apiBindAddress;
    private final int wizardTimeoutSeconds;
    private final String messagePrefix;

    private GatekeeperConfig(
        String databasePath,
        Map<String, String> serverMapping,
        List<String> restrictedServers,
        List<String> defaultServers,
        boolean discordEnabled,
        String discordWebhookUrl,
        String sharedSecret,
        boolean apiEnabled,
        int apiPort,
        String apiBindAddress,
        int wizardTimeoutSeconds,
        String messagePrefix
    ) {
        this.databasePath = databasePath;
        this.serverMapping = serverMapping;
        this.restrictedServers = restrictedServers;
        this.defaultServers = defaultServers;
        this.discordEnabled = discordEnabled;
        this.discordWebhookUrl = discordWebhookUrl;
        this.sharedSecret = sharedSecret;
        this.apiEnabled = apiEnabled;
        this.apiPort = apiPort;
        this.apiBindAddress = apiBindAddress;
        this.wizardTimeoutSeconds = wizardTimeoutSeconds;
        this.messagePrefix = messagePrefix;
    }

    public static GatekeeperConfig load(Path dataDirectory, Logger logger) throws IOException {
        Path configPath = dataDirectory.resolve("config.yml");

        // Copy default config if not exists
        if (!Files.exists(configPath)) {
            Files.createDirectories(dataDirectory);
            try (InputStream defaultConfig = GatekeeperConfig.class.getClassLoader().getResourceAsStream("config.yml")) {
                if (defaultConfig != null) {
                    Files.copy(defaultConfig, configPath);
                    logger.info("Created default config.yml");
                }
            }
        }

        // Parse YAML
        Yaml yaml = new Yaml();
        Map<String, Object> root;
        try (InputStream is = Files.newInputStream(configPath)) {
            root = yaml.load(is);
        }

        return parse(root, logger);
    }

    @SuppressWarnings("unchecked")
    private static GatekeeperConfig parse(Map<String, Object> root, Logger logger) {
        // Database
        Map<String, Object> database = (Map<String, Object>) root.getOrDefault("database", new HashMap<>());
        String databasePath = (String) database.getOrDefault("path", "data/access.db");

        // Servers
        Map<String, Object> servers = (Map<String, Object>) root.getOrDefault("servers", new HashMap<>());
        Map<String, String> serverMapping = (Map<String, String>) servers.getOrDefault("mapping", new HashMap<>());
        List<String> restrictedServers = (List<String>) servers.getOrDefault("restricted", List.of());
        List<String> defaultServers = (List<String>) servers.getOrDefault("default_servers", List.of());

        // Discord
        Map<String, Object> discord = (Map<String, Object>) root.getOrDefault("discord", new HashMap<>());
        boolean discordEnabled = (Boolean) discord.getOrDefault("enabled", false);
        String discordWebhookUrl = (String) discord.getOrDefault("bot_webhook_url", "");
        String sharedSecret = (String) discord.getOrDefault("shared_secret", "");

        // API
        Map<String, Object> api = (Map<String, Object>) root.getOrDefault("api", new HashMap<>());
        boolean apiEnabled = (Boolean) api.getOrDefault("enabled", true);
        int apiPort = (Integer) api.getOrDefault("port", 8080);
        String apiBindAddress = (String) api.getOrDefault("bind_address", "0.0.0.0");

        // Application
        Map<String, Object> application = (Map<String, Object>) root.getOrDefault("application", new HashMap<>());
        int wizardTimeoutSeconds = (Integer) application.getOrDefault("wizard_timeout_seconds", 300);

        // Messages
        Map<String, Object> messages = (Map<String, Object>) root.getOrDefault("messages", new HashMap<>());
        String messagePrefix = (String) messages.getOrDefault("prefix", "&8[&6Gatekeeper&8]&r ");

        // Validate
        if (sharedSecret.equals("CHANGE_ME_TO_A_SECURE_SECRET")) {
            logger.warn("Shared secret has not been changed from default! Please update config.yml");
        }

        return new GatekeeperConfig(
            databasePath,
            serverMapping,
            restrictedServers,
            defaultServers,
            discordEnabled,
            discordWebhookUrl,
            sharedSecret,
            apiEnabled,
            apiPort,
            apiBindAddress,
            wizardTimeoutSeconds,
            messagePrefix
        );
    }

    // Getters
    public String getDatabasePath() { return databasePath; }
    public Map<String, String> getServerMapping() { return serverMapping; }
    public List<String> getRestrictedServers() { return restrictedServers; }
    public List<String> getDefaultServers() { return defaultServers; }
    public boolean isDiscordEnabled() { return discordEnabled; }
    public String getDiscordWebhookUrl() { return discordWebhookUrl; }
    public String getSharedSecret() { return sharedSecret; }
    public boolean isApiEnabled() { return apiEnabled; }
    public int getApiPort() { return apiPort; }
    public String getApiBindAddress() { return apiBindAddress; }
    public int getWizardTimeoutSeconds() { return wizardTimeoutSeconds; }
    public String getMessagePrefix() { return messagePrefix; }

    /**
     * Get logical server ID from Velocity server name.
     * Returns the server name itself if no mapping exists.
     */
    public String getLogicalServerId(String velocityServerName) {
        return serverMapping.getOrDefault(velocityServerName, velocityServerName);
    }

    /**
     * Check if a logical server ID is restricted (requires entitlement).
     */
    public boolean isRestricted(String logicalServerId) {
        return restrictedServers.contains(logicalServerId);
    }
}
