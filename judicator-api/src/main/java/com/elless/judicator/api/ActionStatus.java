package com.elless.judicator.api;

/**
 * How an action requested through {@link JudicatorApi} ended.
 */
public enum ActionStatus {

    /** Done and stored. */
    SUCCESS,
    /** Refused: the target is exempt in {@code immune.yml} (for an IP punishment, any account on the address). */
    TARGET_EXEMPT,
    /** Refused: the address is in {@code exempt-addresses} (a shared network). */
    ADDRESS_EXEMPT,
    /** Refused: an IP punishment of a player whose address is not known (not online, never joined). */
    ADDRESS_UNKNOWN,
    /** Refused: the scope is neither a group of {@code scopes.groups} nor a server of the network, or this Spigot/Paper
     * server has no {@code scopes.server-name}. */
    SCOPE_UNKNOWN,
    /** Refused: a kick of a player who is not online on this server. */
    TARGET_OFFLINE,
    /** Nothing to act on: no punishment or warn with that id, or no ban/mute in force for that player. */
    NOT_FOUND,
    /** The punishment was already revoked. */
    ALREADY_REVOKED,
    /** It could not be stored (the database failed); the cause is in the server log. */
    FAILED

}
