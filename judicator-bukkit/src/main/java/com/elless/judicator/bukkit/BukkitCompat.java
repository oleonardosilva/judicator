package com.elless.judicator.bukkit;

import org.bukkit.entity.Player;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * What Spigot/Paper added after 1.8.8, the API the module compiles against: looked up once, with the 1.8 way as the
 * fallback.
 */
final class BukkitCompat {

    /** {@code Player#getLocale()}, 1.12+; before it, {@code Player.Spigot#getLocale()} */
    private static final MethodHandle PLAYER_LOCALE = find(Player.class, "getLocale", MethodType.methodType(String.class));

    private BukkitCompat() {
    }

    static String locale(Player player) {
        try {
            if (PLAYER_LOCALE != null) return (String) PLAYER_LOCALE.invoke(player);
            return player.spigot().getLocale();
        } catch (Throwable e) {
            return null;
        }
    }

    /**
     * {@code libraries:} in plugin.yml (Spigot/Paper 1.16.5+).
     */
    static boolean hasLibraryLoader() {
        return classExists("org.bukkit.plugin.java.LibraryLoader");
    }

    static boolean classExists(String name) {
        try {
            Class.forName(name, false, BukkitCompat.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    static MethodHandle find(Class<?> owner, String name, MethodType type) {
        try {
            return MethodHandles.publicLookup().findVirtual(owner, name, type);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            return null;
        }
    }
}
