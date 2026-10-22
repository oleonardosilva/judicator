package com.openmc.judicator.discord;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.punish.Punishment;
import com.openmc.judicator.warns.Warn;
import org.spongepowered.configurate.ConfigurationNode;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class DiscordWebhookService {

    private static final String SKIN_FACE_URL = "https://mc-heads.net/avatar/%s";
    private final DateTimeFormatter FORMATTER;

    private final Judicator plugin;

    public DiscordWebhookService(Judicator plugin) {
        this.plugin = plugin;
        FORMATTER = DateTimeFormatter.ofPattern(plugin.getMessagesConfig().node("date-format").getString("dd/MM/yyyy HH:mm"));
    }

    public void sendPunishment(Punishment punishment) {
        final ConfigurationNode discordConfig = plugin.getDiscordConfig();
        if (!discordConfig.node("enabled").getBoolean(false)) return;

        final String eventKey = resolveEventKey(punishment);
        final ConfigurationNode eventNode = discordConfig.node("events", eventKey);
        if (!eventNode.node("enabled").getBoolean(false)) return;

        final String webhookUrl = eventNode.node("webhook-url").getString("");
        if (webhookUrl.isBlank()) return;

        CompletableFuture.runAsync(() -> {
            try {
                final String json = buildPunishmentPayload(punishment, eventNode);
                post(webhookUrl, json);
            } catch (Exception e) {
                plugin.getLogger().error("[Discord] Failed to send punishment webhook: {}", e.getMessage(), e);
            }
        });
    }

    public void sendWarn(Warn warn) {
        final ConfigurationNode discordConfig = plugin.getDiscordConfig();
        if (!discordConfig.node("enabled").getBoolean(false)) return;

        final ConfigurationNode eventNode = discordConfig.node("events", "warn");
        if (!eventNode.node("enabled").getBoolean(false)) return;

        final String webhookUrl = eventNode.node("webhook-url").getString("");
        if (webhookUrl.isBlank()) return;

        CompletableFuture.runAsync(() -> {
            try {
                final String json = buildWarnPayload(warn, eventNode);
                post(webhookUrl, json);
            } catch (Exception e) {
                plugin.getLogger().error("[Discord] Failed to send warn webhook: {}", e.getMessage(), e);
            }
        });
    }

    private String resolveEventKey(Punishment punishment) {
        return punishment.getType().name().toLowerCase();
    }

    private String buildPunishmentPayload(Punishment punishment, ConfigurationNode eventNode) {
        final String skinUrl = String.format(SKIN_FACE_URL, punishment.getPlayerUUID().map(UUID::toString).orElse("MHF_Steve"));
        final int color = hexToInt(eventNode.node("color").getString("#FF0000"));

        final String title = resolvePlaceholders(eventNode.node("title").getString("Punishment"), punishment, null);
        final String description = resolvePlaceholders(eventNode.node("description").getString(""), punishment, null);

        final String finishAt = punishment.getFinishAt()
                .map(dt -> dt.format(FORMATTER))
                .orElse("Permanent");

        final String reason = punishment.getReason().orElse("No reason provided");
        final String avatarUrl = "https://i.imgur.com/TMpUuSu.png";

        return """
                {
                  "embeds": [{
                    "title": %s,
                    "avatar_url": %s,
                    "description": %s,
                    "color": %d,
                    "thumbnail": { "url": "%s" },
                    "fields": [
                      { "name": "Player", "value": "`%s`", "inline": true },
                      { "name": "Punisher", "value": "`%s`", "inline": true },
                      { "name": "Type", "value": "`%s`", "inline": true },
                      { "name": "Reason", "value": "`%s`", "inline": false },
                      { "name": "Started At", "value": "`%s`", "inline": true },
                      { "name": "Duration", "value": "`%s`", "inline": true }
                    ],
                    "footer": { "text": "Judicator • ID #%d" },
                    "timestamp": "%s"
                  }]
                }
                """.formatted(
                jsonString(title),
                jsonString(avatarUrl),
                jsonString(description),
                color,
                skinUrl,
                escape(punishment.getNickname()),
                escape(punishment.getPunisher()),
                punishment.getType().name(),
                escape(reason),
                punishment.getStartedAt().format(FORMATTER),
                escape(finishAt),
                punishment.getId() != null ? punishment.getId() : 0,
                punishment.getStartedAt().toString()
        );
    }

    private String buildWarnPayload(Warn warn, ConfigurationNode eventNode) {
        final String skinUrl = String.format(SKIN_FACE_URL, warn.getPlayerUUID().map(UUID::toString).orElse("MHF_Steve"));
        final int color = hexToInt(eventNode.node("color").getString("#FFA500"));

        final String title = resolvePlaceholders(eventNode.node("title").getString("Warning"), null, warn);
        final String description = resolvePlaceholders(eventNode.node("description").getString(""), null, warn);

        final String finishAt = warn.getFinishAt()
                .map(dt -> dt.format(FORMATTER))
                .orElse("Permanent");

        return """
                {
                  "embeds": [{
                    "title": %s,
                    "description": %s,
                    "color": %d,
                    "thumbnail": { "url": "%s" },
                    "fields": [
                      { "name": "Player", "value": "`%s`", "inline": true },
                      { "name": "Punisher", "value": "`%s`", "inline": true },
                      { "name": "Reason", "value": "`%s`", "inline": false },
                      { "name": "Duration", "value": "`%s`", "inline": true },
                      { "name": "Started At", "value": "`%s`", "inline": true }
                    ],
                    "footer": { "text": "Judicator • ID #%d" },
                    "timestamp": "%s"
                  }]
                }
                """.formatted(
                jsonString(title),
                jsonString(description),
                color,
                skinUrl,
                escape(warn.getNickname()),
                escape(warn.getPunisher()),
                escape(warn.getReason()),
                escape(finishAt),
                warn.getStartedAt().format(FORMATTER),
                warn.getId() != null ? warn.getId() : 0,
                warn.getStartedAt().toString()
        );
    }

    private String resolvePlaceholders(String template, Punishment punishment, Warn warn) {
        if (template == null) return "";
        String result = template;
        if (punishment != null) {
            result = result
                    .replace("{player}", punishment.getNickname())
                    .replace("{punisher}", punishment.getPunisher())
                    .replace("{reason}", punishment.getReason().orElse("N/A"))
                    .replace("{type}", punishment.getType().name())
                    .replace("{id}", String.valueOf(punishment.getId() != null ? punishment.getId() : 0));
        }
        if (warn != null) {
            result = result
                    .replace("{player}", warn.getNickname())
                    .replace("{punisher}", warn.getPunisher())
                    .replace("{reason}", warn.getReason())
                    .replace("{id}", String.valueOf(warn.getId() != null ? warn.getId() : 0));
        }
        return result;
    }

    private void post(String webhookUrl, String json) throws IOException {
        final URL url = new URL(webhookUrl);
        final HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("User-Agent", "Judicator-Plugin/1.0");
        connection.setDoOutput(true);
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);

        try (OutputStream os = connection.getOutputStream()) {
            os.write(json.getBytes(StandardCharsets.UTF_8));
        }

        final int responseCode = connection.getResponseCode();
        if (responseCode < 200 || responseCode >= 300) {
            plugin.getLogger().warn("[Discord] Webhook returned HTTP {}", responseCode);
        }
        connection.disconnect();
    }

    private int hexToInt(String hex) {
        try {
            return Integer.parseInt(hex.replace("#", ""), 16);
        } catch (NumberFormatException e) {
            return 0xFF0000;
        }
    }

    private String escape(String value) {
        if (value == null) return "N/A";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String jsonString(String value) {
        return "\"" + escape(value) + "\"";
    }
}