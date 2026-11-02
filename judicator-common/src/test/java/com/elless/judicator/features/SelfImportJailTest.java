package com.elless.judicator.features;

import com.elless.judicator.commons.db.RelationalDBManager;
import com.elless.judicator.jails.EscapeRoute;
import com.elless.judicator.jails.Jail;
import com.elless.judicator.jails.repository.dao.JailRelationalDAO;
import com.elless.judicator.platform.Location;
import com.elless.judicator.platform.PlatformType;
import com.elless.judicator.punish.Punishment;
import com.elless.judicator.punish.data.repository.dao.PunishmentRelationalDAO;
import com.elless.judicator.punish.types.PunishType;
import com.elless.judicator.testing.TestServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.helpers.NOPLogger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static com.elless.judicator.testing.FakeConfig.map;
import static com.elless.judicator.testing.FakeConfig.of;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * /judicator import judicator with jails: the jails and the jail columns of the punishments come along.
 */
class SelfImportJailTest {

    @TempDir
    Path folder;
    @TempDir
    Path oldServer;

    private TestServer server;

    @AfterEach
    void stop() {
        if (server != null) server.close();
    }

    @Test
    void jailsAndTheirPrisonersAreImported() throws Exception {
        final LocalDateTime start = LocalDateTime.now().minusMinutes(30).truncatedTo(ChronoUnit.SECONDS);
        final Jail jail = Jail.create("cell", "prison", new Location("world", 10.5, 64, 10.5, 0, 0))
                .withEscape(new EscapeRoute(new Location("world", 12.5, 64, 12.5, 0, 0), 1, 40));
        final RelationalDBManager manager = new RelationalDBManager();
        manager.initialize(of(map("database", map("type", "sqlite", "file", "old.db"))), oldServer, NOPLogger.NOP_LOGGER);
        try {
            final PunishmentRelationalDAO punishments = new PunishmentRelationalDAO(manager, NOPLogger.NOP_LOGGER);
            punishments.initialize();
            final JailRelationalDAO jails = new JailRelationalDAO(manager, NOPLogger.NOP_LOGGER);
            jails.initialize();
            jails.save(jail, "Warden");
            final Punishment fugitive = Punishment.create(null, "griefing", "Mod", "Steve", null, start, start.plusHours(1),
                    List.of(), PunishType.TEMPJAIL);
            fugitive.setJail("cell");
            fugitive.setEscapedAt(start.plusMinutes(10));
            fugitive.setJailLeft(3_000_000L);
            punishments.save(fugitive);
        } finally {
            manager.shutdown();
        }
        server = TestServer.in(folder).platform(PlatformType.BUKKIT).start();
        Files.writeString(folder.resolve("import.yml"), "judicator:\n  jdbc-url: 'jdbc:sqlite:"
                + oldServer.resolve("old.db").toString().replace("\\", "/").replace("'", "''") + "'\n", StandardCharsets.UTF_8);

        server.command(server.console, "judicator import judicator confirm");

        assertEquals(jail, server.judicator.getJailService().find("cell").orElseThrow());
        assertEquals(List.of(jail), server.jails.findAll());
        final Punishment imported = server.punishments.findAllByUsername("Steve").get(0);
        assertEquals("cell", imported.getJail().orElseThrow());
        assertEquals(start.plusMinutes(10), imported.getEscapedAt().orElseThrow());
        assertEquals(3_000_000L, imported.getJailLeft());
        assertTrue(server.console.received("jails: 1 new"), String.valueOf(server.console.messages()));
    }
}
