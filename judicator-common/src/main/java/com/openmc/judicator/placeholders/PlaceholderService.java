package com.openmc.judicator.placeholders;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.openmc.judicator.Judicator;
import com.openmc.judicator.api.JudicatorApi;
import com.openmc.judicator.api.event.PlayerKickedEvent;
import com.openmc.judicator.api.event.PunishmentAppliedEvent;
import com.openmc.judicator.api.event.PunishmentEditedEvent;
import com.openmc.judicator.api.event.PunishmentRevokedEvent;
import com.openmc.judicator.api.event.Subscription;
import com.openmc.judicator.api.event.WarnAppliedEvent;
import com.openmc.judicator.api.event.WarnRevokedEvent;
import com.openmc.judicator.api.punishment.PunishmentType;
import com.openmc.judicator.api.punishment.PunishmentView;
import com.openmc.judicator.api.warn.WarnView;
import com.openmc.judicator.commons.DateTimeOffsetParser;
import com.openmc.judicator.punish.scopes.ScopeService;
import org.spongepowered.configurate.ConfigurationNode;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Placeholders about a player for other plugins (PlaceholderAPI on Spigot/Paper): {@code banned}, {@code mute_reason},
 * {@code warns}... A placeholder never waits for the database: each player's punishments and warns are looked up
 * through the API on the first request and kept, refreshed in the background when older than {@link #REFRESH} or as
 * soon as this server changes them. Until the first lookup ends a placeholder is empty.
 */
public class PlaceholderService {

    /** what another server of the network changed shows up after this long at most */
    static final Duration REFRESH = Duration.ofSeconds(30);
    private static final List<String> TOTALS = List.of("banned", "muted", "warns", "warns_total", "bans_total",
            "mutes_total", "kicks_total", "punishments_total");
    /** what {@code ban_<field>} and {@code mute_<field>} can ask for */
    private static final List<String> FIELDS = List.of("id", "reason", "punisher", "expires", "remaining", "scope");

    private final Judicator judicator;
    private final Function<Boolean, String> booleans;
    private final AsyncLoadingCache<UUID, Snapshot> snapshots;
    private final List<Subscription> subscriptions;

    /**
     * @param booleans how {@code banned}, {@code muted}... show true and false
     */
    public PlaceholderService(Judicator judicator, Function<Boolean, String> booleans) {
        this.judicator = judicator;
        this.booleans = booleans;
        final JudicatorApi api = judicator.getApi();
        this.snapshots = Caffeine.newBuilder()
                .expireAfterAccess(5, TimeUnit.MINUTES)
                // leaderboards and menus may ask about many players who are not online
                .maximumSize(5_000)
                .refreshAfterWrite(REFRESH)
                .executor(judicator.getPlatform()::runAsync)
                .buildAsync((uuid, executor) -> load(api, uuid));
        this.subscriptions = List.of(
                api.getEventBus().subscribe(PunishmentAppliedEvent.class, event -> refresh(event.punishment())),
                api.getEventBus().subscribe(PunishmentRevokedEvent.class, event -> refresh(event.punishment())),
                api.getEventBus().subscribe(PunishmentEditedEvent.class, event -> refresh(event.current())),
                api.getEventBus().subscribe(PlayerKickedEvent.class, event -> refresh(event.targetUniqueId())),
                api.getEventBus().subscribe(WarnAppliedEvent.class, event -> event.warn().targetUniqueId().ifPresent(this::refresh)),
                api.getEventBus().subscribe(WarnRevokedEvent.class, event -> event.warn().targetUniqueId().ifPresent(this::refresh)));
    }

    private static CompletableFuture<Snapshot> load(JudicatorApi api, UUID uuid) {
        final CompletableFuture<Optional<PunishmentView>> ban = api.getActiveBan(uuid);
        final CompletableFuture<Optional<PunishmentView>> mute = api.getActiveMute(uuid);
        final CompletableFuture<List<PunishmentView>> history = api.getPunishmentHistory(uuid);
        final CompletableFuture<List<WarnView>> warns = api.getWarns(uuid);
        return CompletableFuture.allOf(ban, mute, history, warns)
                .thenApply(done -> new Snapshot(ban.join(), mute.join(), history.join(), warns.join()));
    }

    private void refresh(PunishmentView punishment) {
        punishment.targetUniqueId().ifPresent(this::refresh);
    }

    /**
     * Looks the player up again, if someone asked for their placeholders; the old values stay until then.
     */
    private void refresh(UUID uuid) {
        if (snapshots.getIfPresent(uuid) != null) snapshots.synchronous().refresh(uuid);
    }

    /**
     * @param uuid   the player the placeholder is about
     * @param params what comes after {@code judicator_}, such as {@code ban_reason}
     * @return the value; empty while the player is being looked up, and for {@code ban_*}/{@code mute_*} without one
     * in force; null when Judicator has no such placeholder
     */
    public String resolve(UUID uuid, String params) {
        final String key = params.toLowerCase(Locale.ROOT);
        if (!isKnown(key)) return null;
        final Snapshot snapshot = snapshots.get(uuid).getNow(null);
        if (snapshot == null) return "";
        final ConfigurationNode messages = judicator.getPlatform().getPlayer(uuid)
                .map(judicator::messagesFor)
                .orElseGet(judicator::getMessagesConfig);
        final Instant now = Instant.now();
        return switch (key) {
            case "banned" -> booleans.apply(snapshot.ban(now).isPresent());
            case "muted" -> booleans.apply(snapshot.mute(now).isPresent());
            case "warns" -> String.valueOf(snapshot.warns().stream().filter(warn -> inForce(warn, now)).count());
            case "warns_total" -> String.valueOf(snapshot.warns().size());
            case "bans_total" -> count(snapshot, view -> view.type().isBan());
            case "mutes_total" -> count(snapshot, view -> view.type().isMute());
            case "kicks_total" -> count(snapshot, view -> view.type() == PunishmentType.KICK);
            case "punishments_total" -> String.valueOf(snapshot.history().size());
            default -> {
                final boolean ban = key.startsWith("ban_");
                final Optional<PunishmentView> punishment = ban ? snapshot.ban(now) : snapshot.mute(now);
                yield punishment.map(view -> field(view, key.substring(key.indexOf('_') + 1), messages, now)).orElse("");
            }
        };
    }

    private static boolean isKnown(String key) {
        if (TOTALS.contains(key)) return true;
        final int underscore = key.indexOf('_');
        if (underscore < 0) return false;
        final String kind = key.substring(0, underscore);
        return (kind.equals("ban") || kind.equals("mute")) && FIELDS.contains(key.substring(underscore + 1));
    }

    private static String count(Snapshot snapshot, Predicate<PunishmentView> kind) {
        return String.valueOf(snapshot.history().stream().filter(kind).count());
    }

    private static String field(PunishmentView punishment, String field, ConfigurationNode messages, Instant now) {
        return switch (field) {
            case "id" -> String.valueOf(punishment.id());
            case "reason" -> punishment.reason();
            case "punisher" -> punishment.punisher();
            case "expires" -> expires(punishment, messages);
            case "remaining" -> remaining(punishment, messages, now);
            case "scope" -> punishment.scope().map(ScopeService::displayName)
                    .orElseGet(() -> legacy(messages.node("scope", "global").getString("every server")));
            default -> "";
        };
    }

    private static String expires(PunishmentView punishment, ConfigurationNode messages) {
        if (punishment.isPaused()) return paused(punishment, messages);
        return punishment.endsAt()
                .map(end -> DateTimeFormatter.ofPattern(messages.node("date-format").getString("dd/MM/yyyy HH:mm"))
                        .format(LocalDateTime.ofInstant(end, ZoneId.systemDefault())))
                .orElseGet(() -> permanent(messages));
    }

    private static String remaining(PunishmentView punishment, ConfigurationNode messages, Instant now) {
        if (punishment.isPaused()) return DateTimeOffsetParser.format(messages, punishment.pausedRemaining().get().toMillis());
        return punishment.endsAt()
                .map(end -> DateTimeOffsetParser.format(messages, Duration.between(now, end).toMillis()))
                .orElseGet(() -> permanent(messages));
    }

    private static String paused(PunishmentView punishment, ConfigurationNode messages) {
        return legacy(messages.node("paused").getString("&e{remaining} of online time left")
                .replace("{remaining}", DateTimeOffsetParser.format(messages, punishment.pausedRemaining().get().toMillis())));
    }

    private static String permanent(ConfigurationNode messages) {
        return legacy(messages.node("permanent").getString("Permanent"));
    }

    private static String legacy(String text) {
        return text.replace("&", "§");
    }

    private static boolean inForce(PunishmentView punishment, Instant now) {
        return punishment.isActive() && (punishment.isPaused() || punishment.endsAt().map(now::isBefore).orElse(true));
    }

    private static boolean inForce(WarnView warn, Instant now) {
        return warn.isActive() && warn.endsAt().map(now::isBefore).orElse(true);
    }

    public void close() {
        subscriptions.forEach(Subscription::unsubscribe);
        snapshots.synchronous().invalidateAll();
    }

    /**
     * What the API gave for a player; a ban or mute that ended since is left out when read.
     */
    private record Snapshot(Optional<PunishmentView> activeBan, Optional<PunishmentView> activeMute,
                            List<PunishmentView> history, List<WarnView> warns) {

        Optional<PunishmentView> ban(Instant now) {
            return activeBan.filter(view -> inForce(view, now));
        }

        Optional<PunishmentView> mute(Instant now) {
            return activeMute.filter(view -> inForce(view, now));
        }
    }

}
