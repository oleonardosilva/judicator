package com.elless.judicator.testing;

import com.elless.judicator.players.PlayerRef;
import com.elless.judicator.commons.ListQuery;
import com.elless.judicator.punish.Punishment;
import com.elless.judicator.punish.data.repository.PunishmentRepository;
import com.elless.judicator.punish.data.repository.dao.PunishmentRelationalDAO;
import com.elless.judicator.punish.types.PunishType;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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
    public List<Punishment> findAllOf(PlayerRef player) {
        return find(of(player));
    }

    @Override
    public List<Punishment> findAllActiveOf(PlayerRef player, PunishType... types) {
        return find(active(types).and(of(player)));
    }

    @Override
    public List<Punishment> findAllActiveOfAddress(String ipAddress, PunishType... types) {
        return find(active(types).and(p -> p.getIpAddress().map(ip -> ip.equals(ipAddress.toLowerCase())).orElse(false)));
    }

    @Override
    public List<Punishment> findAllActiveOfPlayerOrAddress(PlayerRef player, String ipAddress, PunishType... types) {
        return find(active(types).and(of(player).or(p -> p.getIpAddress().map(ip -> ip.equals(ipAddress.toLowerCase())).orElse(false))));
    }

    @Override
    public Set<String> findActiveScopes() {
        final Set<String> scopes = new HashSet<>();
        find(active(PunishType.values())).forEach(p -> p.getScope().ifPresent(scopes::add));
        return scopes;
    }

    @Override
    public List<Punishment> findAllActiveOfAny(Collection<PlayerRef> players, PunishType... types) {
        if (players.isEmpty()) return List.of();
        return find(active(types).and(p -> players.stream().anyMatch(player -> of(player).test(p))));
    }

    @Override
    public synchronized int fillUuid(PlayerRef account) {
        int changed = 0;
        for (Punishment row : rows.values()) {
            if (row.getPlayerUUID().isEmpty() && row.getNickname().equalsIgnoreCase(account.name())) {
                row.setPlayerUUID(account.uuid());
                changed++;
            }
        }
        return changed;
    }

    private static Predicate<Punishment> of(PlayerRef player) {
        return p -> player.matches(p.getPlayerUUID().orElse(null), p.getNickname());
    }

    private static Predicate<Punishment> byPunisher(PlayerRef staff, LocalDateTime since) {
        return p -> staff.matches(p.getPunisherUuid().orElse(null), p.getPunisher()) && (since == null || !p.getStartedAt().isBefore(since));
    }

    @Override
    public List<Punishment> findAllByUUID(UUID uuid) {
        return find(p -> p.getPlayerUUID().map(uuid::equals).orElse(false));
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
    public synchronized boolean changeEnd(Long id, LocalDateTime finishAt, Long pausedRemaining) {
        final Punishment row = rows.get(id);
        if (row == null || row.isRevoked()) return false;
        row.setFinishAt(trim(finishAt));
        row.setPausedRemaining(pausedRemaining);
        return true;
    }

    @Override
    public synchronized boolean changeReason(Long id, String reason) {
        final Punishment row = rows.get(id);
        if (row == null || row.isRevoked()) return false;
        Columns.notNull(reason, "reason");
        Columns.maxLength(reason, 255, "reason");
        row.setReason(reason);
        return true;
    }

    @Override
    public synchronized int deleteKicksBefore(LocalDateTime cutoff) {
        return removeIf(p -> p.getType() == PunishType.KICK && p.getStartedAt().isBefore(cutoff));
    }

    @Override
    public synchronized int deleteFinishedBefore(LocalDateTime cutoff) {
        return removeIf(p -> p.getType() != PunishType.KICK && (p.getFinishAt().map(end -> end.isBefore(cutoff)).orElse(false)
                || (p.isRevoked() && p.getStartedAt().isBefore(cutoff))));
    }

    @Override
    public synchronized int deleteAllOf(PlayerRef player) {
        return removeIf(of(player));
    }

    private int removeIf(Predicate<Punishment> filter) {
        final int before = rows.size();
        rows.values().removeIf(filter);
        return before - rows.size();
    }

    @Override
    public int countByPunisher(PlayerRef staff, LocalDateTime since) {
        return find(byPunisher(staff, since)).size();
    }

    @Override
    public List<Punishment> findByPunisher(PlayerRef staff, LocalDateTime since, int limit, int offset) {
        return newestFirst(find(byPunisher(staff, since)), limit, offset);
    }

    @Override
    public int countActive(ListQuery query, PunishType... types) {
        return find(active(types).and(matching(query))).size();
    }

    @Override
    public List<Punishment> findActive(ListQuery query, int limit, int offset, PunishType... types) {
        final List<Punishment> rows = find(active(types).and(matching(query)));
        final Comparator<Punishment> byId = Comparator.comparing(Punishment::getId);
        final Comparator<Punishment> order = switch (query.order()) {
            case NEWEST -> Comparator.comparing(Punishment::getStartedAt).thenComparing(byId).reversed();
            case OLDEST -> Comparator.comparing(Punishment::getStartedAt).thenComparing(byId);
            case ENDING -> Comparator.comparing((Punishment p) -> p.getFinishAt().isEmpty())
                    .thenComparing(p -> p.getFinishAt().orElse(null), Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(byId);
            case NAME -> Comparator.comparing((Punishment p) -> p.getNickname().toLowerCase(Locale.ROOT)).thenComparing(byId);
        };
        return rows.stream().sorted(order).skip(offset).limit(limit).toList();
    }

    private static Predicate<Punishment> matching(ListQuery query) {
        return p -> query.matches(p.getNickname(), p.getReason().orElse(null), p.getPunisher());
    }

    @Override
    public List<StaffCount> countByStaffSince(LocalDateTime since) {
        final Map<String, List<Punishment>> groups = find(p -> !p.getStartedAt().isBefore(since)).stream()
                .collect(Collectors.groupingBy(p -> p.getPunisherUuid().map(UUID::toString).orElse(p.getPunisher().toLowerCase(Locale.ROOT))
                        + "|" + p.getType().name()));
        return groups.values().stream()
                .map(rows -> new StaffCount(rows.get(0).getPunisher(), rows.get(0).getType().name(), rows.size()))
                .toList();
    }

    private static List<Punishment> newestFirst(List<Punishment> rows, int limit, int offset) {
        return rows.stream()
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
        Columns.maxLength(p.getScope().orElse(null), 64, "scope");
        Columns.notNull(p.getStartedAt(), "started_at");
        Columns.notNull(p.getType(), "type");
    }

    /**
     * What a database row keeps: times to the millisecond, evidence as one comma-separated text (so a comma inside
     * a link splits it, and blank parts are dropped, as the DAO does), no transient flags.
     */
    private static Punishment copy(Punishment p) {
        final Punishment row = new Punishment(p.getId(), p.getPlayerUUID().orElse(null), p.getReason().orElse(null), p.getPunisher(),
                p.getNickname(), p.getIpAddress().orElse(null), trim(p.getStartedAt()), trim(p.getFinishAt().orElse(null)),
                p.isRevoked(), p.getRevokedReason().orElse(null), evidences(p.getEvidences()), p.getType(),
                p.isPermanent());
        row.setSoft(p.isSoft());
        row.setOnlineOnly(p.isOnlineOnly());
        row.setPausedRemaining(p.getPausedRemaining());
        row.setPunisherUuid(p.getPunisherUuid().orElse(null));
        row.setScope(p.getScope().orElse(null));
        return row;
    }

    private static List<String> evidences(List<String> evidences) {
        return new ArrayList<>(Arrays.stream(String.join(",", evidences).split(",")).filter(e -> !e.isBlank()).toList());
    }

    private static LocalDateTime trim(LocalDateTime time) {
        return time == null ? null : time.truncatedTo(ChronoUnit.MILLIS);
    }
}
