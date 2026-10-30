package com.elless.judicator.platform;

/**
 * How {@link Platform#teleport} ended.
 */
public enum TeleportOutcome {
    /** the staff member is next to the player */
    TELEPORTED,
    /** on the proxy: the staff member is on the player's server, but that server did not teleport them (no Judicator
     * in {@code proxy-mode} there, or no answer in time) */
    SERVER_ONLY,
    /** the staff member could not get there */
    FAILED,
    /** a newer {@code /reports tp} of the same staff member took over (nothing to tell) */
    SUPERSEDED
}
