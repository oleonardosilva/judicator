package com.elless.judicator.notes.repository;

import com.elless.judicator.commons.db.Repository;
import com.elless.judicator.notes.Note;
import com.elless.judicator.players.PlayerRef;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface NoteRepository extends Repository<Note, Long> {

    /**
     * How many notes there are about a player ({@link PlayerRef#matches}).
     */
    int countOf(PlayerRef player);

    /**
     * One page of the notes about a player, newest first.
     */
    List<Note> findOf(PlayerRef player, int limit, int offset);

    /**
     * @return how many were deleted
     */
    int deleteAllOf(PlayerRef player);

    /**
     * Gives the account's UUID to the notes written under its name without a UUID.
     *
     * @return how many were changed
     */
    int fillUuid(PlayerRef account);

    /**
     * Deletes the notes written before a moment.
     *
     * @return how many were deleted
     */
    int deleteBefore(LocalDateTime cutoff);

    /**
     * Import keys ({@code NoteRelationalDAO#importKey}) of every stored note. Throws instead of returning a partial set.
     */
    Set<String> findAllImportKeys() throws SQLException;

    /**
     * Inserts new notes in one transaction.
     *
     * @return how many were inserted
     */
    int insertAll(List<Note> notes) throws SQLException;

}
