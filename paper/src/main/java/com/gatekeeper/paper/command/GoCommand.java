package com.gatekeeper.paper.command;

import com.gatekeeper.paper.config.PaperConfig;
import com.gatekeeper.paper.config.WorldEntry;
import com.gatekeeper.paper.gui.GuiService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /gkgo <logical_id> -> join a configured server if allowed, otherwise start
 * an application preselected for that server.
 */
public class GoCommand implements CommandExecutor {

    private final PaperConfig config;
    private final GuiService service;

    public GoCommand(PaperConfig config, GuiService service) {
        this.config = config;
        this.service = service;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }
        if (args.length != 1) {
            service.msg(player, "&7Usage: &f/" + label + " <server>");
            service.msg(player, "&7Servers: &f" + String.join(", ", config.getWorlds().stream()
                .map(WorldEntry::logicalId)
                .toList()));
            return true;
        }

        WorldEntry world = config.getWorldByLogicalId(args[0]);
        if (world == null) {
            service.msg(player, "&cUnknown server: &f" + args[0]);
            service.openWorldMenu(player);
            return true;
        }

        service.go(player, world);
        return true;
    }
}
