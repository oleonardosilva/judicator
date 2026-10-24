package com.openmc.judicator.bukkit;

import com.openmc.judicator.menus.MenuIcon;
import com.openmc.judicator.menus.MenuItem;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.EnumMap;
import java.util.Map;

/**
 * The item of each {@link MenuIcon} on this server's version. Materials are looked up by name — the names of 1.13+
 * first, then the 1.8–1.12 name and data value — and never through a {@code Material} constant, which would not exist
 * on the other side of the 1.13 rename.
 */
final class MenuItems {

    /** {@code SkullMeta#setOwningPlayer(OfflinePlayer)}, 1.12.1+; before it, {@code setOwner(String)} */
    private static final MethodHandle SET_OWNING_PLAYER = BukkitCompat.find(SkullMeta.class, "setOwningPlayer",
            MethodType.methodType(boolean.class, org.bukkit.OfflinePlayer.class));

    private record Look(Material material, short data) {
    }

    /** whether materials have the names of 1.13+ (no data values) */
    private final boolean modern = Material.getMaterial("PLAYER_HEAD") != null;
    private final Map<MenuIcon, Look> looks = new EnumMap<>(MenuIcon.class);

    MenuItems() {
        look(MenuIcon.PLAYER_HEAD, "PLAYER_HEAD", "SKULL_ITEM", 3);
        look(MenuIcon.BAN, "BARRIER", "BARRIER", 0);
        look(MenuIcon.TEMPBAN, "CLOCK", "WATCH", 0);
        look(MenuIcon.MUTE, "WRITABLE_BOOK", "BOOK_AND_QUILL", 0);
        look(MenuIcon.TEMPMUTE, "MAP", "EMPTY_MAP", 0);
        look(MenuIcon.KICK, "LEATHER_BOOTS", "LEATHER_BOOTS", 0);
        look(MenuIcon.WARN, "ORANGE_DYE", "INK_SACK", 14);
        look(MenuIcon.NOTE, "BOOK", "BOOK", 0);
        look(MenuIcon.ALTS, "NAME_TAG", "NAME_TAG", 0);
        look(MenuIcon.REPORT, "OAK_SIGN|SIGN", "SIGN", 0);
        look(MenuIcon.HISTORY, "BOOKSHELF", "BOOKSHELF", 0);
        look(MenuIcon.ONLINE, "EMERALD", "EMERALD", 0);
        look(MenuIcon.SEARCH, "COMPASS", "COMPASS", 0);
        look(MenuIcon.PUNISH, "IRON_SWORD", "IRON_SWORD", 0);
        look(MenuIcon.PARDON, "LIME_DYE", "INK_SACK", 10);
        look(MenuIcon.EXTEND, "CLOCK", "WATCH", 0);
        look(MenuIcon.EDIT, "WRITABLE_BOOK", "BOOK_AND_QUILL", 0);
        look(MenuIcon.TELEPORT, "ENDER_PEARL", "ENDER_PEARL", 0);
        look(MenuIcon.ASSIGN, "NAME_TAG", "NAME_TAG", 0);
        look(MenuIcon.FILTER, "HOPPER", "HOPPER", 0);
        look(MenuIcon.INFO, "PAPER", "PAPER", 0);
        look(MenuIcon.CONFIRM, "LIME_WOOL", "WOOL", 5);
        look(MenuIcon.CANCEL, "RED_WOOL", "WOOL", 14);
        look(MenuIcon.TOGGLE_ON, "LIME_DYE", "INK_SACK", 10);
        look(MenuIcon.TOGGLE_OFF, "GRAY_DYE", "INK_SACK", 8);
        look(MenuIcon.BACK, "ARROW", "ARROW", 0);
        look(MenuIcon.CLOSE, "BARRIER", "BARRIER", 0);
        look(MenuIcon.PREVIOUS, "ARROW", "ARROW", 0);
        look(MenuIcon.NEXT, "ARROW", "ARROW", 0);
        look(MenuIcon.PAGE, "PAPER", "PAPER", 0);
        look(MenuIcon.EMPTY, "GLASS_BOTTLE", "GLASS_BOTTLE", 0);
        look(MenuIcon.FILLER, "GRAY_STAINED_GLASS_PANE", "STAINED_GLASS_PANE", 7);
    }

    boolean isModern() {
        return modern;
    }

    /**
     * @param modernNames the 1.13+ names to try in order, separated by | (OAK_SIGN is 1.14+, SIGN on 1.13)
     */
    private void look(MenuIcon icon, String modernNames, String legacyName, int data) {
        Material material = null;
        if (modern) {
            for (String name : modernNames.split("\\|")) {
                material = Material.getMaterial(name);
                if (material != null) break;
            }
        } else {
            material = Material.getMaterial(legacyName);
        }
        short value = modern ? 0 : (short) data;
        if (material == null) {
            material = Material.getMaterial("PAPER");
            value = 0;
        }
        looks.put(icon, new Look(material, value));
    }

    @SuppressWarnings("deprecation")
    ItemStack create(MenuItem item) {
        final Look look = looks.get(item.icon());
        final ItemStack stack = look.data() == 0
                ? new ItemStack(look.material(), item.amount())
                : new ItemStack(look.material(), item.amount(), look.data());
        final ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;
        meta.setDisplayName(item.name());
        meta.setLore(item.lore());
        meta.addItemFlags(ItemFlag.values());
        if (meta instanceof SkullMeta skull && item.head() != null) skin(skull, item);
        stack.setItemMeta(meta);
        return stack;
    }

    /**
     * Only a player online here: their profile is complete, so no lookup to the Mojang API on the game thread.
     */
    @SuppressWarnings("deprecation")
    private static void skin(SkullMeta skull, MenuItem item) {
        final Player owner = Bukkit.getPlayer(item.head());
        if (owner == null) return;
        try {
            if (SET_OWNING_PLAYER != null) SET_OWNING_PLAYER.invoke(skull, owner);
            else skull.setOwner(owner.getName());
        } catch (Throwable ignored) {
            // the default head
        }
    }
}
