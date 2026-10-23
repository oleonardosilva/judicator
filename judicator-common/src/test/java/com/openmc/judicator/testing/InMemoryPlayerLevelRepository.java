package com.openmc.judicator.testing;

import com.openmc.judicator.players.PlayerRef;
import com.openmc.judicator.punish.hierarchy.PlayerLevelRepository;

import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Same rules as {@code PlayerLevelRelationalDAO}: one row per name, the newest row of an account wins.
 */
public final class InMemoryPlayerLevelRepository implements PlayerLevelRepository {

    private record Row(String nickname, UUID uuid, int level, long updated) {
    }

    private final Map<String, Row> levels = new ConcurrentHashMap<>();
    private final AtomicLong clock = new AtomicLong();

    @Override
    public void initialize() {
    }

    @Override
    public synchronized void saveLevel(String nickname, UUID uuid, int level) {
        final String key = nickname.toLowerCase(Locale.ROOT);
        if (uuid != null) levels.values().removeIf(row -> uuid.equals(row.uuid()) && !row.nickname().equals(key));
        levels.put(key, new Row(key, uuid, level, clock.incrementAndGet()));
    }

    @Override
    public synchronized boolean delete(PlayerRef player) {
        return levels.values().removeIf(row -> player.matches(row.uuid(), row.nickname()));
    }

    @Override
    public OptionalInt findLevel(PlayerRef player) {
        return levels.values().stream()
                .filter(row -> player.matches(row.uuid(), row.nickname()))
                .max(Comparator.comparingLong(Row::updated))
                .map(row -> OptionalInt.of(row.level()))
                .orElse(OptionalInt.empty());
    }
}
