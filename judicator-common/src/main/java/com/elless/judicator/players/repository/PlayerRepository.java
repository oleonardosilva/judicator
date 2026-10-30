package com.elless.judicator.players.repository;

import java.util.Map;
import com.elless.judicator.players.KnownPlayer;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * The {@code players} table: the name each account last joined with.
 */
public interface PlayerRepository {

    void initialize();

    /**
     * Stores the name an account joined with.
     *
     * @return true when the account is new or joined with another name than the stored one (letter case aside)
     */
    boolean record(UUID uuid, String name, LocalDateTime seenAt);

    /**
     * The account that joined last with this name (any letter case): names can change hands.
     */
    Optional<KnownPlayer> findLatestByName(String name);

    Optional<KnownPlayer> findByUuid(UUID uuid);

    /**
     * Every known name (lower case) with the account that joined last with it, read at once.
     */
    Map<String, UUID> latestUuidsByName();

    /**
     * @return how many were deleted (0 or 1)
     */
    int deleteByUuid(UUID uuid);

    /**
     * On the first start of 2.0 (an empty table): fills it with the accounts the punishments, warns, notes and staff
     * levels already know, the most recent name of each.
     *
     * @return how many accounts were added
     */
    int seedIfEmpty();

}
