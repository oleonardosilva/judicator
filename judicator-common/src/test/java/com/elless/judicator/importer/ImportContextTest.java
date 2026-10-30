package com.elless.judicator.importer;

import com.elless.judicator.Judicator;
import com.elless.judicator.punish.Punishment;
import com.elless.judicator.punish.types.PunishType;
import com.elless.judicator.testing.FakeConfig;
import com.elless.judicator.testing.FakePlatform;
import com.elless.judicator.warns.Warn;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImportContextTest {

    private final LocalDateTime start = LocalDateTime.now().minusDays(10).withNano(0);

    private static ImportContext context(boolean onlineMode) {
        return new ImportContext(new Judicator(FakePlatform.create(Path.of("."), onlineMode)), FakeConfig.of(Map.of()));
    }

    @Test
    void addressNamesNeverLookLikeAPlayer() {
        assertEquals("1.2.3.4", ImportContext.nameForAddress("1.2.3.4"));
        assertEquals("#ip-ban", ImportContext.nameForAddress("2001:db8:85a3:0:0:8a2e:370:7334"));
        assertTrue(ImportContext.validAddress("2001:db8:85a3:0:0:8a2e:370:7334"));
        assertFalse(ImportContext.validAddress(" "));
        assertFalse(ImportContext.validAddress("x".repeat(46)));
    }

    @Test
    void nameLimits() {
        assertTrue(ImportContext.validName("Steve"));
        assertFalse(ImportContext.validName(null));
        assertFalse(ImportContext.validName(""));
        assertFalse(ImportContext.validName("a".repeat(21)));
    }

    @Test
    void offlineUuidOnlyOnOfflineModeServers() {
        assertEquals(Optional.of(UUID.nameUUIDFromBytes("OfflinePlayer:Steve".getBytes(StandardCharsets.UTF_8))),
                context(false).uuidForName("Steve"));
        assertEquals(Optional.empty(), context(true).uuidForName("Steve"));
    }

    @Test
    void activeTemporaryBan() {
        final Punishment ban = context(true).punishment(null, "Steve", null, "fly", "Admin", true,
                start, start.plusDays(30), false, true, "Test");
        assertEquals(PunishType.TEMPBAN, ban.getType());
        assertEquals(start.plusDays(30), ban.getFinishAt().orElseThrow());
        assertFalse(ban.isRevoked());
        assertEquals("Admin", ban.getPunisher());
    }

    @Test
    void removedPunishmentIsRevokedUnlessItHadEnded() {
        final ImportContext context = context(true);
        final Punishment removedEarly = context.punishment(null, "Steve", null, "spam", null, false,
                start, start.plusDays(30), false, false, "Test");
        assertTrue(removedEarly.isRevoked());
        assertEquals(PunishType.TEMPMUTE, removedEarly.getType());
        assertEquals("Unknown", removedEarly.getPunisher());

        final Punishment ended = context.punishment(null, "Steve", null, "spam", "console", false,
                start, start.plusDays(1), false, false, "Test");
        assertFalse(ended.isRevoked());
        assertEquals("Console", ended.getPunisher());

        final Punishment permanentRemoved = context.punishment(null, "Steve", null, "x", "Mod", true,
                start, null, true, false, "Test");
        assertTrue(permanentRemoved.isRevoked());
        assertEquals(PunishType.BAN, permanentRemoved.getType());
        assertTrue(permanentRemoved.getFinishAt().isEmpty());
    }

    @Test
    void explicitRemovalReasonAndColumnLimits() {
        final Punishment punishment = context(true).punishment(null, "Steve", "1.2.3.4", "r".repeat(300),
                "AVeryLongStaffNameThatDoesNotFit", true, start, null, true, true, "appeal accepted", "Source");
        assertEquals(255, punishment.getReason().orElseThrow().length());
        assertEquals(20, punishment.getPunisher().length());
        assertEquals("appeal accepted (Source)", punishment.getRevokedReason().orElseThrow());
        assertEquals("1.2.3.4", punishment.getIpAddress().orElseThrow());
    }

    @Test
    void removedWarnEndsAtItsStart() {
        final UUID uuid = UUID.randomUUID();
        final Warn removed = context(true).warn(uuid, "Steve", "toxic", "Mod", start, null, true, false);
        assertFalse(removed.isPermanent());
        assertEquals(start, removed.getFinishAt().orElseThrow());

        final Warn active = context(true).warn(uuid, "Steve", "toxic", "Mod", start, null, true, true);
        assertTrue(active.isPermanent());
        assertNull(active.getFinishAt().orElse(null));
    }

    @Test
    void epochConversionDropsMillis() {
        assertEquals(0, ImportContext.fromEpochMillis(1_700_000_000_123L).getNano());
    }
}
