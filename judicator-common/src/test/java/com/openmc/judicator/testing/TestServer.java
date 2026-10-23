package com.openmc.judicator.testing;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.commons.command.CommandArgs;
import com.openmc.judicator.commons.command.CoreCommand;
import com.openmc.judicator.platform.JudicatorSender;
import com.openmc.judicator.platform.PlatformType;
import com.openmc.judicator.punish.listeners.ConnectionHandler;
import net.kyori.adventure.text.Component;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The whole core running as on a server: the real {@code *.yml} files of the plugin, the real commands, handlers and
 * services, an in-memory database and a {@link FakePlatform}. Player actions go through the same calls the
 * Velocity/Bukkit listeners make, in the same order.
 */
public final class TestServer implements AutoCloseable {

    public final FakePlatform platform;
    public Judicator judicator;
    public final FakeConsole console = new FakeConsole();
    public final InMemoryPunishmentRepository punishments;
    public final InMemoryWarnRepository warns;
    public final InMemoryPlayerAddressRepository addresses;
    public final InMemoryPlayerLevelRepository levels;
    public final InMemoryNoteRepository notes;
    public final InMemorySyncRepository sync;
    public final InMemoryPlayerRepository players;
    public final InMemoryReportRepository reports;

    private TestServer(Path folder, boolean onlineMode, PlatformType type, TestServer sharedDatabase) {
        this.platform = new FakePlatform(folder, onlineMode, type);
        this.punishments = sharedDatabase == null ? new InMemoryPunishmentRepository() : sharedDatabase.punishments;
        this.warns = sharedDatabase == null ? new InMemoryWarnRepository() : sharedDatabase.warns;
        this.addresses = sharedDatabase == null ? new InMemoryPlayerAddressRepository() : sharedDatabase.addresses;
        this.levels = sharedDatabase == null ? new InMemoryPlayerLevelRepository() : sharedDatabase.levels;
        this.notes = sharedDatabase == null ? new InMemoryNoteRepository() : sharedDatabase.notes;
        this.sync = sharedDatabase == null ? new InMemorySyncRepository() : sharedDatabase.sync;
        this.players = sharedDatabase == null ? new InMemoryPlayerRepository() : sharedDatabase.players;
        this.reports = sharedDatabase == null ? new InMemoryReportRepository() : sharedDatabase.reports;
        this.judicator = boot();
    }

    private Judicator boot() {
        final Judicator core = new Judicator(platform, new Judicator.Repositories(punishments, warns, addresses, levels, notes, sync, players, reports));
        core.enable();
        // A test never talks to the Mojang API: unknown names simply have no UUID.
        core.getUuidManager().setRemoteLookups(false);
        return core;
    }

    /**
     * Server restart: every player is disconnected, caches are lost, the stored data stays.
     */
    public void restart() {
        List.copyOf(platform.getOnlinePlayers()).forEach(player -> platform.remove((FakePlayer) player));
        judicator.disable();
        judicator = boot();
    }

    /**
     * Plugin reload (Bukkit /reload): the plugin is disabled and enabled again while the players stay online.
     */
    public void reloadPlugin() {
        judicator.disable();
        judicator = boot();
    }

    public static Builder in(Path folder) {
        return new Builder(folder);
    }

    /**
     * What happened to a chat message or command typed by a player.
     */
    public enum Outcome {
        /** blocked because the player is muted */
        BLOCKED_MUTED,
        /** soft mute: only the sender sees it, nothing tells them */
        SOFT_MUTED,
        /** taken by the staff evidence prompt (confirm/cancel/evidence) */
        TAKEN_BY_PROMPT,
        /** went through */
        ALLOWED
    }

