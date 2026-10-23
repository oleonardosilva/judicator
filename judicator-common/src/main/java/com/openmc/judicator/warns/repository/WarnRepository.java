package com.openmc.judicator.warns.repository;

import com.openmc.judicator.commons.db.Repository;
import com.openmc.judicator.players.PlayerRef;
import com.openmc.judicator.warns.Warn;

import com.openmc.judicator.punish.data.repository.PunishmentRepository;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface WarnRepository extends Repository<Warn, Long> {

    List<Warn> findAllByUsername(String username);

    /**
     * How many warns of a player are in force ({@link PlayerRef#matches}; a warn stored with the offline-mode UUID of
     * its name, in place of an unknown one, counts as one without a UUID).
     */
    Long countActiveWarns(PlayerRef player);

    /**
     * How many warns each staff member applied since a moment, as {@code WARN} counts.
     */
    List<PunishmentRepository.StaffCount> countByStaffSince(LocalDateTime since);

    List<Warn> findAllByUUID(UUID uuid);

    /**
     * Warns a staff member applied ({@code punisher}, case-insensitive) since a moment, newest first.
     */
    List<Warn> findByPunisherSince(PlayerRef staff, LocalDateTime since);

    /**
     * Deletes temporary warns that ended before a moment.
     *
     * @return how many were deleted
     */
    int deleteFinishedBefore(LocalDateTime cutoff);

    /**
     * Deletes every warn of a player, by name and, when known, by UUID.
     *
     * @return how many were deleted
     */
    int deleteAllOf(PlayerRef player);

    List<Warn> findAllOf(PlayerRef player);

    /**
     * Gives the account's UUID to the warns stored under its name with the offline-mode UUID of that name (applied
     * to a name nobody had seen).
     *
     * @return how many were changed
     */
    int fillUuid(PlayerRef account);

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
