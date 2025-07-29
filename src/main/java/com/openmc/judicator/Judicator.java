package com.openmc.judicator;

import com.google.inject.Inject;
import com.openmc.judicator.commons.UUIDManager;
import com.openmc.judicator.commons.db.RelationalDBManager;
import com.openmc.judicator.punish.AccessAddressService;
import com.openmc.judicator.punish.PunishService;
import com.openmc.judicator.punish.commands.*;
import com.openmc.judicator.punish.data.cache.ImmuneCache;
import com.openmc.judicator.punish.data.cache.PunishCache;
import com.openmc.judicator.punish.data.cache.ReasonCache;
import com.openmc.judicator.punish.data.repository.AccessAddressRepository;
import com.openmc.judicator.punish.data.repository.PunishmentRepository;
import com.openmc.judicator.punish.data.repository.dao.AccessAddressRelationalDAO;
import com.openmc.judicator.punish.data.repository.dao.PunishmentRelationalDAO;
import com.openmc.judicator.punish.listeners.OnBuildingPunishListener;
import com.openmc.judicator.punish.listeners.OnPlayerConnectionListener;
import com.openmc.judicator.punish.listeners.OnTalkMutedListener;
import com.openmc.plugin.judicator.BuildConstants;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import lombok.Getter;
import org.slf4j.Logger;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

@Plugin(id = "judicator",
        authors = "oleonardosilva",
        url = "https://github.com/oleonardosilva",
        description = "A moderation plugin",
        name = "Judicator",
        version = BuildConstants.VERSION)
@Getter
public class Judicator {

    private final Logger logger;
    private final ProxyServer server;
    private final Path dataDirectory;
    private final PluginContainer container;
    private UUIDManager uuidManager;
    private ConfigurationNode messagesConfig;
    private ConfigurationNode immuneConfig;
    private ConfigurationNode dbConfig;
    private ConfigurationNode config;
    private ImmuneCache immuneCache;
    private PunishCache punishCache;
    private ReasonCache reasonCache;
    private RelationalDBManager relationalDBManager;
    private PunishService punishService;
    private AccessAddressService addressService;

    @Inject
    public Judicator(Logger logger, ProxyServer server, @DataDirectory Path dataDirectory, PluginContainer container) {
        this.logger = logger;
        this.server = server;
        this.dataDirectory = dataDirectory;
        this.container = container;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        this.messagesConfig = loadConfig("punishments", "messages.yml");
        this.config = loadConfig("punishments", "config.yml");
        this.immuneConfig = loadConfig("punishments", "immune.yml");
        this.dbConfig = loadConfig("data.yml");

        this.uuidManager = new UUIDManager(this);
        this.punishCache = new PunishCache(this);

        this.reasonCache = new ReasonCache();
        this.reasonCache.initialize(this);

        this.immuneCache = new ImmuneCache();
        this.immuneCache.initialize(this);

        this.relationalDBManager = new RelationalDBManager();
        this.relationalDBManager.initialize(dbConfig);

        this.registerServices();
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

    public void reloadConfigs() {
        this.messagesConfig = loadConfig("punishments", "messages.yml");
        this.config = loadConfig("punishments", "config.yml");
        this.immuneConfig = loadConfig("punishments", "immune.yml");
        this.dbConfig = loadConfig("data.yml");
        this.relationalDBManager.initialize(dbConfig);
        this.reasonCache.initialize(this);
        logger.info("Configuration files have been reloaded successfully!");
    }

    public void registerServices() {
        final PunishmentRepository punishmentRepository = new PunishmentRelationalDAO(relationalDBManager, logger);
        this.punishService = new PunishService(this, punishmentRepository);
        final AccessAddressRepository addressRepository = new AccessAddressRelationalDAO(relationalDBManager, logger);
        this.addressService = new AccessAddressService(addressRepository);
    }

    private void registerCommands() {
        new BanCommand(this).register();
        new BanIPCommand(this).register();
        new TempBanCommand(this).register();
        new TempBanIPCommand(this).register();
        new MuteCommand(this).register();
        new MuteIPCommand(this).register();
        new TempMuteIPCommand(this).register();
        new TempMuteCommand(this).register();
        new RevokeCommand(this).register();
        new PunishCommand(this).register();
        new PunishViewCommand(this).register();
        new PunishHistoryCommand(this).register();
        new JudicatorCommand(this).register();
    }

    private void registerListeners() {
        new OnBuildingPunishListener(this).register();
        new OnPlayerConnectionListener(this).register();
        try {
            new OnTalkMutedListener(this).register();
        } catch (SerializationException e) {
            logger.error(e.getMessage(), e);
        }
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
