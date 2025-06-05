package com.openmc.plugin.judicator.commons;

import com.google.common.collect.Maps;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.openmc.plugin.judicator.Judicator;
import com.velocitypowered.api.proxy.Player;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
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

    private final HashMap<String, String> activeUUIDs = Maps.newHashMap();

    private final Judicator judicator;

    public UUIDManager(Judicator judicator) {
        this.judicator = judicator;
    }

    private UUID getOriginalUUID(String name) {
        name = name.toLowerCase();
        Optional<Player> optPlayer = judicator.getServer().getPlayer(name);
        if (optPlayer.isPresent()) {
            final UUID uuid = optPlayer.get().getUniqueId();
            supplyInternUUID(name, uuid);
            return uuid;
        }
        if (activeUUIDs.containsKey(name)) return getUUID(name);
        String uuid = "";
        try {
            uuid = askAPI(name);
        } catch (IOException | URISyntaxException ignored) {
        }
        UUID parsed = fromString(uuid);
        supplyInternUUID(name, parsed);
        return parsed;
    }

    private void supplyInternUUID(String name, UUID uuid) {
        activeUUIDs.put(name.toLowerCase(), uuid.toString().replace("-", ""));
    }

    public UUID fromString(String uuid) {
        if (!uuid.contains("-") && uuid.length() == 32)
            uuid = uuid
                    .replaceFirst(
                            "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)", "$1-$2-$3-$4-$5");
        return uuid.length() == 36 && uuid.contains("-") ? UUID.fromString(uuid) : null;
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
        try (Scanner scanner = new Scanner(new URI("https://api.mojang.com/user/profiles/" + uuid + "/names").toURL().openStream(), StandardCharsets.UTF_8)) {
            String s = scanner.useDelimiter("\\A").next();
            s = s.substring(s.lastIndexOf('{'), s.lastIndexOf('}') + 1);
            return parseJSON(s);
        } catch (Exception exc) {
            return null;
        }
    }

    private String askAPI(String name) throws IOException, URISyntaxException {
        name = name.toLowerCase();
        final HttpURLConnection request = (HttpURLConnection) new URI("https://api.mojang.com/users/profiles/minecraft/%NAME%?at=%TIMESTAMP%"
                .replaceAll("%NAME%", name)
                .replaceAll("%TIMESTAMP%", new Date().getTime() + ""))
                .toURL()
                .openConnection();
        request.connect();
        String uuid = parseJSON(new InputStreamReader(request.getInputStream()));
        if (uuid == null) {
            judicator.getLogger().error("!! Failed fetching UUID of {}", name);
            judicator.getLogger().error("!! Could not find key 'id' in the servers response");
            judicator.getLogger().error("!! Response: {}", request.getResponseMessage());
        } else {
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
        return obj != null ? obj.toString().replaceAll("\"", "") : null;
    }

    private String parseJSON(InputStreamReader json) {
        final JsonElement element = JsonParser.parseReader(json);
        if (element instanceof JsonNull) {
            return null;
        }
        final JsonElement obj = ((JsonObject) element).get("id");
        return obj != null ? obj.toString().replaceAll("\"", "") : null;
    }
}
