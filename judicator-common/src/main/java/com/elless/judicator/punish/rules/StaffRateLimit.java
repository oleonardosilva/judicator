package com.elless.judicator.punish.rules;

import com.elless.judicator.Judicator;
import com.elless.judicator.commons.DateTimeOffsetParser;
import com.elless.judicator.platform.JudicatorPlayer;
import com.elless.judicator.platform.JudicatorSender;
import com.elless.judicator.punish.PunishUtils;
import com.elless.judicator.punish.types.PunishPermissions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.spongepowered.configurate.ConfigurationNode;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Guards against a stolen or abusive staff account ({@code staff-rate-limit}): limits what one staff member applies,
 * whoever the targets are. They wait {@code delay.<category>} seconds between two punishments of a kind and apply at
 * most {@code max.<category>} of a kind within {@code window} seconds; going past it with {@code lock} locks them out
 * of punishing until {@code /staffunlock} (or a restart) and warns the staff, the console and Discord. The console,
 * the API and the explicit {@code judicator.ratelimit.bypass} (not {@code judicator.admin}, the account most worth
 * stealing) are never limited. Kept in memory only, on the server where the staff member types.
 */
public class StaffRateLimit {

    public static final String BYPASS = PunishPermissions.RATELIMIT_BYPASS.getPermission();
    public static final String NOTIFY = PunishPermissions.RATELIMIT_NOTIFY.getPermission();

    private final Judicator judicator;
    private final Map<String, Deque<Long>> applied = new HashMap<>();
    private final Map<UUID, Lock> locks = new LinkedHashMap<>();
    private Clock clock = Clock.systemUTC();

    /**
     * A staff member locked out of punishing: what they went past.
     */
    private record Lock(String name, String category, int count) {
    }

    public StaffRateLimit(Judicator judicator) {
        this.judicator = judicator;
    }

    /**
     * For tests: the clock the delays and the window are measured with.
     */
    public void setClock(Clock clock) {
        this.clock = clock;
    }

    /**
     * Checks without counting a punishment (used when a punishment still waits for confirmation).
     *
     * @return false when the sender may not punish now; the sender was told why
     */
    public boolean allows(JudicatorSender sender, String category) {
        return check(sender, category, false);
    }

    /**
     * Checks and counts the punishment in one step, so two confirmations at the same moment cannot both pass.
     *
     * @return false when the sender may not punish now; the sender was told why
     */
    public boolean acquire(JudicatorSender sender, String category) {
        return check(sender, category, true);
    }

    /**
     * Undoes {@link #acquire} when the punishment could not be applied, so it neither delays nor counts.
     */
    public void release(JudicatorSender sender, String category) {
        final Optional<JudicatorPlayer> player = sender.asPlayer();
        if (player.isEmpty()) return;
        synchronized (this) {
            final Deque<Long> times = applied.get(key(player.get().getUniqueId(), category));
            if (times != null) times.pollLast();
        }
    }

    /**
     * {@code /staffunlock}: lets a locked staff member punish again, with a clean count.
     *
     * @return the name of who was unlocked, empty when nobody of that name was locked
     */
    public synchronized Optional<String> unlock(String name) {
        final Optional<Map.Entry<UUID, Lock>> found = locks.entrySet().stream()
                .filter(entry -> entry.getValue().name().equalsIgnoreCase(name))
                .findFirst();
        found.ifPresent(entry -> {
            locks.remove(entry.getKey());
            applied.keySet().removeIf(key -> key.startsWith(entry.getKey() + ":"));
        });
        return found.map(entry -> entry.getValue().name());
    }

    public synchronized List<String> locked() {
        return locks.values().stream().map(Lock::name).toList();
    }

    public synchronized boolean isLocked(UUID staff) {
        return locks.containsKey(staff);
    }

