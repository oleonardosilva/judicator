package com.openmc.judicator.commons;

import org.spongepowered.configurate.ConfigurationNode;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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

    public static long getMillisFromDuration(String input) throws IllegalArgumentException {
        String[] parts = input.split(",");
        long totalMillis = 0L;

        for (String part : parts) {
            String[] keyValue = part.trim().split(":");
            if (keyValue.length != 2)
                throw new IllegalArgumentException("Formato inválido: " + part);

            long amount;
            try {
                amount = Long.parseLong(keyValue[0].trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Número inválido: " + keyValue[0].trim());
            }

            String unitKey = keyValue[1].trim().toLowerCase();
            ChronoUnit unit = UNITS.get(unitKey);
            if (unit == null)
                throw new IllegalArgumentException("Unidade de tempo inválida: " + unitKey);

            switch (unit) {
                case MILLIS -> totalMillis += amount;
                case SECONDS -> totalMillis += TimeUnit.SECONDS.toMillis(amount);
                case MINUTES -> totalMillis += TimeUnit.MINUTES.toMillis(amount);
                case HOURS -> totalMillis += TimeUnit.HOURS.toMillis(amount);
                case DAYS -> totalMillis += TimeUnit.DAYS.toMillis(amount);
                case WEEKS -> totalMillis += TimeUnit.DAYS.toMillis(amount * 7);
                case MONTHS -> totalMillis += TimeUnit.DAYS.toMillis(amount * 30);
                case YEARS -> totalMillis += TimeUnit.DAYS.toMillis(amount * 365);
                default -> throw new IllegalArgumentException("Unidade de tempo não suportada: " + unitKey);
            }
        }

        return totalMillis;
    }


    public static LocalDateTime getFinishedAtFromDuration(String input, LocalDateTime base) throws IllegalArgumentException {
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

    public static String format(ConfigurationNode config, long tempo) {
        if (tempo <= 0) return "0 " + config.node("time", "format", "second").getString("segundos");

        final long totalSeconds = TimeUnit.MILLISECONDS.toSeconds(tempo);

        final long anos = totalSeconds / (365 * 24 * 60 * 60);
        final long meses = (totalSeconds % (365 * 24 * 60 * 60)) / (30 * 24 * 60 * 60);
        final long semanas = (totalSeconds % (30 * 24 * 60 * 60)) / (7 * 24 * 60 * 60);
        final long dias = (totalSeconds % (7 * 24 * 60 * 60)) / (24 * 60 * 60);
        final long horas = (totalSeconds % (24 * 60 * 60)) / (60 * 60);
        final long minutos = (totalSeconds % (60 * 60)) / 60;
        final long segundos = totalSeconds % 60;

        class Unidade {
            final long valor;
            final String singular, plural;
            Unidade(long valor, String singular, String plural) {
                this.valor = valor;
                this.singular = singular;
                this.plural = plural;
            }
            String format() {
                return valor + " " + (valor == 1 ? singular : plural);
            }
        }

        final List<Unidade> unidades = List.of(
                new Unidade(anos, config.node("time", "format", "year").getString("ano"), config.node("time", "format", "years").getString("anos")),
                new Unidade(meses, config.node("time", "format", "month").getString("mês"), config.node("time", "format", "months").getString("meses")),
                new Unidade(semanas, config.node("time", "format", "week").getString("semana"), config.node("time", "format", "weeks").getString("semanas")),
                new Unidade(dias, config.node("time", "format", "day").getString("dia"), config.node("time", "format", "days").getString("dias")),
                new Unidade(horas, config.node("time", "format", "hour").getString("hora"), config.node("time", "format", "hours").getString("horas")),
                new Unidade(minutos, config.node("time", "format", "minute").getString("minuto"), config.node("time", "format", "minutes").getString("minutos")),
                new Unidade(segundos, config.node("time", "format", "second").getString("segundo"), config.node("time", "format", "seconds").getString("segundos"))
        );

        final List<String> partes = unidades.stream()
                .filter(u -> u.valor > 0)
                .map(Unidade::format)
                .toList();

        if (partes.isEmpty()) return "0 " + config.node("time", "format", "second").getString("segundos");
        if (partes.size() == 1) return partes.get(0);
        return String.join(", ", partes);
    }

}
