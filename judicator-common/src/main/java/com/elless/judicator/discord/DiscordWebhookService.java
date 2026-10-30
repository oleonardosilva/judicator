package com.elless.judicator.discord;

import com.elless.judicator.Judicator;
import com.elless.judicator.punish.Punishment;
import com.elless.judicator.punish.scopes.ScopeService;
import com.elless.judicator.punish.types.PunishType;
import com.elless.judicator.reports.Report;
import com.elless.judicator.warns.Warn;
import org.spongepowered.configurate.ConfigurationNode;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Posts an embed per punishment and warn to the webhook of {@code discord.yml}: the event's own {@code webhook-url},
 * or the top-level one.
 */
public class DiscordWebhookService {

    private static final String DEFAULT_THUMBNAIL = "https://mc-heads.net/avatar/{uuid}";
    private final DateTimeFormatter FORMATTER;

    private final Judicator plugin;

    public DiscordWebhookService(Judicator plugin) {
        this.plugin = plugin;
        FORMATTER = DateTimeFormatter.ofPattern(plugin.getMessagesConfig().node("date-format").getString("dd/MM/yyyy HH:mm"));
    }

    public void sendPunishment(Punishment punishment) {
        final ConfigurationNode labels = plugin.getDiscordConfig().node("labels");
        final String expires = punishment.getFinishAt()
                .map(dt -> dt.format(FORMATTER))
                .orElse(punishment.isPaused() ? label(labels, "paused", "Paused (online time only)") : label(labels, "permanent", "Permanent"));
        final Entry entry = new Entry(punishment.getId(), punishment.getNickname(), punishment.getPlayerUUID(), punishment.getPunisher(),
                punishment.getReason().orElse(label(labels, "no-reason", "No reason provided")), punishment.getDisplayType(),
                punishment.getStartedAt(), punishment.getType() == PunishType.KICK ? null : expires,
                punishment.getScope().map(ScopeService::displayName).orElse(null), false);
        send(punishment.getType().name().toLowerCase(Locale.ROOT), entry);
    }

    public void sendWarn(Warn warn) {
        final ConfigurationNode labels = plugin.getDiscordConfig().node("labels");
        final String expires = warn.getFinishAt().map(dt -> dt.format(FORMATTER)).orElse(label(labels, "permanent", "Permanent"));
        send("warn", new Entry(warn.getId(), warn.getNickname(), warn.getPlayerUUID(), warn.getPunisher(), warn.getReason(),
                label(labels, "warn", "Warn"), warn.getStartedAt(), expires, null, false));
    }

    /**
     * {@code events.report} (off by default): a player reported another.
     */
    public void sendReport(Report report) {
        final ConfigurationNode labels = plugin.getDiscordConfig().node("labels");
        send("report", new Entry(report.getId(), report.getTargetName(), report.getTargetUuid(), report.getReporterName(), report.getReason(),
                label(labels, "report", "Report"), report.getCreatedAt(), null, report.getServer().orElse(null), true));
    }

    /**
     * What one embed shows, from a punishment or a warn.
     *
     * @param expires null when the end means nothing (a kick)
     * @param scope   the server or group it is limited to (for a report, the server of the reported player); null for
     *                every server
     * @param report  {@code punisher} is who reported, and {@code scope} where the reported player was
     */
    private record Entry(Long id, String player, Optional<UUID> uuid, String punisher, String reason, String type,
                         LocalDateTime startedAt, String expires, String scope, boolean report) {
    }

    private void send(String eventKey, Entry entry) {
        final ConfigurationNode discordConfig = plugin.getDiscordConfig();
        if (!discordConfig.node("enabled").getBoolean(false)) return;

        final ConfigurationNode eventNode = discordConfig.node("events", eventKey);
        if (!eventNode.node("enabled").getBoolean(false)) return;

        final String webhookUrl = webhookUrl(discordConfig, eventNode);
        if (!isConfigured(webhookUrl)) return;

        CompletableFuture.runAsync(() -> {
            try {
                post(webhookUrl, buildPayload(discordConfig, eventNode, entry));
            } catch (Exception e) {
                plugin.getLogger().error("[Discord] Failed to send the {} webhook: {}", eventKey, e.getMessage(), e);
            }
        });
    }

    /**
     * The event's {@code webhook-url} when set, otherwise the top-level one (older files had one per event only).
     */
    static String webhookUrl(ConfigurationNode discordConfig, ConfigurationNode eventNode) {
        final String own = eventNode.node("webhook-url").getString("");
        return own.isBlank() ? discordConfig.node("webhook-url").getString("") : own;
    }

