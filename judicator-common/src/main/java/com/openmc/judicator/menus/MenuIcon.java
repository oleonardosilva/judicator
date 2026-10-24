package com.openmc.judicator.menus;

/**
 * What an item of a menu shows; each platform picks the item for its game version.
 */
public enum MenuIcon {

    /** a player's head; with {@link MenuItem#head()} it shows that player's skin when the platform can */
    PLAYER_HEAD,
    BAN,
    TEMPBAN,
    MUTE,
    TEMPMUTE,
    KICK,
    WARN,
    NOTE,
    ALTS,
    REPORT,
    HISTORY,
    ONLINE,
    SEARCH,
    PUNISH,
    PARDON,
    EXTEND,
    EDIT,
    TELEPORT,
    ASSIGN,
    FILTER,
    INFO,
    CONFIRM,
    CANCEL,
    TOGGLE_ON,
    TOGGLE_OFF,
    BACK,
    CLOSE,
    PREVIOUS,
    NEXT,
    PAGE,
    EMPTY,
    FILLER
}
