package com.elless.judicator.importer;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NameHistoryTest {

    private final UUID banned = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private final UUID newOwner = UUID.randomUUID();

    @Test
    void currentHolderKeepsTheName() {
        final NameHistory history = new NameHistory();
        history.add(newOwner, "Bob", 100);
        assertEquals("Bob", history.victimName(newOwner));
    }

    @Test
    void staleNameOfABannedPlayerIsNotReused() {
        final NameHistory history = new NameHistory();
        history.add(banned, "Bob", 100);   // banned while called Bob; could not join after renaming
        history.add(newOwner, "bob", 200); // someone else took the name later (case does not matter)

        final String stored = history.victimName(banned);
        assertTrue(stored.startsWith("#"), stored);
        assertTrue(stored.length() <= 20, stored);
        assertEquals("#1111111122223333444", stored);
        assertEquals("bob", history.victimName(newOwner));
        // Staff names are only displayed, so the latest known name is fine there.
        assertEquals("Bob", history.latestName(banned));
    }

    @Test
    void latestNameOfAPlayerWins() {
        final NameHistory history = new NameHistory();
        history.add(banned, "OldName", 100);
        history.add(banned, "NewName", 300);
        history.add(banned, "Middle", 200);
        assertEquals("NewName", history.victimName(banned));
    }

    @Test
    void unknownPlayer() {
        final NameHistory history = new NameHistory();
        history.add(null, "Ghost", 1);
        history.add(banned, " ", 1);
        assertNull(history.victimName(banned));
        assertNull(history.victimName(null));
        assertNull(history.latestName(null));
    }
}
