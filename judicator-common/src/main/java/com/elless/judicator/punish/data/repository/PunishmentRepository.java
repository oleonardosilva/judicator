package com.elless.judicator.punish.data.repository;

import com.elless.judicator.commons.db.Repository;
import com.elless.judicator.players.PlayerRef;
import com.elless.judicator.commons.ListQuery;
import com.elless.judicator.punish.Punishment;
import com.elless.judicator.punish.types.PunishType;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface PunishmentRepository extends Repository<Punishment, Long> {

    /**
     * Punishments stored under this name (any letter case), whatever their UUID. To find a player's punishments use
     * {@link #findAllOf}.
     */
    List<Punishment> findAllByUsername(String username);

    /**
     * {@link #findAllByUsername} limited to those in force.
     */
    List<Punishment> findAllActiveByUsernameAndTypes(String username, PunishType... types);

    /**
     * Punishments stored with this UUID.
     */
    List<Punishment> findAllByUUID(UUID uuid);

    /**
     * Every punishment of a player ({@link PlayerRef#matches}), oldest first as stored.
     */
    List<Punishment> findAllOf(PlayerRef player);

    /**
     * Punishments of these types in force for a player, IP punishments included when the player was the account named.
     */
    List<Punishment> findAllActiveOf(PlayerRef player, PunishType... types);

    /**
     * IP punishments of these types in force on an address.
     */
    List<Punishment> findAllActiveOfAddress(String ipAddress, PunishType... types);

    /**
     * {@link #findAllActiveOf} and {@link #findAllActiveOfAddress} in one query.
     */
    List<Punishment> findAllActiveOfPlayerOrAddress(PlayerRef player, String ipAddress, PunishType... types);

    /**
     * The scopes of the punishments in force, each once.
     */
    Set<String> findActiveScopes();

    /**
     * Punishments of these types in force for any of these players, in a bounded number of queries.
     */
    List<Punishment> findAllActiveOfAny(Collection<PlayerRef> players, PunishType... types);

    /**
     * Gives the account's UUID to the punishments stored under its name without a UUID (applied to a name nobody had
     * seen): from now on they follow the account.
     *
     * @return how many were changed
     */
    int fillUuid(PlayerRef account);

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
     * Deletes every punishment of a player ({@link PlayerRef#matches}).
     *
     * @return how many were deleted
     */
    int deleteAllOf(PlayerRef player);

    /**
     * How many punishments a staff member applied: by {@code punisher_uuid}, and by the {@code punisher} name
     * (case-insensitive) for those stored without it (the console, plugins, records older than 2.0).
     */
    default int countByPunisher(PlayerRef staff) {
        return countByPunisher(staff, null);
    }

    /**
     * @param since only punishments started at or after this moment; null for all
     */
    int countByPunisher(PlayerRef staff, LocalDateTime since);

    /**
     * One page of the punishments applied by a staff member, newest first.
     */
    default List<Punishment> findByPunisher(PlayerRef staff, int limit, int offset) {
        return findByPunisher(staff, null, limit, offset);
    }

    /**
     * @param since only punishments started at or after this moment; null for all
     */
    List<Punishment> findByPunisher(PlayerRef staff, LocalDateTime since, int limit, int offset);

    /**
     * How many punishments of these types are in force (not revoked, not over).
     */
    default int countActiveByTypes(PunishType... types) {
        return countActive(ListQuery.NEWEST, types);
    }

    /**
     * One page of the punishments of these types in force, newest first.
     */
    default List<Punishment> findActiveByTypes(int limit, int offset, PunishType... types) {
        return findActive(ListQuery.NEWEST, limit, offset, types);
    }

    /**
     * How many punishments of these types in force match the search (player name, reason or author).
     */
    int countActive(ListQuery query, PunishType... types);

    /**
     * One page of the punishments of these types in force that match the search, in the query's order (ties: the
     * newest first for NEWEST, by id otherwise).
     */
    List<Punishment> findActive(ListQuery query, int limit, int offset, PunishType... types);

    /**
     * How many punishments each staff member applied since a moment, per type (one staff member: the same
     * {@code punisher_uuid}, or the same {@code punisher} name case-insensitively when it has none).
     */
    List<StaffCount> countByStaffSince(LocalDateTime since);

    /**
     * @param type a {@link PunishType} name, or {@code WARN} for warns
     */
    record StaffCount(String punisher, String type, int count) {
    }

    /**
     * Import keys ({@link com.elless.judicator.punish.data.repository.dao.PunishmentRelationalDAO#importKey}) of every
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
