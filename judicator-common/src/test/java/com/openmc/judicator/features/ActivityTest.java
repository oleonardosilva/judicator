package com.openmc.judicator.features;

import com.openmc.judicator.punish.Punishment;
import com.openmc.judicator.punish.types.PunishType;
import com.openmc.judicator.testing.FakePlayer;
import com.openmc.judicator.testing.TestServer;
import com.openmc.judicator.warns.Warn;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * /activity: what each staff member applied in the last period.
 */
class ActivityTest {

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

    private void punishment(String staff, PunishType type, LocalDateTime start) {
        server.punishments.save(Punishment.create(null, "r", staff, "Target", null, start, type.isTemp() ? start.plusDays(1) : null,
                List.of(), type));
    }

    private void warn(String staff, LocalDateTime start) {
        server.warns.save(Warn.create(UUID.randomUUID(), "r", staff, "Target", start, null));
    }

    @Test
    void countsPunishmentsAndWarnsPerStaffMemberBusiestFirst() {
        final LocalDateTime recent = LocalDateTime.now().minusHours(2);
        punishment("Mod", PunishType.BAN, recent);
        punishment("mod", PunishType.BAN, recent);
        punishment("Mod", PunishType.TEMPMUTE, recent);
        warn("Mod", recent);
        punishment("Helper", PunishType.TEMPMUTE, recent);
        warn("Helper", recent);
        warn("Helper", recent);
        warn("Helper", recent);
        warn("Helper", recent);
        punishment("Admin", PunishType.KICK, recent);
        punishment("Mod", PunishType.BAN, LocalDateTime.now().minusDays(3));

        server.command(server.console, "activity 24:h");

        final String text = String.join("", server.console.messages());
        assertTrue(text.contains("Staff activity in the last 1 day (3 staff, page 1/1)"), text);
        assertTrue(text.contains("Helper: 1 TEMPMUTE, 4 WARN"), text);
        assertTrue(text.contains("Mod: 2 BAN, 1 TEMPMUTE, 1 WARN"), "names merged ignoring case; older ban left out: " + text);
        assertTrue(text.contains("Admin: 1 KICK"), text);
        assertTrue(text.indexOf("Helper") < text.indexOf("Mod") && text.indexOf("Mod") < text.indexOf("Admin"), text);
    }

    @Test
    void clickingAStaffMemberOpensTheirBlameForThePeriod() {
        punishment("Mod", PunishType.BAN, LocalDateTime.now().minusHours(1));

        server.command(server.console, "activity 7:d");

        assertTrue(server.console.clicks().contains("/blame Mod 7:d"), server.console.clicks().toString());
        assertTrue(server.console.hovers().stream().anyMatch(h -> h.contains("1 in total")), server.console.hovers().toString());
    }

    @Test
    void nothingInThePeriod() {
        punishment("Mod", PunishType.BAN, LocalDateTime.now().minusDays(3));
        server.command(server.console, "activity 1:h");
        assertTrue(server.console.received("No punishments or warns were applied in the last 1 hour."), server.console.messages().toString());
    }

    @Test
    void manyStaffMembersArePaged() {
        for (int i = 0; i < 12; i++) {
            punishment("Staff" + i, PunishType.BAN, LocalDateTime.now().minusHours(1));
        }
        server.command(server.console, "activity 1:d");
        assertTrue(server.console.received("(12 staff, page 1/2)"), server.console.messages().toString());
        assertTrue(server.console.clicks().contains("/activity 1:d 2"), server.console.clicks().toString());
    }

    @Test
    void invalidInput() {
        server.command(server.console, "activity");
        server.command(server.console, "activity week");
        server.command(server.console, "activity 7:d two");
        assertTrue(server.console.messages().stream().filter(m -> m.contains("Use: /activity")).count() == 3,
                server.console.messages().toString());
    }

    @Test
    void needsItsPermission() {
        final FakePlayer helper = server.online(new FakePlayer("Helper", "10.0.0.2", "judicator.blame"));
        assertFalse(server.command(helper, "activity 1:d"));
        helper.grant("judicator.activity");
        assertTrue(server.command(helper, "activity 1:d"));
    }

    @Test
    void suggestsCommonPeriods() {
        assertTrue(server.suggest(server.console, "activity ").containsAll(List.of("24:h", "7:d")));
    }
}
