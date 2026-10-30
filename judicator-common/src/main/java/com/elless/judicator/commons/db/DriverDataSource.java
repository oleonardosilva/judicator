package com.elless.judicator.commons.db;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Connections from one driver instance, for a driver loaded in a class loader of its own: going through
 * {@code DriverManager} (as HikariCP does with a driver class name) could pick another copy of the same driver
 * class, such as the old SQLite and MySQL drivers bundled with Spigot 1.8.
 */
final class DriverDataSource implements DataSource {

    private final Driver driver;
    private final String url;
    private final Properties properties;
    private PrintWriter logWriter;
    private int loginTimeout;

    /**
     * @param properties passed to the driver on every connection: user, password and driver settings
     */
    DriverDataSource(Driver driver, String url, Properties properties) {
        this.driver = driver;
        this.url = url;
        this.properties = properties;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return connect(properties);
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        final Properties withCredentials = new Properties();
        withCredentials.putAll(properties);
        if (username != null) withCredentials.setProperty("user", username);
        if (password != null) withCredentials.setProperty("password", password);
        return connect(withCredentials);
    }

    private Connection connect(Properties info) throws SQLException {
        final Connection connection = driver.connect(url, info);
        if (connection == null) throw new SQLException("The driver " + driver.getClass().getName() + " does not accept " + url);
        return connection;
    }

    @Override
    public PrintWriter getLogWriter() {
        return logWriter;
    }

    @Override
    public void setLogWriter(PrintWriter out) {
        this.logWriter = out;
    }

    @Override
    public void setLoginTimeout(int seconds) {
        this.loginTimeout = seconds;
    }

    @Override
    public int getLoginTimeout() {
        return loginTimeout;
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public <T> T unwrap(Class<T> type) throws SQLException {
        if (type.isInstance(this)) return type.cast(this);
        throw new SQLException("Not a wrapper of " + type.getName());
    }

    @Override
    public boolean isWrapperFor(Class<?> type) {
        return type.isInstance(this);
    }
}
