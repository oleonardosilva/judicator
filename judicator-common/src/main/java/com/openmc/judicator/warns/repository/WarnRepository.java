package com.openmc.judicator.warns.repository;

import com.openmc.judicator.commons.db.Repository;
import com.openmc.judicator.warns.Warn;

import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface WarnRepository extends Repository<Warn, Long> {

    List<Warn> findAllByUsername(String username);

    Long countActiveWarns(String username);

    List<Warn> findAllByUUID(UUID uuid);

    /**
     * Import keys of every stored warn ({@code WarnRelationalDAO#importKey}), used to skip duplicates. Throws instead
     * of returning a partial set.
     */
    Set<String> findAllImportKeys() throws SQLException;

    /**
     * Inserts new warns in one transaction.
     *
     * @return how many were inserted
     */
    int insertAll(List<Warn> warns) throws SQLException;

}
