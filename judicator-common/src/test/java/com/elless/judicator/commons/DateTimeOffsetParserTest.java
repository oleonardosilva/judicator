package com.elless.judicator.commons;

import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.ConfigurationNode;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static com.elless.judicator.testing.FakeConfig.map;
import static com.elless.judicator.testing.FakeConfig.of;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DateTimeOffsetParserTest {

    @Test
    void sumsEveryPart() {
        assertEquals(TimeUnit.DAYS.toMillis(7) + TimeUnit.HOURS.toMillis(2),
                DateTimeOffsetParser.getMillisFromDuration("7:d,2:h"));
    }

    @Test
    void acceptsEnglishPortugueseAndShortUnits() {
        final long day = TimeUnit.DAYS.toMillis(1);
        assertEquals(day, DateTimeOffsetParser.getMillisFromDuration("1:days"));
        assertEquals(day, DateTimeOffsetParser.getMillisFromDuration("1:dia"));
        assertEquals(day, DateTimeOffsetParser.getMillisFromDuration("1:D"));
        assertEquals(TimeUnit.MINUTES.toMillis(30), DateTimeOffsetParser.getMillisFromDuration(" 30 : minutes "));
    }

    @Test
    void finishDateUsesCalendarUnits() {
        final LocalDateTime base = LocalDateTime.of(2026, 1, 31, 12, 0);
        assertEquals(LocalDateTime.of(2026, 2, 28, 12, 0), DateTimeOffsetParser.getFinishedAtFromDuration("1:months", base));
        assertEquals(LocalDateTime.of(2026, 2, 7, 14, 30),
                DateTimeOffsetParser.getFinishedAtFromDuration("1:w,2:h,30:m", base));
    }

    @Test
    void formatTakesEachUnitFromWhatTheLargerOnesLeft() {
        final ConfigurationNode english = of(map("time", map("format",
                map("month", "month", "weeks", "weeks", "day", "day", "hour", "hour"))));

        assertEquals("1 month, 2 weeks, 1 day", DateTimeOffsetParser.format(english, TimeUnit.DAYS.toMillis(45)));
        assertEquals("2 weeks, 1 day, 1 hour", DateTimeOffsetParser.format(english, TimeUnit.HOURS.toMillis(15 * 24 + 1)));
    }

    @Test
    void rejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> DateTimeOffsetParser.getMillisFromDuration("7d"));
        assertThrows(IllegalArgumentException.class, () -> DateTimeOffsetParser.getMillisFromDuration("x:d"));
        assertThrows(IllegalArgumentException.class, () -> DateTimeOffsetParser.getMillisFromDuration("7:fortnights"));
        assertThrows(IllegalArgumentException.class,
                () -> DateTimeOffsetParser.getFinishedAtFromDuration("7", LocalDateTime.now()));
    }
}
