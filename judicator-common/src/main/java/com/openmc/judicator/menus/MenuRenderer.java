package com.openmc.judicator.menus;

import com.openmc.judicator.platform.JudicatorPlayer;

/**
 * Draws menus for a platform that has them (Spigot/Paper inventories). Called on the game thread; the platform reports
 * clicks and closes back to {@link MenuService#click} and {@link MenuService#closed} with the {@link Menu} it showed.
 */
public interface MenuRenderer {

    /**
     * Shows the menu, replacing the one the player has open.
     */
    void open(JudicatorPlayer viewer, Menu menu);

    /**
     * Closes the menu the player has open, if any.
     */
    void close(JudicatorPlayer viewer);
}
