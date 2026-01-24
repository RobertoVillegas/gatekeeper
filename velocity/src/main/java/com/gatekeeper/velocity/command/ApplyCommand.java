package com.gatekeeper.velocity.command;

import com.gatekeeper.velocity.config.GatekeeperConfig;
import com.gatekeeper.velocity.database.ApplicationRepository;
import com.gatekeeper.velocity.database.EntitlementRepository;
import com.gatekeeper.velocity.gui.ApplicationData;
import com.gatekeeper.velocity.gui.GuiManager;
import com.gatekeeper.velocity.model.Application;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.slf4j.Logger;

import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Command for players to apply for server access.
 *
 * Usage:
 *   /apply "RealName" "Inviter" ["Discord"] ["Notes"]
 *   /apply status
 *
 * Examples:
 *   /apply "Roberto" "FriendName"
 *   /apply "Roberto" "FriendName" "rob#1234"
 *   /apply "Roberto" "FriendName" "rob#1234" "I'm a friend from school"
 *   /apply status
 */
public class ApplyCommand implements SimpleCommand {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a")
        .withZone(ZoneId.systemDefault());

    // Pattern to match quoted strings or unquoted words
    private static final Pattern ARG_PATTERN = Pattern.compile("\"([^\"]*)\"|([^\\s]+)");

    private final Logger logger;
    private final GatekeeperConfig config;
    private final GuiManager guiManager;
    private final ApplicationRepository applicationRepository;
    private final EntitlementRepository entitlementRepository;

    public ApplyCommand(
        Logger logger,
        GatekeeperConfig config,
        GuiManager guiManager,
        ApplicationRepository applicationRepository,
        EntitlementRepository entitlementRepository
    ) {
        this.logger = logger;
        this.config = config;
        this.guiManager = guiManager;
        this.applicationRepository = applicationRepository;
        this.entitlementRepository = entitlementRepository;
    }

    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof Player player)) {
            invocation.source().sendMessage(Component.text("This command can only be used by players."));
            return;
        }

        // Join all arguments back into a single string for proper parsing
        String fullArgs = String.join(" ", invocation.arguments());

        if (fullArgs.isEmpty()) {
            sendUsage(player);
            return;
        }

        // Parse arguments
        List<String> args = parseArguments(fullArgs);

        if (args.isEmpty()) {
            sendUsage(player);
            return;
        }

        // Check for subcommands
        String firstArg = args.get(0).toLowerCase();
        if (firstArg.equals("status")) {
            handleStatus(player);
            return;
        }

        // Otherwise, treat as application with arguments
        handleApply(player, args);
    }

    private void handleApply(Player player, List<String> args) {
        // Validate argument count (need at least: name, inviter)
        if (args.size() < 2) {
            sendMessage(player, "&cNot enough arguments.");
            sendUsage(player);
            return;
        }

        try {
            // Check if already has GUI open
            if (guiManager.hasOpenGui(player.getUniqueId())) {
                sendMessage(player, "&eYou already have an application form open.");
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

            // Parse application data: name, inviter, [discord], [notes]
            String name = sanitize(args.get(0));
            String inviter = sanitize(args.get(1));
            String discord = args.size() > 2 ? sanitize(args.get(2)) : null;
            String notes = args.size() > 3 ? sanitize(args.get(3)) : null;

            // Validate lengths
            if (name.length() > 50) {
                sendMessage(player, "&cName is too long (max 50 characters).");
                return;
            }
            if (inviter.length() > 50) {
                sendMessage(player, "&cInviter name is too long (max 50 characters).");
                return;
            }
            if (discord != null && discord.length() > 50) {
                sendMessage(player, "&cDiscord tag is too long (max 50 characters).");
                return;
            }
            if (notes != null && notes.length() > 200) {
                sendMessage(player, "&cNotes are too long (max 200 characters).");
                return;
            }

            // Create application data
            ApplicationData data = new ApplicationData(name, discord, inviter, notes);

            // Get available servers (restricted ones)
            List<String> availableServers = config.getRestrictedServers();

            if (availableServers.isEmpty()) {
                sendMessage(player, "&cNo servers are configured for applications.");
                return;
            }

            // Open the GUI
            guiManager.openApplicationGui(player, data, availableServers);

            logger.info("Player {} ({}) started application with name='{}', inviter='{}', discord='{}'",
                player.getUsername(), player.getUniqueId(), name, inviter, discord);

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
                sendUsage(player);
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

    /**
     * Parse a command string into arguments, handling quoted strings.
     */
    private List<String> parseArguments(String input) {
        List<String> args = new ArrayList<>();
        Matcher matcher = ARG_PATTERN.matcher(input);

        while (matcher.find()) {
            if (matcher.group(1) != null) {
                // Quoted string
                args.add(matcher.group(1));
            } else if (matcher.group(2) != null) {
                // Unquoted word
                args.add(matcher.group(2));
            }
        }

        return args;
    }

    private void sendUsage(Player player) {
        player.sendMessage(Component.empty());
        sendMessage(player, "&fServer Access Application");
        player.sendMessage(Component.empty());
        player.sendMessage(colorize("&7Usage:"));
        player.sendMessage(colorize("&f/apply \"Name\" \"Inviter\" &7[\"Discord\"] [\"Notes\"]"));
        player.sendMessage(Component.empty());
        player.sendMessage(colorize("&7Examples:"));
        player.sendMessage(colorize("&f/apply \"Roberto\" \"FriendName\""));
        player.sendMessage(colorize("&f/apply \"Roberto\" \"FriendName\" \"robdiscord\""));
        player.sendMessage(Component.empty());

        // Clickable example
        Component clickable = Component.text("Click here to use example command", NamedTextColor.AQUA)
            .clickEvent(ClickEvent.suggestCommand("/apply \"YourName\" \"WhoInvitedYou\""))
            .hoverEvent(HoverEvent.showText(Component.text("Click to fill in the command")));
        player.sendMessage(clickable);

        player.sendMessage(Component.empty());
        player.sendMessage(colorize("&7Other commands:"));
        player.sendMessage(colorize("&f/apply status &7- Check your application status"));
    }

    private void sendMessage(Player player, String message) {
        player.sendMessage(colorize(config.getMessagePrefix() + message));
    }

    private String formatTime(Instant instant) {
        if (instant == null) return "—";
        return DATE_FORMAT.format(instant);
    }

    private String sanitize(String input) {
        if (input == null) return null;
        return input
            .replaceAll("§[0-9a-fk-or]", "")
            .replaceAll("&[0-9a-fk-or]", "")
            .trim();
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
        String[] args = invocation.arguments();

        if (args.length == 0) {
            return List.of("status", "\"YourName\" \"WhoInvitedYou\"");
        }

        if (args.length == 1 && !args[0].startsWith("\"")) {
            String partial = args[0].toLowerCase();
            List<String> suggestions = new ArrayList<>();
            if ("status".startsWith(partial)) {
                suggestions.add("status");
            }
            return suggestions;
        }

        return List.of();
    }
}
