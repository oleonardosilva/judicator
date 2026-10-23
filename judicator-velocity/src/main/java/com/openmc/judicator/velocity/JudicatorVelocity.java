package com.openmc.judicator.velocity;

import com.google.inject.Inject;
import com.openmc.judicator.Judicator;
import com.openmc.judicator.commons.command.CoreCommand;
import com.openmc.judicator.commons.db.Dialect;
import com.openmc.judicator.commons.db.RelationalDBManager;
import com.openmc.plugin.judicator.BuildConstants;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Plugin(id = "judicator",
        authors = "oleonardosilva",
        url = "https://github.com/oleonardosilva",
        description = "A moderation plugin",
        name = "Judicator",
        version = BuildConstants.VERSION)
public class JudicatorVelocity {

    private final Logger logger;
    private final ProxyServer server;
    private final Path dataDirectory;
    private Judicator judicator;

    @Inject
    public JudicatorVelocity(Logger logger, ProxyServer server, @DataDirectory Path dataDirectory) {
        this.logger = logger;
        this.server = server;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        logger.info("Initializing plugin with automatic dependency download...");
        final DependencyManager dependencyManager = new DependencyManager(this, server.getPluginManager(), logger, dataDirectory);

        // SHA-256 of each jar on Maven Central. Changing a version means changing its hash too.
        final List<DependencyManager.Dependency> dependencies = new ArrayList<>(Arrays.asList(
                new DependencyManager.Dependency("com.mysql:mysql-connector-j:9.3.0",
                        "6c8e6692b521376d89bc5618c16cdeaf8c61854329f4fa25677ed08776c5bb76"),
                new DependencyManager.Dependency("org.postgresql:postgresql:42.7.7",
                        "157963d60ae66d607e09466e8c0cdf8087e9cb20d0159899ffca96bca2528460"),
                new DependencyManager.Dependency("com.github.ben-manes.caffeine:caffeine:3.1.8",
                        "7dd15f9df1be238ffaa367ce6f556737a88031de4294dad18eef57c474ddf1d3"),
                new DependencyManager.Dependency("com.zaxxer:HikariCP:6.3.0",
                        "07c6344059af30a135144209c7c8bd666b8823124422ec859864d209d4ab7ca1")
        ));
        // The SQLite driver bundles native libraries (~13 MB): only fetch it when data.yml asks for SQLite.
        if (RelationalDBManager.readConfiguredDialect(dataDirectory) == Dialect.SQLITE) {
            dependencies.add(new DependencyManager.Dependency("org.xerial:sqlite-jdbc:3.46.1.2",
                    "f94550638a187cb8dd34391594303aa04ed3f168b31ab8026fe1e57db3a78c62"));
        }

        try {
            dependencyManager.downloadAndLoadDependencies(dependencies);
        } catch (IllegalStateException e) {
            logger.error("Judicator was not enabled: {}", e.getMessage());
            return;
        }

        final VelocityPlatform platform = new VelocityPlatform(this, server, logger, dataDirectory);
        final Judicator core = new Judicator(platform);
        try {
            core.enable();
        } catch (RuntimeException e) {
            logger.error("Judicator could not be enabled; check the configuration files.", e);
            core.disable();
            return;
        }
        this.judicator = core;

        registerCommands(core.getCommands());
        server.getEventManager().register(this, new VelocityListener(core));
        logger.info("Plugin has been initialized successfully!");
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        logger.info("Plugin is shutting down...");
        if (judicator != null) judicator.disable();
        logger.info("Plugin has been shut down successfully!");
    }

    private void registerCommands(List<CoreCommand> commands) {
        final CommandManager commandManager = server.getCommandManager();
        for (CoreCommand command : commands) {
            final CommandMeta meta = commandManager.metaBuilder(command.getName())
                    .aliases(command.getAliases().toArray(String[]::new))
                    .plugin(this)
                    .build();
            commandManager.register(meta, new VelocityCommandAdapter(judicator, command));
        }
    }

}
