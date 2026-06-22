package com.gatekeeper.paper;

import com.gatekeeper.paper.api.GatekeeperApiClient;
import com.gatekeeper.paper.command.ApplyCommand;
import com.gatekeeper.paper.command.GoCommand;
import com.gatekeeper.paper.command.MenuCommand;
import com.gatekeeper.paper.config.PaperConfig;
import com.gatekeeper.paper.gui.GuiListener;
import com.gatekeeper.paper.gui.GuiService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Lobby-side companion plugin for the Gatekeeper access system.
 *
 * <p>Renders the application/world GUIs as native backend inventories (protocol-robust,
 * Geyser-friendly) instead of proxy-injected Protocolize packets, and talks to the
 * Velocity Gatekeeper over its HTTP admin API.
 */
public class GatekeeperPaperPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        saveDefaultConfig();

        PaperConfig config = PaperConfig.load(getConfig(), getLogger());
        GatekeeperApiClient api = new GatekeeperApiClient(config, getLogger());
        GuiService service = new GuiService(this, config, api);

        // Outgoing channel used to route players to backend servers via the proxy.
        getServer().getMessenger().registerOutgoingPluginChannel(this, GuiService.BUNGEE_CHANNEL);

        getServer().getPluginManager().registerEvents(new GuiListener(service), this);

        register("apply", new ApplyCommand(service));
        register("gkgo", new GoCommand(config, service));
        register("gkmenu", new MenuCommand(service));

        getLogger().info("GatekeeperPaper enabled. API target: " + config.getApiBaseUrl()
            + " (" + config.getWorlds().size() + " worlds configured)");
    }

    private void register(String name, org.bukkit.command.CommandExecutor executor) {
        PluginCommand command = getCommand(name);
        if (command != null) {
            command.setExecutor(executor);
        } else {
            getLogger().warning("Command '" + name + "' missing from plugin.yml");
        }
    }
}
