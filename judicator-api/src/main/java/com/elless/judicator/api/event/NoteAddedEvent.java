package com.elless.judicator.api.event;

import com.elless.judicator.api.note.NoteView;

import java.util.Objects;

/**
 * A staff note was written ({@code /note} or the API).
 *
 * @param note the note as stored
 */
public record NoteAddedEvent(NoteView note) implements JudicatorEvent {

    public NoteAddedEvent {
        Objects.requireNonNull(note, "note");
    }

}
