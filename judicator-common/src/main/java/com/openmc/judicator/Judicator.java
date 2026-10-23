package com.openmc.judicator;

import com.openmc.judicator.commons.DataRetentionService;
import com.openmc.judicator.commons.JudicatorCommand;
import com.openmc.judicator.commons.StaffNotifier;
import com.openmc.judicator.commons.UUIDManager;
import com.openmc.judicator.commons.command.CoreCommand;
import com.openmc.judicator.commons.db.RelationalDBManager;
import com.openmc.judicator.discord.DiscordWebhookService;
import com.openmc.judicator.importer.ImportService;
import com.openmc.judicator.notes.NoteService;
import com.openmc.judicator.notes.commands.DelNoteCommand;
import com.openmc.judicator.notes.commands.NoteCommand;
import com.openmc.judicator.notes.commands.NotesCommand;
import com.openmc.judicator.notes.repository.NoteRepository;
import com.openmc.judicator.notes.repository.dao.NoteRelationalDAO;
import com.openmc.judicator.platform.JudicatorPlayer;
import com.openmc.judicator.platform.JudicatorSender;
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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Getter
public class Judicator {

    /**
     * Languages with a bundled {@code messages_<language>.yml}, offered per player with {@code per-player-language}.
     */
    public static final List<String> LANGUAGES = List.of("en", "es", "pt");

    private final Platform platform;
    private final Logger logger;
    private final Path dataDirectory;
    private UUIDManager uuidManager;
    private ConfigurationNode messagesConfig;
    private volatile Map<String, ConfigurationNode> messagesByLanguage = Map.of();
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
    private NoteService noteService;
    private AccessAddressService addressService;
    private AddressPolicy addressPolicy;
    private HierarchyService hierarchyService;
    private TimeLimitService timeLimitService;
    private PunishCooldown punishCooldown;
    private StaffNotifier staffNotifier;
    private ImportService importService;
    private DataRetentionService dataRetentionService;
    private ConnectionHandler connectionHandler;
    private ChatHandler chatHandler;
    private List<CoreCommand> commands;

    /**
     * Where the services keep their data. The plugin uses the relational database of {@code data.yml}; tests pass
     * in-memory repositories so commands and listeners can be exercised without a database.
     */
    public record Repositories(PunishmentRepository punishments, WarnRepository warns,
                               AccessAddressRepository addresses, PlayerLevelRepository levels, NoteRepository notes) {
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
        loadMessages();
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
                    new PlayerLevelRelationalDAO(relationalDBManager, logger),
                    new NoteRelationalDAO(relationalDBManager, logger));
        } else {
            repositories = providedRepositories;
        }

        this.registerServices(repositories);
        this.connectionHandler = new ConnectionHandler(this);
        this.chatHandler = new ChatHandler(this);
        this.commands = createCommands();
        this.dataRetentionService.start();
        // A plugin reload (Bukkit /reload) paused the online-only mutes of the players still online in disable().
        final List<String> online = platform.getOnlinePlayers().stream().map(JudicatorPlayer::getName).toList();
        if (!online.isEmpty()) platform.runAsync(() -> online.forEach(punishService::resumeOnlineOnlyMutes));
    }

    public void disable() {
        // Players still online when the server stops never send a disconnect to the plugin: stop their online-only mutes here.
        if (punishService != null) {
            platform.getOnlinePlayers().forEach(player -> {
                try {
                    punishService.pauseOnlineOnlyMutes(player.getName());
                } catch (RuntimeException e) {
                    logger.warn("Could not pause the online-only mutes of {}", player.getName(), e);
                }
            });
        }
        if (dataRetentionService != null) dataRetentionService.stop();
        if (punishCache != null) punishCache.shutdown();
        if (relationalDBManager != null) relationalDBManager.shutdown();
    }

    public void reloadConfigs() {
        this.config = loadConfig("config.yml");
        this.discordConfig = loadConfig("discord.yml");
        loadMessages();
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
        this.noteService = new NoteService(repositories.notes());
        this.hierarchyService = new HierarchyService(this, repositories.levels());
        this.timeLimitService = new TimeLimitService(this);
        this.punishCooldown = new PunishCooldown(this);
        this.staffNotifier = new StaffNotifier(this);
        this.importService = new ImportService(this, repositories.punishments(), repositories.warns(), repositories.addresses(),
                repositories.notes());
        this.dataRetentionService = new DataRetentionService(this, repositories);
    }

    /**
     * Messages in the server language ({@code language}); with {@code per-player-language: true}, also the bundled
     * languages, so each player can get theirs.
     */
    private void loadMessages() {
        final String language = config.node("language").getString("en").toLowerCase(Locale.ROOT);
        this.messagesConfig = loadConfig("messages_" + language + ".yml");
        if (!config.node("per-player-language").getBoolean(false)) {
            this.messagesByLanguage = Map.of();
            return;
        }
        final Map<String, ConfigurationNode> bundles = new HashMap<>();
        for (String other : LANGUAGES) {
            bundles.put(other, other.equals(language) ? messagesConfig : loadConfig("messages_" + other + ".yml"));
        }
        this.messagesByLanguage = Map.copyOf(bundles);
    }

    /**
     * Whether the UUIDs players join with are their real (Mojang) ones: the server is in online mode, or it is a
     * Spigot/Paper backend behind a proxy that forwards them ({@code trust-uuids: true}; such a backend reports
     * online-mode false).
     */
    public boolean trustsUuids() {
        return platform.isOnlineMode() || (config != null && config.node("trust-uuids").getBoolean(false));
    }

    /**
     * The messages to send to someone: in the language of their game client with {@code per-player-language: true}
     * (when it is one of {@link #LANGUAGES}), otherwise in the server language. The console, and a player whose client
     * has not told its language yet, get the server language.
     */
    public ConfigurationNode messagesFor(JudicatorSender recipient) {
        if (recipient == null || messagesByLanguage.isEmpty()) return messagesConfig;
        final Optional<ConfigurationNode> own = recipient.getLocale()
                .map(locale -> messagesByLanguage.get(locale.getLanguage().toLowerCase(Locale.ROOT)));
        return own.orElse(messagesConfig);
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
        list.add(new StaffRollbackCommand(this));
        list.addAll(EditPunishmentCommand.all(this));
        list.addAll(ActiveListCommand.all(this));
        list.add(new ActivityCommand(this));
        list.add(new WarnCommand(this));
        list.add(new TempWarnCommand(this));
        list.add(new UnwarnCommand(this));
        list.add(new WarnHistoryCommand(this));
        list.add(new WarnViewCommand(this));
        list.add(new NoteCommand(this));
        list.add(new NotesCommand(this));
        list.add(new DelNoteCommand(this));
        list.add(new PlayerInfoCommand(this));
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
