package com.elless.judicator.velocity;

import com.google.inject.Inject;
import com.elless.judicator.Judicator;
import com.elless.judicator.commons.command.CoreCommand;
import com.elless.judicator.commons.db.Dialect;
import com.elless.judicator.commons.db.RelationalDBManager;
import com.elless.judicator.commons.libs.DependencyManager;
import com.elless.judicator.commons.libs.Libraries;
import com.elless.plugin.judicator.BuildConstants;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import org.bstats.velocity.Metrics;
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

    // https://bstats.org/what-is-my-plugin-id
    private static final int BSTATS_PLUGIN_ID = 34894;

    private final Logger logger;
    private final ProxyServer server;
    private final Path dataDirectory;
    private final Metrics.Factory metricsFactory;
    private Judicator judicator;

    @Inject
    public JudicatorVelocity(Logger logger, ProxyServer server, @DataDirectory Path dataDirectory,
                             Metrics.Factory metricsFactory) {
        this.logger = logger;
        this.server = server;
        this.dataDirectory = dataDirectory;
        this.metricsFactory = metricsFactory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        logger.info("Initializing plugin with automatic dependency download...");
        final DependencyManager dependencyManager = new DependencyManager(logger, dataDirectory);
        final List<DependencyManager.Dependency> dependencies = new ArrayList<>(Arrays.asList(
                Libraries.MYSQL, Libraries.POSTGRESQL, Libraries.CAFFEINE, Libraries.HIKARI));
        if (RelationalDBManager.readConfiguredDialect(dataDirectory) == Dialect.SQLITE) {
            dependencies.add(Libraries.SQLITE);
        }

        try {
            dependencyManager.download(dependencies).forEach(jar -> server.getPluginManager().addToClasspath(this, jar));
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
        server.getEventManager().register(this, new VelocityListener(core, server, platform));
        server.getChannelRegistrar().register(VelocityPlatform.TELEPORT_CHANNEL);
        metricsFactory.make(this, BSTATS_PLUGIN_ID);
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
