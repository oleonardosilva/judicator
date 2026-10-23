package com.openmc.judicator.features;

import com.openmc.judicator.punish.types.PunishType;
import com.openmc.judicator.testing.FakePlayer;
import com.openmc.judicator.testing.TestServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * /judicator: reload, old message files, and the console-only import with dry run and de-duplication.
 */
class AdministrationTest {

    @TempDir
    Path folder;

    private TestServer server;

    @BeforeEach
    void start() {
        server = TestServer.in(folder).start();
    }

    @AfterEach
    void stop() {
        server.close();
    }

    @Test
    void reload_appliesConfigChanges() throws Exception {
        final FakePlayer bystander = server.online(new FakePlayer("Bystander", "10.0.0.2"));
        final Path config = folder.resolve("config.yml");
        Files.writeString(config, Files.readString(config).replace("announce: true", "announce: false"), StandardCharsets.UTF_8);

        server.command(server.console, "judicator reload");
        assertTrue(server.console.received("The plugin has been successfully reloaded."));

        server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.command(server.console, "ban Steve hacking");
        assertFalse(bystander.received("was banned"));
    }

    @Test
    void messageFileFromAnOlderVersion_fallsBackToTheBundledTexts() throws Exception {
        // A 1.0.0 messages file has no "blame" or "usages.blame" keys.
        final Path messages = folder.resolve("messages_en.yml");
        final String current = Files.readString(messages).replace("\r\n", "\n");
        Files.writeString(messages, current.substring(0, current.indexOf("\nblame:")).replace("  blame: \"&cIncorrect command. Use: /blame (staff) [period, e.g. 7:d] [page].\"\n", ""), StandardCharsets.UTF_8);

        server.command(server.console, "judicator reload");
        server.command(server.console, "blame");
        server.command(server.console, "blame Nobody");

        assertTrue(server.console.received("Use: /blame (staff) [period, e.g. 7:d] [page]."), server.console.messages().toString());
        assertTrue(server.console.received("Nobody has not applied any punishment."));
        assertFalse(server.console.received("Find Message Error"));
    }

    @Test
    void onlyAdminsSeeTheAdminCommand() {
        final FakePlayer mod = server.online(new FakePlayer("Mod", "10.0.0.2", "judicator.ban"));
        assertFalse(server.command(mod, "judicator reload"));
    }

    @Test
    void import_isConsoleOnlyDryRunFirstAndNeverDuplicates() throws Exception {
        final Path bans = folder.resolve("vanilla");
        Files.createDirectories(bans);
        Files.writeString(bans.resolve("banned-players.json"), """
                [{"uuid": "069a79f4-44e9-4726-a5be-fca90e38aaf5", "name": "Notch", "created": "2024-01-15 10:30:00 +0000",
                  "source": "Server", "expires": "forever", "reason": "griefing"}]""", StandardCharsets.UTF_8);
        Files.writeString(folder.resolve("import.yml"), "vanilla:\n  directory: '" + bans.toString().replace("'", "''") + "'\n", StandardCharsets.UTF_8);

        final FakePlayer admin = server.online(new FakePlayer("Admin", "10.0.0.2", "judicator.admin"));
        server.command(admin, "judicator import vanilla confirm");
        assertTrue(admin.received("Run /judicator import from the server console."));
        assertTrue(server.punishments.all().isEmpty());

        server.command(server.console, "judicator import vanilla");
        assertTrue(server.console.received("vanilla: 1 punishments and 0 warns to import, 0 already in Judicator."), server.console.messages().toString());
        assertTrue(server.console.received("Nothing was written."));
        assertTrue(server.punishments.all().isEmpty());

        server.command(server.console, "judicator import vanilla confirm");
        assertTrue(server.console.received("Imported 1 punishments and 0 warns from vanilla."));
        assertEquals(1, server.punishments.findAllActiveByUsernameAndTypes("Notch", PunishType.BAN).size());
        assertTrue(server.join(new FakePlayer("Notch", UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5"), "10.0.0.7")).isPresent(),
                "imported ban is enforced on the account it was stored for");

        server.console.clearMessages();
        server.command(server.console, "judicator import vanilla confirm");
        assertTrue(server.console.received("0 punishments and 0 warns to import, 1 already in Judicator."));
        assertEquals(1, server.punishments.all().size());
    }

    @Test
    void import_ofAnUnknownSource() {
        server.command(server.console, "judicator import essentials");
        assertTrue(server.console.received("Unknown source. Use one of: advancedban, banmanager, judicator, libertybans, litebans, vanilla"),
                server.console.messages().toString());
    }

    @Test
    void import_reportsConfigurationProblems() {
        server.command(server.console, "judicator import litebans");
        assertTrue(server.console.received("Import failed: Set jdbc-url for this source in import.yml."), server.console.messages().toString());
        assertTrue(Files.exists(folder.resolve("import.yml")), "the file is created for the owner to fill in");
    }
}
