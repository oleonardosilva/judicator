package com.elless.judicator.database;

import com.elless.judicator.commons.db.RelationalDBManager;
import com.elless.judicator.punish.Punishment;
import com.elless.judicator.punish.Revocation;
import com.elless.judicator.punish.data.repository.dao.PunishmentRelationalDAO;
import com.elless.judicator.punish.types.PunishType;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.helpers.NOPLogger;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static com.elless.judicator.testing.FakeConfig.map;
import static com.elless.judicator.testing.FakeConfig.of;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The contract against a real MySQL or PostgreSQL server, given by an environment variable as {@code host:port},
 * with a database, user and password all named {@code judicator}, e.g. a throwaway container:
 * <pre>
 * docker run -d --name judicator-mysql -p 3307:3306 -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=judicator \
 *     -e MYSQL_USER=judicator -e MYSQL_PASSWORD=judicator mysql:8.4
 * docker run -d --name judicator-postgres -p 5433:5432 -e POSTGRES_DB=judicator -e POSTGRES_USER=judicator \
 *     -e POSTGRES_PASSWORD=judicator postgres:16
 * JUDICATOR_TEST_MYSQL=localhost:3307 JUDICATOR_TEST_POSTGRES=localhost:5433 ./gradlew :judicator-common:test
 * </pre>
 * Each test has tables of its own (a random table prefix), so the database needs no cleaning.
 */
abstract class ExternalRepositoryContract extends RepositoryContract {

    private static final Logger LOGGER = NOPLogger.NOP_LOGGER;

    /**
     * {@code mysql} or {@code postgresql}, as in {@code data.yml}.
     */
    protected abstract String type();

    /**
     * The variable that holds {@code host:port}.
     */
    protected abstract String variable();

    /**
     * The punishments table as version 2.0.0 created it.
     */
    protected abstract String punishmentsTableOf200(String table);

    private RelationalDBManager connect(String prefix) {
        final String[] address = System.getenv(variable()).split(":");
        final RelationalDBManager manager = new RelationalDBManager();
        manager.initialize(of(map("database", map("type", type(), "host", address[0], "port", address[1],
                "database", "judicator", "user", "judicator", "password", "judicator", "table-prefix", prefix))), folder, LOGGER);
        return manager;
    }

    private static String randomPrefix() {
        return "t" + Long.toString(ThreadLocalRandom.current().nextLong(Long.MAX_VALUE), 36)
                .substring(0, 6).toLowerCase(Locale.ROOT) + "_";
    }

    @Override
    protected Store open(Path folder) {
        return RelationalStores.open(connect(randomPrefix()), LOGGER);
    }

    @Test
    void aPunishmentsTableOf200_getsTheRevocationColumnsAndKeepsItsRows() throws Exception {
        final String prefix = randomPrefix();
        final RelationalDBManager manager = connect(prefix);
        try {
            try (Connection connection = manager.getDataSource().getConnection(); Statement statement = connection.createStatement()) {
                statement.executeUpdate(punishmentsTableOf200(prefix + "punishments"));
                statement.executeUpdate("INSERT INTO " + prefix + "punishments (reason, punisher, nickname, lower_nickname, started_at, "
                        + "revoked, evidences, type, permanent) VALUES ('spam', 'Mod', 'Steve', 'steve', '2026-01-01 10:00:00', false, '', "
                        + "'MUTE', true)");
            }
            final PunishmentRelationalDAO dao = new PunishmentRelationalDAO(manager, LOGGER);
            dao.initialize();
            dao.initialize(); // every startup runs it again

            final Punishment existing = dao.findAllByUsername("steve").get(0);
            assertTrue(existing.getRevokedBy().isEmpty());
            final LocalDateTime at = LocalDateTime.of(2026, 11, 10, 15, 30);
            final UUID mod = UUID.randomUUID();
            assertTrue(dao.revoke(existing.getId(), new Revocation("appeal", "Mod", mod, at)));
            final Punishment revoked = dao.findById(existing.getId()).orElseThrow();
            assertEquals("Mod", revoked.getRevokedBy().orElseThrow());
            assertEquals(mod, revoked.getRevokedByUuid().orElseThrow());
            assertEquals(at, revoked.getRevokedAt().orElseThrow());

            final Punishment saved = dao.save(Punishment.create(null, "x", "Mod", "Alex", null, at, null, List.of(), PunishType.BAN));
            assertTrue(dao.findById(saved.getId()).orElseThrow().getRevokedAt().isEmpty());
        } finally {
            manager.shutdown();
        }
    }
}
