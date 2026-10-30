package com.elless.judicator.menus.screens;

import com.elless.judicator.menus.MenuIcon;
import com.elless.judicator.punish.types.PunishType;

final class Icons {

    private Icons() {
    }

    static MenuIcon of(PunishType type) {
        return switch (type) {
            case BAN -> MenuIcon.BAN;
            case TEMPBAN -> MenuIcon.TEMPBAN;
            case MUTE -> MenuIcon.MUTE;
            case TEMPMUTE -> MenuIcon.TEMPMUTE;
            case KICK -> MenuIcon.KICK;
        };
    }
}
