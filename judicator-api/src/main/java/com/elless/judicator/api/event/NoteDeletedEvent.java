package com.elless.judicator.api.event;

import com.elless.judicator.api.note.NoteView;

import java.util.Objects;

/**
 * A staff note was deleted ({@code /delnote} or the API).
 *
 * @param note      the note as it was
 * @param deletedBy who deleted it: a staff member, {@code Console}, or the name a plugin gave
 */
public record NoteDeletedEvent(NoteView note, String deletedBy) implements JudicatorEvent {

    public NoteDeletedEvent {
        Objects.requireNonNull(note, "note");
        Objects.requireNonNull(deletedBy, "deletedBy");
    }

}
