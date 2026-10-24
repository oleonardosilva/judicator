package com.openmc.judicator.importer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * The server's own ban lists ({@code banned-players.json} and {@code banned-ips.json}, also used by Essentials):
 * {@code uuid}/{@code name} or {@code ip}, {@code created}, {@code source}, {@code expires} ("forever" or a date) and
 * {@code reason}; dates use {@code yyyy-MM-dd HH:mm:ss Z}.
 */
final class VanillaImporter implements ImportSource {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z", Locale.ROOT);

    @Override
    public String name() {
        return "vanilla";
    }

    @Override
    public ImportResult read(ImportContext context) throws Exception {
        final String configured = context.config(name()).node("directory").getString("").trim();
        // Empty: the server's working directory, where both Spigot/Paper and Velocity are started from.
        final Path directory = configured.isEmpty() ? Paths.get("").toAbsolutePath() : Paths.get(configured);
        final Path players = directory.resolve("banned-players.json");
        final Path addresses = directory.resolve("banned-ips.json");
        if (!Files.isRegularFile(players) && !Files.isRegularFile(addresses)) {
            throw new ImportException("banned-players.json / banned-ips.json not found in " + directory
                    + " (set vanilla.directory in import.yml).");
        }

        final ImportResult result = new ImportResult();
        for (JsonObject entry : entries(players)) {
            final String name = string(entry, "name");
            if (!ImportContext.validName(name)) {
                result.skip("ban without a valid player name");
                continue;
            }
            UUID uuid = null;
            final String uuidText = string(entry, "uuid");
            if (uuidText != null) {
                try {
                    uuid = UUID.fromString(uuidText);
                } catch (IllegalArgumentException ignored) {
                }
            }
            if (uuid == null) uuid = context.uuidForName(name).orElse(null);
            add(context, entry, uuid, name, null, result);
        }
        for (JsonObject entry : entries(addresses)) {
            final String address = string(entry, "ip");
            if (address == null || address.equals("<unknown>") || !ImportContext.validAddress(address)) {
                result.skip("IP ban without a valid address");
                continue;
            }
            add(context, entry, null, ImportContext.nameForAddress(address), address, result);
        }
        return result;
    }

    private static void add(ImportContext context, JsonObject entry, UUID uuid, String name, String address, ImportResult result) {
        final Optional<LocalDateTime> created = date(string(entry, "created"));
        if (created.isEmpty()) {
            result.skip("ban with an unreadable 'created' date");
            return;
        }
        final String expires = string(entry, "expires");
        final boolean permanent = expires == null || expires.equalsIgnoreCase("forever");
        LocalDateTime finishAt = null;
        if (!permanent) {
            final Optional<LocalDateTime> parsed = date(expires);
            if (parsed.isEmpty()) {
                result.skip("ban with an unreadable 'expires' date");
                return;
            }
            finishAt = parsed.get();
        }
        String source = string(entry, "source");
        // The server writes "Server" for bans from the console and "(Unknown)" when it does not know.
        if (source != null && source.equalsIgnoreCase("Server")) source = ImportContext.CONSOLE;
        if (source != null && source.equalsIgnoreCase("(Unknown)")) source = null;
        result.add(context.punishment(uuid, name, address, string(entry, "reason"), source,
                true, created.get(), finishAt, permanent, true, "vanilla"));
    }

    // new JsonParser().parse(...): Spigot 1.8 provides Gson 2.2.4, which has no JsonParser.parseReader
    @SuppressWarnings("deprecation")
    private static Iterable<JsonObject> entries(Path file) throws Exception {
        final java.util.List<JsonObject> list = new java.util.ArrayList<>();
        if (!Files.isRegularFile(file)) return list;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            final JsonElement root = new JsonParser().parse(reader);
            if (!root.isJsonArray()) throw new ImportException(file.getFileName() + " is not a JSON list.");
            final JsonArray array = root.getAsJsonArray();
            for (JsonElement element : array) {
                if (element.isJsonObject()) list.add(element.getAsJsonObject());
            }
        }
        return list;
    }

    private static String string(JsonObject object, String key) {
        final JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? null : element.getAsString();
    }

    private static Optional<LocalDateTime> date(String value) {
        if (value == null) return Optional.empty();
        try {
            return Optional.of(ImportContext.toLocal(ZonedDateTime.parse(value, DATE).toInstant()));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }
}
