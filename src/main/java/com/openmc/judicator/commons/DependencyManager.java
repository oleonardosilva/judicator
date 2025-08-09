package com.openmc.judicator.commons;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.openmc.judicator.Judicator;
import com.velocitypowered.api.plugin.PluginManager;
import org.slf4j.Logger;

import java.io.*;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public class DependencyManager {

    private final Judicator judicator;
    private final PluginManager pluginManager;
    private final Logger logger;
    private final Path libsDirectory;
    private final Path dataDirectory;
    private final Set<String> loadedDependencies = ConcurrentHashMap.newKeySet();
    private final List<Repository> repositories = new ArrayList<>();
    private final Gson gson = new Gson();

    public DependencyManager(Judicator judicator) {
        this.judicator = judicator;
        this.pluginManager = judicator.getServer().getPluginManager();
        this.logger = judicator.getLogger();
        this.dataDirectory = judicator.getDataDirectory();
        this.libsDirectory = judicator.getDataDirectory().resolve("libs");

        try {
            Files.createDirectories(libsDirectory);
        } catch (IOException e) {
            logger.error("Error creating libs directory", e);
        }

        addRepository(new Repository("central", "https://repo1.maven.org/maven2/"));
        addRepository(new Repository("jitpack", "https://jitpack.io/"));
        addRepository(new Repository("sonatype", "https://oss.sonatype.org/content/repositories/snapshots/"));
    }

    public void addRepository(Repository repository) {
        repositories.add(repository);
        logger.info("Repository added: {} - {}", repository.getName(), repository.getUrl());
    }

    public void downloadAndLoadDependencies(List<Dependency> dependencies) {
        logger.info("Starting download of {} dependencies...", dependencies.size());

        for (Dependency dependency : dependencies) {
            try {
                downloadDependency(dependency);
            } catch (Exception e) {
                logger.error("Error when downloading dependence: {}", dependency.getCoordinate(), e);
            }
        }

        loadAllJars();
    }

    private void downloadDependency(Dependency dependency) throws Exception {
        final String fileName = dependency.getFileName();
        final Path jarPath = libsDirectory.resolve(fileName);

        if (Files.exists(jarPath)) {
            if (dependency.getSha1() != null && validateChecksum(jarPath, dependency.getSha1())) {
                logger.debug("Dependence {} already exists and is valid", dependency.getCoordinate());
                return;
            } else if (dependency.getSha1() == null) {
                logger.debug("Dependence {} already exists (no checksum validation)", dependency.getCoordinate());
                return;
            }
        }

        boolean downloaded = false;
        for (Repository repo : repositories) {
            try {
                final String downloadUrl = repo.getUrl() + dependency.getPath();
                logger.info("Trying to download: {}", downloadUrl);

                if (downloadFile(downloadUrl, jarPath)) {
                    if (dependency.getSha1() != null) {
                        if (validateChecksum(jarPath, dependency.getSha1())) {
                            logger.info("Dependence {} successfully downloaded from {}",
                                    dependency.getCoordinate(), repo.getName());
                            downloaded = true;
                            break;
                        } else {
                            logger.warn("Invalid checksum for {}, trying to next repository",
                                    dependency.getCoordinate());
                            Files.deleteIfExists(jarPath);
                        }
                    } else {
                        logger.info("Dependence {} successfully downloaded from {} (no checksum validation)",
                                dependency.getCoordinate(), repo.getName());
                        downloaded = true;
                        break;
                    }
                }
            } catch (Exception e) {
                logger.debug("Failure to downlaod {}: {}", repo.getName(), e.getMessage());
            }
        }

        if (!downloaded) {
            throw new RuntimeException("It was not possible to download the dependence: " + dependency.getCoordinate());
        }
    }

    private boolean downloadFile(String url, Path destination) throws Exception {
        try (final ReadableByteChannel channel = Channels.newChannel(new URL(url).openStream());
             final FileOutputStream fos = new FileOutputStream(destination.toFile())) {

            fos.getChannel().transferFrom(channel, 0, Long.MAX_VALUE);
            return true;
        }
    }

    private boolean validateChecksum(Path file, String expectedSha1) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-1");
            try (InputStream fis = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }

            final StringBuilder sb = new StringBuilder();
            for (byte b : digest.digest()) {
                sb.append(String.format("%02x", b));
            }

            return expectedSha1.equals(sb.toString());
        } catch (Exception e) {
            logger.error("Error Validation Checksum", e);
            return false;
        }
    }

    private void loadAllJars() {
        try {
            final List<Path> jarUrls = new ArrayList<>();

            final Stream<Path> walk = Files.walk(libsDirectory);
            walk.filter(path -> path.toString().endsWith(".jar"))
                    .forEach(jarPath -> {
                        try {
                            String jarName = jarPath.getFileName().toString();
                            if (!loadedDependencies.contains(jarName)) {
                                jarUrls.add(jarPath);
                                loadedDependencies.add(jarName);
                                logger.info("Preparing to load: {}", jarName);
                            }
                        } catch (Exception e) {
                            logger.error("Error preparing JAR: {}", jarPath, e);
                        }
                    });
            walk.close();
            if (!jarUrls.isEmpty()) {
                for (Path jarUrl : jarUrls) {
                    pluginManager.addToClasspath(judicator, jarUrl);
                }
                logger.info("Successfully loaded {} dependencies", jarUrls.size());
            }
        } catch (Exception e) {
            logger.error("Error loading dependencies", e);
            throw new RuntimeException("Dependency loading failed", e);
        }
    }

    public void saveDependencyCache(List<Dependency> dependencies) {
        try {
            final Path cacheFile = dataDirectory.resolve("dependency-cache.json");
            try (FileWriter writer = new FileWriter(cacheFile.toFile())) {
                gson.toJson(dependencies, writer);
            }
        } catch (IOException e) {
            logger.error("Error saving dependencies cache", e);
        }
    }

    public List<Dependency> loadDependencyCache() {
        try {
            final Path cacheFile = dataDirectory.resolve("dependency-cache.json");
            if (Files.exists(cacheFile)) {
                try (FileReader reader = new FileReader(cacheFile.toFile())) {
                    return gson.fromJson(reader, new TypeToken<List<Dependency>>() {
                    }.getType());
                }
            }
        } catch (IOException e) {
            logger.error("Error loading dependencies cache", e);
        }
        return new ArrayList<>();
    }

    public static class Dependency {
        private final String groupId;
        private final String artifactId;
        private final String version;
        private final String sha1;

        public Dependency(String groupId, String artifactId, String version) {
            this(groupId, artifactId, version, null);
        }

        public Dependency(String groupId) {
            this(groupId.split(":")[0], groupId.split(":")[1], groupId.split(":")[2], null);
        }

        public Dependency(String groupId, String artifactId, String version, String sha1) {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.version = version;
            this.sha1 = sha1;
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

        public String getGroupId() {
            return groupId;
        }

        public String getArtifactId() {
            return artifactId;
        }

        public String getVersion() {
            return version;
        }

        public String getSha1() {
            return sha1;
        }
    }

    public static class Repository {
        private final String name;
        private final String url;

        public Repository(String name, String url) {
            this.name = name;
            this.url = url.endsWith("/") ? url : url + "/";
        }

        public String getName() {
            return name;
        }

        public String getUrl() {
            return url;
        }
    }
}