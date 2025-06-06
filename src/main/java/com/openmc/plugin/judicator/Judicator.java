package com.openmc.plugin.judicator;

import com.google.inject.Inject;
import com.openmc.plugin.judicator.commons.UUIDManager;
import com.openmc.plugin.judicator.commons.db.RelationalDBManager;
import com.openmc.plugin.judicator.punish.Immune;
import com.openmc.plugin.judicator.punish.PunishCache;
import com.openmc.plugin.judicator.punish.commands.BanCommand;
import com.openmc.plugin.judicator.punish.db.PunishmentRelationalDAO;
import com.openmc.plugin.judicator.punish.db.PunishmentRepository;
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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

@Plugin(id = "judicator", name = "Judicator", version = BuildConstants.VERSION)
@Getter
public class Judicator {

    private final Logger logger;
    private final ProxyServer server;
    private final UUIDManager uuidManager;
    private final Path dataDirectory;
    private final ConfigurationNode messagesConfig;
    private final ConfigurationNode immuneConfig;
    private final ConfigurationNode dbConfig;
    private final ConfigurationNode config;
    private final Immune immune;
    private final PunishCache punishCache;
    private final RelationalDBManager relationalDBManager;
    private final PunishmentRepository punishmentRepository;

    @Inject
    public Judicator(Logger logger, ProxyServer server, @DataDirectory Path dataDirectory) {
        this.logger = logger;
        this.server = server;
        this.dataDirectory = dataDirectory;
        this.messagesConfig = loadConfig("punishments" + File.pathSeparator + "messages.yml");
        this.config = loadConfig("punishments" + File.pathSeparator + "config.yml");
        this.immuneConfig = loadConfig("punishments" + File.pathSeparator + "immune.yml");
        this.dbConfig = loadConfig("data.yml");
        this.uuidManager = new UUIDManager(this);
        this.punishCache = new PunishCache(this);
        this.relationalDBManager = new RelationalDBManager(dbConfig);
        this.punishmentRepository = new PunishmentRelationalDAO(relationalDBManager, logger);
        punishmentRepository.initialize();
        this.immune = new Immune(this);
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
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

    private ConfigurationNode loadConfig(String fileName) {
        final Path pathConfig = dataDirectory.resolve(fileName);
        final YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
                .path(pathConfig)
                .build();

        if (!Files.exists(pathConfig)) {
            try {
                Files.createDirectories(pathConfig.getParent());
                Files.copy(Objects.requireNonNull(getClass().getResourceAsStream(fileName)), pathConfig);
            } catch (IOException e) {
                logger.error("Failed to create configuration file: {}", e.getMessage());
                System.exit(1);
            }
        }

        try {
            CommentedConfigurationNode node = loader.load();
            logger.info("Loaded configuration file: {}", fileName);
            return node;
        } catch (IOException e) {
            logger.error("Unable to read YAML configuration: {}", e.getMessage());
            if (e.getCause() != null) {
                logger.error(e.getMessage(), e);
            }
            System.exit(1);
            return null;
        }
    }
}
