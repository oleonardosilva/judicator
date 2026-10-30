package com.elless.judicator.minecraft;

import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.util.Tristate;
import net.minecraft.server.level.ServerPlayer;

/**
 * Any permission straight from LuckPerms, also the ones the permission API of the loader cannot ask (they must be
 * registered first): {@code judicator.level.<N>}, {@code judicator.timelimit.<group>}, the permissions of the reasons.
 * Only loaded when LuckPerms is installed; no LuckPerms type leaves this class.
 */
public final class LuckPermsCheck {

    /**
     * @return null when LuckPerms has no value for it
     */
    public Boolean value(ServerPlayer player, String permission) {
        final Tristate value = LuckPermsProvider.get().getPlayerAdapter(ServerPlayer.class).getPermissionData(player)
                .checkPermission(permission);
        return value == Tristate.UNDEFINED ? null : value.asBoolean();
    }
}
