package com.gatekeeper.velocity.wizard;

import com.gatekeeper.velocity.config.GatekeeperConfig;
import com.gatekeeper.velocity.database.ApplicationRepository;
import com.gatekeeper.velocity.database.PlayerRepository;
import com.gatekeeper.velocity.model.Application;
import com.gatekeeper.velocity.model.GatekeeperPlayer;
import com.gatekeeper.velocity.model.Platform;
import com.gatekeeper.velocity.service.DiscordNotifier;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.slf4j.Logger;

import java.sql.SQLException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class WizardManager {
    private static final int MAX_NAME_LENGTH = 50;
    private static final int MAX_NOTES_LENGTH = 200;

    private final Logger logger;
    private final GatekeeperConfig config;
    private final PlayerRepository playerRepository;
    private final ApplicationRepository applicationRepository;
    private final DiscordNotifier discordNotifier;

    private final Map<UUID, WizardSession> activeSessions = new ConcurrentHashMap<>();
    private ScheduledFuture<?> cleanupTask;

    public WizardManager(
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

    public void startCleanupTask(ScheduledExecutorService scheduler) {
        cleanupTask = scheduler.scheduleAtFixedRate(
            this::cleanupTimedOutSessions,
            30, 30, TimeUnit.SECONDS
        );
    }

    public void shutdown() {
        if (cleanupTask != null) {
            cleanupTask.cancel(false);
        }
        activeSessions.clear();
    }

    /**
     * Check if a player has an active wizard session.
     */
    public boolean hasActiveSession(UUID uuid) {
        return activeSessions.containsKey(uuid);
    }

    /**
     * Get the active session for a player.
     */
    public Optional<WizardSession> getSession(UUID uuid) {
        return Optional.ofNullable(activeSessions.get(uuid));
    }

    /**
     * Start a new wizard session for a player.
     */
    public WizardSession startSession(Player player) {
        WizardSession session = new WizardSession(player.getUniqueId());
        activeSessions.put(player.getUniqueId(), session);

        logger.info("Player {} ({}) started application wizard",
            player.getUsername(), player.getUniqueId());

        sendWizardStart(player);
        sendCurrentQuestion(player, session);

        return session;
    }

    /**
     * Cancel a wizard session.
     */
    public void cancelSession(Player player) {
        WizardSession session = activeSessions.remove(player.getUniqueId());
        if (session != null) {
            sendMessage(player, "&7Application cancelled. You can start again with &f/apply&7.");
            logger.info("Player {} ({}) cancelled application wizard",
                player.getUsername(), player.getUniqueId());
        }
    }

    /**
     * Handle chat input from a player in a wizard session.
     * Returns true if the message was consumed by the wizard.
     */
    public boolean handleInput(Player player, String message) {
        WizardSession session = activeSessions.get(player.getUniqueId());
        if (session == null) {
            return false;
        }

        session.touch();
        String input = message.trim();
        String inputLower = input.toLowerCase();

        // Handle special commands
        if (inputLower.equals("cancel")) {
            cancelSession(player);
            return true;
        }

        // Handle based on current state
        switch (session.getState()) {
            case ASK_REAL_NAME -> handleRealName(player, session, input);
            case ASK_DISCORD -> handleDiscord(player, session, input);
            case ASK_INVITER -> handleInviter(player, session, input);
            case ASK_NOTES -> handleNotes(player, session, input);
            case CONFIRM -> handleConfirm(player, session, input);
            default -> { return false; }
        }

        return true;
    }

    private void handleRealName(Player player, WizardSession session, String input) {
        if (input.isEmpty()) {
            sendError(player, "This field is required. Please enter your name.");
            return;
        }

        if (input.length() > MAX_NAME_LENGTH) {
            sendError(player, "That's too long. Please keep it under " + MAX_NAME_LENGTH + " characters.");
            return;
        }

        session.setRealName(sanitize(input));
        sendConfirmation(player, "Real name", session.getRealName());
        session.advance();
        sendCurrentQuestion(player, session);
    }

    private void handleDiscord(Player player, WizardSession session, String input) {
        if (input.toLowerCase().equals("skip")) {
            session.setDiscordTag(null);
            sendSkipped(player, "Discord");
        } else {
            if (input.length() > MAX_NAME_LENGTH) {
                sendError(player, "That's too long. Please keep it under " + MAX_NAME_LENGTH + " characters.");
                return;
            }
            session.setDiscordTag(sanitize(input));
            sendConfirmation(player, "Discord", session.getDiscordTag());
        }
        session.advance();
        sendCurrentQuestion(player, session);
    }

    private void handleInviter(Player player, WizardSession session, String input) {
        if (input.toLowerCase().equals("skip")) {
            session.setInviter(null);
            sendSkipped(player, "Inviter");
        } else {
            if (input.length() > MAX_NAME_LENGTH) {
                sendError(player, "That's too long. Please keep it under " + MAX_NAME_LENGTH + " characters.");
                return;
            }
            session.setInviter(sanitize(input));
            sendConfirmation(player, "Invited by", session.getInviter());
        }
        session.advance();
        sendCurrentQuestion(player, session);
    }

    private void handleNotes(Player player, WizardSession session, String input) {
        if (input.toLowerCase().equals("skip")) {
            session.setNotes(null);
            sendSkipped(player, "Notes");
        } else {
            if (input.length() > MAX_NOTES_LENGTH) {
                sendError(player, "That's too long. Please keep it under " + MAX_NOTES_LENGTH + " characters.");
                return;
            }
            session.setNotes(sanitize(input));
            sendConfirmation(player, "Notes", session.getNotes());
        }
        session.advance();
        sendConfirmationScreen(player, session);
    }

    private void handleConfirm(Player player, WizardSession session, String input) {
        String inputLower = input.toLowerCase();

        if (inputLower.equals("confirm")) {
            submitApplication(player, session);
        } else if (inputLower.equals("cancel")) {
            cancelSession(player);
        } else {
            sendError(player, "Please type &fconfirm &cto submit or &fcancel &cto abort.");
        }
    }

    private void submitApplication(Player player, WizardSession session) {
        try {
            // Ensure player exists in database
            Platform platform = isBedrockPlayer(player) ? Platform.BEDROCK : Platform.JAVA;
            GatekeeperPlayer gkPlayer = GatekeeperPlayer.create(
                player.getUniqueId(),
                player.getUsername(),
                platform
            );
            playerRepository.upsert(gkPlayer);

            // Create application
            Application application = Application.createPending(
                player.getUniqueId(),
                session.getRealName(),
                session.getDiscordTag(),
                session.getInviter(),
                session.getNotes()
            );

            application = applicationRepository.insert(application);

            logger.info("Player {} ({}) submitted application #{}",
                player.getUsername(), player.getUniqueId(), application.id());

            // Remove session
            activeSessions.remove(player.getUniqueId());
            session.setState(WizardState.SUBMITTED);

            // Send confirmation
            sendSubmissionConfirmation(player);

            // Notify Discord
            if (discordNotifier != null) {
                discordNotifier.notifyNewApplication(application, gkPlayer);
            }

        } catch (SQLException e) {
            logger.error("Failed to submit application for {}", player.getUsername(), e);
            sendError(player, "Could not save your application. Please try again later.");
        }
    }

    private void sendWizardStart(Player player) {
        player.sendMessage(Component.empty());
        sendMessage(player, "&aStarting access application...");
        player.sendMessage(Component.empty());
        player.sendMessage(colorize("&7Answer the following questions by typing in chat."));
        player.sendMessage(colorize("&7Type &fcancel &7at any time to abort."));
        player.sendMessage(Component.empty());
        player.sendMessage(colorize("&8─────────────────────────────────────"));
        player.sendMessage(Component.empty());
    }

    private void sendCurrentQuestion(Player player, WizardSession session) {
        switch (session.getState()) {
            case ASK_REAL_NAME -> sendQuestion(player, 1, 4,
                "What is your real name?",
                "This helps us know who you are. First name is fine.");

            case ASK_DISCORD -> sendQuestion(player, 2, 4,
                "What is your Discord username?",
                "Optional. Type &fskip &7to leave blank.");

            case ASK_INVITER -> sendQuestion(player, 3, 4,
                "Who invited you to the server?",
                "Optional. Type &fskip &7if no one.");

            case ASK_NOTES -> sendQuestion(player, 4, 4,
                "Anything else you'd like us to know?",
                "Optional. Type &fskip &7to leave blank.");

            default -> {}
        }
    }

    private void sendQuestion(Player player, int current, int total, String question, String hint) {
        player.sendMessage(colorize("&6Question " + current + " of " + total));
        player.sendMessage(Component.empty());
        player.sendMessage(colorize("&f" + question));
        player.sendMessage(colorize("&7" + hint));
        player.sendMessage(Component.empty());
        player.sendMessage(colorize("&8▶ &fType your answer:"));
    }

    private void sendConfirmationScreen(Player player, WizardSession session) {
        player.sendMessage(Component.empty());
        player.sendMessage(colorize("&8─────────────────────────────────────"));
        player.sendMessage(Component.empty());
        player.sendMessage(colorize("&6Review Your Application"));
        player.sendMessage(Component.empty());
        player.sendMessage(colorize("&7Real name:    &f" + session.getRealName()));
        player.sendMessage(colorize("&7Discord:      &f" + valueOrDash(session.getDiscordTag())));
        player.sendMessage(colorize("&7Invited by:   &f" + valueOrDash(session.getInviter())));
        player.sendMessage(colorize("&7Notes:        &f" + valueOrDash(session.getNotes())));
        player.sendMessage(Component.empty());
        player.sendMessage(colorize("&8─────────────────────────────────────"));
        player.sendMessage(Component.empty());
        player.sendMessage(colorize("&aType &fconfirm &ato submit your application."));
        player.sendMessage(colorize("&cType &fcancel &cto abort."));
    }

    private void sendSubmissionConfirmation(Player player) {
        player.sendMessage(Component.empty());
        sendMessage(player, "&aApplication submitted!");
        player.sendMessage(Component.empty());
        player.sendMessage(colorize("&7Your request is now pending review."));
        player.sendMessage(colorize("&7You'll be notified when an admin responds."));
        player.sendMessage(colorize("&7Use &f/apply status &7to check anytime."));
        player.sendMessage(Component.empty());
    }

    private void sendConfirmation(Player player, String field, String value) {
        player.sendMessage(colorize("&7" + field + ": &f" + value));
        player.sendMessage(Component.empty());
    }

    private void sendSkipped(Player player, String field) {
        player.sendMessage(colorize("&7" + field + ": &8(skipped)"));
        player.sendMessage(Component.empty());
    }

    private void sendMessage(Player player, String message) {
        player.sendMessage(colorize(config.getMessagePrefix() + message));
    }

    private void sendError(Player player, String message) {
        sendMessage(player, "&c" + message);
    }

    private String valueOrDash(String value) {
        return value != null && !value.isEmpty() ? value : "—";
    }

    private String sanitize(String input) {
        // Remove color codes
        return input.replaceAll("§[0-9a-fk-or]", "")
                   .replaceAll("&[0-9a-fk-or]", "")
                   .trim();
    }

    private boolean isBedrockPlayer(Player player) {
        // Floodgate prefixes Bedrock UUIDs with zeros
        // Or check if username starts with configured prefix (usually ".")
        String uuid = player.getUniqueId().toString();
        return uuid.startsWith("00000000-0000-0000");
    }

    private void cleanupTimedOutSessions() {
        int timeout = config.getWizardTimeoutSeconds();

        activeSessions.entrySet().removeIf(entry -> {
            WizardSession session = entry.getValue();
            if (session.isTimedOut(timeout)) {
                logger.debug("Wizard session timed out for {}", entry.getKey());
                return true;
            }
            return false;
        });
    }

    /**
     * Handle player disconnect - clean up their session.
     */
    public void handleDisconnect(UUID uuid) {
        WizardSession removed = activeSessions.remove(uuid);
        if (removed != null) {
            logger.debug("Cleaned up wizard session for disconnected player {}", uuid);
        }
    }

    private Component colorize(String message) {
        return Component.text(translateColorCodes(message));
    }

    private String translateColorCodes(String message) {
        // Simple color code translation for display
        // In production, you'd use MiniMessage or similar
        return message
            .replace("&0", "§0")
            .replace("&1", "§1")
            .replace("&2", "§2")
            .replace("&3", "§3")
            .replace("&4", "§4")
            .replace("&5", "§5")
            .replace("&6", "§6")
            .replace("&7", "§7")
            .replace("&8", "§8")
            .replace("&9", "§9")
            .replace("&a", "§a")
            .replace("&b", "§b")
            .replace("&c", "§c")
            .replace("&d", "§d")
            .replace("&e", "§e")
            .replace("&f", "§f")
            .replace("&r", "§r");
    }
}