    /**
     * Player connects: pre-login check, login check (online mode), then joins. Returns the kick screen when
     * Judicator refuses the connection.
     */
    public Optional<String> join(FakePlayer player) {
        final Optional<Component> preLogin = judicator.getConnectionHandler().checkPreLogin(player.getName(), player.getHostAddress());
        if (preLogin.isPresent()) return preLogin.map(Texts::plain);
        final Optional<Component> login = judicator.getConnectionHandler().checkLogin(player.getUniqueId(), player.getName(), player.getHostAddress());
        if (login.isPresent()) return login.map(Texts::plain);
        judicator.getConnectionHandler().onLoginAllowed(player.getUniqueId(), player.getName(), player.getHostAddress());
        platform.add(player);
        judicator.getConnectionHandler().onJoin(player);
        return Optional.empty();
    }

    /**
     * Proxy: where the player goes right after logging in ({@code PlayerChooseInitialServerEvent}), as
     * {@code VelocityListener} does it.
     *
     * @return the server they are on, or empty when they were disconnected (the screen is in {@link FakePlayer})
     */
    public Optional<String> chooseInitialServer(FakePlayer player, String chosen) {
        final ConnectionHandler.InitialServer decision = judicator.getConnectionHandler().chooseInitialServer(player, chosen);
        if (decision.kick() != null) {
            player.disconnect(decision.kick());
            quit(player);
            return Optional.empty();
        }
        platform.moveTo(player, decision.server());
        return Optional.of(decision.server());
    }

    /**
     * Proxy: the player connects to another server ({@code ServerPreConnectEvent}).
     *
     * @return whether they got there
     */
    public boolean switchServer(FakePlayer player, String server) {
        final Optional<Component> denied = judicator.getConnectionHandler().checkServerSwitch(player, server);
        if (denied.isPresent()) {
            player.sendMessage(denied.get());
            return false;
        }
        platform.moveTo(player, server);
        return true;
    }

    /**
     * Joins and fails the test if the player is refused.
     */
    public FakePlayer online(FakePlayer player) {
        join(player).ifPresent(kick -> {
            throw new AssertionError(player.getName() + " could not join: " + kick);
        });
        return player;
    }

    /**
     * One sync poll: this server reads and applies what the servers sharing its database changed
     * ({@code sync.enabled}).
     */
    public void pollSync() {
        judicator.getSyncService().poll();
    }

    public void quit(FakePlayer player) {
        platform.remove(player);
        judicator.getConnectionHandler().onDisconnect(player.getUniqueId(), player.getName(), player.getHostAddress());
    }

    /**
     * Runs a Judicator command line ("ban Steve hacking") as the given sender, as the command adapters do: the
     * command is refused when {@code canUse} is false.
     *
     * @return false if the sender may not use the command (the platform hides it)
     */
    public boolean command(JudicatorSender sender, String line) {
        final String[] parts = line.trim().split(" ", 2);
        final CoreCommand command = find(parts[0]);
        if (!command.canUse(sender)) return false;
        command.execute(sender, CommandArgs.of(parts.length > 1 ? parts[1] : ""));
        return true;
    }

    public List<String> suggest(JudicatorSender sender, String line) {
        final String[] parts = line.split(" ", -1);
        final String[] args = new String[parts.length - 1];
        System.arraycopy(parts, 1, args, 0, args.length);
        return find(parts[0]).suggest(sender, args);
    }

    /**
     * Player types in chat (mute check first, then the evidence prompt, like the listeners' priorities).
     */
    public Outcome chat(FakePlayer player, String message) {
        switch (judicator.getChatHandler().chatDecision(player)) {
            case BLOCK -> {
                return Outcome.BLOCKED_MUTED;
            }
            case SOFT -> {
                return Outcome.SOFT_MUTED;
            }
            default -> {
            }
        }
        if (judicator.getChatHandler().handlePromptChat(player, message)) return Outcome.TAKEN_BY_PROMPT;
        return Outcome.ALLOWED;
    }

    /**
     * Player types a command of another plugin ("/tell Bob hi"; Velocity passes it without the slash).
     */
    public Outcome playerCommand(FakePlayer player, String commandLine) {
        if (judicator.getChatHandler().shouldBlockCommand(player, commandLine)) return Outcome.BLOCKED_MUTED;
        if (judicator.getChatHandler().handlePromptCommand(player, commandLine)) return Outcome.TAKEN_BY_PROMPT;
        return Outcome.ALLOWED;
    }

