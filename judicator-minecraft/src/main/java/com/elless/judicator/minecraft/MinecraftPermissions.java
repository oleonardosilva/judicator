package com.elless.judicator.minecraft;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.permissions.Permissions;

/**
 * The {@code judicator.*} permissions of a player, as the loader gets them (a permissions mod such as LuckPerms, or the
 * op level without one).
 */
public interface MinecraftPermissions {

    boolean has(ServerPlayer player, String permission);

    /**
     * Given on purpose (a permissions mod): never from the op level, like {@code judicator.level.<N>}.
     */
    boolean explicit(ServerPlayer player, String permission);

    /**
     * Without a permissions mod: what everyone may do ({@code default: true} on Spigot/Paper), the rest from the op
     * level of the vanilla {@code /ban}.
     */
    static PermissionLevel defaultLevel(String permission) {
        return "judicator.report".equals(permission) ? PermissionLevel.ALL : PermissionLevel.ADMINS;
    }

    /**
     * {@link #defaultLevel} of the player.
     */
    static boolean byDefault(ServerPlayer player, String permission) {
        return defaultLevel(permission) == PermissionLevel.ALL || player.permissions().hasPermission(Permissions.COMMANDS_ADMIN);
    }
}