    private boolean check(JudicatorSender sender, String category, boolean count) {
        final ConfigurationNode config = judicator.getConfig().node("staff-rate-limit");
        if (!config.node("enabled").getBoolean(true) || !sender.isPlayer() || sender.hasExplicitPermission(BYPASS)) return true;
        final JudicatorPlayer staff = sender.asPlayer().orElseThrow();
        final long now = clock.millis();
        final long delayMillis = Math.max(0, config.node("delay", category).getLong(0)) * 1000L;
        final long windowMillis = Math.max(0, config.node("window").getLong(600)) * 1000L;
        final int max = Math.max(0, config.node("max", category).getInt(0));

        final Map<String, String> values = new HashMap<>();
        values.put("type", category);
        final String refusal;
        boolean lockedNow = false;
        synchronized (this) {
            if (locks.containsKey(staff.getUniqueId())) {
                refusal = "locked";
            } else {
                final Deque<Long> times = applied.computeIfAbsent(key(staff.getUniqueId(), category), key -> new ArrayDeque<>());
                final long kept = Math.max(delayMillis, windowMillis);
                while (!times.isEmpty() && now - times.peekFirst() >= kept) times.pollFirst();
                final int inWindow = (int) times.stream().filter(time -> now - time < windowMillis).count();
                if (!times.isEmpty() && now - times.peekLast() < delayMillis) {
                    refusal = "delay";
                    values.put("remaining", seconds(delayMillis - (now - times.peekLast())));
                } else if (max > 0 && windowMillis > 0 && inWindow >= max) {
                    values.put("count", String.valueOf(inWindow));
                    if (config.node("lock").getBoolean(true)) {
                        locks.put(staff.getUniqueId(), new Lock(staff.getName(), category, inWindow));
                        refusal = "locked-now";
                        lockedNow = true;
                    } else {
                        refusal = "too-many";
                        final long oldest = times.stream().filter(time -> now - time < windowMillis).findFirst().orElse(now);
                        values.put("remaining", seconds(windowMillis - (now - oldest)));
                    }
                } else {
                    if (count) times.addLast(now);
                    return true;
                }
            }
        }
        final ConfigurationNode messages = judicator.messagesFor(sender);
        values.put("window", DateTimeOffsetParser.format(messages, windowMillis));
        sender.sendMessage(PunishUtils.format(messages, values, "rate-limit", refusal));
        if (lockedNow) alert(staff, category, Integer.parseInt(values.get("count")), windowMillis);
        return false;
    }

    /**
     * A staff member was just locked: the staff with {@link #NOTIFY} (a click previews {@code /staffrollback} of the
     * window), the console and Discord are told.
     */
    private void alert(JudicatorPlayer staff, String category, int count, long windowMillis) {
        final String period = windowMillis / 1000 + ":s";
        final String rollback = "/staffrollback " + staff.getName() + " " + period;
        judicator.getLogger().warn("{} applied {} {}(s) in {} seconds and was locked out of punishing: see what to undo "
                + "with {}, release with /staffunlock {}", staff.getName(), count, category, windowMillis / 1000, rollback, staff.getName());
        judicator.getStaffNotifier().sendToPermission(NOTIFY, messages -> {
            final Component alert = PunishUtils.format(messages, Map.of(
                    "staff", staff.getName(),
                    "type", category,
                    "count", String.valueOf(count),
                    "window", DateTimeOffsetParser.format(messages, windowMillis)), "rate-limit", "alert");
            return alert.hoverEvent(HoverEvent.showText(Component.text(rollback))).clickEvent(ClickEvent.runCommand(rollback));
        });
        judicator.getDiscordWebhookService().sendStaffLocked(staff.getName(), staff.getUniqueId(), category, count,
                DateTimeOffsetParser.format(judicator.getMessagesConfig(), windowMillis), period);
    }

    private static String seconds(long millis) {
        return String.valueOf((millis + 999) / 1000);
    }

    private static String key(UUID staff, String category) {
        return staff + ":" + category;
    }
}