    private CoreCommand find(String label) {
        final String name = label.toLowerCase(Locale.ROOT);
        return judicator.getCommands().stream()
                .filter(c -> c.getName().equals(name) || c.getAliases().contains(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No command " + label));
    }

    @Override
    public void close() {
        judicator.disable();
    }

    public static final class Builder {

        private final Path folder;
        private boolean onlineMode = true;
        private final Map<String, Object> configValues = new LinkedHashMap<>();
        private String immunePlayers = "";
        private String immuneFile;
        private PlatformType type = PlatformType.VELOCITY;
        private TestServer sharedDatabase;

        private Builder(Path folder) {
            this.folder = folder;
        }

        public Builder offlineMode() {
            this.onlineMode = false;
            return this;
        }

        /**
         * Sets a value of the plugin's default {@code config.yml}, e.g. {@code set("ip-limits.max-online", 2)} or
         * {@code set("time-limits.senior.ban", "30:d")}; lists and maps are written as given. Fails when the setting,
         * or the section of a new one, is not in the default file, so a renamed setting breaks the test loudly.
         */
        public Builder set(String path, Object value) {
            configValues.put(path, value);
            return this;
        }

        /**
         * Removes a setting or a whole section of the default {@code config.yml}, as in a file written by an older
         * version.
         */
        public Builder remove(String path) {
            configValues.put(path, null);
            return this;
        }

        /**
         * The platform the server reports (Velocity by default); {@code proxy-mode} only applies on Bukkit.
         */
        public Builder platform(PlatformType type) {
            this.type = type;
            return this;
        }

        /**
         * Another server of the same network: both read and write the same (in-memory) database.
         */
        public Builder sharingDatabaseWith(TestServer other) {
            this.sharedDatabase = other;
            return this;
        }

        /**
         * Entries of the {@code players:} section of {@code immune.yml}, e.g. {@code "  Steve: [mute]\n"}.
         */
        public Builder immunePlayers(String yaml) {
            this.immunePlayers = yaml;
            return this;
        }

        /**
         * The whole {@code immune.yml}, e.g. one written by an older version.
         */
        public Builder immuneFile(String yaml) {
            this.immuneFile = yaml;
            return this;
        }

        public TestServer start() {
            try {
                Files.createDirectories(folder);
                Files.writeString(folder.resolve("config.yml"), resource("/config.yml"), StandardCharsets.UTF_8);
                if (!configValues.isEmpty()) applyConfigValues(folder.resolve("config.yml"));
                // Never post to Discord from a test.
                Files.writeString(folder.resolve("discord.yml"), "enabled: false\n", StandardCharsets.UTF_8);
                Files.writeString(folder.resolve("immune.yml"), immuneFile != null ? immuneFile : "players:\n" + immunePlayers,
                        StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return new TestServer(folder, onlineMode, type, sharedDatabase);
        }

        private void applyConfigValues(Path file) throws IOException {
            final YamlConfigurationLoader loader = YamlConfigurationLoader.builder().path(file).nodeStyle(NodeStyle.BLOCK).build();
            final CommentedConfigurationNode root = loader.load();
            for (Map.Entry<String, Object> change : configValues.entrySet()) {
                final String path = change.getKey();
                final int dot = path.lastIndexOf('.');
                final boolean known = !root.node((Object[]) path.split("\\.")).virtual()
                        || (dot > 0 && !root.node((Object[]) path.substring(0, dot).split("\\.")).virtual());
                if (!known) throw new AssertionError("config.yml has no '" + path + "'");
                if (change.getValue() == null) root.node((Object[]) path.split("\\.")).set(null);
                else root.node((Object[]) path.split("\\.")).raw(change.getValue());
            }
            loader.save(root);
        }

        private static String resource(String path) throws IOException {
            try (InputStream in = TestServer.class.getResourceAsStream(path)) {
                if (in == null) throw new IOException("Missing resource " + path);
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
    }
}
