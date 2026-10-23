package com.openmc.judicator.sync;

/**
 * One notice in the {@code sync_messages} table: instance {@code sender} changed punishment {@code punishmentId}.
 * It carries no player data; whoever receives it reads the punishment from the database.
 *
 * @param kind a {@link SyncKind} name; an unknown one (written by a newer version) is ignored
 */
public record SyncMessage(long id, String sender, String kind, long punishmentId) {
}
