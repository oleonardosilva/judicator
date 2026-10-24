package com.openmc.judicator.velocity;

import com.velocitypowered.api.plugin.PluginManager;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Downloads the libraries Velocity does not provide (JDBC drivers, HikariCP, Caffeine) into {@code libs/} and adds
 * them to the plugin class path. Every library has a SHA-256 fixed in the code: a jar that does not match is never
 * loaded, so a compromised repository or network cannot run code in the proxy.
 */
public class DependencyManager {

    static final String MAVEN_CENTRAL = "https://repo1.maven.org/maven2/";
    private static final int CONNECT_TIMEOUT_MILLIS = 10_000;
    private static final int READ_TIMEOUT_MILLIS = 60_000;

    private final Object plugin;
    private final PluginManager pluginManager;
    private final Logger logger;
    private final Path libsDirectory;
    private final String repository;

    public DependencyManager(Object plugin, PluginManager pluginManager, Logger logger, Path dataDirectory) {
        this(plugin, pluginManager, logger, dataDirectory, MAVEN_CENTRAL);
    }

    DependencyManager(Object plugin, PluginManager pluginManager, Logger logger, Path dataDirectory, String repository) {
        this.plugin = plugin;
        this.pluginManager = pluginManager;
        this.logger = logger;
        this.libsDirectory = dataDirectory.resolve("libs");
        this.repository = repository.endsWith("/") ? repository : repository + "/";
    }

    /**
     * Makes sure every dependency is in {@code libs/} with the expected hash (downloading it otherwise) and loads
     * exactly those jars; other files in the folder are ignored.
     *
     * @throws IllegalStateException if a dependency could not be obtained with the expected hash
     */
    public void downloadAndLoadDependencies(List<Dependency> dependencies) {
        try {
            Files.createDirectories(libsDirectory);
        } catch (IOException e) {
            throw new IllegalStateException("Could not create " + libsDirectory, e);
        }

        final List<String> failed = new ArrayList<>();
        final List<Path> verified = new ArrayList<>();
        for (Dependency dependency : dependencies) {
            try {
                verified.add(ensure(dependency));
            } catch (IOException | IllegalStateException e) {
                logger.error("Dependency {} was not loaded: {}", dependency.getCoordinate(), e.getMessage());
                failed.add(dependency.getCoordinate());
            }
        }
        if (!failed.isEmpty()) {
            throw new IllegalStateException("Dependencies not loaded: " + String.join(", ", failed));
        }

        for (Path jar : verified) {
            pluginManager.addToClasspath(plugin, jar);
        }
        logger.info("Loaded {} verified dependencies", verified.size());
    }

    /**
     * @return the jar in {@code libs/}, already checked against the dependency's SHA-256
     */
    Path ensure(Dependency dependency) throws IOException {
        final Path jar = libsDirectory.resolve(dependency.getFileName());
        if (Files.isRegularFile(jar)) {
            if (dependency.matches(sha256(jar))) return jar;
            logger.warn("{} does not match the expected SHA-256; downloading it again", jar.getFileName());
        }

        final Path partial = libsDirectory.resolve(dependency.getFileName() + ".part");
        final String url = repository + dependency.getPath();
        logger.info("Downloading {}", url);
        try {
            download(url, partial);
            final String actual = sha256(partial);
            if (!dependency.matches(actual)) {
                throw new IllegalStateException("SHA-256 mismatch for " + url + " (expected " + dependency.getSha256()
                        + ", got " + actual + ")");
            }
            Files.move(partial, jar, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return jar;
        } finally {
            Files.deleteIfExists(partial);
        }
    }

    private static void download(String url, Path destination) throws IOException {
        final URLConnection connection = URI.create(url).toURL().openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        connection.setReadTimeout(READ_TIMEOUT_MILLIS);
        try (InputStream in = connection.getInputStream()) {
            Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static String sha256(Path file) throws IOException {
        final MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
        try (InputStream in = Files.newInputStream(file)) {
            final byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    public static class Dependency {

        private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

        private final String groupId;
        private final String artifactId;
        private final String version;
        private final String sha256;

        /**
         * @param coordinate {@code group:artifact:version}
         * @param sha256     SHA-256 of the jar on Maven Central, in hex
         */
        public Dependency(String coordinate, String sha256) {
            final String[] parts = coordinate.split(":");
            if (parts.length != 3) throw new IllegalArgumentException("Invalid coordinate: " + coordinate);
            final String hash = sha256 == null ? "" : sha256.toLowerCase(Locale.ROOT);
            if (!SHA256.matcher(hash).matches()) {
                throw new IllegalArgumentException("Invalid SHA-256 for " + coordinate);
            }
            this.groupId = parts[0];
            this.artifactId = parts[1];
            this.version = parts[2];
            this.sha256 = hash;
        }

        boolean matches(String actualSha256) {
            return MessageDigest.isEqual(sha256.getBytes(StandardCharsets.US_ASCII),
                    actualSha256.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII));
        }

        public String getCoordinate() {
            return String.format("%s:%s:%s", groupId, artifactId, version);
        }

        public String getFileName() {
            return String.format("%s-%s.jar", artifactId, version);
        }

        public String getPath() {
            return String.format("%s/%s/%s/%s",
                    groupId.replace('.', '/'),
                    artifactId,
                    version,
                    getFileName());
        }

        public String getSha256() {
            return sha256;
        }
    }
}
