package com.gatekeeper.paper.command;

import com.gatekeeper.paper.api.ApplicationData;
import com.gatekeeper.paper.gui.GuiService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * /apply "Name" "Inviter" ["Discord"] ["Notes"]  -> opens the server-selection GUI
 * /apply status                                   -> shows the latest application status
 */
public class ApplyCommand implements CommandExecutor {

    private static final Pattern ARG_PATTERN = Pattern.compile("\"([^\"]*)\"|(\\S+)");

    private final GuiService service;

    public ApplyCommand(GuiService service) {
        this.service = service;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        List<String> parsed = parse(String.join(" ", args));

        if (parsed.isEmpty()) {
            sendUsage(player);
            return true;
        }

        if (parsed.get(0).equalsIgnoreCase("status")) {
            service.showStatus(player);
            return true;
        }

        if (parsed.size() < 2) {
            service.msg(player, "&cNot enough arguments.");
            sendUsage(player);
            return true;
        }

        String name = sanitize(parsed.get(0));
        String inviter = sanitize(parsed.get(1));
        String discord = parsed.size() > 2 ? sanitize(parsed.get(2)) : null;
        String notes = parsed.size() > 3 ? sanitize(parsed.get(3)) : null;

        if (name.length() > 50 || inviter.length() > 50
            || (discord != null && discord.length() > 50)
            || (notes != null && notes.length() > 200)) {
            service.msg(player, "&cOne of your fields is too long.");
            return true;
        }

        service.openApply(player, new ApplicationData(name, inviter, discord, notes));
        return true;
    }

    private void sendUsage(Player player) {
        service.msg(player, "&fServer Access Application");
        service.msg(player, "&7Usage: &f/apply \"Name\" \"Inviter\" &7[\"Discord\"] [\"Notes\"]");
        service.msg(player, "&7Example: &f/apply \"Roberto\" \"FriendName\"");
        service.msg(player, "&7Check status: &f/apply status");
    }

    private List<String> parse(String input) {
        List<String> args = new ArrayList<>();
        Matcher m = ARG_PATTERN.matcher(input);
        while (m.find()) {
            args.add(m.group(1) != null ? m.group(1) : m.group(2));
        }
        return args;
    }

    private String sanitize(String input) {
        if (input == null) {
            return null;
        }
        return input.replaceAll("[§&][0-9a-fk-orA-FK-OR]", "").trim();
    }
}
