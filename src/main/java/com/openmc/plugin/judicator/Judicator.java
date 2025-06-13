package com.openmc.plugin.judicator;

import com.google.inject.Inject;
import com.openmc.plugin.judicator.commons.UUIDManager;
import com.openmc.plugin.judicator.commons.db.RelationalDBManager;
import com.openmc.plugin.judicator.punish.PunishService;
import com.openmc.plugin.judicator.punish.commands.BanCommand;
import com.openmc.plugin.judicator.punish.data.cache.ImmuneCache;
import com.openmc.plugin.judicator.punish.data.cache.PunishCache;
import com.openmc.plugin.judicator.punish.data.repository.PunishmentRelationalDAO;
import com.openmc.plugin.judicator.punish.data.repository.PunishmentRepository;
import com.openmc.plugin.judicator.punish.listeners.ChatListener;
import com.openmc.plugin.judicator.punish.listeners.PlayerConnectionListener;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import lombok.Getter;
import org.slf4j.Logger;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

@Plugin(id = "judicator",
        authors = "oleonardosilva",
        url = "https://github.com/oleonardosilva",
        description = "A moderation plugin for your server",
        name = "Judicator",
        version = BuildConstants.VERSION)
@Getter
public class Judicator {

    private final Logger logger;
    private final ProxyServer server;
    private UUIDManager uuidManager;
    private final Path dataDirectory;
    private ConfigurationNode messagesConfig;
    private ConfigurationNode immuneConfig;
    private ConfigurationNode dbConfig;
    private ConfigurationNode config;
    private ImmuneCache immuneCache;
    private PunishCache punishCache;
    private RelationalDBManager relationalDBManager;
    private PunishmentRepository punishmentRepository;
    private PunishService punishService;

    @Inject
    public Judicator(Logger logger, ProxyServer server, @DataDirectory Path dataDirectory) {
        this.logger = logger;
        this.server = server;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        this.messagesConfig = loadConfig("punishments", "messages.yml");
        this.config = loadConfig("punishments", "config.yml");
        this.immuneConfig = loadConfig("punishments", "immune.yml");
        this.dbConfig = loadConfig("data.yml");
        this.uuidManager = new UUIDManager(this);
        this.punishCache = new PunishCache(this);
        this.relationalDBManager = new RelationalDBManager(dbConfig);
        this.punishmentRepository = new PunishmentRelationalDAO(relationalDBManager, logger);
        this.punishService = new PunishService(this);
        this.immuneCache = new ImmuneCache(this);

        this.registerCommands();
        this.registerListeners();
        logger.info("Plugin has been initialized successfully!");
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        logger.info("Plugin is shutting down...");
        punishCache.shutdown();
        relationalDBManager.shutdown();
        logger.info("Plugin has been shut down successfully!");
    }

    private void registerCommands() {
        new BanCommand(this).register();
    }

    private void registerListeners() {
        new ChatListener(this).register();
        new PlayerConnectionListener(this).register();
    }

    private ConfigurationNode loadConfig(String... subpaths) {
        final Path pathConfig = dataDirectory.resolve(Paths.get("", subpaths));
        final String resourcePath = "/" + String.join("/", subpaths);

        if (!Files.exists(pathConfig)) {
            try {
                Files.createDirectories(pathConfig.getParent());
                logger.info("Creating config file {} at {} ", resourcePath, pathConfig.toAbsolutePath());
                Files.copy(
                        Objects.requireNonNull(getClass().getResourceAsStream(resourcePath),
                                "Resource not found: " + resourcePath),
                        pathConfig
                );
            } catch (IOException e) {
                logger.error("Failed to create config file: {}", e.getMessage(), e);
                System.exit(1);
            }
        }

        try {
            final YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
                    .path(pathConfig)
                    .build();

            final CommentedConfigurationNode node = loader.load();
            logger.info("Loaded config file: {}", pathConfig.getFileName());
            return node;
        } catch (IOException e) {
            logger.error("Failed to load YAML config: {}", e.getMessage(), e);
            System.exit(1);
            return null;
        }
    }

}
