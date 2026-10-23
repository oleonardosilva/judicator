package com.openmc.judicator.testing;

import com.openmc.judicator.punish.hierarchy.PlayerLevelRepository;

import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryPlayerLevelRepository implements PlayerLevelRepository {

    private final Map<String, Integer> levels = new ConcurrentHashMap<>();

    @Override
    public void initialize() {
    }

    @Override
    public void saveLevel(String nickname, UUID uuid, int level) {
        levels.put(nickname.toLowerCase(Locale.ROOT), level);
    }

    @Override
    public boolean delete(String nickname) {
        return levels.remove(nickname.toLowerCase(Locale.ROOT)) != null;
    }

    @Override
    public OptionalInt findLevel(String nickname) {
        final Integer level = levels.get(nickname.toLowerCase(Locale.ROOT));
        return level == null ? OptionalInt.empty() : OptionalInt.of(level);
    }
}
