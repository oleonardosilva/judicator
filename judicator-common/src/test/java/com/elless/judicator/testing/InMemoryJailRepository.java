package com.elless.judicator.testing;

import com.elless.judicator.jails.EscapeRoute;
import com.elless.judicator.jails.Jail;
import com.elless.judicator.jails.repository.JailRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The {@code jails} table in memory, with its limits (the names and places as the DAO stores them).
 */
public final class InMemoryJailRepository implements JailRepository {

    private final Map<String, Jail> rows = new TreeMap<>();

    @Override
    public void initialize() {
    }

    @Override
    public synchronized List<Jail> findAll() {
        return new ArrayList<>(rows.values());
    }

    @Override
    public synchronized boolean save(Jail jail, String author) {
        Columns.notNull(jail.name(), "name");
        Columns.maxLength(jail.name(), 32, "name");
        Columns.maxLength(jail.server(), 32, "server");
        Columns.maxLength(jail.spawn().encode(), 160, "spawn");
        Columns.maxLength(jail.area().encode(), 160, "area");
        Columns.maxLength(EscapeRoute.encode(jail.escapes()), 2000, "escapes");
        rows.put(jail.name(), jail);
        return true;
    }

    @Override
    public synchronized boolean delete(String name) {
        return rows.remove(name) != null;
    }
}
