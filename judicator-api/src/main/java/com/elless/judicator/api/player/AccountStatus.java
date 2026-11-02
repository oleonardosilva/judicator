package com.elless.judicator.api.player;

/**
 * The heaviest punishment an account has in force, as {@code /alts} shows it.
 */
public enum AccountStatus {

    /** nothing in force */
    CLEAN,
    /** a mute (soft mutes included) */
    MUTED,
    /** a jail (a fugitive's included) */
    JAILED,
    /** a ban (one for some servers included) */
    BANNED
}
