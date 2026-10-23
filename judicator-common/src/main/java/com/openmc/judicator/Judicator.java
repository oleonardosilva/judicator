package com.openmc.judicator;

import com.openmc.judicator.commons.JudicatorCommand;
import com.openmc.judicator.commons.StaffNotifier;
import com.openmc.judicator.commons.UUIDManager;
import com.openmc.judicator.commons.command.CoreCommand;
import com.openmc.judicator.commons.db.RelationalDBManager;
import com.openmc.judicator.discord.DiscordWebhookService;
import com.openmc.judicator.importer.ImportService;
import com.openmc.judicator.platform.Platform;
import com.openmc.judicator.punish.AccessAddressService;
import com.openmc.judicator.punish.AddressPolicy;
import com.openmc.judicator.punish.PunishService;
import com.openmc.judicator.punish.commands.*;
import com.openmc.judicator.punish.data.cache.ImmuneCache;
import com.openmc.judicator.punish.hierarchy.HierarchyService;
import com.openmc.judicator.punish.hierarchy.PlayerLevelRelationalDAO;
import com.openmc.judicator.punish.hierarchy.PlayerLevelRepository;
import com.openmc.judicator.punish.rules.PunishCooldown;
import com.openmc.judicator.punish.rules.TimeLimitService;
import com.openmc.judicator.punish.data.cache.PunishCache;
import com.openmc.judicator.punish.data.cache.ReasonCache;
import com.openmc.judicator.punish.data.repository.AccessAddressRepository;
import com.openmc.judicator.punish.data.repository.PunishmentRepository;
import com.openmc.judicator.punish.data.repository.dao.AccessAddressRelationalDAO;
import com.openmc.judicator.punish.data.repository.dao.PunishmentRelationalDAO;
import com.openmc.judicator.punish.listeners.ChatHandler;
import com.openmc.judicator.punish.listeners.ConnectionHandler;
import com.openmc.judicator.warns.WarnService;
import com.openmc.judicator.warns.cache.ActionCache;
import com.openmc.judicator.warns.commands.*;
import com.openmc.judicator.warns.repository.WarnRepository;
import com.openmc.judicator.warns.repository.dao.WarnRelationalDAO;
import lombok.Getter;
import org.slf4j.Logger;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Getter
public class Judicator {

    private final Platform platform;
    private final Logger logger;
    private final Path dataDirectory;
    private UUIDManager uuidManager;
    private ConfigurationNode messagesConfig;
    private ConfigurationNode immuneConfig;
    private ConfigurationNode dbConfig;
    private ConfigurationNode config;
    private ConfigurationNode discordConfig;
    private DiscordWebhookService discordWebhookService;
    private ImmuneCache immuneCache;
    private PunishCache punishCache;
    private ReasonCache reasonCache;
    private ActionCache actionCache;
    private RelationalDBManager relationalDBManager;
    private PunishService punishService;
    private WarnService warnService;
    private AccessAddressService addressService;
    private AddressPolicy addressPolicy;
    private HierarchyService hierarchyService;
    private TimeLimitService timeLimitService;
    private PunishCooldown punishCooldown;
    private StaffNotifier staffNotifier;
    private ImportService importService;
    private ConnectionHandler connectionHandler;
    private ChatHandler chatHandler;
    private List<CoreCommand> commands;

    /**
     * Where the services keep their data. The plugin uses the relational database of {@code data.yml}; tests pass
     * in-memory repositories so commands and listeners can be exercised without a database.
     */
    public record Repositories(PunishmentRepository punishments, WarnRepository warns,
                               AccessAddressRepository addresses, PlayerLevelRepository levels) {
    }

    private final Repositories providedRepositories;

    public Judicator(Platform platform) {
        this(platform, null);
    }

    /**
     * @param repositories repositories to use instead of the database configured in {@code data.yml}; {@code null}
     *                     for the database (what the plugin does)
     */
    public Judicator(Platform platform, Repositories repositories) {
        this.platform = platform;
        this.logger = platform.getLogger();
        this.dataDirectory = platform.getDataDirectory();
        this.providedRepositories = repositories;
    }

    public void enable() {
        this.config = loadConfig("config.yml");
        this.messagesConfig = loadConfig("messages_" + config.node("language").getString("en") + ".yml");
        this.immuneConfig = loadConfig("immune.yml");
        this.dbConfig = loadConfig("data.yml");
        this.discordConfig = loadConfig("discord.yml");

        this.discordWebhookService = new DiscordWebhookService(this);

        this.uuidManager = new UUIDManager(this);
        this.punishCache = new PunishCache(this);

        this.reasonCache = new ReasonCache();
        this.reasonCache.initialize(this);

        this.immuneCache = new ImmuneCache();
        this.immuneCache.initialize(this);

        this.actionCache = new ActionCache();
        this.actionCache.initialize(this);

        final Repositories repositories;
        if (providedRepositories == null) {
            this.relationalDBManager = new RelationalDBManager();
            this.relationalDBManager.initialize(dbConfig, dataDirectory, logger);
            repositories = new Repositories(
                    new PunishmentRelationalDAO(relationalDBManager, logger),
                    new WarnRelationalDAO(relationalDBManager, logger),
                    new AccessAddressRelationalDAO(relationalDBManager, logger),
                    new PlayerLevelRelationalDAO(relationalDBManager, logger));
        } else {
            repositories = providedRepositories;
        }

        this.registerServices(repositories);
        this.connectionHandler = new ConnectionHandler(this);
        this.chatHandler = new ChatHandler(this);
        this.commands = createCommands();
    }

