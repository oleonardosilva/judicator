package com.elless.judicator.database;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * The relational DAOs against MySQL; runs only with {@code JUDICATOR_TEST_MYSQL} ({@link ExternalRepositoryContract}).
 */
@EnabledIfEnvironmentVariable(named = "JUDICATOR_TEST_MYSQL", matches = ".+")
class MysqlRepositoryContractTest extends ExternalRepositoryContract {

    @Override
    protected String type() {
        return "mysql";
    }

    @Override
    protected String variable() {
        return "JUDICATOR_TEST_MYSQL";
    }

    @Override
    protected String punishmentsTableOf200(String table) {
        return "CREATE TABLE " + table + """
                 (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY, player_uuid VARCHAR(36), reason VARCHAR(255) NOT NULL,
                    punisher VARCHAR(20) NOT NULL, nickname VARCHAR(20), ip_address VARCHAR(45), lower_nickname VARCHAR(20),
                    started_at DATETIME NOT NULL, finish_at DATETIME, revoked BOOLEAN NOT NULL, evidences VARCHAR(255),
                    type VARCHAR(20) NOT NULL, permanent BOOLEAN NOT NULL, revoked_reason VARCHAR(100),
                    soft BOOLEAN NOT NULL DEFAULT FALSE, online_only BOOLEAN NOT NULL DEFAULT FALSE, paused_remaining BIGINT,
                    punisher_uuid VARCHAR(36), scope VARCHAR(64)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""";
    }
}
