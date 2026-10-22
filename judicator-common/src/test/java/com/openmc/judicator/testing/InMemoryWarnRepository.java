package com.openmc.judicator.testing;

import com.openmc.judicator.warns.Warn;
import com.openmc.judicator.warns.repository.WarnRepository;
import com.openmc.judicator.warns.repository.dao.WarnRelationalDAO;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Same rules as {@link WarnRelationalDAO}, kept in memory.
 */
public final class InMemoryWarnRepository implements WarnRepository {

    private final Map<Long, Warn> rows = new TreeMap<>();
    private long nextId = 1;

    @Override
    public void initialize() {
    }

    @Override
    public synchronized Warn save(Warn warn) {
        check(warn);
        if (warn.getId() == null) warn.setId(nextId++);
        rows.put(warn.getId(), copy(warn));
        return warn;
    }

    @Override
    public synchronized Optional<Warn> findById(Long id) {
        return Optional.ofNullable(rows.get(id)).map(InMemoryWarnRepository::copy);
    }

    @Override
    public synchronized void deleteById(Long id) {
        rows.remove(id);
    }

    @Override
    public List<Warn> findAllByUsername(String username) {
        return find(w -> w.getNickname().equalsIgnoreCase(username));
    }

    @Override
    public Long countActiveWarns(String username) {
        final LocalDateTime now = LocalDateTime.now();
        return (long) find(w -> w.getNickname().equalsIgnoreCase(username)
                && (w.isPermanent() || w.getFinishAt().map(end -> end.isAfter(now)).orElse(false))).size();
    }

    @Override
    public List<Warn> findAllByUUID(UUID uuid) {
        return find(w -> w.getPlayerUUID().map(uuid::equals).orElse(false));
    }

    @Override
    public Set<String> findAllImportKeys() {
        final Set<String> keys = new HashSet<>();
        find(w -> true).forEach(w -> keys.add(WarnRelationalDAO.importKey(
                w.getPlayerUUID().map(UUID::toString).orElse(null), w.getNickname(), w.getStartedAt())));
        return keys;
    }

    @Override
    public synchronized int insertAll(List<Warn> warns) {
        warns.forEach(InMemoryWarnRepository::check);
        warns.forEach(w -> {
            final Warn row = copy(w);
            row.setId(nextId++);
            rows.put(row.getId(), row);
        });
        return warns.size();
    }

    public synchronized List<Warn> all() {
        return find(w -> true);
    }

    private synchronized List<Warn> find(Predicate<Warn> filter) {
        return new ArrayList<>(rows.values().stream().filter(filter).map(InMemoryWarnRepository::copy).toList());
    }

    /**
     * The constraints of the warns table (see WarnRelationalDAO#initialize).
     */
    private static void check(Warn w) {
        Columns.notNull(w.getPlayerUUID().orElse(null), "player_uuid");
        Columns.notNull(w.getReason(), "reason");
        Columns.maxLength(w.getReason(), 255, "reason");
        Columns.notNull(w.getPunisher(), "punisher");
        Columns.maxLength(w.getPunisher(), 20, "punisher");
        Columns.notNull(w.getNickname(), "nickname (the DAO lower-cases it)");
        Columns.maxLength(w.getNickname(), 20, "nickname");
        Columns.notNull(w.getStartedAt(), "started_at");
    }

    private static Warn copy(Warn w) {
        return new Warn(w.getId(), w.getPlayerUUID().orElse(null), w.getReason(), w.getPunisher(), w.getNickname(),
                w.getStartedAt(), w.getFinishAt().orElse(null), w.isPermanent());
    }
}
