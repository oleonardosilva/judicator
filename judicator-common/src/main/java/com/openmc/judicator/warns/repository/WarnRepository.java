package com.openmc.judicator.warns.repository;

import com.openmc.judicator.commons.db.Repository;
import com.openmc.judicator.warns.Warn;

import com.openmc.judicator.punish.data.repository.PunishmentRepository;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface WarnRepository extends Repository<Warn, Long> {

    List<Warn> findAllByUsername(String username);

    Long countActiveWarns(String username);

    /**
     * How many warns each staff member applied since a moment, as {@code WARN} counts.
     */
    List<PunishmentRepository.StaffCount> countByStaffSince(LocalDateTime since);

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
