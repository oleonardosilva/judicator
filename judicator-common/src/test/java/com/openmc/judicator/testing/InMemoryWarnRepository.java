package com.openmc.judicator.testing;

import com.openmc.judicator.punish.data.repository.PunishmentRepository;
import com.openmc.judicator.warns.Warn;
import com.openmc.judicator.warns.repository.WarnRepository;
import com.openmc.judicator.warns.repository.dao.WarnRelationalDAO;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

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
    public List<PunishmentRepository.StaffCount> countByStaffSince(LocalDateTime since) {
        final Map<String, List<Warn>> byStaff = find(w -> !w.getStartedAt().isBefore(since)).stream()
                .collect(Collectors.groupingBy(w -> w.getPunisher().toLowerCase(Locale.ROOT)));
        return byStaff.values().stream()
                .map(warns -> new PunishmentRepository.StaffCount(warns.get(0).getPunisher(), "WARN", warns.size()))
                .toList();
    }

    @Override
    public Long countActiveWarns(String username) {
        final LocalDateTime now = LocalDateTime.now();
        return (long) find(w -> w.getNickname().equalsIgnoreCase(username)
                && (w.isPermanent() || w.getFinishAt().map(end -> end.isAfter(now)).orElse(false))).size();
    }

    @Override
    public List<Warn> findByPunisherSince(String punisher, LocalDateTime since) {
        return find(w -> w.getPunisher().equalsIgnoreCase(punisher) && !w.getStartedAt().isBefore(since)).stream()
                .sorted(Comparator.comparing(Warn::getStartedAt).thenComparing(Warn::getId).reversed())
                .toList();
    }

    @Override
    public synchronized int deleteFinishedBefore(LocalDateTime cutoff) {
        final int before = rows.size();
        rows.values().removeIf(w -> !w.isPermanent() && w.getFinishAt().map(end -> end.isBefore(cutoff)).orElse(false));
        return before - rows.size();
    }

    @Override
    public synchronized int deleteAllOf(String username, UUID uuid) {
        final int before = rows.size();
        rows.values().removeIf(w -> w.getNickname().equalsIgnoreCase(username)
                || (uuid != null && w.getPlayerUUID().map(uuid::equals).orElse(false)));
        return before - rows.size();
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
