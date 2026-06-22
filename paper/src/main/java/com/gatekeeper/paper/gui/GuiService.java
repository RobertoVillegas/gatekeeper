package com.gatekeeper.paper.gui;

import com.gatekeeper.paper.api.ApplicationContext;
import com.gatekeeper.paper.api.ApplicationData;
import com.gatekeeper.paper.api.GatekeeperApiClient;
import com.gatekeeper.paper.api.SubmitResult;
import com.gatekeeper.paper.config.PaperConfig;
import com.gatekeeper.paper.config.WorldEntry;
import com.gatekeeper.paper.util.Text;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * Orchestrates the GUI flows: fetches context off-thread, opens inventories on the
 * main thread, submits applications, and routes players to backend servers.
 */
public class GuiService {

    public static final String BUNGEE_CHANNEL = "BungeeCord";

    private final JavaPlugin plugin;
    private final PaperConfig config;
    private final GatekeeperApiClient api;

    public GuiService(JavaPlugin plugin, PaperConfig config, GatekeeperApiClient api) {
        this.plugin = plugin;
        this.config = config;
        this.api = api;
    }

    /** Open the application GUI after fetching the player's context from Velocity. */
    public void openApply(Player player, ApplicationData data) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            ApplicationContext ctx = api.getContext(player.getUniqueId(), player.getName());
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                if (ctx == null) {
                    msg(player, "&cCould not reach the access server. Please try again later.");
                    return;
                }
                if (ctx.hasPending) {
                    msg(player, "&eYou already have a pending application. Use &f/apply status&e to check it.");
                    return;
                }
                if (ctx.availableServers == null || ctx.availableServers.isEmpty()) {
                    msg(player, "&aYou already have access to all available servers!");
                    return;
                }
                ApplyGuiHolder holder = new ApplyGuiHolder(
                    data,
                    ctx.availableServers,
                    ctx.defaultServers == null ? List.of() : ctx.defaultServers);
                player.openInventory(holder.create());
            });
        });
    }

    /** Open the world-selection menu. */
    public void openWorldMenu(Player player) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            ApplicationContext ctx = api.getContext(player.getUniqueId(), player.getName());
            List<String> access = ctx != null && ctx.currentAccess != null ? ctx.currentAccess : List.of();
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                WorldMenuHolder holder = new WorldMenuHolder(config.getWorlds(), access);
                player.openInventory(holder.create());
            });
        });
    }

    /** Submit the selected application (called from the GUI click). */
    public void submit(Player player, ApplyGuiHolder holder) {
        ApplicationData data = holder.data();
        List<String> servers = holder.selectedServers();
        boolean bedrock = isBedrock(player);
        player.closeInventory();
        msg(player, "&7Submitting your application...");

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            SubmitResult result = api.submit(player.getUniqueId(), player.getName(), bedrock, data, servers);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                if (result.success()) {
                    msg(player, "&aApplication submitted! Requesting: &f" + String.join(", ", servers));
                    msg(player, "&7Your request is now pending review. Use &f/apply status&7 anytime.");
                } else {
                    msg(player, "&c" + result.error());
                }
            });
        });
    }

    /** Show the player the status of their latest application. */
    public void showStatus(Player player) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            ApplicationContext ctx = api.getContext(player.getUniqueId(), player.getName());
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                if (ctx == null) {
                    msg(player, "&cCould not reach the access server. Please try again later.");
                    return;
                }
                if (ctx.currentAccess != null && !ctx.currentAccess.isEmpty()) {
                    msg(player, "&aAccess: &f" + String.join(", ", ctx.currentAccess));
                }
                if (ctx.latest == null) {
                    if (ctx.currentAccess == null || ctx.currentAccess.isEmpty()) {
                        msg(player, "&7You have no application on record. Use &f/apply&7 to start one.");
                    }
                    return;
                }
                switch (ctx.latest.status) {
                    case "PENDING" -> msg(player, "&eYour application is &fpending review&e.");
                    case "APPROVED" -> msg(player, "&aYour application was &fapproved&a.");
                    case "DENIED" -> {
                        msg(player, "&cYour application was &fdenied&c.");
                        if (ctx.latest.decisionNote != null && !ctx.latest.decisionNote.isBlank()) {
                            msg(player, "&7Reason: &f" + ctx.latest.decisionNote);
                        }
                    }
                    default -> msg(player, "&7Application status: &f" + ctx.latest.status);
                }
            });
        });
    }

    /** Connect the player to a backend server through the Velocity proxy. */
    public void connect(Player player, WorldEntry world) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Connect");
        out.writeUTF(world.velocityName());
        player.sendPluginMessage(plugin, BUNGEE_CHANNEL, out.toByteArray());
    }

    public void msg(Player player, String legacy) {
        player.sendMessage(Text.of(config.getMessagePrefix() + legacy));
    }

    /** Floodgate assigns Bedrock players a UUID beginning with all-zero bits. */
    public static boolean isBedrock(Player player) {
        return player.getUniqueId().getMostSignificantBits() == 0L;
    }
}
