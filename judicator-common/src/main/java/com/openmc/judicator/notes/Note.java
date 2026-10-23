package com.openmc.judicator.notes;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * A staff note about a player: context for the next shift, not a punishment.
 */
@Getter
@Setter
@NoArgsConstructor
public class Note {

    private Long id;
    private UUID playerUUID;
    private String nickname, author, content;
    /**
     * The UUID of the author when it was a player; null for the console, a plugin or a record older than 2.0.
     */
    private UUID authorUuid;
    private LocalDateTime createdAt;

    public Note(Long id, UUID playerUUID, String nickname, String author, String content, LocalDateTime createdAt) {
        this.id = id;
        this.playerUUID = playerUUID;
        this.nickname = nickname;
        this.author = author;
        this.content = content;
        this.createdAt = createdAt;
    }

    public static Note create(UUID playerUUID, String nickname, String author, String content) {
        return new Note(null, playerUUID, nickname, author, content, LocalDateTime.now());
    }

    public Optional<UUID> getPlayerUUID() {
        return Optional.ofNullable(playerUUID);
    }

    public Optional<UUID> getAuthorUuid() {
        return Optional.ofNullable(authorUuid);
    }

}
