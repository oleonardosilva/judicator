package com.openmc.judicator.punish.data.repository;

import com.openmc.judicator.players.PlayerRef;
import com.openmc.judicator.punish.PlayerAddress;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * The {@code player_addresses} table: one row per account and IP address.
 */
public interface PlayerAddressRepository {

    void initialize();

    /**
     * A player joined from an address: its row gets the time, the name and the UUID; a new pair gets a row.
     */
    void record(PlayerRef player, String address, LocalDateTime at);

    /**
     * Every address a player used ({@link PlayerRef#matches}), the most recently used first.
     */
    List<PlayerAddress> findAllOf(PlayerRef player);

    /**
     * How many addresses the player used.
     */
    int countOf(PlayerRef player);

    /**
     * Every account that used an address, the most recent first.
     */
    List<PlayerAddress> findByAddress(String address);

    /**
     * @return how many rows were deleted
     */
    int deleteAllOf(PlayerRef player);

    /**
     * Deletes the rows not used since a moment.
     *
     * @return how many were deleted
     */
    int deleteUnusedBefore(LocalDateTime cutoff);

    /**
     * Gives the account's UUID to the rows of its name without a UUID (copied from 1.x).
     *
     * @return how many were changed
     */
    int fillUuid(PlayerRef account);

    /**
     * The stored row of the same account (its UUID, or its name when it has none) and address, for imports.
     */
    Optional<PlayerAddress> findSame(PlayerAddress address);

    /**
     * Stores an imported row: inserts it, or updates the one {@link #findSame} finds.
     */
    void save(PlayerAddress address);

    /**
     * On the first start of 2.0: copies the {@code access_addresses} table of 1.x (one row per address with a list of
     * names) into this one and keeps the old table as {@code access_addresses_old}. The server that renames the old
     * table first (to {@code access_addresses_migrating}) copies it, so two servers starting together copy it once;
     * nothing happens when there is no such table.
     *
     * @param uuidOfName the UUID of an account of 1.x by its name; null when unknown
     * @return how many rows were copied
     * @throws IllegalStateException when the copy could not be completed (nothing is left half done)
     */
    int migrateLegacy(Function<String, UUID> uuidOfName);

}
