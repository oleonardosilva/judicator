package com.openmc.judicator.testing;

import com.openmc.judicator.players.PlayerRef;
import com.openmc.judicator.punish.PlayerAddress;
import com.openmc.judicator.punish.data.repository.PlayerAddressRepository;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Same rules as {@code PlayerAddressRelationalDAO} (checked by the repository contract tests), kept in memory.
 */
public final class InMemoryPlayerAddressRepository implements PlayerAddressRepository {

    private final Map<Long, PlayerAddress> rows = new TreeMap<>();
    private long nextId = 1;

    @Override
    public void initialize() {
    }

    @Override
    public synchronized void record(PlayerRef player, String address, LocalDateTime at) {
        boolean updated = false;
        for (PlayerAddress row : List.copyOf(rows.values())) {
            if (!row.hostAddress().equals(address) || !player.matches(row.uuid(), row.nickname())) continue;
            // the DAO keeps the oldest row when two became the same account's
            if (updated) rows.remove(row.id());
            else rows.put(row.id(), new PlayerAddress(row.id(), player.uuid(), player.name(), address, trim(at)));
            updated = true;
        }
        if (!updated) insert(new PlayerAddress(null, player.uuid(), player.name(), address, at));
    }

    @Override
    public int countOf(PlayerRef player) {
        return findAllOf(player).size();
    }

    private void insert(PlayerAddress address) {
        Columns.notNull(address.nickname(), "nickname");
        Columns.maxLength(address.nickname(), 20, "nickname");
        Columns.notNull(address.hostAddress(), "host_address");
        Columns.maxLength(address.hostAddress(), 50, "host_address");
        Columns.notNull(address.lastUsage(), "last_usage");
        final long id = nextId++;
        rows.put(id, new PlayerAddress(id, address.uuid(), address.nickname(), address.hostAddress(), trim(address.lastUsage())));
    }

    @Override
    public List<PlayerAddress> findAllOf(PlayerRef player) {
        return newestFirst(row -> player.matches(row.uuid(), row.nickname()));
    }

    @Override
    public List<PlayerAddress> findByAddress(String address) {
        return newestFirst(row -> row.hostAddress().equals(address));
    }

    private synchronized List<PlayerAddress> newestFirst(Predicate<PlayerAddress> filter) {
        return rows.values().stream().filter(filter)
                .sorted(Comparator.comparing(PlayerAddress::lastUsage).thenComparing(PlayerAddress::id).reversed())
                .toList();
    }

    @Override
    public synchronized int deleteAllOf(PlayerRef player) {
        return removeIf(row -> player.matches(row.uuid(), row.nickname()));
    }

    @Override
    public synchronized int deleteUnusedBefore(LocalDateTime cutoff) {
        return removeIf(row -> row.lastUsage().isBefore(cutoff));
    }

    private int removeIf(Predicate<PlayerAddress> filter) {
        final int before = rows.size();
        rows.values().removeIf(filter);
        return before - rows.size();
    }

    @Override
    public synchronized int fillUuid(PlayerRef account) {
        int changed = 0;
        for (PlayerAddress row : List.copyOf(rows.values())) {
            if (row.uuid() != null || !row.nickname().equalsIgnoreCase(account.name())) continue;
            rows.put(row.id(), new PlayerAddress(row.id(), account.uuid(), row.nickname(), row.hostAddress(), row.lastUsage()));
            changed++;
        }
        return changed;
    }

    @Override
    public synchronized Optional<PlayerAddress> findSame(PlayerAddress address) {
        return rows.values().stream()
                .filter(row -> row.hostAddress().equals(address.hostAddress()))
                .filter(row -> address.uuid() != null ? address.uuid().equals(row.uuid())
                        : row.uuid() == null && row.nickname().equalsIgnoreCase(address.nickname()))
                .findFirst();
    }

    @Override
    public synchronized void save(PlayerAddress address) {
        final Optional<PlayerAddress> stored = findSame(address);
        if (stored.isEmpty()) {
            insert(address);
            return;
        }
        rows.put(stored.get().id(), new PlayerAddress(stored.get().id(), stored.get().uuid(), address.nickname(),
                address.hostAddress(), trim(address.lastUsage())));
    }

    @Override
    public int migrateLegacy(Function<String, UUID> uuidOfName) {
        return 0;
    }

    /**
     * Every row, for tests.
     */
    public synchronized List<PlayerAddress> all() {
        return new ArrayList<>(rows.values());
    }

    /**
     * The names that joined from an address, lower case, newest first, for tests.
     */
    public List<String> accountsOn(String address) {
        return findByAddress(address).stream().map(row -> row.nickname().toLowerCase(Locale.ROOT)).toList();
    }

    private static LocalDateTime trim(LocalDateTime time) {
        return time == null ? null : time.truncatedTo(ChronoUnit.MILLIS);
    }
}
