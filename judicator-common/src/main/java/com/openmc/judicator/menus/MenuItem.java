package com.openmc.judicator.menus;

import java.util.List;
import java.util.UUID;

/**
 * One item of a menu: texts with § colour codes, and what a click does (runs on the game thread).
 *
 * @param head   whose head a {@link MenuIcon#PLAYER_HEAD} shows; null for the default head
 * @param action null when clicking does nothing
 */
public record MenuItem(MenuIcon icon, String name, List<String> lore, int amount, UUID head, Runnable action) {

    public MenuItem {
        lore = List.copyOf(lore);
        amount = Math.max(1, Math.min(64, amount));
    }

    public static MenuItem of(MenuIcon icon, String name, List<String> lore) {
        return new MenuItem(icon, name, lore, 1, null, null);
    }

    public MenuItem onClick(Runnable action) {
        return new MenuItem(icon, name, lore, amount, head, action);
    }

    public MenuItem headOf(UUID player) {
        return new MenuItem(icon, name, lore, amount, player, action);
    }

    public MenuItem amount(int count) {
        return new MenuItem(icon, name, lore, count, head, action);
    }
}
