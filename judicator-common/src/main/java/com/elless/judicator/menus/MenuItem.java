package com.elless.judicator.menus;

import java.util.List;
import java.util.UUID;

/**
 * One item of a menu: texts with § colour codes, and what a click does (runs on the game thread).
 *
 * @param head     whose head a {@link MenuIcon#PLAYER_HEAD} shows (with {@code headName}); both null for the default head
 * @param headName that player's name: what servers before 1.12 and offline-mode servers show the skin by
 * @param action   null when clicking does nothing
 * @param material the item to show instead of the icon's, by its id ({@code diamond_sword}); null for the icon's.
 *                 A platform without that item (an older version) shows the icon's
 */
public record MenuItem(MenuIcon icon, String name, List<String> lore, int amount, UUID head, String headName, Runnable action,
                       String material) {

    public MenuItem {
        lore = List.copyOf(lore);
        amount = Math.max(1, Math.min(64, amount));
    }

    public static MenuItem of(MenuIcon icon, String name, List<String> lore) {
        return new MenuItem(icon, name, lore, 1, null, null, null, null);
    }

    public MenuItem onClick(Runnable action) {
        return new MenuItem(icon, name, lore, amount, head, headName, action, material);
    }

    /**
     * A player's head, with their skin: by UUID where the server trusts UUIDs, by name otherwise.
     */
    public MenuItem headOf(UUID player, String playerName) {
        return new MenuItem(MenuIcon.PLAYER_HEAD, name, lore, amount, player, playerName, action, null);
    }

    public MenuItem amount(int count) {
        return new MenuItem(icon, name, lore, count, head, headName, action, material);
    }

    /**
     * @param id an item id ({@code tnt}, {@code minecraft:diamond_sword}); null or blank keeps the icon's item
     */
    public MenuItem material(String id) {
        final String chosen = id == null || id.isBlank() ? null : id.trim();
        return new MenuItem(icon, name, lore, amount, head, headName, action, chosen);
    }
}
