package com.gatekeeper.velocity.command;

import com.gatekeeper.velocity.config.GatekeeperConfig;
import com.gatekeeper.velocity.database.ApplicationRepository;
import com.gatekeeper.velocity.database.EntitlementRepository;
import com.gatekeeper.velocity.model.Application;
import com.gatekeeper.velocity.model.ApplicationStatus;
import com.gatekeeper.velocity.wizard.WizardManager;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

public class ApplyCommand implements SimpleCommand {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a")
        .withZone(ZoneId.systemDefault());

    private final Logger logger;
    private final GatekeeperConfig config;
    private final WizardManager wizardManager;
    private final ApplicationRepository applicationRepository;
    private final EntitlementRepository entitlementRepository;

    public ApplyCommand(
        Logger logger,
        GatekeeperConfig config,
        WizardManager wizardManager,
        ApplicationRepository applicationRepository,
        EntitlementRepository entitlementRepository
    ) {
        this.logger = logger;
        this.config = config;
        this.wizardManager = wizardManager;
        this.applicationRepository = applicationRepository;
        this.entitlementRepository = entitlementRepository;
    }

    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof Player player)) {
            invocation.source().sendMessage(Component.text("This command can only be used by players."));
            return;
        }

        String[] args = invocation.arguments();

        if (args.length == 0) {
            handleApply(player);
        } else {
            switch (args[0].toLowerCase()) {
                case "status" -> handleStatus(player);
                case "cancel" -> handleCancel(player);
                default -> sendUsage(player);
            }
        }
    }

    private void handleApply(Player player) {
        try {
            // Check if already in wizard
            if (wizardManager.hasActiveSession(player.getUniqueId())) {
                sendMessage(player, "&eYou already have an application in progress.");
                sendMessage(player, "&7Type &fcancel &7to abort it, or continue answering.");
                return;
            }

            // Check if already has access
            List<String> servers = entitlementRepository.findActiveServerIdsByUuid(player.getUniqueId());
            if (!servers.isEmpty()) {
                sendMessage(player, "&aYou already have access to the server!");
                player.sendMessage(colorize("&7Servers: &f" + String.join(", ", servers)));
                return;
            }

            // Check for pending application
            Optional<Application> pending = applicationRepository.findPendingByUuid(player.getUniqueId());
            if (pending.isPresent()) {
                sendMessage(player, "&eYou already have a pending application.");
                player.sendMessage(colorize("&7Use &f/apply status &7to check its status."));
                return;
            }

            // Start wizard
            wizardManager.startSession(player);

        } catch (SQLException e) {
            logger.error("Database error in /apply for {}", player.getUsername(), e);
            sendMessage(player, "&cSomething went wrong. Please try again later.");
        }
    }

    private void handleStatus(Player player) {
        try {
            // Check active entitlements
            List<String> servers = entitlementRepository.findActiveServerIdsByUuid(player.getUniqueId());

            // Get latest application
            Optional<Application> latest = applicationRepository.findLatestByUuid(player.getUniqueId());

            if (latest.isEmpty() && servers.isEmpty()) {
                sendMessage(player, "&7You have no application on record.");
                player.sendMessage(colorize("&7Use &f/apply &7to request access."));
                return;
            }

            if (latest.isPresent()) {
                Application app = latest.get();

                player.sendMessage(Component.empty());

                switch (app.status()) {
                    case PENDING -> {
                        sendMessage(player, "&eYour application is pending review.");
                        player.sendMessage(Component.empty());
                        player.sendMessage(colorize("&7Submitted: &f" + formatTime(app.createdAt())));
                        player.sendMessage(colorize("&7Status:    &e⏳ Pending"));
                        player.sendMessage(Component.empty());
                        player.sendMessage(colorize("&7An admin will review it soon."));
                    }
                    case APPROVED -> {
                        sendMessage(player, "&aYour application was approved!");
                        player.sendMessage(Component.empty());
                        player.sendMessage(colorize("&7Decided:   &f" + formatTime(app.decidedAt())));
                        player.sendMessage(colorize("&7Status:    &a✓ Approved"));
                        if (!servers.isEmpty()) {
                            player.sendMessage(colorize("&7Access:    &f" + String.join(", ", servers)));
                        }
                        player.sendMessage(Component.empty());
                        player.sendMessage(colorize("&7You can now join these servers."));
                    }
                    case DENIED -> {
                        sendMessage(player, "&cYour application was denied.");
                        player.sendMessage(Component.empty());
                        player.sendMessage(colorize("&7Decided:   &f" + formatTime(app.decidedAt())));
                        player.sendMessage(colorize("&7Status:    &c✗ Denied"));
                        if (app.decisionNote() != null && !app.decisionNote().isEmpty()) {
                            player.sendMessage(colorize("&7Reason:    &f" + app.decisionNote()));
                        }
                    }
                }
            } else if (!servers.isEmpty()) {
                // Has access but no application (granted directly)
                sendMessage(player, "&aYou have server access.");
                player.sendMessage(colorize("&7Access: &f" + String.join(", ", servers)));
            }

        } catch (SQLException e) {
            logger.error("Database error in /apply status for {}", player.getUsername(), e);
            sendMessage(player, "&cSomething went wrong. Please try again later.");
        }
    }

    private void handleCancel(Player player) {
        if (wizardManager.hasActiveSession(player.getUniqueId())) {
            wizardManager.cancelSession(player);
        } else {
            sendMessage(player, "&7You don't have an active application wizard.");
        }
    }

    private void sendUsage(Player player) {
        sendMessage(player, "&7Usage:");
        player.sendMessage(colorize("&f/apply &7- Start an access application"));
        player.sendMessage(colorize("&f/apply status &7- Check your application status"));
        player.sendMessage(colorize("&f/apply cancel &7- Cancel an in-progress application"));
    }

    private void sendMessage(Player player, String message) {
        player.sendMessage(colorize(config.getMessagePrefix() + message));
    }

    private String formatTime(Instant instant) {
        if (instant == null) return "—";
        return DATE_FORMAT.format(instant);
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
    public List<String> suggest(Invocation invocation) {
        if (invocation.arguments().length <= 1) {
            return List.of("status", "cancel");
        }
        return List.of();
    }
}
