package com.elless.judicator.bukkit;

import com.elless.judicator.commons.libs.DriverClassLoader;
import org.slf4j.Logger;

import java.net.URLClassLoader;
import java.nio.file.Path;
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
        logger.info("This server does not load plugin libraries");
        return Optional.of(DriverClassLoader.load(dataDirectory, logger));
    }
}
