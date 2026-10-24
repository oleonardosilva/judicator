package com.openmc.judicator;

import com.openmc.judicator.reports.ReportService;
import com.openmc.judicator.reports.commands.ReportCommand;
import com.openmc.judicator.reports.commands.ReportsCommand;
import com.openmc.judicator.players.PlayerDirectory;
import com.openmc.judicator.commons.DataRetentionService;
import com.openmc.judicator.commons.JudicatorCommand;
import com.openmc.judicator.commons.StaffNotifier;
import com.openmc.judicator.commons.UUIDManager;
import com.openmc.judicator.commons.command.CoreCommand;
import com.openmc.judicator.commons.db.LegacyTables;
import com.openmc.judicator.commons.db.RelationalDBManager;
import com.openmc.judicator.discord.DiscordWebhookService;
import com.openmc.judicator.importer.ImportService;
import com.openmc.judicator.api.JudicatorProvider;
import com.openmc.judicator.integration.JudicatorApiService;
import com.openmc.judicator.integration.JudicatorEventBus;
import com.openmc.judicator.notes.NoteService;
import com.openmc.judicator.notes.commands.DelNoteCommand;
import com.openmc.judicator.notes.commands.NoteCommand;
import com.openmc.judicator.notes.commands.NotesCommand;
import com.openmc.judicator.notes.repository.NoteRepository;
import com.openmc.judicator.notes.repository.dao.NoteRelationalDAO;
import com.openmc.judicator.platform.JudicatorSender;
import com.openmc.judicator.platform.Platform;
import com.openmc.judicator.players.PlayerRef;
import com.openmc.judicator.players.repository.PlayerRepository;
import com.openmc.judicator.players.repository.dao.PlayerRelationalDAO;
import com.openmc.judicator.punish.AccessAddressService;
import com.openmc.judicator.punish.AddressPolicy;
import com.openmc.judicator.punish.PunishService;
import com.openmc.judicator.punish.commands.*;
import com.openmc.judicator.punish.data.cache.ImmuneCache;
import com.openmc.judicator.punish.data.repository.PlayerAddressRepository;
import com.openmc.judicator.punish.data.repository.dao.PlayerAddressRelationalDAO;
import com.openmc.judicator.punish.hierarchy.HierarchyService;
import com.openmc.judicator.punish.hierarchy.PlayerLevelRelationalDAO;
import com.openmc.judicator.punish.hierarchy.PlayerLevelRepository;
import com.openmc.judicator.punish.rules.PunishCooldown;
import com.openmc.judicator.punish.rules.TimeLimitService;
import com.openmc.judicator.punish.data.cache.PunishCache;
import com.openmc.judicator.punish.data.cache.ReasonCache;
import com.openmc.judicator.punish.data.repository.PunishmentRepository;
import com.openmc.judicator.punish.data.repository.dao.PunishmentRelationalDAO;
import com.openmc.judicator.punish.handlers.PunishmentEffects;
import com.openmc.judicator.punish.listeners.ChatHandler;
import com.openmc.judicator.punish.listeners.ConnectionHandler;
import com.openmc.judicator.punish.scopes.ScopeService;
import com.openmc.judicator.reports.repository.ReportRepository;
import com.openmc.judicator.reports.repository.dao.ReportRelationalDAO;
import com.openmc.judicator.sync.SyncService;
import com.openmc.judicator.sync.repository.SyncRepository;
import com.openmc.judicator.sync.repository.dao.SyncRelationalDAO;
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
import java.util.Objects;
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
    /** who a name or a player is for the data (UUID or name) and the {@code players} table */
    private PlayerDirectory playerDirectory;
    /** where punishments apply ({@code scopes}) */
    private ScopeService scopeService;
    /** player reports ({@code /report}, {@code /reports}) */
    private ReportService reportService;
    private AddressPolicy addressPolicy;
    private HierarchyService hierarchyService;
    private TimeLimitService timeLimitService;
    private PunishCooldown punishCooldown;
    private StaffNotifier staffNotifier;
    private ImportService importService;
    private DataRetentionService dataRetentionService;
    private PunishmentEffects punishmentEffects;
    private SyncService syncService;
    /** events for other plugins ({@code judicator-api}) */
    private JudicatorEventBus eventBus;
    /** what other plugins get from {@link JudicatorProvider} */
    private JudicatorApiService api;
    /** {@code proxy-mode} as read at startup (not reloadable) */
    private boolean proxyMode;
    private ConnectionHandler connectionHandler;
    private ChatHandler chatHandler;
    private List<CoreCommand> commands;

    /**
     * Where the services keep their data. The plugin uses the relational database of {@code data.yml}; tests pass
     * in-memory repositories so commands and listeners can be exercised without a database.
     */
    public record Repositories(PunishmentRepository punishments, WarnRepository warns,
                               PlayerAddressRepository addresses, PlayerLevelRepository levels, NoteRepository notes,
                               SyncRepository sync, PlayerRepository players, ReportRepository reports) {
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
        this.proxyMode = readProxyMode();
        this.eventBus = new JudicatorEventBus(logger);

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
            this.relationalDBManager = new RelationalDBManager(platform.databaseDrivers().orElse(null));
            this.relationalDBManager.initialize(dbConfig, dataDirectory, logger);
            LegacyTables.migrate(relationalDBManager, logger);
            repositories = new Repositories(
                    new PunishmentRelationalDAO(relationalDBManager, logger),
                    new WarnRelationalDAO(relationalDBManager, logger),
                    new PlayerAddressRelationalDAO(relationalDBManager, logger),
                    new PlayerLevelRelationalDAO(relationalDBManager, logger),
                    new NoteRelationalDAO(relationalDBManager, logger),
                    new SyncRelationalDAO(relationalDBManager, logger),
                    new PlayerRelationalDAO(relationalDBManager, logger),
                    new ReportRelationalDAO(relationalDBManager, logger));
        } else {
            repositories = providedRepositories;
        }

        this.registerServices(repositories);
        // first start of 2.0: the accounts already known, then the IP history of 1.x by account
        this.playerDirectory.seed();
        repositories.addresses().migrateLegacy(playerDirectory.legacyNames());
        this.connectionHandler = new ConnectionHandler(this);
        this.chatHandler = new ChatHandler(this);
        this.commands = createCommands();
        this.api = new JudicatorApiService(this);
        JudicatorProvider.register(api);
        this.syncService.start();
        if (proxyMode && !syncService.isEnabled()) {
            logger.warn("proxy-mode without sync.enabled: punishments applied on the proxy reach this server only at the player's next login.");
        }
        // The proxy keeps the data and the online time: a backend in proxy mode leaves both alone.
        if (proxyMode) return;
        this.dataRetentionService.start();
        // A plugin reload (Bukkit /reload) paused the online-only mutes of the players still online in disable().
        final List<PlayerRef> online = platform.getOnlinePlayers().stream().map(playerDirectory::refOf).toList();
        if (!online.isEmpty()) platform.runAsync(() -> online.forEach(punishService::resumeOnlineOnlyMutes));
    }

    /**
     * {@code proxy-mode: true} on a Spigot/Paper backend behind a proxy (Velocity, BungeeCord) that runs Judicator: the
     * proxy refuses logins, applies the effects players see and keeps IP and online-time records; this server keeps the
     * mute cache and blocks muted chat. Ignored on the proxy.
     */
    private boolean readProxyMode() {
        final boolean configured = config.node("proxy-mode").getBoolean(false);
        if (configured && platform.getType().isProxy()) {
            logger.warn("proxy-mode is for Spigot/Paper backends and is ignored on the proxy.");
            return false;
        }
        return configured;
    }

    public void disable() {
        if (api != null) JudicatorProvider.unregister(api);
        // Players still online when the server stops never send a disconnect to the plugin: stop their online-only mutes here.
        if (punishService != null && !proxyMode) {
            platform.getOnlinePlayers().forEach(player -> {
                try {
                    punishService.pauseOnlineOnlyMutes(playerDirectory.refOf(player));
                } catch (RuntimeException e) {
                    logger.warn("Could not pause the online-only mutes of {}", player.getName(), e);
                }
            });
        }
        if (dataRetentionService != null) dataRetentionService.stop();
        if (syncService != null) syncService.stop();
        if (punishCache != null) punishCache.shutdown();
        if (relationalDBManager != null) relationalDBManager.shutdown();
    }

    public void reloadConfigs() {
        final ConfigurationNode previous = config;
        this.config = loadConfig("config.yml");
        warnIfRestartNeeded(previous);
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
        this.scopeService.reload();
        this.addressPolicy.reload();
        this.chatHandler.reload();
    }

    /**
     * The sync settings and {@code proxy-mode} are read at startup only (as the database type).
     */
    private void warnIfRestartNeeded(ConfigurationNode previous) {
        for (List<String> path : List.of(List.of("sync", "enabled"), List.of("sync", "poll-interval"), List.of("proxy-mode"))) {
            if (!Objects.equals(previous.node(path).raw(), config.node(path).raw())) {
                logger.warn("{} changed: restart the server to apply it.", String.join(".", path));
            }
        }
    }

    private void registerServices(Repositories repositories) {
        this.playerDirectory = new PlayerDirectory(this, repositories.players());
        this.scopeService = new ScopeService(this);
        this.syncService = new SyncService(this, repositories.sync());
        this.punishmentEffects = new PunishmentEffects(this);
        this.punishService = new PunishService(this, repositories.punishments());
        this.addressService = new AccessAddressService(this, repositories.addresses());
        this.addressPolicy = new AddressPolicy(this);
        this.warnService = new WarnService(repositories.warns());
        this.noteService = new NoteService(repositories.notes());
        this.reportService = new ReportService(this, repositories.reports());
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
        list.add(new ReportCommand(this));
        list.add(new ReportsCommand(this));
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
