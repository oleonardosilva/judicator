package com.openmc.judicator.sync;

import java.util.Optional;

/**
 * What happened to the punishment a {@link SyncMessage} points at.
 */
public enum SyncKind {

    /** applied: the other instances apply its effects to their own players */
    PUNISHED,
    /** revoked: lifted everywhere */
    REVOKED,
    /** end or reason changed, online-only mute paused or resumed */
    CHANGED,
    /** deleted from the database */
    DELETED;

    public static Optional<SyncKind> parse(String name) {
        for (SyncKind kind : values()) {
            if (kind.name().equals(name)) return Optional.of(kind);
        }
        return Optional.empty();
    }
}
