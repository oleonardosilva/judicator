package com.openmc.judicator.api.punishment;

/**
 * Where a punishment stands at the moment it was read.
 */
public enum PunishmentStatus {

    /** In force (a paused online-only mute is in force too). */
    ACTIVE,
    /** Its time is over; kicks are always finished. */
    FINISHED,
    /** Lifted by staff ({@code /unban}, {@code /unmute}, {@code /revoke}, a rollback) or through the API. */
    REVOKED

}
