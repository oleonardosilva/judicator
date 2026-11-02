package com.elless.judicator.api.note;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A staff note about a player ({@code /note}), only seen by the staff.
 *
 * @param id             the id {@code /delnote} takes
 * @param targetName     the player's name when it was written
 * @param targetUniqueId the account's UUID; empty for a name nobody had seen (offline mode)
 * @param author         who wrote it: a staff member, {@code Console}, or the name a plugin gave
 * @param authorUniqueId the author's UUID when it was a player
 * @param content        the text
 * @param createdAt      when it was written
 */
public record NoteView(long id, String targetName, Optional<UUID> targetUniqueId, String author, Optional<UUID> authorUniqueId,
                       String content, Instant createdAt) {

    public NoteView {
        Objects.requireNonNull(targetName, "targetName");
        Objects.requireNonNull(targetUniqueId, "targetUniqueId");
        Objects.requireNonNull(author, "author");
        Objects.requireNonNull(authorUniqueId, "authorUniqueId");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
