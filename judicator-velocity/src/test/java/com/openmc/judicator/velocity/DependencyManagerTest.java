package com.openmc.judicator.velocity;

import com.velocitypowered.api.plugin.PluginManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.helpers.NOPLogger;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The hash check that keeps a tampered repository or network from loading code into the proxy. The "repository" is
 * a local folder with the Maven layout.
 */
class DependencyManagerTest {

    private static final byte[] GOOD_JAR = "the real library".getBytes(StandardCharsets.UTF_8);
    private static final byte[] TAMPERED_JAR = "a tampered library".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path repository;
    @TempDir
    Path dataDirectory;

    private final List<Path> classpath = new ArrayList<>();
    private DependencyManager manager;

    @BeforeEach
    void setUp() {
        final PluginManager pluginManager = (PluginManager) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{PluginManager.class}, (proxy, method, args) -> {
                    if (method.getName().equals("addToClasspath")) classpath.add((Path) args[1]);
                    return null;
                });
        manager = new DependencyManager(new Object(), pluginManager, NOPLogger.NOP_LOGGER, dataDirectory,
                repository.toUri().toString());
    }

    @Test
    void downloadsAndLoadsAJarWithTheExpectedHash() throws IOException {
        publish("org.example", "lib", "1.0", GOOD_JAR);

        manager.downloadAndLoadDependencies(List.of(dependency("org.example:lib:1.0", GOOD_JAR)));

        final Path jar = dataDirectory.resolve("libs/lib-1.0.jar");
        assertEquals(List.of(jar), classpath);
        assertArrayEquals(GOOD_JAR, Files.readAllBytes(jar));
    }

    @Test
    void refusesAJarWhoseHashDoesNotMatchAndLeavesNothingBehind() throws IOException {
        publish("org.example", "lib", "1.0", TAMPERED_JAR);

        final IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> manager.downloadAndLoadDependencies(List.of(dependency("org.example:lib:1.0", GOOD_JAR))));

        assertTrue(error.getMessage().contains("org.example:lib:1.0"), error.getMessage());
        assertTrue(classpath.isEmpty());
        try (var files = Files.list(dataDirectory.resolve("libs"))) {
            assertEquals(0, files.count());
        }
    }

    @Test
    void oneBadDependencyStopsEveryOneFromLoading() throws IOException {
        publish("org.example", "good", "1.0", GOOD_JAR);
        publish("org.example", "bad", "1.0", TAMPERED_JAR);

        assertThrows(IllegalStateException.class, () -> manager.downloadAndLoadDependencies(List.of(
                dependency("org.example:good:1.0", GOOD_JAR),
                dependency("org.example:bad:1.0", GOOD_JAR))));

        assertTrue(classpath.isEmpty());
    }

    @Test
    void replacesAModifiedJarAlreadyInTheLibsFolder() throws IOException {
        publish("org.example", "lib", "1.0", GOOD_JAR);
        final Path jar = dataDirectory.resolve("libs/lib-1.0.jar");
        Files.createDirectories(jar.getParent());
        Files.write(jar, TAMPERED_JAR);

        manager.downloadAndLoadDependencies(List.of(dependency("org.example:lib:1.0", GOOD_JAR)));

        assertArrayEquals(GOOD_JAR, Files.readAllBytes(jar));
        assertEquals(List.of(jar), classpath);
    }

    @Test
    void reusesAVerifiedJarWithoutDownloading() throws IOException {
        final Path jar = dataDirectory.resolve("libs/lib-1.0.jar");
        Files.createDirectories(jar.getParent());
        Files.write(jar, GOOD_JAR);

        // Nothing is published: a download attempt would fail.
        manager.downloadAndLoadDependencies(List.of(dependency("org.example:lib:1.0", GOOD_JAR)));

        assertEquals(List.of(jar), classpath);
    }

    @Test
    void ignoresOtherJarsInTheLibsFolder() throws IOException {
        publish("org.example", "lib", "1.0", GOOD_JAR);
        final Path stray = dataDirectory.resolve("libs/evil.jar");
        Files.createDirectories(stray.getParent());
        Files.write(stray, TAMPERED_JAR);

        manager.downloadAndLoadDependencies(List.of(dependency("org.example:lib:1.0", GOOD_JAR)));

        assertFalse(classpath.contains(stray));
        assertEquals(1, classpath.size());
    }

    @Test
    void aDependencyWithoutAValidHashCannotBeDeclared() {
        assertThrows(IllegalArgumentException.class, () -> new DependencyManager.Dependency("org.example:lib:1.0", null));
        assertThrows(IllegalArgumentException.class, () -> new DependencyManager.Dependency("org.example:lib:1.0", "abc"));
        assertThrows(IllegalArgumentException.class, () -> new DependencyManager.Dependency("org.example:lib", "0".repeat(64)));
    }

    @Test
    void theProductionRepositoryIsMavenCentralOverHttps() {
        assertEquals("https://repo1.maven.org/maven2/", DependencyManager.MAVEN_CENTRAL);
    }

    private void publish(String group, String artifact, String version, byte[] content) throws IOException {
        final Path jar = repository.resolve(group.replace('.', '/')).resolve(artifact).resolve(version)
                .resolve(artifact + "-" + version + ".jar");
        Files.createDirectories(jar.getParent());
        Files.write(jar, content);
    }

    private static DependencyManager.Dependency dependency(String coordinate, byte[] content) throws IOException {
        final Path temp = Files.createTempFile("dep", ".jar");
        try {
            Files.write(temp, content);
            return new DependencyManager.Dependency(coordinate, DependencyManager.sha256(temp));
        } finally {
            Files.delete(temp);
        }
    }
}
