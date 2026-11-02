package com.elless.judicator.notes;

import com.elless.judicator.Judicator;
import com.elless.judicator.api.event.NoteAddedEvent;
import com.elless.judicator.api.event.NoteDeletedEvent;
import com.elless.judicator.integration.ApiViews;
import com.elless.judicator.players.PlayerRef;

import java.util.UUID;

/**
 * Writes and deletes staff notes ({@code /note}, {@code /delnote}, the menus through them, and the API), and posts their
 * API events.
 */
public class NoteHandler {

    public static final int CONTENT_MAX_LENGTH = 255;

    private final Judicator judicator;

    public NoteHandler(Judicator judicator) {
        this.judicator = judicator;
    }

    /**
     * @param authorUuid the author's UUID when a player wrote it; null for the console and plugins
     */
    public Note add(PlayerRef target, String content, String author, UUID authorUuid) {
        final Note created = Note.create(target.uuid(), target.name(), author, content);
        created.setAuthorUuid(authorUuid);
        final Note note = judicator.getNoteService().save(created);
        judicator.getEventBus().post(() -> new NoteAddedEvent(ApiViews.note(note)));
        return note;
    }

    public void delete(Note note, String deletedBy) {
        judicator.getNoteService().deleteById(note.getId());
        judicator.getLogger().info("{} deleted note #{} about {} by {}: {}", deletedBy, note.getId(),
                note.getNickname(), note.getAuthor(), note.getContent());
        judicator.getEventBus().post(() -> new NoteDeletedEvent(ApiViews.note(note), deletedBy));
    }
}
