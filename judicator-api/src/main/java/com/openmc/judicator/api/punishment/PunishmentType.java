package com.openmc.judicator.api.punishment;

/**
 * What a punishment does. A soft mute is a {@link #MUTE} or {@link #TEMPMUTE} with {@link PunishmentView#soft()}.
 */
public enum PunishmentType {

    /** Permanent ban: the player cannot join. */
    BAN(false),
    /** Ban with an end. */
    TEMPBAN(true),
    /** Permanent mute: the player cannot chat. */
    MUTE(false),
    /** Mute with an end. */
    TEMPMUTE(true),
    /** A kick; stored in the history only with {@code log-kicks: true}, and never in force. */
    KICK(false);

    private final boolean temporary;

    PunishmentType(boolean temporary) {
        this.temporary = temporary;
    }

    /**
     * @return whether a punishment of this type is applied with a duration
     */
    public boolean isTemporary() {
        return temporary;
    }

    /**
     * @return whether this is {@link #BAN} or {@link #TEMPBAN}
     */
    public boolean isBan() {
        return this == BAN || this == TEMPBAN;
    }

    /**
     * @return whether this is {@link #MUTE} or {@link #TEMPMUTE}
     */
    public boolean isMute() {
        return this == MUTE || this == TEMPMUTE;
    }

}
