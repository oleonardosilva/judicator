package com.elless.judicator.testing;

import com.elless.judicator.players.PlayerRef;
import com.elless.judicator.commons.ListQuery;
import com.elless.judicator.reports.Report;
import com.elless.judicator.reports.ReportState;
import com.elless.judicator.reports.repository.ReportRepository;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Same rules as {@code ReportRelationalDAO} (checked by the repository contract tests), kept in memory.
 */
public final class InMemoryReportRepository implements ReportRepository {

    private final Map<Long, Report> rows = new TreeMap<>();
    private long nextId = 1;

    @Override
    public void initialize() {
    }

    @Override
    public synchronized Report insert(Report report) {
        Columns.notNull(report.getReporterName(), "reporter_name");
        Columns.maxLength(report.getReporterName(), 20, "reporter_name");
        Columns.notNull(report.getTargetName(), "target_name");
        Columns.maxLength(report.getTargetName(), 20, "target_name");
        Columns.notNull(report.getReason(), "reason");
        Columns.maxLength(report.getReason(), 255, "reason");
        Columns.maxLength(report.getServer().orElse(null), 64, "server");
        report.setId(nextId++);
        rows.put(report.getId(), copy(report));
        return report;
    }

    @Override
    public synchronized boolean update(Report report, ReportState expectedState, UUID expectedAssignee) {
        final Report row = rows.get(report.getId());
        if (row == null || row.getState() != expectedState) return false;
        if (!Objects.equals(row.getAssigneeUuid().orElse(null), expectedAssignee)) return false;
        Columns.maxLength(report.getCloseComment().orElse(null), 255, "close_comment");
        row.setState(report.getState());
        row.setAssigneeUuid(report.getAssigneeUuid().orElse(null));
        row.setAssigneeName(report.getAssigneeName().orElse(null));
        row.setUpdatedAt(trim(report.getUpdatedAt()));
        row.setClosedAt(trim(report.getClosedAt().orElse(null)));
        row.setCloseComment(report.getCloseComment().orElse(null));
        return true;
    }

    @Override
    public synchronized Optional<LocalDateTime> lastReportBy(UUID reporter) {
        return rows.values().stream().filter(report -> report.getReporterUuid().equals(reporter))
                .map(Report::getCreatedAt).max(Comparator.naturalOrder());
    }

    @Override
    public synchronized Optional<Report> findById(long id) {
        return Optional.ofNullable(rows.get(id)).map(InMemoryReportRepository::copy);
    }

    @Override
    public int countByStates(Collection<ReportState> states, ListQuery query) {
        return find(report -> states.contains(report.getState()) && matches(report, query)).size();
    }

    @Override
    public List<Report> findByStates(Collection<ReportState> states, ListQuery query, int limit, int offset) {
        final Comparator<Report> byId = Comparator.comparing(Report::getId);
        final Comparator<Report> order = switch (query.order()) {
            case NEWEST, ENDING -> Comparator.comparing(Report::getCreatedAt).thenComparing(byId).reversed();
            case OLDEST -> Comparator.comparing(Report::getCreatedAt).thenComparing(byId);
            case NAME -> Comparator.comparing((Report report) -> report.getTargetName().toLowerCase(java.util.Locale.ROOT)).thenComparing(byId);
        };
        return find(report -> states.contains(report.getState()) && matches(report, query)).stream()
                .sorted(order).skip(offset).limit(limit).toList();
    }

    private static boolean matches(Report report, ListQuery query) {
        return query.matches(report.getTargetName(), report.getReporterName(), report.getReason());
    }

    @Override
    public boolean hasOpen(UUID reporter, PlayerRef target) {
        return !find(report -> report.getReporterUuid().equals(reporter) && report.getState().isOpen()
                && target.matches(report.getTargetUuid().orElse(null), report.getTargetName())).isEmpty();
    }

    @Override
    public int countAgainst(PlayerRef target) {
        return find(report -> target.matches(report.getTargetUuid().orElse(null), report.getTargetName())).size();
    }

    @Override
    public int countAllOf(PlayerRef player) {
        return find(of(player)).size();
    }

    @Override
    public synchronized int deleteAllOf(PlayerRef player) {
        return removeIf(of(player));
    }

    private static Predicate<Report> of(PlayerRef player) {
        return report -> (player.uuid() != null ? player.uuid().equals(report.getReporterUuid())
                : report.getReporterName().equalsIgnoreCase(player.name()))
                || player.matches(report.getTargetUuid().orElse(null), report.getTargetName());
    }

    @Override
    public synchronized int deleteClosedBefore(LocalDateTime cutoff) {
        return removeIf(report -> report.getState() == ReportState.CLOSED && report.getClosedAt().map(at -> at.isBefore(cutoff)).orElse(false));
    }

    public synchronized List<Report> all() {
        return find(report -> true);
    }

    private int removeIf(Predicate<Report> filter) {
        final int before = rows.size();
        rows.values().removeIf(filter);
        return before - rows.size();
    }

    private synchronized List<Report> find(Predicate<Report> filter) {
        return rows.values().stream().filter(filter).map(InMemoryReportRepository::copy).toList();
    }

    private static Report copy(Report report) {
        final Report row = new Report();
        row.setId(report.getId());
        row.setReporterUuid(report.getReporterUuid());
        row.setReporterName(report.getReporterName());
        row.setTargetUuid(report.getTargetUuid().orElse(null));
        row.setTargetName(report.getTargetName());
        row.setReason(report.getReason());
        row.setServer(report.getServer().orElse(null));
        row.setState(report.getState());
        row.setAssigneeUuid(report.getAssigneeUuid().orElse(null));
        row.setAssigneeName(report.getAssigneeName().orElse(null));
        row.setCreatedAt(trim(report.getCreatedAt()));
        row.setUpdatedAt(trim(report.getUpdatedAt()));
        row.setClosedAt(trim(report.getClosedAt().orElse(null)));
        row.setCloseComment(report.getCloseComment().orElse(null));
        return row;
    }

    private static LocalDateTime trim(LocalDateTime time) {
        return time == null ? null : time.truncatedTo(ChronoUnit.MILLIS);
    }
}
