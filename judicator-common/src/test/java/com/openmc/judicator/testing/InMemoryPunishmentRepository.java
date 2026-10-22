package com.openmc.judicator.testing;

import com.openmc.judicator.punish.Punishment;
import com.openmc.judicator.punish.data.repository.PunishmentRepository;
import com.openmc.judicator.punish.data.repository.dao.PunishmentRelationalDAO;
import com.openmc.judicator.punish.types.PunishType;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Same rules as {@link PunishmentRelationalDAO} (checked by the repository contract tests), kept in memory.
 * Stores copies, like a database: changing a returned object does not change what is stored.
 */
public final class InMemoryPunishmentRepository implements PunishmentRepository {

    private final Map<Long, Punishment> rows = new TreeMap<>();
    private long nextId = 1;

    @Override
    public void initialize() {
    }

    @Override
    public synchronized Punishment save(Punishment punishment) {
        check(punishment);
        if (punishment.getId() == null) punishment.setId(nextId++);
        rows.put(punishment.getId(), copy(punishment));
        return punishment;
    }

    @Override
    public synchronized Optional<Punishment> findById(Long id) {
        return Optional.ofNullable(rows.get(id)).map(InMemoryPunishmentRepository::copy);
    }

    @Override
    public synchronized void deleteById(Long id) {
        rows.remove(id);
    }

    @Override
    public List<Punishment> findAllByUsername(String username) {
        return find(p -> p.getNickname().equalsIgnoreCase(username));
    }

    @Override
    public List<Punishment> findAllActiveByUsernameAndTypes(String username, PunishType... types) {
        return find(active(types).and(p -> p.getNickname().equalsIgnoreCase(username)));
    }

    @Override
    public List<Punishment> findAllActiveByUsernameOrIpAndTypes(String username, String ipAddress, PunishType... types) {
        return find(active(types).and(p -> p.getNickname().equalsIgnoreCase(username)
                || p.getIpAddress().map(ip -> ip.equals(ipAddress.toLowerCase())).orElse(false)));
    }

    @Override
    public List<Punishment> findAllByUUID(UUID uuid) {
        return find(p -> p.getPlayerUUID().map(uuid::equals).orElse(false));
    }

    @Override
    public List<Punishment> findAllActiveByUUIDAndTypes(UUID uuid, PunishType... types) {
        return find(active(types).and(p -> p.getPlayerUUID().map(uuid::equals).orElse(false)));
    }

    @Override
    public synchronized boolean revoke(Long id, String reason) {
        final Punishment row = rows.get(id);
        if (row == null || row.isRevoked()) return false;
        Columns.maxLength(reason, 100, "revoked_reason");
        row.setRevoked(true);
        row.setRevokedReason(reason);
        return true;
    }

    @Override
    public int countByPunisher(String punisher) {
        return find(p -> p.getPunisher().equalsIgnoreCase(punisher)).size();
    }

    @Override
    public List<Punishment> findByPunisher(String punisher, int limit, int offset) {
        return find(p -> p.getPunisher().equalsIgnoreCase(punisher)).stream()
                .sorted(Comparator.comparing(Punishment::getStartedAt).thenComparing(Punishment::getId).reversed())
                .skip(offset).limit(limit).toList();
    }

    @Override
    public Set<String> findAllImportKeys() {
        final Set<String> keys = new HashSet<>();
        find(p -> true).forEach(p -> keys.add(PunishmentRelationalDAO.importKey(p)));
        return keys;
    }

    @Override
    public synchronized int insertAll(List<Punishment> punishments) {
        punishments.forEach(InMemoryPunishmentRepository::check);
        punishments.forEach(p -> {
            final Punishment row = copy(p);
            row.setId(nextId++);
            rows.put(row.getId(), row);
        });
        return punishments.size();
    }

    public synchronized List<Punishment> all() {
        return find(p -> true);
    }

    private static Predicate<Punishment> active(PunishType... types) {
        final Set<PunishType> wanted = Set.of(types);
        final LocalDateTime now = LocalDateTime.now();
        return p -> !p.isRevoked() && wanted.contains(p.getType())
                && p.getFinishAt().map(end -> end.isAfter(now)).orElse(true);
    }

    private synchronized List<Punishment> find(Predicate<Punishment> filter) {
        return new ArrayList<>(rows.values().stream().filter(filter).map(InMemoryPunishmentRepository::copy).toList());
    }

    /**
     * The constraints of the punishments table (see PunishmentRelationalDAO#initialize).
     */
    private static void check(Punishment p) {
        Columns.notNull(p.getReason().orElse(null), "reason");
        Columns.maxLength(p.getReason().orElse(null), 255, "reason");
        Columns.notNull(p.getPunisher(), "punisher");
        Columns.maxLength(p.getPunisher(), 20, "punisher");
        Columns.notNull(p.getNickname(), "nickname (the DAO lower-cases it)");
        Columns.maxLength(p.getNickname(), 20, "nickname");
        Columns.maxLength(p.getIpAddress().orElse(null), 45, "ip_address");
        Columns.maxLength(String.join(",", p.getEvidences()), 255, "evidences");
        Columns.maxLength(p.getRevokedReason().orElse(null), 100, "revoked_reason");
        Columns.notNull(p.getStartedAt(), "started_at");
        Columns.notNull(p.getType(), "type");
    }

    /**
     * What a database row keeps: times to the millisecond, evidence as one comma-separated text (so a comma inside
     * a link splits it, and blank parts are dropped, as the DAO does), no transient flags.
     */
    private static Punishment copy(Punishment p) {
        return new Punishment(p.getId(), p.getPlayerUUID().orElse(null), p.getReason().orElse(null), p.getPunisher(),
                p.getNickname(), p.getIpAddress().orElse(null), trim(p.getStartedAt()), trim(p.getFinishAt().orElse(null)),
                p.isRevoked(), p.getRevokedReason().orElse(null), evidences(p.getEvidences()), p.getType(),
                p.isPermanent());
    }

    private static List<String> evidences(List<String> evidences) {
        return new ArrayList<>(Arrays.stream(String.join(",", evidences).split(",")).filter(e -> !e.isBlank()).toList());
    }

    private static LocalDateTime trim(LocalDateTime time) {
        return time == null ? null : time.truncatedTo(ChronoUnit.MILLIS);
    }
}
