package com.openmc.judicator.commons;

import com.google.common.collect.Maps;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.openmc.judicator.Judicator;
import com.openmc.judicator.platform.JudicatorPlayer;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.Map.Entry;

/**
 * Minecraft AdvancedBans plugin inspired codes, an adaptation was made for the Punishments plugin.
 * <a href="https://github.com/DevLeoko/AdvancedBan/blob/master/src/main/java/me/leoko/advancedban/manager/UUIDManager.java">Original source</a>
 * Adapted by Leonardo for plugin Judicator.
 * <a href="https://github.com/oleonardosilva/Judicator">GitHub</a>
 */
public class UUIDManager {

    private static final int TIMEOUT_MILLIS = 5000;
    private final Map<String, String> activeUUIDs = Maps.newConcurrentMap();

    private final Judicator judicator;
    private volatile boolean remoteLookups = true;

    public UUIDManager(Judicator judicator) {
        this.judicator = judicator;
    }

    private UUID getOriginalUUID(String name) {
        try {
            name = name.toLowerCase();
            Optional<JudicatorPlayer> optPlayer = judicator.getPlatform().getPlayer(name);
            if (optPlayer.isPresent()) {
                final UUID uuid = optPlayer.get().getUniqueId();
                supplyInternUUID(name, uuid);
                return uuid;
            }
            if (activeUUIDs.containsKey(name)) return fromString(activeUUIDs.get(name.toLowerCase()));
            String uuid = "";
            try {
                if (remoteLookups) uuid = askAPI(name);
            } catch (IOException | URISyntaxException ignored) {
            }
            UUID parsed = fromString(uuid);
            supplyInternUUID(name, parsed);
            return parsed;
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Remembers the UUID of a player who joined, so punishing them after they log off needs no Mojang lookup.
     */
    public void remember(String name, UUID uuid) {
        if (name != null && uuid != null) supplyInternUUID(name, uuid);
    }

    /**
     * Turns the Mojang API lookups on or off. Off, a player who never joined has no known UUID (tests use this so
     * they never depend on the network).
     */
    public void setRemoteLookups(boolean enabled) {
        this.remoteLookups = enabled;
    }

    private void supplyInternUUID(String name, UUID uuid) {
        activeUUIDs.put(name.toLowerCase(), uuid.toString().replace("-", ""));
    }

    public UUID fromString(String uuid) {
        try {
            if (!uuid.contains("-") && uuid.length() == 32)
                uuid = uuid
                        .replaceFirst(
                                "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)", "$1-$2-$3-$4-$5");
            return uuid.length() == 36 && uuid.contains("-") ? UUID.fromString(uuid) : null;
        } catch (Exception ignored) {
            return null;
        }

    }

    /**
     * The UUID of a player online or seen joining since startup (any letter case), without asking the Mojang API.
     */
    public UUID getKnownUUID(String name) {
        final Optional<JudicatorPlayer> online = judicator.getPlatform().getPlayer(name);
        if (online.isPresent()) return online.get().getUniqueId();
        final String known = activeUUIDs.get(name.toLowerCase());
        return known == null ? null : fromString(known);
    }

    public UUID getUUID(String name) {
        UUID inMemoryUuid = fromString(activeUUIDs.get(name.toLowerCase()));
        return (inMemoryUuid != null) ? inMemoryUuid : getOriginalUUID(name);
    }

    private String getInMemoryName(String uuid) {
        for (Entry<String, String> rs : activeUUIDs.entrySet()) {
            if (rs.getValue().equalsIgnoreCase(uuid)) {
                return rs.getKey();
            }
        }
        return null;
    }

    public String getNameFromUUID(String uuid, boolean forceInitial) {
        if (!forceInitial) {
            String inMemoryName = getInMemoryName(uuid);
            if (inMemoryName != null) {
                return inMemoryName;
            }
        }
        if (!remoteLookups) return null;
        try {
            final URLConnection connection = new URI("https://api.mojang.com/user/profiles/" + uuid + "/names").toURL().openConnection();
            connection.setConnectTimeout(TIMEOUT_MILLIS);
            connection.setReadTimeout(TIMEOUT_MILLIS);
            try (Scanner scanner = new Scanner(connection.getInputStream(), StandardCharsets.UTF_8)) {
            String s = scanner.useDelimiter("\\A").next();
            s = s.substring(s.lastIndexOf('{'), s.lastIndexOf('}') + 1);
            return parseJSON(s);
            }
        } catch (Exception exc) {
            return null;
        }
    }

    private String askAPI(String name) throws IOException, URISyntaxException {
        name = name.toLowerCase();
        judicator.getLogger().info("Fetching UUID of {} from Mojang API...", name);
        final HttpURLConnection request = (HttpURLConnection) new URI("https://api.mojang.com/users/profiles/minecraft/%NAME%?at=%TIMESTAMP%"
                .replaceAll("%NAME%", name)
                .replaceAll("%TIMESTAMP%", new Date().getTime() + ""))
                .toURL()
                .openConnection();
        // Commands run this off the main thread, but a silent network must not hold the command forever.
        request.setConnectTimeout(TIMEOUT_MILLIS);
        request.setReadTimeout(TIMEOUT_MILLIS);
        request.connect();
        String uuid = parseJSON(new InputStreamReader(request.getInputStream()));
        if (uuid == null || uuid.isBlank()) {
            judicator.getLogger().error("!! Failed fetching UUID of {}", name);
            judicator.getLogger().error("!! Could not find key 'id' in the servers response");
            judicator.getLogger().error("!! Response: {}", request.getResponseMessage());
        } else {
            judicator.getLogger().info("UUID: {}", uuid);
            activeUUIDs.put(name, uuid);
        }
        return uuid;
    }

    private String parseJSON(String json) {
        final JsonElement element = JsonParser.parseString(json);
        if (element instanceof JsonNull) {
            return null;
        }
        final JsonElement obj = ((JsonObject) element).get("name");
        return obj != null ? obj.toString().replace("\"", "") : null;
    }

    private String parseJSON(InputStreamReader json) {
        final JsonElement element = JsonParser.parseReader(json);
        if (element instanceof JsonNull) {
            return null;
        }
        final JsonElement obj = ((JsonObject) element).get("id");
        return obj != null ? obj.toString().replace("\"", "") : null;
    }
}
