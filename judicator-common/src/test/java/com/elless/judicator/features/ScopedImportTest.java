package com.elless.judicator.features;

import com.elless.judicator.punish.Punishment;
import com.elless.judicator.testing.TestServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Importing punishments for some servers from LibertyBans and LiteBans: they keep their scope (a SQLite file with the
 * columns each plugin uses stands for the source database).
 */
class ScopedImportTest {

    @TempDir
    Path folder;
    @TempDir
    Path source;

    private TestServer server;

    @AfterEach
    void stop() {
        if (server != null) server.close();
    }

    private void sql(Path file, String... statements) throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
             Statement statement = connection.createStatement()) {
            for (String sql : statements) statement.executeUpdate(sql);
        }
    }

    private void importFrom(String plugin, Path file) throws Exception {
        Files.writeString(folder.resolve("import.yml"), plugin + ":\n  jdbc-url: 'jdbc:sqlite:"
                + file.toString().replace("\\", "/").replace("'", "''") + "'\n", StandardCharsets.UTF_8);
        server.command(server.console, "judicator import " + plugin + " confirm");
    }

    private Map<String, String> scopesByReason() {
        return server.punishments.all().stream().collect(Collectors.toMap(p -> p.getReason().orElseThrow(),
                (Punishment p) -> p.getScope().orElse("global")));
    }

    @Test
    void libertyBans_serversAndCategoriesKeepTheirScope() throws Exception {
        server = TestServer.in(folder).set("scopes.groups.minigames", List.of("bedwars")).start();
        final UUID steve = UUID.randomUUID();
        final Path file = source.resolve("libertybans.db");
        sql(file,
                "CREATE TABLE libertybans_names (uuid TEXT, name TEXT, updated INTEGER)",
                "INSERT INTO libertybans_names VALUES ('" + steve + "', 'Steve', 1)",
                "CREATE TABLE libertybans_bans (id INTEGER)",
                "CREATE TABLE libertybans_mutes (id INTEGER)",
                "CREATE TABLE libertybans_warns (id INTEGER)",
                "INSERT INTO libertybans_bans VALUES (1), (2), (3)",
                "CREATE TABLE libertybans_simple_history (id INTEGER, type INTEGER, victim_type INTEGER, victim_uuid TEXT,"
                        + " victim_address BLOB, operator TEXT, reason TEXT, scope TEXT, start INTEGER, end INTEGER, scope_type INTEGER)",
                row(1, 0, steve, "everywhere", "", 0, 100),
                row(2, 0, steve, "on survival", "Survival", 1, 200),
                row(3, 0, steve, "on minigames", "minigames", 2, 300),
                row(4, 1, steve, "category nobody defined", "lobbies", 2, 400),
                row(5, 2, steve, "a warn on survival", "survival", 1, 500));

        importFrom("libertybans", file);

        assertEquals(Map.of("everywhere", "global", "on survival", "server:survival", "on minigames", "group:minigames",
                "category nobody defined", "group:lobbies"), scopesByReason());
        assertTrue(server.console.received("add the group 'lobbies' to scopes.groups"), server.console.messages().toString());
        assertTrue(server.console.received("warn limited to some servers"), server.console.messages().toString());
    }

    private static String row(int id, int type, UUID victim, String reason, String scope, int scopeType, long start) {
        return "INSERT INTO libertybans_simple_history VALUES (" + id + ", " + type + ", 0, '" + victim + "', NULL, '"
                + new UUID(0, 0) + "', '" + reason + "', '" + scope + "', " + start + ", 0, " + scopeType + ")";
    }

    @Test
    void liteBans_aServerScopeIsKept_andAGroupJudicatorHasIsUsedAsTheGroup() throws Exception {
        server = TestServer.in(folder).set("scopes.groups.minigames", List.of("bedwars")).start();
        final UUID steve = UUID.randomUUID();
        final Path file = source.resolve("litebans.db");
        final String columns = "(id INTEGER, uuid TEXT, ip TEXT, reason TEXT, banned_by_uuid TEXT, banned_by_name TEXT,"
                + " time INTEGER, until INTEGER, active INTEGER, ipban INTEGER, ipban_wildcard INTEGER, server_scope TEXT)";
        sql(file,
                "CREATE TABLE litebans_history (uuid TEXT, name TEXT, date INTEGER)",
                "INSERT INTO litebans_history VALUES ('" + steve + "', 'Steve', 0)",
                "CREATE TABLE litebans_bans " + columns,
                "CREATE TABLE litebans_mutes " + columns,
                "CREATE TABLE litebans_warnings " + columns,
                "INSERT INTO litebans_bans VALUES (1, '" + steve + "', '#', 'everywhere', 'CONSOLE', 'Console', 1000, 0, 1, 0, 0, '*')",
                "INSERT INTO litebans_bans VALUES (2, '" + steve + "', '#', 'on survival', 'CONSOLE', 'Console', 2000, 0, 1, 0, 0, 'survival')",
                "INSERT INTO litebans_mutes VALUES (3, '" + steve + "', '#', 'on minigames', 'CONSOLE', 'Console', 3000, 0, 1, 0, 0, 'minigames')",
                "INSERT INTO litebans_mutes VALUES (4, '" + steve + "', '#', 'odd name', 'CONSOLE', 'Console', 4000, 0, 1, 0, 0, 'a server with spaces')");

        importFrom("litebans", file);

        assertEquals(Map.of("everywhere", "global", "on survival", "server:survival", "on minigames", "group:minigames"), scopesByReason());
        assertTrue(server.console.received("scope that is not a server or group name"), server.console.messages().toString());
    }
}