    public void disable() {
        if (punishCache != null) punishCache.shutdown();
        if (relationalDBManager != null) relationalDBManager.shutdown();
    }

    public void reloadConfigs() {
        this.config = loadConfig("config.yml");
        this.discordConfig = loadConfig("discord.yml");
        this.messagesConfig = loadConfig("messages_" + config.node("language").getString("en") + ".yml");
        this.immuneConfig = loadConfig("immune.yml");
        this.dbConfig = loadConfig("data.yml");
        logger.info("Configuration files have been reloaded successfully!");
        if (relationalDBManager != null) this.relationalDBManager.initialize(dbConfig, dataDirectory, logger);
        this.reasonCache.initialize(this);
        this.immuneCache.initialize(this);
        this.actionCache.initialize(this);
        this.timeLimitService.reload();
        this.addressPolicy.reload();
        this.chatHandler.reload();
    }

    private void registerServices(Repositories repositories) {
        this.punishService = new PunishService(this, repositories.punishments());
        this.addressService = new AccessAddressService(repositories.addresses());
        this.addressPolicy = new AddressPolicy(this);
        this.warnService = new WarnService(repositories.warns());
        this.hierarchyService = new HierarchyService(this, repositories.levels());
        this.timeLimitService = new TimeLimitService(this);
        this.punishCooldown = new PunishCooldown(this);
        this.staffNotifier = new StaffNotifier(this);
        this.importService = new ImportService(this, repositories.punishments(), repositories.warns(), repositories.addresses());
    }

    /**
     * {@code import.yml} is only needed by {@code /judicator import}, so it is read (and created) on demand.
     */
    public ConfigurationNode loadImportConfig() {
        return loadConfig("import.yml");
    }

    private List<CoreCommand> createCommands() {
        final List<CoreCommand> list = new ArrayList<>(PunishTypeCommand.all(this));
        list.add(new RevokeCommand(this));
        list.add(new PunishCommand(this));
        list.add(new PunishViewCommand(this));
        list.add(new PunishHistoryCommand(this));
        list.add(new KickCommand(this));
        list.addAll(UnpunishCommand.all(this));
        list.add(new AltsCommand(this));
        list.add(new BlameCommand(this));
        list.addAll(ActiveListCommand.all(this));
        list.add(new ActivityCommand(this));
        list.add(new WarnCommand(this));
        list.add(new TempWarnCommand(this));
        list.add(new UnwarnCommand(this));
        list.add(new WarnHistoryCommand(this));
        list.add(new WarnViewCommand(this));
        list.add(new JudicatorCommand(this));
        return List.copyOf(list);
    }

    private ConfigurationNode loadConfig(String... subpaths) {
        final Path pathConfig = dataDirectory.resolve(Paths.get("", subpaths));
        final String resourcePath = "/" + String.join("/", subpaths);

        if (!Files.exists(pathConfig)) {
            try (InputStream resource = Judicator.class.getResourceAsStream(resourcePath)) {
                if (resource == null) {
                    throw new IllegalStateException("Resource not found: " + resourcePath);
                }
                Files.createDirectories(pathConfig.getParent());
                logger.info("Creating config file {} at {} ", resourcePath, pathConfig.toAbsolutePath());
                Files.copy(resource, pathConfig);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to create config file " + pathConfig.getFileName(), e);
            }
        }

        try {
            final YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
                    .path(pathConfig)
                    .build();

            final CommentedConfigurationNode node = loader.load();
            logger.info("Loaded config file: {}", pathConfig.getFileName());
            if (pathConfig.getFileName().toString().startsWith("messages_")) {
                mergeBundledDefaults(node, resourcePath);
            }
            return node;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load YAML config " + pathConfig.getFileName(), e);
        }
    }

    /**
     * Fills keys missing from an older messages file with the bundled ones, in memory only: the file on disk is
     * not rewritten, so the server owner's edits and comments stay as they are.
     */
    private void mergeBundledDefaults(ConfigurationNode node, String resourcePath) {
        final URL resource = Judicator.class.getResource(resourcePath);
        if (resource == null) return;
        try {
            node.mergeFrom(YamlConfigurationLoader.builder().url(resource).build().load());
        } catch (IOException e) {
            logger.warn("Could not read bundled defaults for {}", resourcePath, e);
        }
    }

}
