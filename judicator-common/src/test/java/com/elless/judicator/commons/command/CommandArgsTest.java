package com.elless.judicator.commons.command;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandArgsTest {

    @Test
    void readsWordsAndKeepsTheRestAsReason() {
        final CommandArgs args = CommandArgs.of("Steve  hacking with  fly");
        assertEquals(Optional.of("Steve"), args.word());
        assertEquals("hacking with  fly", args.remaining());
        assertEquals(Optional.empty(), args.word());
        assertEquals("", args.remaining());
    }

    @Test
    void readsQuotedDuration() {
        final CommandArgs args = CommandArgs.of(new String[]{"Steve", "\"7:d,2:h\"", "spam"});
        assertEquals(Optional.of("Steve"), args.word());
        assertEquals(Optional.of("7:d,2:h"), args.quotableString());
        assertEquals("spam", args.remaining());
    }

    @Test
    void unquotedDurationIsAWord() {
        final CommandArgs args = CommandArgs.of("Steve 7:d spam");
        args.word();
        assertEquals(Optional.of("7:d"), args.quotableString());
    }

    @Test
    void silentFlagOnlyMatchesTheWholeWord() {
        final CommandArgs silent = CommandArgs.of("Steve -s reason");
        silent.word();
        assertTrue(silent.consumeFlag("-s"));
        assertEquals("reason", silent.remaining());

        final CommandArgs notAFlag = CommandArgs.of("Steve -spam reason");
        notAFlag.word();
        assertFalse(notAFlag.consumeFlag("-s"));
        assertEquals("-spam reason", notAFlag.remaining());
    }

    @Test
    void emptyInput() {
        final CommandArgs args = CommandArgs.of((String) null);
        assertEquals(Optional.empty(), args.word());
        assertEquals(Optional.empty(), args.quotableString());
        assertFalse(args.consumeFlag("-s"));
        assertEquals("", args.remaining());
    }
}
