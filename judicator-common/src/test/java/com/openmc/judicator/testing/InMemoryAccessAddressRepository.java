package com.openmc.judicator.testing;

import com.openmc.judicator.punish.AccessAddress;
import com.openmc.judicator.punish.data.repository.AccessAddressRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Same rules as the relational DAO: one row per IP, accounts matched by whole (lower-case) name, newest use first.
 */
public final class InMemoryAccessAddressRepository implements AccessAddressRepository {

    private final Map<Long, AccessAddress> rows = new TreeMap<>();
    private long nextId = 1;

    @Override
    public void initialize() {
    }

    @Override
    public synchronized AccessAddress save(AccessAddress address) {
        Columns.notNull(address.getHostAddress(), "host_address");
        Columns.maxLength(address.getHostAddress(), 50, "host_address");
        if (address.getId() == null) address.setId(nextId++);
        rows.put(address.getId(), copy(address));
        return address;
    }

    @Override
    public synchronized Optional<AccessAddress> findById(String id) {
        return Optional.ofNullable(rows.get(Long.parseLong(id))).map(InMemoryAccessAddressRepository::copy);
    }

    @Override
    public synchronized void deleteById(String id) {
        rows.remove(Long.parseLong(id));
    }

    @Override
    public synchronized Optional<AccessAddress> findByIp(String ip) {
        return rows.values().stream().filter(a -> a.getHostAddress().equals(ip)).findFirst().map(InMemoryAccessAddressRepository::copy);
    }

    @Override
    public Optional<AccessAddress> findByUsername(String username) {
        return findAllByUsername(username).stream().findFirst();
    }

    @Override
    public synchronized List<AccessAddress> findAllByUsername(String username) {
        return new ArrayList<>(rows.values().stream()
                .filter(a -> a.contains(username))
                .sorted(Comparator.comparing(AccessAddress::getLastUsage).reversed())
                .map(InMemoryAccessAddressRepository::copy).toList());
    }

    @Override
    public synchronized int deleteUnusedBefore(LocalDateTime cutoff) {
        final int before = rows.size();
        rows.values().removeIf(a -> a.getLastUsage().isBefore(cutoff));
        return before - rows.size();
    }

    private static AccessAddress copy(AccessAddress a) {
        return new AccessAddress(a.getId(), a.getHostAddress(), a.getAccounts(), a.getLastUsage());
    }
}
