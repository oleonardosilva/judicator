package com.openmc.judicator.bukkit;

import com.openmc.judicator.commons.db.Dialect;
import com.openmc.judicator.commons.db.RelationalDBManager;
import com.openmc.judicator.commons.libs.DependencyManager;
import com.openmc.judicator.commons.libs.Libraries;
import org.slf4j.Logger;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Spigot/Paper before 1.16.5 ignores {@code libraries:} in plugin.yml: the driver of the database in {@code data.yml}
 * is downloaded (with its SHA-256 checked) into {@code libs/} and loaded in a class loader that only sees the JDK, so
 * the old SQLite and MySQL drivers bundled with those servers are never picked instead.
 */
final class LegacyDrivers {

    private LegacyDrivers() {
    }

    /**
     * @return the class loader with the driver; empty when the server loads {@code libraries:} itself
     * @throws IllegalStateException when the driver could not be obtained with the expected hash
     */
    static Optional<URLClassLoader> load(Path dataDirectory, Logger logger) {
        if (BukkitCompat.hasLibraryLoader()) return Optional.empty();
        final Dialect dialect = RelationalDBManager.readConfiguredDialect(dataDirectory);
        logger.info("This server does not load plugin libraries: fetching the {} driver", dialect);
        final List<Path> jars = new DependencyManager(logger, dataDirectory).download(List.of(Libraries.driverOf(dialect)));
        final URL[] urls = new URL[jars.size()];
        for (int i = 0; i < urls.length; i++) {
            try {
                urls[i] = jars.get(i).toUri().toURL();
            } catch (MalformedURLException e) {
                throw new IllegalStateException("Invalid library path " + jars.get(i), e);
            }
        }
        return Optional.of(new URLClassLoader(urls, ClassLoader.getPlatformClassLoader()));
    }
}