    /**
     * The default {@code discord.yml} of older versions shipped with a placeholder URL; posting to it only fills the
     * log with errors.
     */
    public static boolean isConfigured(String webhookUrl) {
        return webhookUrl != null && !webhookUrl.isBlank() && !webhookUrl.contains("YOUR_WEBHOOK_HERE");
    }

    private String buildPayload(ConfigurationNode discordConfig, ConfigurationNode eventNode, Entry entry) {
        final ConfigurationNode labels = discordConfig.node("labels");
        final String uuid = entry.uuid().map(UUID::toString).orElse("MHF_Steve");
        final String thumbnail = discordConfig.node("thumbnail-url").getString(DEFAULT_THUMBNAIL).replace("{uuid}", uuid);

        final List<String> fields = new ArrayList<>();
        fields.add(field(label(labels, "player", "Player"), entry.player(), true));
        fields.add(entry.report() ? field(label(labels, "reporter", "Reported by"), entry.punisher(), true)
                : field(label(labels, "punisher", "Punisher"), entry.punisher(), true));
        fields.add(field(label(labels, "type", "Type"), entry.type(), true));
        fields.add(field(label(labels, "reason", "Reason"), entry.reason(), false));
        fields.add(field(label(labels, "started-at", "Started At"), entry.startedAt().format(FORMATTER), true));
        if (entry.expires() != null) fields.add(field(label(labels, "expires", "Expires"), entry.expires(), true));
        if (entry.scope() != null) {
            fields.add(entry.report() ? field(label(labels, "server", "Server"), entry.scope(), true)
                    : field(label(labels, "scope", "Only on"), entry.scope(), true));
        }

        final Map<String, String> embed = new LinkedHashMap<>();
        embed.put("title", jsonString(resolvePlaceholders(eventNode.node("title").getString(entry.type()), entry)));
        embed.put("description", jsonString(resolvePlaceholders(eventNode.node("description").getString(""), entry)));
        embed.put("color", String.valueOf(hexToInt(eventNode.node("color").getString("#FF0000"))));
        if (!thumbnail.isBlank()) embed.put("thumbnail", "{ \"url\": " + jsonString(thumbnail) + " }");
        embed.put("fields", "[" + String.join(", ", fields) + "]");
        final String footer = resolvePlaceholders(discordConfig.node("footer").getString("Judicator • ID #{id}"), entry);
        if (!footer.isBlank()) embed.put("footer", "{ \"text\": " + jsonString(footer) + " }");
        embed.put("timestamp", jsonString(entry.startedAt().toString()));

        final Map<String, String> payload = new LinkedHashMap<>();
        final String username = discordConfig.node("username").getString("");
        final String avatar = discordConfig.node("avatar-url").getString("");
        if (!username.isBlank()) payload.put("username", jsonString(username));
        if (!avatar.isBlank()) payload.put("avatar_url", jsonString(avatar));
        payload.put("embeds", "[" + object(embed) + "]");
        return object(payload);
    }

    private String resolvePlaceholders(String template, Entry entry) {
        if (template == null) return "";
        return template
                .replace("{player}", entry.player())
                .replace("{punisher}", entry.punisher())
                .replace("{reason}", entry.reason())
                .replace("{type}", entry.type())
                .replace("{id}", entry.id() != null ? String.valueOf(entry.id()) : "-")
                .replace("{expires}", entry.expires() != null ? entry.expires() : "-");
    }

    private static String label(ConfigurationNode labels, String key, String fallback) {
        return labels.node(key).getString(fallback);
    }

    private String field(String name, String value, boolean inline) {
        // a backtick in a reason would close the code span and let Discord markdown (masked links) through
        final String shown = value == null ? "N/A" : value.replace('`', '\'');
        return "{ \"name\": " + jsonString(name) + ", \"value\": " + jsonString("`" + shown + "`")
                + ", \"inline\": " + inline + " }";
    }

    private static String object(Map<String, String> members) {
        final List<String> parts = new ArrayList<>();
        members.forEach((key, value) -> parts.add("\"" + key + "\": " + value));
        return "{ " + String.join(", ", parts) + " }";
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

    /**
     * A JSON string literal: quotes, backslashes and control characters (a line break in a reason) escaped.
     */
    static String jsonString(String value) {
        if (value == null) return "\"N/A\"";
        final StringBuilder out = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        return out.append('"').toString();
    }
}
