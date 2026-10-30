package com.elless.judicator.commons.db;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DialectTest {

    @Test
    void missingTypeKeepsTheHistoricalDefault() {
        assertEquals(Dialect.POSTGRESQL, Dialect.fromConfig(null));
    }

    @Test
    void knownTypes() {
        assertEquals(Dialect.POSTGRESQL, Dialect.fromConfig("postgres"));
        assertEquals(Dialect.POSTGRESQL, Dialect.fromConfig("PostgreSQL"));
        assertEquals(Dialect.SQLITE, Dialect.fromConfig(" SQLite "));
        assertEquals(Dialect.MYSQL, Dialect.fromConfig("mysql"));
    }

    @Test
    void anythingElseIsMysqlAsBeforeSqliteExisted() {
        // Versions before SQLite treated every value other than "postgres" as MySQL; existing configs rely on it.
        assertEquals(Dialect.MYSQL, Dialect.fromConfig("MySQL8"));
        assertEquals(Dialect.MYSQL, Dialect.fromConfig("mariadb"));
        assertEquals(Dialect.MYSQL, Dialect.fromConfig(""));
    }

    @Test
    void anEmptyPortMeansTheDefaultOfTheType() {
        assertEquals("5432", Dialect.POSTGRESQL.getDefaultPort());
        assertEquals("3306", Dialect.MYSQL.getDefaultPort());
    }
}
