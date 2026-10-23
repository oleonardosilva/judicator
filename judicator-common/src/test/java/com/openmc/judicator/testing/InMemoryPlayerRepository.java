package com.openmc.judicator.testing;

import com.openmc.judicator.players.KnownPlayer;
import com.openmc.judicator.players.repository.PlayerRepository;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Same rules as {@code PlayerRelationalDAO}, kept in memory (seeding is only done by the DAO).
 */
public final class InMemoryPlayerRepository implements PlayerRepository {

    private final Map<UUID, KnownPlayer> rows = new ConcurrentHashMap<>();

    @Override
    public void initialize() {
    }

    @Override
    public synchronized boolean record(UUID uuid, String name, LocalDateTime seenAt) {
        Columns.maxLength(name, 20, "name");
        final KnownPlayer old = rows.put(uuid, new KnownPlayer(uuid, name, seenAt.truncatedTo(ChronoUnit.MILLIS)));
        return old == null || !old.name().equalsIgnoreCase(name);
    }

    @Override
    public Optional<KnownPlayer> findLatestByName(String name) {
        return rows.values().stream().filter(player -> player.name().equalsIgnoreCase(name))
                .max(Comparator.comparing(KnownPlayer::lastSeen));
    }

    @Override
    public Optional<KnownPlayer> findByUuid(UUID uuid) {
        return Optional.ofNullable(rows.get(uuid));
    }

    @Override
    public Map<String, UUID> latestUuidsByName() {
        final Map<String, UUID> names = new java.util.HashMap<>();
        rows.values().stream().sorted(Comparator.comparing(KnownPlayer::lastSeen))
                .forEach(player -> names.put(player.name().toLowerCase(java.util.Locale.ROOT), player.uuid()));
        return names;
    }

    @Override
    public int deleteByUuid(UUID uuid) {
        return rows.remove(uuid) == null ? 0 : 1;
    }

    @Override
    public int seedIfEmpty() {
        return 0;
    }
}
