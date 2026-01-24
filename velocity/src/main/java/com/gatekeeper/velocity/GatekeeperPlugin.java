package com.gatekeeper.velocity;

import com.gatekeeper.velocity.api.AdminApiServer;
import com.gatekeeper.velocity.command.AccessCommand;
import com.gatekeeper.velocity.command.ApplyCommand;
import com.gatekeeper.velocity.config.GatekeeperConfig;
import com.gatekeeper.velocity.database.ApplicationRepository;
import com.gatekeeper.velocity.database.AuditLogRepository;
import com.gatekeeper.velocity.database.Database;
import com.gatekeeper.velocity.database.EntitlementRepository;
import com.gatekeeper.velocity.database.PlayerRepository;
import com.gatekeeper.velocity.gui.GuiManager;
import com.gatekeeper.velocity.listener.ConnectionListener;
import com.gatekeeper.velocity.service.AccessService;
import com.gatekeeper.velocity.service.DiscordNotifier;
import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Plugin(
    id = "gatekeeper",
    name = "Gatekeeper",
    version = "2.2.0-SNAPSHOT",
    description = "Access management and application system",
    authors = {"Gatekeeper Team"},
    dependencies = {
        @Dependency(id = "protocolize", optional = true)
    }
)
public class GatekeeperPlugin {

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;

    // Subsystems
    private GatekeeperConfig config;
    private Database database;
    private ScheduledExecutorService scheduler;
    private GuiManager guiManager;
    private AdminApiServer adminApiServer;

    @Inject
    public GatekeeperPlugin(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        logger.info("Gatekeeper v2.2.0 starting...");

        try {
            // 1. Load configuration
            config = GatekeeperConfig.load(dataDirectory, logger);
            logger.info("Configuration loaded");

            // 2. Initialize database
            database = new Database(logger, dataDirectory, config.getDatabasePath());
            database.initialize();

            // 3. Create repositories
            PlayerRepository playerRepository = new PlayerRepository(database);
            ApplicationRepository applicationRepository = new ApplicationRepository(database);
            EntitlementRepository entitlementRepository = new EntitlementRepository(database);
            AuditLogRepository auditLogRepository = new AuditLogRepository(database);

            // 4. Create scheduler
            scheduler = Executors.newScheduledThreadPool(2);

            // 5. Create Discord notifier (if enabled)
            DiscordNotifier discordNotifier = null;
            if (config.isDiscordEnabled()) {
                discordNotifier = new DiscordNotifier(logger, config, scheduler);
                logger.info("Discord notifications enabled");
            }

            // 6. Create services
            AccessService accessService = new AccessService(
                logger,
                config,
                server,
                playerRepository,
                applicationRepository,
                entitlementRepository,
                auditLogRepository,
                discordNotifier
            );

            // 7. Create GUI manager
            guiManager = new GuiManager(
                logger,
                config,
                playerRepository,
                applicationRepository,
                discordNotifier
            );

            // 8. Register event listeners
            ConnectionListener connectionListener = new ConnectionListener(
                logger,
                config,
                playerRepository,
                entitlementRepository,
                guiManager
            );
            server.getEventManager().register(this, connectionListener);

            logger.info("Event listeners registered");

            // 9. Register commands
            CommandManager commandManager = server.getCommandManager();

            CommandMeta applyMeta = commandManager.metaBuilder("apply")
                .aliases("request", "whitelist")
                .plugin(this)
                .build();
            commandManager.register(applyMeta, new ApplyCommand(
                logger,
                config,
                guiManager,
                applicationRepository,
                entitlementRepository
            ));

            CommandMeta accessMeta = commandManager.metaBuilder("access")
                .aliases("gatekeeper", "gk")
                .plugin(this)
                .build();
            commandManager.register(accessMeta, new AccessCommand(
                logger,
                config,
                accessService,
                playerRepository,
                applicationRepository
            ));

            logger.info("Commands registered: /apply, /access");

            // 10. Start Admin API (if enabled)
            if (config.isApiEnabled()) {
                adminApiServer = new AdminApiServer(
                    logger,
                    config,
                    accessService,
                    playerRepository,
                    applicationRepository,
                    entitlementRepository
                );
                adminApiServer.start();
            }

            // Log server mappings
            logger.info("Loaded {} server mappings, {} restricted servers",
                config.getServerMapping().size(),
                config.getRestrictedServers().size());

            logger.info("Gatekeeper started successfully");

        } catch (Exception e) {
            logger.error("Failed to initialize Gatekeeper", e);
            logger.error("Gatekeeper will be disabled. Please check configuration and restart.");
        }
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        logger.info("Gatekeeper shutting down...");

        // Stop Admin API
        if (adminApiServer != null) {
            adminApiServer.stop();
        }

        // Stop GUI manager
        if (guiManager != null) {
            guiManager.shutdown();
        }

        // Shutdown scheduler
        if (scheduler != null) {
            scheduler.shutdownNow();
        }

        // Close database
        if (database != null) {
            database.close();
        }

        logger.info("Gatekeeper shutdown complete");
    }

    public ProxyServer getServer() {
        return server;
    }

    public Logger getLogger() {
        return logger;
    }

    public Path getDataDirectory() {
        return dataDirectory;
    }
}
