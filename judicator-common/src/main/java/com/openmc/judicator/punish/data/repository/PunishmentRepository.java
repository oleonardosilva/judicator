package com.openmc.judicator.punish.data.repository;

import com.openmc.judicator.commons.db.Repository;
import com.openmc.judicator.punish.Punishment;
import com.openmc.judicator.punish.types.PunishType;

import java.sql.SQLException;
import java.time.LocalDateTime;
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
    default int countByPunisher(String punisher) {
        return countByPunisher(punisher, null);
    }

    /**
     * @param since only punishments started at or after this moment; null for all
     */
    int countByPunisher(String punisher, LocalDateTime since);

    /**
     * One page of the punishments applied by a staff member, newest first.
     */
    default List<Punishment> findByPunisher(String punisher, int limit, int offset) {
        return findByPunisher(punisher, null, limit, offset);
    }

    /**
     * @param since only punishments started at or after this moment; null for all
     */
    List<Punishment> findByPunisher(String punisher, LocalDateTime since, int limit, int offset);

    /**
     * How many punishments of these types are in force (not revoked, not over).
     */
    int countActiveByTypes(PunishType... types);

    /**
     * One page of the punishments of these types in force, newest first.
     */
    List<Punishment> findActiveByTypes(int limit, int offset, PunishType... types);

    /**
     * How many punishments each staff member applied since a moment, per type ({@code punisher} compared
     * case-insensitively).
     */
    List<StaffCount> countByStaffSince(LocalDateTime since);

    /**
     * @param type a {@link PunishType} name, or {@code WARN} for warns
     */
    record StaffCount(String punisher, String type, int count) {
    }

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
