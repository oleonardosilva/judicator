package com.elless.judicator.punish.rules;

import com.elless.judicator.Judicator;
import com.elless.judicator.commons.DateTimeOffsetParser;
import com.elless.judicator.platform.JudicatorSender;
import com.elless.judicator.punish.PunishUtils;
import com.elless.judicator.punish.types.PunishPermissions;
import com.elless.judicator.punish.types.PunishType;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Longest ban/mute each staff group may apply ({@code time-limits.<group>.ban|mute} in {@code config.yml}), given to
 * a staff member with the explicit permission {@code judicator.timelimit.<group>}. With several groups the longest
 * limit wins; a limited staff member cannot apply a permanent punishment. Console and {@code judicator.admin} have no
 * limit.
 */
public class TimeLimitService {

    public static final String PERMISSION_PREFIX = "judicator.timelimit.";

    private final Judicator judicator;
    private Map<String, Map<String, String>> limits = Map.of();

    public TimeLimitService(Judicator judicator) {
        this.judicator = judicator;
        reload();
    }

    public void reload() {
        final Map<String, Map<String, String>> loaded = new HashMap<>();
        judicator.getConfig().node("time-limits").childrenMap().forEach((group, node) -> {
            final Map<String, String> byCategory = new HashMap<>();
            for (String category : new String[]{"ban", "mute"}) {
                final String duration = node.node(category).getString();
                if (duration == null || duration.isBlank()) continue;
                try {
                    DateTimeOffsetParser.getFinishedAtFromDuration(duration, LocalDateTime.now());
                    byCategory.put(category, duration);
                } catch (RuntimeException e) {
                    judicator.getLogger().warn("Ignoring time-limits.{}.{}: invalid duration '{}'", group, category, duration);
                }
            }
            loaded.put(String.valueOf(group).toLowerCase(Locale.ROOT), Map.copyOf(byCategory));
        });
        this.limits = Map.copyOf(loaded);
    }

    /**
     * @return true when the sender may apply this punishment; otherwise the limit is sent to the sender
     */
    public boolean allows(JudicatorSender sender, PunishType type, LocalDateTime startedAt, LocalDateTime finishAt) {
        final Optional<String> limit = exceededLimit(sender, type, startedAt, finishAt);
        if (limit.isEmpty()) return true;
        sender.sendMessage(PunishUtils.format(judicator.messagesFor(sender),
                Map.of("type", category(type), "limit", limit.get()), "error", "time-limit"));
        return false;
    }

    /**
     * @param finishAt null for a permanent punishment
     * @return the sender's limit, formatted, when the punishment goes beyond it
     */
    public Optional<String> exceededLimit(JudicatorSender sender, PunishType type, LocalDateTime startedAt, LocalDateTime finishAt) {
        if (!sender.isPlayer() || sender.hasPermission(PunishPermissions.ADMIN.getPermission())) return Optional.empty();
        final String category = category(type);
        if (category == null) return Optional.empty();

        LocalDateTime latestEnd = null;
        for (Map.Entry<String, Map<String, String>> group : limits.entrySet()) {
            final String duration = group.getValue().get(category);
            if (duration == null || !sender.hasExplicitPermission(PERMISSION_PREFIX + group.getKey())) continue;
            final LocalDateTime end = DateTimeOffsetParser.getFinishedAtFromDuration(duration, startedAt);
            if (latestEnd == null || end.isAfter(latestEnd)) latestEnd = end;
        }
        if (latestEnd == null) return Optional.empty();
        if (finishAt != null && !finishAt.isAfter(latestEnd)) return Optional.empty();

        final long millis = Duration.between(startedAt, latestEnd).toMillis();
        return Optional.of(DateTimeOffsetParser.format(judicator.messagesFor(sender), millis));
    }

    static String category(PunishType type) {
        return switch (type) {
            case BAN, TEMPBAN -> "ban";
            case MUTE, TEMPMUTE -> "mute";
            default -> null;
        };
    }
}
