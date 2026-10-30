package com.elless.judicator.punish.data.repository.dao;

import com.elless.judicator.warns.repository.dao.WarnRelationalDAO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ImportKeyTest {

    private final LocalDateTime start = LocalDateTime.of(2025, 5, 1, 12, 0, 0);

    @Test
    void addressWinsOverUuidAndName() {
        assertEquals(PunishmentRelationalDAO.importKey("u", "1.2.3.4", "steve", "BAN", start),
                PunishmentRelationalDAO.importKey(null, "1.2.3.4", "bob", "BAN", start));
    }

    @Test
    void uuidWinsOverNameSoRenamesDoNotDuplicate() {
        assertEquals(PunishmentRelationalDAO.importKey("AAAA", null, "oldname", "MUTE", start),
                PunishmentRelationalDAO.importKey("aaaa", null, "newname", "MUTE", start));
    }

    @Test
    void startIsComparedToTheSecond() {
        assertEquals(PunishmentRelationalDAO.importKey(null, null, "steve", "BAN", start.withNano(999_000_000)),
                PunishmentRelationalDAO.importKey(null, null, "Steve", "BAN", start));
        assertNotEquals(PunishmentRelationalDAO.importKey(null, null, "steve", "BAN", start.plusSeconds(1)),
                PunishmentRelationalDAO.importKey(null, null, "steve", "BAN", start));
    }

    @Test
    void typeIsPartOfTheKey() {
        assertNotEquals(PunishmentRelationalDAO.importKey("u", null, "s", "BAN", start),
                PunishmentRelationalDAO.importKey("u", null, "s", "TEMPBAN", start));
    }

    @Test
    void warnKeys() {
        assertEquals(WarnRelationalDAO.importKey("UUID-1", "old", start), WarnRelationalDAO.importKey("uuid-1", "new", start));
        assertNotEquals(WarnRelationalDAO.importKey(null, "steve", start), WarnRelationalDAO.importKey(null, "alex", start));
    }
}
