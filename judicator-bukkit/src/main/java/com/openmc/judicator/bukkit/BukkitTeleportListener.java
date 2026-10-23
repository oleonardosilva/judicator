package com.openmc.judicator.bukkit;

import com.openmc.judicator.punish.types.PunishPermissions;
import com.openmc.judicator.reports.TeleportRequest;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

/**
 * {@code proxy-mode}: the proxy running Judicator moved a staff member here for {@code /reports tp} and asks, through
 * that staff member's connection, to teleport them to the reported player. Only registered in {@code proxy-mode}, and
 * the proxy never passes on what a client sends on this channel; a request is still only obeyed for the player whose
 * connection carried it, and only when that player may use {@code /reports tp} here too (a backend reachable without
 * the proxy, or behind another proxy, must not teleport players who ask).
 */
final class BukkitTeleportListener implements PluginMessageListener {

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!TeleportRequest.CHANNEL.equals(channel)) return;
        TeleportRequest.decode(message)
                .filter(request -> request.staff().equals(player.getUniqueId()))
                .filter(request -> player.hasPermission(PunishPermissions.REPORTS_TP.getPermission())
                        || player.hasPermission(PunishPermissions.ADMIN.getPermission()))
                .ifPresent(request -> {
                    final Player target = Bukkit.getPlayer(request.target());
                    if (target != null) player.teleport(target);
                });
    }
}
