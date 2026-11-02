package com.elless.judicator.discord;

import com.elless.judicator.Judicator;
import com.elless.judicator.punish.Punishment;
import com.elless.judicator.punish.Revocation;
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
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Posts an embed per punishment and warn, and for what changes them (revocations, edits, warns removed, rollbacks,
 * staff locked by the rate limit), to the webhook of {@code discord.yml}: the event's own {@code webhook-url}, or the
 * top-level one.
 */
public class DiscordWebhookService {

    private static final String DEFAULT_THUMBNAIL = "https://mc-heads.net/avatar/{uuid}";
    private final DateTimeFormatter FORMATTER;

    private final Judicator plugin;
    private volatile BiConsumer<String, String> poster;

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
     * {@code events.revoke}: a ban, mute or jail revoked ({@code /unban}, {@code /unmute}, {@code /unjail},
     * {@code /revoke}, the API); a {@code /staffrollback} is posted once with {@link #sendRollback}.
     */
    public void sendRevoked(Punishment punishment, Revocation revocation) {
        final ConfigurationNode labels = plugin.getDiscordConfig().node("labels");
        final String reason = punishment.getReason().orElse(label(labels, "no-reason", "No reason provided"));
        final String revokeReason = revocation.reason() == null || revocation.reason().isBlank()
                ? label(labels, "no-reason", "No reason provided") : revocation.reason();
        final Map<String, String> values = Map.of("{player}", punishment.getNickname(), "{punisher}", punishment.getPunisher(),
                "{type}", punishment.getDisplayType(), "{reason}", reason, "{id}", String.valueOf(punishment.getId()),
                "{revoker}", revocation.by(), "{revoke-reason}", revokeReason);
        notice("revoke", values, punishment.getPlayerUUID(), punishment.getId(), List.of(
                field(label(labels, "player", "Player"), punishment.getNickname(), true),
                field(label(labels, "type", "Type"), punishment.getDisplayType(), true),
                field(label(labels, "punisher", "Punisher"), punishment.getPunisher(), true),
                field(label(labels, "reason", "Reason"), reason, false),
                field(label(labels, "revoked-by", "Revoked by"), revocation.by(), true),
                field(label(labels, "revoke-reason", "Revocation reason"), revokeReason, false)));
    }

    /**
     * {@code events.edit}: {@code /extend} or {@code /changereason}.
     *
     * @param change what changed, as text
     */
    public void sendEdited(Punishment punishment, String author, String change) {
        final ConfigurationNode labels = plugin.getDiscordConfig().node("labels");
        final Map<String, String> values = Map.of("{player}", punishment.getNickname(), "{type}", punishment.getDisplayType(),
                "{id}", String.valueOf(punishment.getId()), "{author}", author, "{change}", change);
        notice("edit", values, punishment.getPlayerUUID(), punishment.getId(), List.of(
                field(label(labels, "player", "Player"), punishment.getNickname(), true),
                field(label(labels, "type", "Type"), punishment.getDisplayType(), true),
                field(label(labels, "edited-by", "Changed by"), author, true),
                field(label(labels, "change", "Change"), change, false)));
    }

    /**
     * {@code events.unwarn}: {@code /unwarn} or the API deleted a warn (the only record left of it).
     */
    public void sendUnwarn(Warn warn, String author) {
        final ConfigurationNode labels = plugin.getDiscordConfig().node("labels");
        final String reason = warn.getReason() == null ? label(labels, "no-reason", "No reason provided") : warn.getReason();
        final Map<String, String> values = Map.of("{player}", warn.getNickname(), "{punisher}", warn.getPunisher(),
                "{reason}", reason, "{id}", String.valueOf(warn.getId()), "{revoker}", author);
        notice("unwarn", values, warn.getPlayerUUID(), warn.getId(), List.of(
                field(label(labels, "player", "Player"), warn.getNickname(), true),
                field(label(labels, "punisher", "Punisher"), warn.getPunisher(), true),
                field(label(labels, "reason", "Reason"), reason, false),
                field(label(labels, "started-at", "Started At"), warn.getStartedAt().format(FORMATTER), true),
                field(label(labels, "removed-by", "Removed by"), author, true)));
    }

    /**
     * {@code events.rollback}: {@code /staffrollback} undid what a staff member applied.
     *
     * @param period the period, as text
     */
    public void sendRollback(String author, String staff, String period, int punishments, int warns) {
        notice("rollback", Map.of("{author}", author, "{staff}", staff, "{period}", period,
                "{punishments}", String.valueOf(punishments), "{warns}", String.valueOf(warns)), Optional.empty(), null, List.of());
    }

    /**
     * {@code events.staff-locked}: a staff member went past {@code staff-rate-limit.max} and was locked out of
     * punishing.
     *
     * @param window the window of the limit, as text
     * @param period the same window as {@code /staffrollback} takes it
     */
    public void sendStaffLocked(String staff, UUID uuid, String type, int count, String window, String period) {
        notice("staff-locked", Map.of("{staff}", staff, "{type}", type, "{count}", String.valueOf(count), "{window}", window,
                "{period}", period), Optional.of(uuid), null, List.of());
    }

    /**
     * An embed for an event that is not a punishment applied: title and description from the event with the
     * placeholders replaced, the given fields, the player's picture when there is one and the footer when there is an
     * id.
     */
    private void notice(String eventKey, Map<String, String> values, Optional<UUID> uuid, Long id, List<String> fields) {
        final ConfigurationNode discordConfig = plugin.getDiscordConfig();
        if (!discordConfig.node("enabled").getBoolean(false)) return;
        final ConfigurationNode eventNode = discordConfig.node("events", eventKey);
        if (!eventNode.node("enabled").getBoolean(false)) return;
        final String webhookUrl = webhookUrl(discordConfig, eventNode);
        if (!isConfigured(webhookUrl)) return;

        final Map<String, String> embed = new LinkedHashMap<>();
        embed.put("title", jsonString(replace(eventNode.node("title").getString(eventKey), values)));
        embed.put("description", jsonString(replace(eventNode.node("description").getString(""), values)));
        embed.put("color", String.valueOf(hexToInt(eventNode.node("color").getString("#95A5A6"))));
        if (uuid.isPresent()) {
            final String thumbnail = discordConfig.node("thumbnail-url").getString(DEFAULT_THUMBNAIL).replace("{uuid}", uuid.get().toString());
            if (!thumbnail.isBlank()) embed.put("thumbnail", "{ \"url\": " + jsonString(thumbnail) + " }");
        }
        if (!fields.isEmpty()) embed.put("fields", "[" + String.join(", ", fields) + "]");
        if (id != null) {
            final String footer = discordConfig.node("footer").getString("Judicator • ID #{id}").replace("{id}", String.valueOf(id));
            if (!footer.isBlank()) embed.put("footer", "{ \"text\": " + jsonString(footer) + " }");
        }
        embed.put("timestamp", jsonString(LocalDateTime.now().toString()));

        final Map<String, String> payload = new LinkedHashMap<>();
        final String username = discordConfig.node("username").getString("");
        final String avatar = discordConfig.node("avatar-url").getString("");
        if (!username.isBlank()) payload.put("username", jsonString(username));
        if (!avatar.isBlank()) payload.put("avatar_url", jsonString(avatar));
        payload.put("embeds", "[" + object(embed) + "]");
        deliver(eventKey, webhookUrl, () -> object(payload));
    }

    private static String replace(String template, Map<String, String> values) {
        String result = template;
        for (Map.Entry<String, String> value : values.entrySet()) result = result.replace(value.getKey(), value.getValue());
        return result;
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

        deliver(eventKey, webhookUrl, () -> buildPayload(discordConfig, eventNode, entry));
    }

    /**
     * For tests: each payload goes to {@code poster} (webhook URL, JSON) on the calling thread instead of over HTTP.
     */
    public void postWith(BiConsumer<String, String> poster) {
        this.poster = poster;
    }

    private void deliver(String eventKey, String webhookUrl, Supplier<String> payload) {
        if (poster != null) {
            poster.accept(webhookUrl, payload.get());
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                post(webhookUrl, payload.get());
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
