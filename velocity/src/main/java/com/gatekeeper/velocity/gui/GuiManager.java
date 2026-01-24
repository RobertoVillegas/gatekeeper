package com.gatekeeper.velocity.gui;

import com.gatekeeper.velocity.config.GatekeeperConfig;
import com.gatekeeper.velocity.database.ApplicationRepository;
import com.gatekeeper.velocity.database.PlayerRepository;
import com.gatekeeper.velocity.model.Application;
import com.gatekeeper.velocity.model.GatekeeperPlayer;
import com.gatekeeper.velocity.model.Platform;
import com.gatekeeper.velocity.service.DiscordNotifier;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages open application GUIs and handles their lifecycle.
 */
public class GuiManager {

    private final Logger logger;
    private final GatekeeperConfig config;
    private final PlayerRepository playerRepository;
    private final ApplicationRepository applicationRepository;
    private final DiscordNotifier discordNotifier;

    private final Map<UUID, ApplicationGui> openGuis = new ConcurrentHashMap<>();

    public GuiManager(
        Logger logger,
        GatekeeperConfig config,
        PlayerRepository playerRepository,
        ApplicationRepository applicationRepository,
        DiscordNotifier discordNotifier
    ) {
        this.logger = logger;
        this.config = config;
        this.playerRepository = playerRepository;
        this.applicationRepository = applicationRepository;
        this.discordNotifier = discordNotifier;
    }

    /**
     * Open the application GUI for a player.
     *
     * @return true if GUI opened successfully, false if fallback is needed (e.g., unsupported protocol)
     */
    public boolean openApplicationGui(Player player, ApplicationData data, List<String> availableServers) {
        UUID uuid = player.getUniqueId();

        // Close any existing GUI
        if (openGuis.containsKey(uuid)) {
            openGuis.get(uuid).close();
            openGuis.remove(uuid);
        }

        // Get default servers from config
        List<String> defaultServers = config.getDefaultServers();

        // Create and open the GUI
        ApplicationGui gui = new ApplicationGui(
            uuid,
            data,
            availableServers,
            defaultServers,
            result -> handleGuiResult(player, result)
        );

        openGuis.put(uuid, gui);

        try {
            gui.open();
            logger.info("Opened application GUI for player {} ({})", player.getUsername(), uuid);
            return true;
        } catch (IllegalStateException e) {
            // Protocolize doesn't support this protocol version
            if (e.getMessage() != null && e.getMessage().contains("protocol version")) {
                openGuis.remove(uuid);
                logger.warn("GUI unavailable for player {} - unsupported protocol version", player.getUsername());
                return false;
            }
            throw e; // Re-throw other errors
        }
    }

    /**
     * Submit an application directly (used for text fallback when GUI is unavailable).
     */
    public void submitApplicationDirect(Player player, ApplicationData data, List<String> servers) {
        try {
            submitApplication(player, data, servers);
        } catch (SQLException e) {
            logger.error("Failed to submit application for {}", player.getUsername(), e);
            sendMessage(player, "&cFailed to submit application. Please try again later.");
        }
    }

    /**
     * Handle the result when a player closes or submits the GUI.
     */
    private void handleGuiResult(Player player, ApplicationGui.ApplicationResult result) {
        UUID uuid = player.getUniqueId();
        openGuis.remove(uuid);

        if (!result.submitted()) {
            sendMessage(player, "&7Application cancelled.");
            logger.info("Player {} ({}) cancelled application via GUI", player.getUsername(), uuid);
            return;
        }

        // Submit the application
        try {
            submitApplication(player, result.data(), result.selectedServers());
        } catch (SQLException e) {
            logger.error("Failed to submit application for {}", player.getUsername(), e);
            sendMessage(player, "&cFailed to submit application. Please try again later.");
        }
    }

    private void submitApplication(Player player, ApplicationData data, List<String> servers) throws SQLException {
        UUID uuid = player.getUniqueId();

        // Ensure player exists in database
        Platform platform = isBedrockPlayer(player) ? Platform.BEDROCK : Platform.JAVA;
        GatekeeperPlayer gkPlayer = GatekeeperPlayer.create(
            uuid,
            player.getUsername(),
            platform
        );
        playerRepository.upsert(gkPlayer);

        // Create application with requested servers in notes
        String serversNote = "Requested servers: " + String.join(", ", servers);
        String fullNotes = data.notes() != null
            ? data.notes() + " | " + serversNote
            : serversNote;

        Application application = Application.createPending(
            uuid,
            data.realName(),
            data.discord(),
            data.inviter(),
            fullNotes
        );

        application = applicationRepository.insert(application);

        logger.info("Player {} ({}) submitted application #{} for servers: {}",
            player.getUsername(), uuid, application.id(), servers);

        // Send confirmation
        sendSubmissionConfirmation(player, servers);

        // Notify Discord
        if (discordNotifier != null) {
            discordNotifier.notifyNewApplication(application, gkPlayer);
        }
    }

    private void sendSubmissionConfirmation(Player player, List<String> servers) {
        player.sendMessage(Component.empty());
        sendMessage(player, "&aApplication submitted!");
        player.sendMessage(Component.empty());
        player.sendMessage(colorize("&7Requested servers: &f" + String.join(", ", servers)));
        player.sendMessage(colorize("&7Your request is now pending review."));
        player.sendMessage(colorize("&7Use &f/apply status &7to check anytime."));
        player.sendMessage(Component.empty());
    }

    /**
     * Check if a player has an open GUI.
     */
    public boolean hasOpenGui(UUID uuid) {
        return openGuis.containsKey(uuid);
    }

    /**
     * Handle player disconnect - clean up their GUI.
     */
    public void handleDisconnect(UUID uuid) {
        ApplicationGui gui = openGuis.remove(uuid);
        if (gui != null) {
            logger.debug("Cleaned up GUI for disconnected player {}", uuid);
        }
    }

    /**
     * Shutdown - close all open GUIs.
     */
    public void shutdown() {
        for (ApplicationGui gui : openGuis.values()) {
            gui.close();
        }
        openGuis.clear();
    }

    private boolean isBedrockPlayer(Player player) {
        String uuid = player.getUniqueId().toString();
        return uuid.startsWith("00000000-0000-0000");
    }

    private void sendMessage(Player player, String message) {
        player.sendMessage(colorize(config.getMessagePrefix() + message));
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
