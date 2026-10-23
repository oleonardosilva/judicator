package com.openmc.judicator.punish.data.repository;

import com.openmc.judicator.commons.db.Repository;
import com.openmc.judicator.punish.Punishment;
import com.openmc.judicator.punish.types.PunishType;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface PunishmentRepository extends Repository<Punishment, Long> {

    List<Punishment> findAllByUsername(String username);

    List<Punishment> findAllActiveByUsernameAndTypes(String username, PunishType... types);

    List<Punishment> findAllActiveByUsernameOrIpAndTypes(String username, String ipAddress, PunishType... types);

    List<Punishment> findAllByUUID(UUID uuid);

    /**
     * Punishments of these types in force for any of these player names (case-insensitive), in one query.
     */
    List<Punishment> findAllActiveByUsernamesAndTypes(Collection<String> usernames, PunishType... types);

    List<Punishment> findAllActiveByUUIDAndTypes(UUID uuid, PunishType... types);

    boolean revoke(Long id, String reason);

    /**
     * Changes only the end of a punishment that was not revoked (a revocation at the same moment wins).
     *
     * @param finishAt        the new end; null while an online-only mute is paused
     * @param pausedRemaining milliseconds left of a paused online-only mute; null while it runs
     * @return false when there is no such punishment or it was revoked
     */
    boolean changeEnd(Long id, LocalDateTime finishAt, Long pausedRemaining);

    /**
     * Changes only the reason of a punishment that was not revoked.
     *
     * @return false when there is no such punishment or it was revoked
     */
    boolean changeReason(Long id, String reason);

    /**
     * Deletes the logged kicks that started before a moment.
     *
     * @return how many were deleted
     */
    int deleteKicksBefore(LocalDateTime cutoff);

    /**
     * Deletes the bans and mutes that are over: ended before the moment, or revoked and started before it. A paused
     * online-only mute has no end and stays; kicks are left to {@link #deleteKicksBefore}.
     *
     * @return how many were deleted
     */
    int deleteFinishedBefore(LocalDateTime cutoff);

    /**
     * Deletes every punishment of a player: by name and, when known, by UUID (older names of the same account).
     *
     * @return how many were deleted
     */
    int deleteAllOf(String username, UUID uuid);

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
