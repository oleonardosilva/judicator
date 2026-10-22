package com.openmc.judicator.importer;

import org.spongepowered.configurate.ConfigurationNode;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

/**
 * Read-only JDBC connection to another plugin's database, configured in {@code import.yml}
 * ({@code jdbc-url}, {@code user}, {@code password} and, for databases whose driver Judicator does not ship such as
 * H2 or HSQLDB, {@code driver-jar} + {@code driver-class}).
 */
final class SourceConnection implements AutoCloseable {

    private final Connection connection;
    private final URLClassLoader driverLoader;
    private List<String> tables;

    private SourceConnection(Connection connection, URLClassLoader driverLoader) {
        this.connection = connection;
        this.driverLoader = driverLoader;
    }

    static SourceConnection open(ConfigurationNode node, Path dataDirectory) throws ImportException {
        final String url = node.node("jdbc-url").getString("").trim();
        if (url.isEmpty()) throw new ImportException("Set jdbc-url for this source in import.yml.");

        final String jar = node.node("driver-jar").getString("").trim();
        String driverClass = node.node("driver-class").getString("").trim();
        URLClassLoader loader = null;
        try {
            final Driver driver;
            if (!jar.isEmpty()) {
                // Only a file name: the jar must be put in plugins/<Judicator>/drivers by the server owner.
                if (jar.contains("/") || jar.contains("\\") || jar.contains("..") || !jar.endsWith(".jar")) {
                    throw new ImportException("driver-jar must be the name of a .jar file inside the drivers folder.");
                }
                final Path driversDirectory = dataDirectory.resolve("drivers").toAbsolutePath().normalize();
                final Path jarPath = driversDirectory.resolve(jar).normalize();
                // Rejects anything that resolves outside the folder (e.g. "C:x.jar" on Windows).
                if (!driversDirectory.equals(jarPath.getParent())) {
                    throw new ImportException("driver-jar must be the name of a .jar file inside the drivers folder.");
                }
                if (!Files.isRegularFile(jarPath)) throw new ImportException("Driver jar not found: " + jarPath);
                if (driverClass.isEmpty()) throw new ImportException("Set driver-class together with driver-jar.");
                loader = new URLClassLoader(new URL[]{jarPath.toUri().toURL()}, SourceConnection.class.getClassLoader());
                driver = (Driver) Class.forName(driverClass, true, loader).getDeclaredConstructor().newInstance();
            } else {
                if (driverClass.isEmpty()) driverClass = guessDriver(url);
                driver = (Driver) Class.forName(driverClass).getDeclaredConstructor().newInstance();
            }

            final Properties properties = new Properties();
            final String user = node.node("user").getString("");
            final String password = node.node("password").getString("");
            if (!user.isEmpty()) properties.setProperty("user", user);
            if (!password.isEmpty()) properties.setProperty("password", password);

            final Connection connection = driver.connect(url, properties);
            if (connection == null) throw new ImportException("The driver " + driverClass + " does not accept this jdbc-url.");
            try {
                connection.setReadOnly(true);
            } catch (SQLException ignored) {
                // Not every driver supports the hint; Judicator only runs SELECTs on the source anyway.
            }
            return new SourceConnection(connection, loader);
        } catch (ImportException e) {
            closeQuietly(loader);
            throw e;
        } catch (ClassNotFoundException e) {
            closeQuietly(loader);
            throw new ImportException("JDBC driver not found: " + e.getMessage()
                    + ". For H2 or HSQLDB put the driver jar in the drivers folder and set driver-jar and driver-class.", e);
        } catch (SQLException e) {
            closeQuietly(loader);
            throw new ImportException("Could not connect to the source database: " + e.getMessage(), e);
        } catch (ReflectiveOperationException | IOException | ClassCastException e) {
            closeQuietly(loader);
            throw new ImportException("Could not load the JDBC driver: " + e.getMessage(), e);
        }
    }

