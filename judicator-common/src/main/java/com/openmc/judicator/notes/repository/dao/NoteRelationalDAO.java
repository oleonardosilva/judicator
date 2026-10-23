package com.openmc.judicator.notes.repository.dao;

import com.openmc.judicator.commons.db.Dialect;
import com.openmc.judicator.commons.db.RelationalDBManager;
import com.openmc.judicator.commons.db.SchemaUtil;
import com.openmc.judicator.commons.db.SqlTime;
import com.openmc.judicator.notes.Note;
import com.openmc.judicator.notes.repository.NoteRepository;
import org.slf4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class NoteRelationalDAO implements NoteRepository {

    private static final String INSERT = "INSERT INTO notes (player_uuid, nickname, lower_nickname, author, content, created_at) VALUES (?, ?, ?, ?, ?, ?)";

    private final RelationalDBManager manager;
    private final Logger logger;

    public NoteRelationalDAO(RelationalDBManager manager, Logger logger) {
        this.manager = manager;
        this.logger = logger;
    }

    @Override
    public void initialize() {
        try (Connection connection = manager.getDataSource().getConnection()) {
            final Dialect dialect = manager.getDialect();
            final String sql;
            if (dialect == Dialect.POSTGRESQL) {
                sql = """
                        CREATE TABLE IF NOT EXISTS notes (
                            id BIGSERIAL PRIMARY KEY,
                            player_uuid VARCHAR(36),
                            nickname VARCHAR(20) NOT NULL,
                            lower_nickname VARCHAR(20) NOT NULL,
                            author VARCHAR(20) NOT NULL,
                            content VARCHAR(255) NOT NULL,
                            created_at TIMESTAMP NOT NULL
                        );
                        """;
            } else if (dialect == Dialect.SQLITE) {
                sql = """
                        CREATE TABLE IF NOT EXISTS notes (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            player_uuid VARCHAR(36),
                            nickname VARCHAR(20) NOT NULL,
                            lower_nickname VARCHAR(20) NOT NULL,
                            author VARCHAR(20) NOT NULL,
                            content VARCHAR(255) NOT NULL,
                            created_at TIMESTAMP NOT NULL
                        );
                        """;
            } else {
                sql = """
                        CREATE TABLE IF NOT EXISTS notes (
                            id BIGINT AUTO_INCREMENT PRIMARY KEY,
                            player_uuid VARCHAR(36),
                            nickname VARCHAR(20) NOT NULL,
                            lower_nickname VARCHAR(20) NOT NULL,
                            author VARCHAR(20) NOT NULL,
                            content VARCHAR(255) NOT NULL,
                            created_at DATETIME NOT NULL
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                        """;
            }
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.execute();
            }
            SchemaUtil.ensureIndexExists(connection, "notes", "idx_notes_lower_nickname",
                    "CREATE INDEX idx_notes_lower_nickname ON notes(lower_nickname)");
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
    }

    @Override
    public Note save(Note note) {
        try (Connection connection = manager.getDataSource().getConnection()) {
            if (note.getId() != null) {
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE notes SET player_uuid = ?, nickname = ?, lower_nickname = ?, author = ?, content = ?, created_at = ? WHERE id = ?")) {
                    bind(statement, note);
                    statement.setLong(7, note.getId());
                    statement.executeUpdate();
                }
                return note;
            }
            try (PreparedStatement statement = connection.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {
                bind(statement, note);
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (keys.next()) note.setId(keys.getLong(1));
                }
            }
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
        return note;
    }

    private static void bind(PreparedStatement statement, Note note) throws SQLException {
        statement.setString(1, note.getPlayerUUID().map(UUID::toString).orElse(null));
        statement.setString(2, note.getNickname());
        statement.setString(3, note.getNickname().toLowerCase());
        statement.setString(4, note.getAuthor());
        statement.setString(5, note.getContent());
        statement.setTimestamp(6, SqlTime.of(note.getCreatedAt()));
    }

    @Override
    public Optional<Note> findById(Long id) {
        try (Connection connection = manager.getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM notes WHERE id = ?")) {
            statement.setLong(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) return Optional.of(load(resultSet));
            }
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
        return Optional.empty();
    }

    @Override
    public void deleteById(Long id) {
        try (Connection connection = manager.getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM notes WHERE id = ?")) {
            statement.setLong(1, id);
            statement.executeUpdate();
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
    }

    @Override
    public int countByUsername(String username) {
        try (Connection connection = manager.getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM notes WHERE lower_nickname = ?")) {
            statement.setString(1, username.toLowerCase());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt(1) : 0;
            }
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
            return 0;
        }
    }

    @Override
    public List<Note> findByUsername(String username, int limit, int offset) {
        try (Connection connection = manager.getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT * FROM notes WHERE lower_nickname = ? ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?")) {
            statement.setString(1, username.toLowerCase());
            statement.setInt(2, limit);
            statement.setInt(3, offset);
            try (ResultSet resultSet = statement.executeQuery()) {
                final List<Note> notes = new ArrayList<>();
                while (resultSet.next()) notes.add(load(resultSet));
                return notes;
            }
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    public int countAllOf(String username, UUID uuid) {
        try (Connection connection = manager.getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM notes WHERE " + ofPlayer(uuid))) {
            bindPlayer(statement, username, uuid);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt(1) : 0;
            }
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
            return 0;
        }
    }

    @Override
    public int deleteAllOf(String username, UUID uuid) {
        try (Connection connection = manager.getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM notes WHERE " + ofPlayer(uuid))) {
            bindPlayer(statement, username, uuid);
            return statement.executeUpdate();
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
            return 0;
        }
    }

    private static String ofPlayer(UUID uuid) {
        return uuid == null ? "lower_nickname = ?" : "(lower_nickname = ? OR player_uuid = ?)";
    }

    private static void bindPlayer(PreparedStatement statement, String username, UUID uuid) throws SQLException {
        statement.setString(1, username.toLowerCase());
        if (uuid != null) statement.setString(2, uuid.toString());
    }

    @Override
    public int deleteBefore(LocalDateTime cutoff) {
        try (Connection connection = manager.getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM notes WHERE created_at < ?")) {
            statement.setTimestamp(1, SqlTime.of(cutoff));
            return statement.executeUpdate();
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
            return 0;
        }
    }

    @Override
    public Set<String> findAllImportKeys() throws SQLException {
        final Set<String> keys = new HashSet<>();
        try (Connection connection = manager.getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT lower_nickname, author, created_at FROM notes");
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                keys.add(importKey(resultSet.getString("lower_nickname"), resultSet.getString("author"), SqlTime.read(resultSet, "created_at")));
            }
        }
        return keys;
    }

    @Override
    public int insertAll(List<Note> notes) throws SQLException {
        if (notes.isEmpty()) return 0;
        try (Connection connection = manager.getDataSource().getConnection()) {
            final boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(INSERT)) {
                for (Note note : notes) {
                    bind(statement, note);
                    statement.addBatch();
                }
                statement.executeBatch();
                connection.commit();
                return notes.size();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        }
    }

    /**
     * Identity of a note for imports: the player, the author and the moment to the second.
     */
    public static String importKey(String nickname, String author, LocalDateTime createdAt) {
        final String start = createdAt == null ? "" : createdAt.truncatedTo(ChronoUnit.SECONDS).toString();
        return (nickname == null ? "" : nickname.toLowerCase()) + "|" + (author == null ? "" : author.toLowerCase()) + "|" + start;
    }

    public static String importKey(Note note) {
        return importKey(note.getNickname(), note.getAuthor(), note.getCreatedAt());
    }

    private static Note load(ResultSet rs) throws SQLException {
        final String uuid = rs.getString("player_uuid");
        return new Note(rs.getLong("id"), uuid == null || uuid.isBlank() ? null : UUID.fromString(uuid),
                rs.getString("nickname"), rs.getString("author"), rs.getString("content"), SqlTime.read(rs, "created_at"));
    }

}
