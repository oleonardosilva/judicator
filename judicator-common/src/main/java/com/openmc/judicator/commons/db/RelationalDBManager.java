package com.openmc.judicator.commons.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RelationalDBManager {

    public static final String DEFAULT_TABLE_PREFIX = "judicator_";
    /**
     * The prefix goes into SQL as it is (unquoted): a letter, then letters, digits and underscores, or nothing.
     */
    private static final Pattern VALID_PREFIX = Pattern.compile("([a-z][a-z0-9_]{0,15})?");
    private static final Pattern NAME = Pattern.compile("\\{([a-z_]+)}");

    /** where the JDBC driver comes from when it is not on the plugin class path; null otherwise */
    private final ClassLoader driverLoader;
    private HikariDataSource dataSource;
    private Dialect dialect;
    /** {@code database.table-prefix}, read at startup only; null before the first {@link #initialize} */
    private String tablePrefix;
    /** statements with their table names in place: the prefix never changes while running */
    private final Map<String, String> statements = new ConcurrentHashMap<>();

    public RelationalDBManager() {
        this(null);
    }

    /**
     * @param driverLoader a class loader with the JDBC driver of the configured database, used through a data source of
     *                     its own; null when the driver is on the plugin class path (what every platform but Spigot/Paper
     *                     before 1.16.5 has)
     */
    public RelationalDBManager(ClassLoader driverLoader) {
        this.driverLoader = driverLoader;
    }

    public void initialize(ConfigurationNode config, Path dataDirectory, Logger logger) {
        try {
            config = config.node("database");
            readTablePrefix(config, logger);
            final Dialect configured = Dialect.fromConfig(config.node("type").getString("postgres"));

            if (dialect != null && dialect != configured && dataSource != null && !dataSource.isClosed()) {
                // Tables are created on startup only, so switching backends on /judicator reload would leave the
                // new database without them. Keep the running pool.
                logger.warn("database.type changed from {} to {}: restart the server to switch databases.", dialect, configured);
                return;
            }

            if (dataSource != null && !dataSource.isClosed()) {
                dataSource.close();
            }

            final String driverClass = configured.getDriverClass();
            final HikariConfig hikari = new HikariConfig();
            hikari.setPoolName("Judicator");
            // passed to the driver on each connection, e.g. useSSL for MySQL or sslmode for PostgreSQL
            final Properties properties = new Properties();
            final String url;
            String user = null;
            String password = null;

            if (configured == Dialect.SQLITE) {
                final Path file = dataDirectory.resolve(config.node("file").getString("judicator.db")).normalize();
                if (file.getParent() != null) Files.createDirectories(file.getParent());
                url = "jdbc:sqlite:" + file.toAbsolutePath();
                // One file, one writer at a time: WAL lets reads run during a write and busy_timeout makes a second
                // writer wait instead of failing with SQLITE_BUSY.
                properties.setProperty("journal_mode", "WAL");
                properties.setProperty("busy_timeout", "10000");
                hikari.setMaximumPoolSize(4);
                hikari.setMinimumIdle(1);
                logger.info("Using SQLite database file {}", file.toAbsolutePath());
            } else {
                final String host = config.node("host").getString("localhost");
                final String port = config.node("port").getString("").trim();
                final String database = config.node("database").getString("postgres");
                url = (configured == Dialect.POSTGRESQL ? "jdbc:postgresql://" : "jdbc:mysql://")
                        + host + ":" + (port.isEmpty() ? configured.getDefaultPort() : port) + "/" + database;
                user = config.node("user").getString("postgres");
                password = config.node("password").getString("password");
                final ConfigurationNode pool = config.node("pool");
                hikari.setMaximumPoolSize(Math.max(1, pool.node("maximum-size").getInt(10)));
                hikari.setMinimumIdle(Math.max(0, Math.min(hikari.getMaximumPoolSize(), pool.node("minimum-idle").getInt(2))));
                hikari.setConnectionTimeout(TimeUnit.SECONDS.toMillis(Math.max(1, pool.node("connection-timeout").getLong(30))));
                config.node("properties").childrenMap().forEach((key, value) -> {
                    if (value.getString() != null) properties.setProperty(String.valueOf(key), value.getString());
                });
            }

            if (driverLoader == null) {
                try {
                    Class.forName(driverClass);
                } catch (ClassNotFoundException e) {
                    logger.error("Database driver not found: {}. Make sure dependencies are loaded first.", driverClass);
                    throw new RuntimeException("Database driver not available", e);
                }
                hikari.setDriverClassName(driverClass);
                hikari.setJdbcUrl(url);
                if (user != null) hikari.setUsername(user);
                if (password != null) hikari.setPassword(password);
                properties.forEach((key, value) -> hikari.addDataSourceProperty(String.valueOf(key), value));
            } else {
                final Driver driver;
                try {
                    driver = (Driver) Class.forName(driverClass, true, driverLoader).getDeclaredConstructor().newInstance();
                } catch (ReflectiveOperationException e) {
                    logger.error("Database driver not found: {}. Make sure dependencies are loaded first.", driverClass);
                    throw new RuntimeException("Database driver not available", e);
                }
                if (user != null) properties.setProperty("user", user);
                if (password != null) properties.setProperty("password", password);
                hikari.setDataSource(new DriverDataSource(driver, url, properties));
            }
            logger.info("Database driver loaded successfully: {}", driverClass);

            this.dataSource = new HikariDataSource(hikari);
            this.dialect = configured;
        } catch (Exception e) {
            logger.error("Database initialization failed, verify your data.yml file.");
            logger.error(e.getMessage(), e);
        }
    }

    /**
     * An invalid prefix on startup stops the database from being used at all: falling back to another prefix would
     * rename the tables of 1.x to names the server owner did not choose.
     */
    private void readTablePrefix(ConfigurationNode config, Logger logger) {
        final String raw = config.node("table-prefix").getString(DEFAULT_TABLE_PREFIX);
        final Optional<String> prefix = normalizePrefix(raw);
        if (tablePrefix == null) {
            tablePrefix = prefix.orElseThrow(() -> new IllegalArgumentException("database.table-prefix '" + raw
                    + "' is not valid: use a letter followed by up to 15 letters, digits and _ (or '' for no prefix)."));
        } else if (!prefix.map(tablePrefix::equals).orElse(false)) {
            logger.warn("database.table-prefix changed to '{}': restart the server to apply it (still using '{}').", raw, tablePrefix);
        }
    }

    /**
     * @return the prefix in lower case, or empty when it cannot go into SQL unquoted
     */
    public static Optional<String> normalizePrefix(String prefix) {
        if (prefix == null) return Optional.empty();
        final String normalized = prefix.trim().toLowerCase(Locale.ROOT);
        return VALID_PREFIX.matcher(normalized).matches() ? Optional.of(normalized) : Optional.empty();
    }

    public String getTablePrefix() {
        return tablePrefix == null ? DEFAULT_TABLE_PREFIX : tablePrefix;
    }

    /**
     * @param name a table name without the prefix, e.g. {@code punishments}
     */
    public String table(String name) {
        return getTablePrefix() + name;
    }

    /**
     * The name of the index of a table on a column: {@code idx_<prefix><table>_<column>} (index names are unique per
     * schema in PostgreSQL, so they carry the prefix too; without a prefix they are the names of version 1.x).
     */
    public String index(String table, String column) {
        return "idx_" + table(table) + "_" + column;
    }

    /**
     * Replaces each {@code {table}} of a statement with the prefixed table name:
     * {@code SELECT * FROM {punishments}} becomes {@code SELECT * FROM judicator_punishments}.
     */
    public String sql(String template) {
        return statements.computeIfAbsent(template, this::resolve);
    }

    private String resolve(String template) {
        final Matcher matcher = NAME.matcher(template);
        final StringBuilder sql = new StringBuilder();
        while (matcher.find()) matcher.appendReplacement(sql, Matcher.quoteReplacement(table(matcher.group(1))));
        matcher.appendTail(sql);
        return sql.toString();
    }

    /**
     * {@link SchemaUtil#ensureColumnExists} on a table named without the prefix.
     */
    public void ensureColumn(Connection connection, String table, String column, String definition) throws SQLException {
        SchemaUtil.ensureColumnExists(connection, table(table), column, definition);
    }

    /**
     * Creates the index of {@link #index} when the table lacks it.
     */
    public void ensureIndex(Connection connection, String table, String column) throws SQLException {
        final String index = index(table, column);
        SchemaUtil.ensureIndexExists(connection, table(table), index,
                "CREATE INDEX " + index + " ON " + table(table) + "(" + column + ")");
    }

    /**
     * Reads {@code database.type} from {@code data.yml} before the plugin is enabled, so a platform can fetch only
     * the driver it needs. A missing file is about to be created from the bundled one, which uses SQLite; an
     * unreadable file or one without {@code type} means PostgreSQL, as in {@link Dialect#fromConfig}.
     */
    public static Dialect readConfiguredDialect(Path dataDirectory) {
        final Path file = dataDirectory.resolve("data.yml");
        if (!Files.exists(file)) return Dialect.SQLITE;
        try {
            return Dialect.fromConfig(YamlConfigurationLoader.builder().path(file).build().load()
                    .node("database", "type").getString("postgres"));
        } catch (Exception e) {
            return Dialect.POSTGRESQL;
        }
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    /**
     * @return the backend in use; PostgreSQL until {@link #initialize} succeeds
     */
    public Dialect getDialect() {
        return dialect == null ? Dialect.POSTGRESQL : dialect;
    }

    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
