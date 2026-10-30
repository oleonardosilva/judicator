package com.elless.judicator.commons.libs;

import com.elless.judicator.commons.db.Dialect;
import com.elless.judicator.commons.db.RelationalDBManager;
import org.slf4j.Logger;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.List;

/**
 * The JDBC driver of the database in {@code data.yml}, downloaded (with its SHA-256 checked) into {@code libs/} and
 * loaded in a class loader that only sees the JDK: for platforms that cannot put it on the plugin's class path
 * (Spigot/Paper before 1.16.5, the mod loaders). {@code RelationalDBManager} uses it through
 * {@code Platform.databaseDrivers()}.
 */
public final class DriverClassLoader {

    private DriverClassLoader() {
    }

    /**
     * @throws IllegalStateException when the driver could not be obtained with the expected hash
     */
    public static URLClassLoader load(Path dataDirectory, Logger logger) {
        final Dialect dialect = RelationalDBManager.readConfiguredDialect(dataDirectory);
        logger.info("Fetching the {} driver", dialect);
        final List<Path> jars = new DependencyManager(logger, dataDirectory).download(List.of(Libraries.driverOf(dialect)));
        final URL[] urls = new URL[jars.size()];
        for (int i = 0; i < urls.length; i++) {
            try {
                urls[i] = jars.get(i).toUri().toURL();
            } catch (MalformedURLException e) {
                throw new IllegalStateException("Invalid library path " + jars.get(i), e);
            }
        }
        return new URLClassLoader(urls, ClassLoader.getPlatformClassLoader());
    }
}
