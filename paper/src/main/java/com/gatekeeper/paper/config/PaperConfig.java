package com.gatekeeper.paper.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Typed view over the plugin's config.yml.
 */
public class PaperConfig {

    private final String apiBaseUrl;
    private final String sharedSecret;
    private final int timeoutSeconds;
    private final String messagePrefix;
    private final List<WorldEntry> worlds;

    private PaperConfig(String apiBaseUrl, String sharedSecret, int timeoutSeconds,
                        String messagePrefix, List<WorldEntry> worlds) {
        this.apiBaseUrl = apiBaseUrl;
        this.sharedSecret = sharedSecret;
        this.timeoutSeconds = timeoutSeconds;
        this.messagePrefix = messagePrefix;
        this.worlds = worlds;
    }

    public static PaperConfig load(FileConfiguration cfg, Logger logger) {
        String base = cfg.getString("api.base_url", "http://velocity:8080");
        // Trim a trailing slash so we can append paths cleanly.
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String secret = cfg.getString("api.shared_secret", "");
        int timeout = cfg.getInt("api.timeout_seconds", 5);
        String prefix = cfg.getString("messages.prefix", "&8[&6Gatekeeper&8]&r ");

        List<WorldEntry> worlds = new ArrayList<>();
        List<?> rawWorlds = cfg.getList("worlds");
        if (rawWorlds != null) {
            for (Object obj : rawWorlds) {
                if (!(obj instanceof java.util.Map<?, ?> map)) {
                    continue;
                }
                String logicalId = str(map.get("logical_id"));
                String velocityName = str(map.get("velocity_name"));
                String displayName = map.get("display_name") != null ? str(map.get("display_name")) : logicalId;
                boolean restricted = Boolean.parseBoolean(str(map.get("restricted")));
                String materialName = map.get("material") != null ? str(map.get("material")) : "PAPER";
                Material material = Material.matchMaterial(materialName);
                if (logicalId == null || velocityName == null) {
                    logger.warning("Skipping world entry missing logical_id/velocity_name: " + map);
                    continue;
                }
                if (material == null) {
                    material = Material.PAPER;
                }
                worlds.add(new WorldEntry(logicalId, velocityName, displayName, material, restricted));
            }
        }

        if (secret.isBlank() || "CHANGE_ME_TO_A_SECURE_SECRET".equals(secret)) {
            logger.warning("api.shared_secret is not set! Calls to Velocity will be unauthorized. "
                + "Set it to match the Velocity config.");
        }

        return new PaperConfig(base, secret, timeout, prefix, worlds);
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    public String getSharedSecret() {
        return sharedSecret;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public String getMessagePrefix() {
        return messagePrefix;
    }

    public List<WorldEntry> getWorlds() {
        return worlds;
    }

    public WorldEntry getWorldByLogicalId(String logicalId) {
        return worlds.stream()
            .filter(w -> w.logicalId().equals(logicalId))
            .findFirst()
            .orElse(null);
    }
}
