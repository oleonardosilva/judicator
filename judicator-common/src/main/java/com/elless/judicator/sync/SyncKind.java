package com.elless.judicator.sync;

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
    DELETED,
    /** a player report was made (the id is the report's): the staff of the other instances hear of it */
    REPORTED,
    /** a report was assigned, given back or closed (the id is the report's): its reporter hears of it where they are */
    REPORT_CHANGED;

    public static Optional<SyncKind> parse(String name) {
        for (SyncKind kind : values()) {
            if (kind.name().equals(name)) return Optional.of(kind);
        }
        return Optional.empty();
    }
}
