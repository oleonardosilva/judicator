package com.openmc.judicator.menus;

/**
 * One screen of the menus (a list, a punishment, a confirmation). Built again each time it is shown, so it always has
 * the current data.
 */
@FunctionalInterface
public interface Screen {

    /**
     * Runs off the game thread: may read the database. The actions of the items run on the game thread.
     */
    Menu build(MenuContext context);
}
