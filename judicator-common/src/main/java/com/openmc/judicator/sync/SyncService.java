package com.openmc.judicator.sync;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.commons.db.Dialect;
import com.openmc.judicator.platform.PlatformTask;
import com.openmc.judicator.punish.Punishment;
import com.openmc.judicator.punish.data.cache.PunishCache;
import com.openmc.judicator.punish.rules.PunishCooldown;
import com.openmc.judicator.punish.types.PunishStatus;
import com.openmc.judicator.punish.types.PunishType;
import com.openmc.judicator.sync.repository.SyncRepository;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Keeps the instances that share one database in step ({@code sync.enabled}): every change to a punishment leaves a
 * notice with its id in {@code sync_messages}, and every instance reads the new notices every
 * {@code sync.poll-interval} seconds and applies them to its own players (kick, mute cache, alerts, staff
 * notifications), reading the punishment itself from the database. Not reloadable: a restart applies changes.
 * <p>
 * An instance skips its own notices and, when it starts, everything already in the table. Ids up to {@link #LOOKBACK}
 * below the highest one seen are read again, so a notice whose insert committed after a later one is not lost. A
 * notice is deleted about a minute after this instance first saw it.
 */
public class SyncService {

    static final int LOOKBACK = 64;
    /** at most half the time a notice is kept: an instance polling slower would miss notices */
    private static final long MAX_POLL_SECONDS = 30;
    private static final int BATCH = 500;
    private static final long CLEANUP_SECONDS = 60;

    private final Judicator judicator;
    private final SyncRepository repository;
    private final String instanceId = UUID.randomUUID().toString();
    private final Set<Long> seen = new HashSet<>();
    private volatile boolean enabled;
    /** highest id read; -1 until the table could be read */
    private long lastId = -1;
    /** ids up to this one existed when this instance started: never applied */
    private long startId = -1;
    private long cleanupMark = -1;
    private PlatformTask pollTask;
    private PlatformTask cleanupTask;

    public SyncService(Judicator judicator, SyncRepository repository) {
        this.judicator = judicator;
        this.repository = repository;
    }

    public void start() {
        if (!judicator.getConfig().node("sync", "enabled").getBoolean(false)) return;
        if (judicator.getRelationalDBManager() != null && judicator.getRelationalDBManager().getDialect() == Dialect.SQLITE) {
            judicator.getLogger().warn("sync.enabled with a SQLite database: only instances on this machine reading the same file can sync.");
        }
        repository.initialize();
        synchronized (this) {
            repository.maxId().ifPresent(this::startFrom);
        }
        this.enabled = true;
        final long interval = Math.min(MAX_POLL_SECONDS, Math.max(1, judicator.getConfig().node("sync", "poll-interval").getLong(1)));
        this.pollTask = judicator.getPlatform().runAsyncRepeating(this::pollSafely, interval, TimeUnit.SECONDS);
        this.cleanupTask = judicator.getPlatform().runAsyncRepeating(this::cleanupSafely, CLEANUP_SECONDS, TimeUnit.SECONDS);
        judicator.getLogger().info("Cross-server sync enabled: checking for changes every {} s.", interval);
    }

    public void stop() {
        this.enabled = false;
        if (pollTask != null) pollTask.cancel();
        if (cleanupTask != null) cleanupTask.cancel();
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Tells the other instances that a punishment changed. Called right after the change is stored, on the same
     * (database) thread.
     */
    public void publish(SyncKind kind, Long punishmentId) {
        if (!enabled || punishmentId == null) return;
        repository.insert(instanceId, kind.name(), punishmentId);
    }

    private void startFrom(long maxId) {
        this.startId = maxId;
        this.lastId = maxId;
        this.cleanupMark = maxId;
    }

    private void pollSafely() {
        try {
            poll();
        } catch (RuntimeException e) {
            judicator.getLogger().error("Sync: could not read the changes of the other servers", e);
        }
    }

    /**
     * Reads and applies the notices of the other instances that arrived since the last poll.
     */
    public synchronized void poll() {
        if (!enabled) return;
        if (lastId < 0) {
            // the database was down at startup: create the table and start from what is there now
            repository.initialize();
            repository.maxId().ifPresent(this::startFrom);
            return;
        }
        final List<Punishment> revoked = new ArrayList<>();
        for (SyncMessage message : repository.findAfter(Math.max(startId, lastId - LOOKBACK), BATCH)) {
            if (!seen.add(message.id())) continue;
            lastId = Math.max(lastId, message.id());
            if (instanceId.equals(message.sender())) continue;
            final Optional<SyncKind> kind = SyncKind.parse(message.kind());
            if (kind.isEmpty()) continue;
            try {
                receive(kind.get(), message.punishmentId(), revoked);
            } catch (RuntimeException e) {
                judicator.getLogger().error("Sync: could not apply {} of punishment #{}", message.kind(), message.punishmentId(), e);
            }
        }
        seen.removeIf(id -> id <= lastId - LOOKBACK);
        if (!revoked.isEmpty()) judicator.getPunishmentEffects().pardoned(revoked);
    }

    private void receive(SyncKind kind, long id, List<Punishment> revoked) {
        if (kind == SyncKind.REPORTED || kind == SyncKind.REPORT_CHANGED) {
            judicator.getReportService().received(kind, id);
            return;
        }
        final PunishCache cache = judicator.getPunishCache();
        final Optional<Punishment> stored = kind == SyncKind.DELETED ? Optional.empty() : judicator.getPunishService().findById(id);
        switch (kind) {
            case PUNISHED -> stored.ifPresent(this::punished);
            case REVOKED -> {
                cache.forgetMute(id);
                stored.ifPresent(punishment -> {
                    judicator.getPunishCooldown().release(PunishCooldown.categoryOf(punishment.getType()), punishment.getNickname());
                    revoked.add(punishment);
                });
            }
            case CHANGED -> {
                if (stored.isPresent() && stored.get().getStatus() == PunishStatus.ACTIVE) cache.refreshMute(stored.get());
                else cache.forgetMute(id);
            }
            case DELETED -> cache.forgetMute(id);
            default -> {
            }
        }
    }

    /**
     * A punishment applied elsewhere: same cooldown as if it had been applied here, and its effects on the players
     * online here.
     */
    private void punished(Punishment punishment) {
        if (punishment.getType() != PunishType.KICK && punishment.getStatus() != PunishStatus.ACTIVE) return;
        judicator.getPunishCooldown().record(PunishCooldown.categoryOf(punishment.getType()), punishment.getNickname());
        judicator.getPunishmentEffects().punished(punishment);
        // An online-only mute starts paused where the player is not online; their time runs from now on the server they are on.
        if (!punishment.isPaused() || judicator.isProxyMode()) return;
        judicator.getPlatform().getOnlinePlayers().stream()
                .filter(player -> judicator.getPunishService().isOnlinePlayerAffected(player, punishment))
                .findFirst()
                .ifPresent(player -> judicator.getPunishService().resumeOnlineOnlyMutes(judicator.getPlayerDirectory().refOf(player)));
    }

    private void cleanupSafely() {
        try {
            cleanup();
        } catch (RuntimeException e) {
            judicator.getLogger().error("Sync: could not delete old notices", e);
        }
    }

    /**
     * Deletes the notices this instance had already seen at the previous cleanup (a minute ago): every instance had
     * that long to read them.
     * <p>
     * Ids below the highest one seen mean the table is empty, or its counter started again (MySQL 5.7 forgets
     * {@code AUTO_INCREMENT} when the database restarts with the table empty): whatever is there is new, so reading
     * starts again from the first id instead of waiting for ids that will not come.
     */
    public synchronized void cleanup() {
        if (lastId < 0) return;
        final OptionalLong max = repository.maxId();
        if (max.isPresent() && max.getAsLong() < lastId) {
            if (max.getAsLong() > 0) judicator.getLogger().warn("Sync: notice ids restarted at {} (database restarted?); reading them from the start.", max.getAsLong());
            startFrom(0);
            seen.clear();
            return;
        }
        if (cleanupMark > 0) repository.deleteUpTo(cleanupMark);
        cleanupMark = lastId;
    }

}