    private static String guessDriver(String url) throws ImportException {
        if (url.startsWith("jdbc:mysql:")) return "com.mysql.cj.jdbc.Driver";
        if (url.startsWith("jdbc:mariadb:")) throw new ImportException("For MariaDB use a jdbc:mysql:// URL (the MySQL driver connects to MariaDB).");
        if (url.startsWith("jdbc:postgresql:")) return "org.postgresql.Driver";
        if (url.startsWith("jdbc:sqlite:")) return "org.sqlite.JDBC";
        throw new ImportException("Unknown jdbc-url type; set driver-class (and driver-jar if Judicator does not ship that driver).");
    }

    Connection connection() {
        return connection;
    }

    /**
     * Finds a table (or view) by name ignoring case, because each plugin and database stores names differently
     * (quoted lower case in LibertyBans, upper case for unquoted names in H2/HSQLDB, as typed in MySQL), and quotes
     * the stored name with the database's own quote character.
     *
     * @return the quoted name, or empty if the database has no such table
     */
    Optional<String> findTable(String name) throws SQLException {
        final DatabaseMetaData meta = connection.getMetaData();
        if (tables == null) {
            tables = new ArrayList<>();
            try (ResultSet rs = meta.getTables(connection.getCatalog(), null, "%", new String[]{"TABLE", "VIEW"})) {
                while (rs.next()) tables.add(rs.getString("TABLE_NAME"));
            }
        }
        String stored = null;
        for (String table : tables) {
            if (table.equals(name)) {
                stored = table;
                break;
            }
            if (stored == null && table.equalsIgnoreCase(name)) stored = table;
        }
        if (stored == null) return Optional.empty();
        final String quote = meta.getIdentifierQuoteString();
        return Optional.of(quote == null || quote.isBlank() ? stored : quote + stored + quote);
    }

    /**
     * Like {@link #findTable} for a table the import cannot do without.
     */
    String table(String name) throws SQLException, ImportException {
        return findTable(name).orElseThrow(() -> new ImportException("Table " + name
                + " not found in the source database (check jdbc-url and table-prefix in import.yml)."));
    }

    /**
     * UUID column stored as BINARY(16) (MySQL/H2), UUID (PostgreSQL/HSQLDB) or text.
     */
    static UUID uuid(ResultSet resultSet, String column) throws SQLException {
        final Object value = resultSet.getObject(column);
        if (value == null) return null;
        if (value instanceof UUID uuid) return uuid;
        if (value instanceof byte[] bytes) return bytes.length == 16 ? fromBytes(bytes) : null;
        final String text = value.toString();
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            final byte[] bytes = resultSet.getBytes(column);
            return bytes != null && bytes.length == 16 ? fromBytes(bytes) : null;
        }
    }

    private static UUID fromBytes(byte[] bytes) {
        final ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }

    /**
     * IP address column stored as 4 or 16 raw bytes (VARBINARY/BYTEA).
     */
    static String address(ResultSet resultSet, String column) throws SQLException {
        final byte[] bytes = resultSet.getBytes(column);
        if (bytes == null || (bytes.length != 4 && bytes.length != 16)) return null;
        try {
            return InetAddress.getByAddress(bytes).getHostAddress();
        } catch (UnknownHostException e) {
            return null;
        }
    }

    static boolean hasColumn(ResultSet resultSet, String column) throws SQLException {
        final ResultSetMetaData meta = resultSet.getMetaData();
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            if (meta.getColumnLabel(i).equalsIgnoreCase(column)) return true;
        }
        return false;
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException ignored) {
        }
        closeQuietly(driverLoader);
    }

    private static void closeQuietly(URLClassLoader loader) {
        if (loader == null) return;
        // Drivers such as H2 register themselves in DriverManager when loaded; unregister them so the class loader
        // (and the jar) is not kept alive after the import.
        final Enumeration<Driver> drivers = DriverManager.getDrivers();
        while (drivers.hasMoreElements()) {
            final Driver driver = drivers.nextElement();
            if (driver.getClass().getClassLoader() == loader) {
                try {
                    DriverManager.deregisterDriver(driver);
                } catch (SQLException ignored) {
                }
            }
        }
        try {
            loader.close();
        } catch (IOException ignored) {
        }
    }
}
