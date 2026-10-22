package com.openmc.judicator.commons;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.punish.PunishUtils;
import com.openmc.judicator.punish.Punishment;
import com.openmc.judicator.punish.types.PunishPermissions;
import com.openmc.judicator.warns.Warn;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.spongepowered.configurate.ConfigurationNode;

import java.util.Map;

public class StaffNotifier {

    private final Judicator judicator;

    public StaffNotifier(Judicator judicator) {
        this.judicator = judicator;
    }

    public void notifyPunishment(Punishment punishment) {
        send(punishment.isSilent(), "punish", Map.of(
                "author", punishment.getPunisher(),
                "nickname", punishment.getNickname(),
                "type", punishment.getType().name(),
                "reason", punishment.getReason().filter(r -> !r.isBlank()).orElse("None")));
    }

    public void notifyWarn(Warn warn) {
        send(warn.isSilent(), "punish", Map.of(
                "author", warn.getPunisher(),
                "nickname", warn.getNickname(),
                "type", "WARN",
                "reason", warn.getReason() == null ? "None" : warn.getReason()));
    }

    public void notifyKick(String author, String nickname, String reason, boolean silent) {
        send(silent, "punish", Map.of("author", author, "nickname", nickname, "type", "KICK", "reason", reason));
    }

    public void notifyUnpunish(String author, String nickname, String type, int count, boolean silent) {
        send(silent, "unpunish", Map.of("author", author, "nickname", nickname, "type", type, "count", String.valueOf(count)));
    }

    public void sendToPermission(String permission, Component message) {
        judicator.getPlatform().getOnlinePlayers().stream()
                .filter(player -> player.hasPermission(permission))
                .forEach(player -> player.sendMessage(message));
    }

    private void send(boolean silent, String key, Map<String, String> placeholders) {
        if (!judicator.getConfig().node("staff-notifications").getBoolean(true)) return;
        final ConfigurationNode messages = judicator.getMessagesConfig();
        TextComponent message = PunishUtils.format(messages, placeholders, "notifications", key);
        if (silent) {
            final String prefix = messages.node("notifications", "silent-prefix").getString("&8[silent] ").replace("&", "§");
            message = Component.text(prefix + message.content());
            sendToPermission(PunishPermissions.NOTIFY_SILENT.getPermission(), message);
            return;
        }
        sendToPermission(PunishPermissions.NOTIFY.getPermission(), message);
    }

}
