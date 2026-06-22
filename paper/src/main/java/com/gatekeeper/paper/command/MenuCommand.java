package com.gatekeeper.paper.command;

import com.gatekeeper.paper.gui.GuiService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /gkmenu (aliases: worlds, servers) -> opens the world-selection menu.
 * Intended to be triggered by a FancyNpc "run command as player".
 */
public class MenuCommand implements CommandExecutor {

    private final GuiService service;

    public MenuCommand(GuiService service) {
        this.service = service;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }
        service.openWorldMenu(player);
        return true;
    }
}
