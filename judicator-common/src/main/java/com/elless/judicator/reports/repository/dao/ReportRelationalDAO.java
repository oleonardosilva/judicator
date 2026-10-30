package com.elless.judicator.reports.repository.dao;

import com.elless.judicator.commons.ListQuery;
import com.elless.judicator.commons.db.Dialect;
import com.elless.judicator.commons.db.IdentitySql;
import com.elless.judicator.commons.db.RelationalDBManager;
import com.elless.judicator.commons.db.SqlTime;
import com.elless.judicator.players.PlayerRef;
import com.elless.judicator.reports.Report;
import com.elless.judicator.reports.ReportState;
import com.elless.judicator.reports.repository.ReportRepository;
import org.slf4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public class ReportRelationalDAO implements ReportRepository {

    private final RelationalDBManager manager;
    private final Logger logger;

    public ReportRelationalDAO(RelationalDBManager manager, Logger logger) {
        this.manager = manager;
        this.logger = logger;
    }

    @Override
    public void initialize() {
        try (Connection connection = manager.getDataSource().getConnection()) {
            final Dialect dialect = manager.getDialect();
            final String id = dialect == Dialect.POSTGRESQL ? "id BIGSERIAL PRIMARY KEY"
                    : dialect == Dialect.SQLITE ? "id INTEGER PRIMARY KEY AUTOINCREMENT" : "id BIGINT AUTO_INCREMENT PRIMARY KEY";
            final String time = dialect == Dialect.MYSQL ? "DATETIME" : "TIMESTAMP";
            final String sql = """
                    CREATE TABLE IF NOT EXISTS {reports} (
                        %s,
                        reporter_uuid VARCHAR(36) NOT NULL,
                        reporter_name VARCHAR(20) NOT NULL,
                        target_uuid VARCHAR(36),
                        target_name VARCHAR(20) NOT NULL,
                        lower_target VARCHAR(20) NOT NULL,
                        reason VARCHAR(255) NOT NULL,
                        server VARCHAR(64),
                        state VARCHAR(16) NOT NULL,
                        assignee_uuid VARCHAR(36),
                        assignee_name VARCHAR(20),
                        created_at %s NOT NULL,
                        updated_at %s NOT NULL,
                        closed_at %s,
                        close_comment VARCHAR(255)
                    )%s
                    """.formatted(id, time, time, time, dialect == Dialect.MYSQL ? " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4" : "");
            try (PreparedStatement statement = connection.prepareStatement(manager.sql(sql))) {
                statement.execute();
            }
            manager.ensureIndex(connection, "reports", "state");
            manager.ensureIndex(connection, "reports", "target_uuid");
            manager.ensureIndex(connection, "reports", "lower_target");
            manager.ensureIndex(connection, "reports", "reporter_uuid");
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
    }

    @Override
    public Report insert(Report report) {
        try (Connection connection = manager.getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(manager.sql("""
                     INSERT INTO {reports} (reporter_uuid, reporter_name, target_uuid, target_name, lower_target, reason, server,
                         state, assignee_uuid, assignee_name, created_at, updated_at, closed_at, close_comment)
                     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                     """), Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, report.getReporterUuid().toString());
            statement.setString(2, report.getReporterName());
            statement.setString(3, report.getTargetUuid().map(UUID::toString).orElse(null));
            statement.setString(4, report.getTargetName());
            statement.setString(5, report.getTargetName().toLowerCase(Locale.ROOT));
            statement.setString(6, report.getReason());
            statement.setString(7, report.getServer().orElse(null));
            bindChange(statement, 8, report);
            statement.setTimestamp(11, SqlTime.of(report.getCreatedAt()));
            statement.setTimestamp(12, SqlTime.of(report.getUpdatedAt()));
            statement.setTimestamp(13, SqlTime.of(report.getClosedAt().orElse(null)));
            statement.setString(14, report.getCloseComment().orElse(null));
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) report.setId(keys.getLong(1));
            }
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
        return report;
    }

    /**
     * state, assignee_uuid, assignee_name from {@code index}.
     */
    private static void bindChange(PreparedStatement statement, int index, Report report) throws SQLException {
        statement.setString(index, report.getState().name());
        statement.setString(index + 1, report.getAssigneeUuid().map(UUID::toString).orElse(null));
        statement.setString(index + 2, report.getAssigneeName().orElse(null));
    }

    @Override
    public boolean update(Report report, ReportState expectedState, UUID expectedAssignee) {
        try (Connection connection = manager.getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(manager.sql("""
                     UPDATE {reports} SET state = ?, assignee_uuid = ?, assignee_name = ?, updated_at = ?, closed_at = ?, close_comment = ?
                     WHERE id = ? AND state = ? AND\s""" + (expectedAssignee == null ? "assignee_uuid IS NULL" : "assignee_uuid = ?")))) {
            bindChange(statement, 1, report);
            statement.setTimestamp(4, SqlTime.of(report.getUpdatedAt()));
            statement.setTimestamp(5, SqlTime.of(report.getClosedAt().orElse(null)));
            statement.setString(6, report.getCloseComment().orElse(null));
            statement.setLong(7, report.getId());
            statement.setString(8, expectedState.name());
            if (expectedAssignee != null) statement.setString(9, expectedAssignee.toString());
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
            return false;
        }
    }

    @Override
    public Optional<LocalDateTime> lastReportBy(UUID reporter) {
        try (Connection connection = manager.getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(manager.sql(
                     "SELECT created_at FROM {reports} WHERE reporter_uuid = ? ORDER BY created_at DESC LIMIT 1"))) {
            statement.setString(1, reporter.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.ofNullable(SqlTime.read(resultSet, "created_at")) : Optional.empty();
            }
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
            return Optional.empty();
        }
    }

    @Override
    public Optional<Report> findById(long id) {
        return query("SELECT * FROM {reports} WHERE id = ?", statement -> statement.setLong(1, id)).stream().findFirst();
    }

    private static String inStates(Collection<ReportState> states) {
        return "state IN (" + states.stream().map(state -> "?").collect(Collectors.joining(", ")) + ")";
    }

    private static int bindStates(PreparedStatement statement, int index, Collection<ReportState> states) throws SQLException {
        for (ReportState state : states) statement.setString(index++, state.name());
        return index;
    }

    @Override
    public int countByStates(Collection<ReportState> states, ListQuery query) {
        if (states.isEmpty()) return 0;
        try (Connection connection = manager.getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(manager.sql("SELECT COUNT(*) FROM {reports} WHERE "
                     + inStates(states) + searching(query)))) {
            bindSearch(statement, bindStates(statement, 1, states), query);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt(1) : 0;
            }
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
            return 0;
        }
    }

    @Override
    public List<Report> findByStates(Collection<ReportState> states, ListQuery query, int limit, int offset) {
        if (states.isEmpty()) return List.of();
        final String order = switch (query.order()) {
            case NEWEST, ENDING -> " ORDER BY created_at DESC, id DESC";
            case OLDEST -> " ORDER BY created_at ASC, id ASC";
            case NAME -> " ORDER BY lower_target ASC, id ASC";
        };
        return query("SELECT * FROM {reports} WHERE " + inStates(states) + searching(query) + order + " LIMIT ? OFFSET ?", statement -> {
            final int next = bindSearch(statement, bindStates(statement, 1, states), query);
            statement.setInt(next, limit);
            statement.setInt(next + 1, offset);
        });
    }

    private static String searching(ListQuery query) {
        return query.searches()
                ? " AND (lower_target LIKE ? ESCAPE '!' OR LOWER(reporter_name) LIKE ? ESCAPE '!' OR LOWER(reason) LIKE ? ESCAPE '!')"
                : "";
    }

    /**
     * @return the index of the next parameter
     */
    private static int bindSearch(PreparedStatement statement, int index, ListQuery query) throws SQLException {
        if (!query.searches()) return index;
        for (int i = 0; i < 3; i++) statement.setString(index++, query.likePattern());
        return index;
    }

    @Override
    public boolean hasOpen(UUID reporter, PlayerRef target) {
        final List<ReportState> open = List.of(ReportState.OPEN, ReportState.ASSIGNED);
        return !query("SELECT * FROM {reports} WHERE reporter_uuid = ? AND " + inStates(open) + " AND "
                + IdentitySql.of(target, "target_uuid", "lower_target"), statement -> {
            statement.setString(1, reporter.toString());
            IdentitySql.bind(statement, bindStates(statement, 2, open), target);
        }).isEmpty();
    }

    @Override
    public int countAgainst(PlayerRef target) {
        try (Connection connection = manager.getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(manager.sql("SELECT COUNT(*) FROM {reports} WHERE "
                     + IdentitySql.of(target, "target_uuid", "lower_target")))) {
            IdentitySql.bind(statement, 1, target);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt(1) : 0;
            }
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
            return 0;
        }
    }

    @Override
    public int countAllOf(PlayerRef player) {
        try (Connection connection = manager.getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(manager.sql("SELECT COUNT(*) FROM {reports} WHERE " + ofPlayer(player)))) {
            bindPlayer(statement, player);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt(1) : 0;
            }
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
            return 0;
        }
    }

    @Override
    public int deleteAllOf(PlayerRef player) {
        return execute("DELETE FROM {reports} WHERE " + ofPlayer(player), statement -> bindPlayer(statement, player));
    }

    /**
     * The reports a player made (by UUID; a reporter always has one) or got.
     */
    private static String ofPlayer(PlayerRef player) {
        final String reporter = player.uuid() != null ? "reporter_uuid = ?" : "LOWER(reporter_name) = ?";
        return "(" + reporter + " OR " + IdentitySql.of(player, "target_uuid", "lower_target") + ")";
    }

    private static void bindPlayer(PreparedStatement statement, PlayerRef player) throws SQLException {
        statement.setString(1, player.uuid() != null ? player.uuid().toString() : player.lowerName());
        IdentitySql.bind(statement, 2, player);
    }

    @Override
    public int deleteClosedBefore(LocalDateTime cutoff) {
        return execute("DELETE FROM {reports} WHERE state = ? AND closed_at < ?", statement -> {
            statement.setString(1, ReportState.CLOSED.name());
            statement.setTimestamp(2, SqlTime.of(cutoff));
        });
    }

    private interface Binder {
        void bind(PreparedStatement statement) throws SQLException;
    }

    private List<Report> query(String sql, Binder binder) {
        try (Connection connection = manager.getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(manager.sql(sql))) {
            binder.bind(statement);
            try (ResultSet resultSet = statement.executeQuery()) {
                final List<Report> reports = new ArrayList<>();
                while (resultSet.next()) reports.add(load(resultSet));
                return reports;
            }
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
            return List.of();
        }
    }

    private int execute(String sql, Binder binder) {
        try (Connection connection = manager.getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(manager.sql(sql))) {
            binder.bind(statement);
            return statement.executeUpdate();
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
            return 0;
        }
    }

    private static Report load(ResultSet rs) throws SQLException {
        final Report report = new Report();
        report.setId(rs.getLong("id"));
        report.setReporterUuid(UUID.fromString(rs.getString("reporter_uuid")));
        report.setReporterName(rs.getString("reporter_name"));
        report.setTargetUuid(uuid(rs.getString("target_uuid")));
        report.setTargetName(rs.getString("target_name"));
        report.setReason(rs.getString("reason"));
        report.setServer(rs.getString("server"));
        report.setState(ReportState.parse(rs.getString("state")).orElse(ReportState.OPEN));
        report.setAssigneeUuid(uuid(rs.getString("assignee_uuid")));
        report.setAssigneeName(rs.getString("assignee_name"));
        report.setCreatedAt(SqlTime.read(rs, "created_at"));
        report.setUpdatedAt(SqlTime.read(rs, "updated_at"));
        report.setClosedAt(SqlTime.read(rs, "closed_at"));
        report.setCloseComment(rs.getString("close_comment"));
        return report;
    }

    private static UUID uuid(String text) {
        return text == null || text.isBlank() ? null : UUID.fromString(text);
    }

}
