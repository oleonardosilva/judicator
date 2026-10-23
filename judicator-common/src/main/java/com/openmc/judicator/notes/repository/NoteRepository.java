package com.openmc.judicator.notes.repository;

import com.openmc.judicator.commons.db.Repository;
import com.openmc.judicator.notes.Note;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface NoteRepository extends Repository<Note, Long> {

    int countByUsername(String username);

    /**
     * One page of the notes about a player, newest first.
     */
    List<Note> findByUsername(String username, int limit, int offset);

    /**
     * Notes about a player by name and, when known, by UUID (older names of the same account).
     */
    int countAllOf(String username, UUID uuid);

    /**
     * @return how many were deleted
     */
    int deleteAllOf(String username, UUID uuid);

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
