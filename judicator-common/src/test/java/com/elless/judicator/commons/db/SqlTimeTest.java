package com.elless.judicator.commons.db;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlTimeTest {

    @Test
    void writesTheSameEncodingAsEarlierVersions() {
        final LocalDateTime time = LocalDateTime.of(2026, 10, 22, 18, 30, 15);
        // Rows written before SqlTime existed used exactly this conversion; reading them must keep working.
        assertEquals(Timestamp.from(time.toInstant(ZoneOffset.UTC)), SqlTime.of(time));
    }

    @Test
    void readsBackTheTimeThatWasWritten() throws Exception {
        final LocalDateTime time = LocalDateTime.of(2026, 3, 1, 0, 15, 0);
        assertEquals(time, SqlTime.read(resultSetWith(SqlTime.of(time)), "started_at"));
    }

    @Test
    void nullStaysNull() throws Exception {
        assertNull(SqlTime.of(null));
        assertNull(SqlTime.read(resultSetWith(null), "finish_at"));
    }

    @Test
    void nowIsComparableWithStoredValues() {
        final Timestamp past = SqlTime.of(LocalDateTime.now().minusMinutes(1));
        final Timestamp future = SqlTime.of(LocalDateTime.now().plusMinutes(1));
        final Timestamp now = SqlTime.now();
        assertTrue(past.before(now));
        assertTrue(future.after(now));
    }

    private static ResultSet resultSetWith(Timestamp value) {
        return (ResultSet) Proxy.newProxyInstance(SqlTimeTest.class.getClassLoader(), new Class<?>[]{ResultSet.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getTimestamp")) return value;
                    throw new UnsupportedOperationException(method.getName());
                });
    }
}
