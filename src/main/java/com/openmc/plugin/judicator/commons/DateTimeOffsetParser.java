package com.openmc.plugin.judicator.commons;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

public class DateTimeOffsetParser {

    private static final Map<String, ChronoUnit> UNITS = new HashMap<>();

    static {
        // portuguese
        UNITS.put("segundos", ChronoUnit.SECONDS);
        UNITS.put("segundo", ChronoUnit.SECONDS);
        UNITS.put("minutos", ChronoUnit.MINUTES);
        UNITS.put("minuto", ChronoUnit.MINUTES);
        UNITS.put("horas", ChronoUnit.HOURS);
        UNITS.put("hora", ChronoUnit.HOURS);
        UNITS.put("dias", ChronoUnit.DAYS);
        UNITS.put("dia", ChronoUnit.DAYS);
        UNITS.put("semanas", ChronoUnit.WEEKS);
        UNITS.put("semana", ChronoUnit.WEEKS);
        UNITS.put("meses", ChronoUnit.MONTHS);
        UNITS.put("mês", ChronoUnit.MONTHS);
        UNITS.put("anos", ChronoUnit.YEARS);
        UNITS.put("ano", ChronoUnit.YEARS);

        // english
        UNITS.put("seconds", ChronoUnit.SECONDS);
        UNITS.put("second", ChronoUnit.SECONDS);
        UNITS.put("minutes", ChronoUnit.MINUTES);
        UNITS.put("minute", ChronoUnit.MINUTES);
        UNITS.put("hours", ChronoUnit.HOURS);
        UNITS.put("hour", ChronoUnit.HOURS);
        UNITS.put("days", ChronoUnit.DAYS);
        UNITS.put("day", ChronoUnit.DAYS);
        UNITS.put("weeks", ChronoUnit.WEEKS);
        UNITS.put("week", ChronoUnit.WEEKS);
        UNITS.put("months", ChronoUnit.MONTHS);
        UNITS.put("month", ChronoUnit.MONTHS);
        UNITS.put("years", ChronoUnit.YEARS);
        UNITS.put("year", ChronoUnit.YEARS);

        // abbreviate
        UNITS.put("s", ChronoUnit.SECONDS);
        UNITS.put("m", ChronoUnit.MINUTES);
        UNITS.put("h", ChronoUnit.HOURS);
        UNITS.put("d", ChronoUnit.DAYS);
        UNITS.put("w", ChronoUnit.WEEKS);
        UNITS.put("mo", ChronoUnit.MONTHS);
        UNITS.put("y", ChronoUnit.YEARS);
    }

    public static LocalDateTime apply(String input, LocalDateTime base) throws IllegalArgumentException {
        String[] parts = input.split(",");
        LocalDateTime result = base;

        for (String part : parts) {
            String[] keyValue = part.trim().split(":");
            if (keyValue.length != 2)
                throw new IllegalArgumentException("Invalid format: " + part);

            long amount;
            try {
                amount = Long.parseLong(keyValue[0].trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid number: " + keyValue[0].trim());
            }

            String unitKey = keyValue[1].trim().toLowerCase();
            ChronoUnit unit = UNITS.get(unitKey);
            if (unit == null)
                throw new IllegalArgumentException("Invalid time unit: " + unitKey);

            result = result.plus(amount, unit);
        }

        return result;
    }
}
