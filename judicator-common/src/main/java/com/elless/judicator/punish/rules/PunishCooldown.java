package com.elless.judicator.punish.rules;

import com.elless.judicator.Judicator;
import com.elless.judicator.platform.JudicatorSender;
import com.elless.judicator.punish.PunishUtils;
import com.elless.judicator.punish.types.PunishPermissions;
import com.elless.judicator.punish.types.PunishType;

import java.time.Clock;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Keeps two staff members from punishing the same player for the same thing at once: after a ban, mute, warn or kick,
 * the same target cannot get another one of the same category for {@code cooldowns.<category>} seconds. Console and
 * {@code judicator.admin} are not blocked (the warn tree runs its commands from the console), but what they apply
 * still starts the cooldown. Kept in memory only.
 */
public class PunishCooldown {

    public static final String BAN = "ban";
    public static final String MUTE = "mute";
    public static final String WARN = "warn";
    public static final String KICK = "kick";

    private final Judicator judicator;
    private final Map<String, Long> lastApplied = new ConcurrentHashMap<>();
    private Clock clock = Clock.systemUTC();

    public PunishCooldown(Judicator judicator) {
        this.judicator = judicator;
    }

    /**
     * For tests: the clock the cooldowns are measured with.
     */
    public void setClock(Clock clock) {
        this.clock = clock;
    }

    public static String categoryOf(PunishType type) {
        return switch (type) {
            case BAN, TEMPBAN -> BAN;
            case MUTE, TEMPMUTE -> MUTE;
            case KICK -> KICK;
        };
    }

    /**
     * Checks without starting the cooldown (used when a punishment still waits for confirmation).
     *
     * @return false when the sender must wait; the sender was told how long
     */
    public boolean allows(JudicatorSender sender, String category, String target) {
        if (bypasses(sender)) return true;
        final Long last = lastApplied.get(key(category, target));
        return last == null || !blocked(sender, category, target, last, clock.millis());
    }

    /**
     * Checks and starts the cooldown in one step, so two confirmations at the same moment cannot both pass.
     *
     * @param sender who applies the punishment; console and {@code judicator.admin} always pass
     * @return false when the sender must wait; the sender was told how long
     */
    public boolean acquire(JudicatorSender sender, String category, String target) {
        final long now = clock.millis();
        final boolean bypass = bypasses(sender);
        final AtomicBoolean allowed = new AtomicBoolean(true);
        lastApplied.compute(key(category, target), (key, last) -> {
            if (!bypass && last != null && now - last < seconds(category) * 1000L) {
                allowed.set(false);
                return last;
            }
            return now;
        });
        if (!allowed.get()) {
            blocked(sender, category, target, lastApplied.getOrDefault(key(category, target), now), now);
            return false;
        }
        forgetExpired(now);
        return true;
    }

    /**
     * Starts the cooldown for a punishment applied by the console.
     */
    public void record(String category, String target) {
        final long now = clock.millis();
        lastApplied.put(key(category, target), now);
        forgetExpired(now);
    }

    /**
     * Undoes {@link #acquire}/{@link #record} when the punishment could not be stored, so a retry is not blocked by a
     * punishment that does not exist.
     */
    public void release(String category, String target) {
        lastApplied.remove(key(category, target));
    }

    private boolean blocked(JudicatorSender sender, String category, String target, long last, long now) {
        final long cooldownMillis = seconds(category) * 1000L;
        final long elapsed = now - last;
        if (elapsed >= cooldownMillis) return false;
        sender.sendMessage(PunishUtils.format(judicator.messagesFor(sender), Map.of(
                "nickname", target,
                "type", category,
                "elapsed", String.valueOf(elapsed / 1000),
                "remaining", String.valueOf((cooldownMillis - elapsed + 999) / 1000)), "error", "cooldown"));
        return true;
    }

    private void forgetExpired(long now) {
        final long longest = Math.max(Math.max(seconds(BAN), seconds(MUTE)), Math.max(seconds(WARN), seconds(KICK))) * 1000L;
        lastApplied.values().removeIf(last -> now - last >= longest);
    }

    private boolean bypasses(JudicatorSender sender) {
        return !sender.isPlayer() || sender.hasPermission(PunishPermissions.ADMIN.getPermission());
    }

    private long seconds(String category) {
        return Math.max(0, judicator.getConfig().node("cooldowns", category).getLong(0));
    }

    private static String key(String category, String target) {
        return category + ":" + target.toLowerCase(Locale.ROOT);
    }
}
