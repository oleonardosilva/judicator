package com.openmc.judicator.punish.data.repository;

import com.openmc.judicator.commons.db.Repository;
import com.openmc.judicator.punish.Punishment;
import com.openmc.judicator.punish.types.PunishType;

import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface PunishmentRepository extends Repository<Punishment, Long> {

    List<Punishment> findAllByUsername(String username);

    List<Punishment> findAllActiveByUsernameAndTypes(String username, PunishType... types);

    List<Punishment> findAllActiveByUsernameOrIpAndTypes(String username, String ipAddress, PunishType... types);

    List<Punishment> findAllByUUID(UUID uuid);

    List<Punishment> findAllActiveByUUIDAndTypes(UUID uuid, PunishType... types);

    boolean revoke(Long id, String reason);

    /**
     * How many punishments a staff member applied (the {@code punisher} name, case-insensitive).
     */
    int countByPunisher(String punisher);

    /**
     * One page of the punishments applied by a staff member, newest first.
     */
    List<Punishment> findByPunisher(String punisher, int limit, int offset);

    /**
     * Import keys ({@link com.openmc.judicator.punish.data.repository.dao.PunishmentRelationalDAO#importKey}) of every
     * stored punishment, used to skip duplicates. Throws instead of returning a partial set: an empty set would make
     * an import write everything again.
     */
    Set<String> findAllImportKeys() throws SQLException;

    /**
     * Inserts new punishments in one transaction.
     *
     * @return how many were inserted
     */
    int insertAll(List<Punishment> punishments) throws SQLException;

}
