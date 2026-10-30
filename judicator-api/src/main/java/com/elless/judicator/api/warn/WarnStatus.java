package com.elless.judicator.api.warn;

/**
 * Where a warn stands at the moment it was read.
 */
public enum WarnStatus {

    /** Counts towards the warn tree (permanent, or temporary and not over). */
    ACTIVE,
    /** A temporary warn whose time is over. */
    FINISHED

}
