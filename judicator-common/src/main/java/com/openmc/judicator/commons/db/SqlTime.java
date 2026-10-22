package com.openmc.judicator.commons.db;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * One conversion for every date column.
 * <p>
 * Judicator has always written a {@link LocalDateTime} as the instant it would be in UTC
 * ({@code Timestamp.from(time.toInstant(UTC))}). Reading goes back the same way, so every stored row (old or new)
 * returns the time that was written whatever the JVM or database time zone, and "now" is passed as a parameter
 * encoded the same way instead of the database's {@code CURRENT_TIMESTAMP}, which follows the database time zone
 * and cannot be compared with stored dates in SQLite.
 */
public final class SqlTime {

    private SqlTime() {
    }

    public static Timestamp of(LocalDateTime time) {
        return time == null ? null : Timestamp.from(time.toInstant(ZoneOffset.UTC));
    }

    public static Timestamp now() {
        return of(LocalDateTime.now());
    }

    public static LocalDateTime read(ResultSet resultSet, String column) throws SQLException {
        final Timestamp timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : LocalDateTime.ofInstant(timestamp.toInstant(), ZoneOffset.UTC);
    }
}
