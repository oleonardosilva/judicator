package com.openmc.judicator.testing;

import com.openmc.judicator.sync.SyncMessage;
import com.openmc.judicator.sync.repository.SyncRepository;
import com.openmc.judicator.sync.repository.dao.SyncRelationalDAO;

import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.TreeMap;

/**
 * Same rules as {@link SyncRelationalDAO} (checked by the repository contract tests), kept in memory.
 */
public final class InMemorySyncRepository implements SyncRepository {

    private final Map<Long, SyncMessage> rows = new TreeMap<>();
    private long nextId = 1;

    @Override
    public void initialize() {
    }

    @Override
    public synchronized void insert(String sender, String kind, long punishmentId) {
        if (sender == null || kind == null) throw new IllegalArgumentException("sender and kind are NOT NULL");
        if (sender.length() > 36 || kind.length() > 16) throw new IllegalArgumentException("value too long");
        final long id = nextId++;
        rows.put(id, new SyncMessage(id, sender, kind, punishmentId));
    }

    @Override
    public synchronized OptionalLong maxId() {
        return OptionalLong.of(rows.keySet().stream().mapToLong(Long::longValue).max().orElse(0));
    }

    @Override
    public synchronized List<SyncMessage> findAfter(long afterId, int limit) {
        return rows.values().stream().filter(message -> message.id() > afterId).limit(limit).toList();
    }

    @Override
    public synchronized int deleteUpTo(long id) {
        final int before = rows.size();
        rows.keySet().removeIf(key -> key <= id);
        return before - rows.size();
    }

    /**
     * For tests: puts back a notice taken out of the table, keeping its id (an insert that committed late).
     */
    public synchronized void restore(SyncMessage message) {
        rows.put(message.id(), message);
    }

    /**
     * For tests: the id counter starts again after the highest id stored, as MySQL 5.7 does when the database restarts.
     */
    public synchronized void restartIds() {
        nextId = maxId().getAsLong() + 1;
    }

    public synchronized int size() {
        return rows.size();
    }
}
