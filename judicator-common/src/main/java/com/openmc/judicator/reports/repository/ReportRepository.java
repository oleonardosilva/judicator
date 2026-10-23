package com.openmc.judicator.reports.repository;

import com.openmc.judicator.players.PlayerRef;
import com.openmc.judicator.reports.Report;
import com.openmc.judicator.reports.ReportState;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The {@code reports} table.
 */
public interface ReportRepository {

    void initialize();

    /**
     * Inserts a new report and gives it its id.
     */
    Report insert(Report report);

    /**
     * Stores the state, assignee and closing of a report, unless another staff member changed it meanwhile.
     *
     * @param expectedState    the state the change was decided on
     * @param expectedAssignee the assignee it was decided on; null when it had none
     * @return false when the report is gone or no longer has that state and assignee
     */
    boolean update(Report report, ReportState expectedState, UUID expectedAssignee);

    /**
     * When a player last made a report, on any server sharing the database.
     */
    Optional<LocalDateTime> lastReportBy(UUID reporter);

    Optional<Report> findById(long id);

    int countByStates(Collection<ReportState> states);

    /**
     * One page of the reports in these states, newest first.
     */
    List<Report> findByStates(Collection<ReportState> states, int limit, int offset);

    /**
     * Whether a player has a report still open about this player.
     */
    boolean hasOpen(UUID reporter, PlayerRef target);

    /**
     * How many reports were made about a player.
     */
    int countAgainst(PlayerRef target);

    /**
     * How many reports a player made or got.
     */
    int countAllOf(PlayerRef player);

    /**
     * Deletes the reports a player made or got ({@code /judicator clear}).
     *
     * @return how many were deleted
     */
    int deleteAllOf(PlayerRef player);

    /**
     * Deletes the reports closed before a moment ({@code retention.reports}).
     *
     * @return how many were deleted
     */
    int deleteClosedBefore(LocalDateTime cutoff);

}
