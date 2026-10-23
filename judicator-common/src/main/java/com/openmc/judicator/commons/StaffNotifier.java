package com.openmc.judicator.commons;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.punish.PunishUtils;
import com.openmc.judicator.punish.Punishment;
import com.openmc.judicator.punish.types.PunishPermissions;
import com.openmc.judicator.warns.Warn;
import net.kyori.adventure.text.Component;
import org.spongepowered.configurate.ConfigurationNode;

import java.util.Map;
import java.util.function.Function;

public class StaffNotifier {

    private final Judicator judicator;

    public StaffNotifier(Judicator judicator) {
        this.judicator = judicator;
    }

    public void notifyPunishment(Punishment punishment) {
        send(punishment.isSilent(), "punish", Map.of(
                "author", punishment.getPunisher(),
                "nickname", punishment.getNickname(),
                "type", punishment.getDisplayType(),
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

    /**
     * @param change {@code edit.change-duration} or {@code edit.change-reason}: what was changed, shown after the id
     */
    public void notifyEdit(String author, Punishment punishment, String change) {
        if (!judicator.getConfig().node("staff-notifications").getBoolean(true)) return;
        sendToPermission(PunishPermissions.NOTIFY.getPermission(), messages -> PunishUtils.format(messages, Map.of(
                "author", author,
                "id", String.valueOf(punishment.getId()),
                "nickname", punishment.getNickname(),
                "change", PunishUtils.getMessageList(messages, punishment, "edit", change).content()), "notifications", "edit"));
    }

    public void notifyRollback(String author, String staff, long periodMillis, int punishments, int warns) {
        if (!judicator.getConfig().node("staff-notifications").getBoolean(true)) return;
        sendToPermission(PunishPermissions.NOTIFY.getPermission(), messages -> PunishUtils.format(messages, Map.of(
                "author", author,
                "staff", staff,
                "period", DateTimeOffsetParser.format(messages, periodMillis),
                "punishments", String.valueOf(punishments),
                "warns", String.valueOf(warns)), "notifications", "rollback"));
    }

    public void sendToPermission(String permission, Component message) {
        sendToPermission(permission, messages -> message);
    }

    /**
     * @param message built from the messages in each recipient's language
     */
    public void sendToPermission(String permission, Function<ConfigurationNode, Component> message) {
        judicator.getPlatform().getOnlinePlayers().stream()
                .filter(player -> player.hasPermission(permission))
                .forEach(player -> player.sendMessage(message.apply(judicator.messagesFor(player))));
    }

    private void send(boolean silent, String key, Map<String, String> placeholders) {
        if (!judicator.getConfig().node("staff-notifications").getBoolean(true)) return;
        if (silent) {
            sendToPermission(PunishPermissions.NOTIFY_SILENT.getPermission(), messages -> {
                final String prefix = messages.node("notifications", "silent-prefix").getString("&8[silent] ").replace("&", "§");
                return Component.text(prefix + PunishUtils.format(messages, placeholders, "notifications", key).content());
            });
            return;
        }
        sendToPermission(PunishPermissions.NOTIFY.getPermission(),
                messages -> PunishUtils.format(messages, placeholders, "notifications", key));
    }

}
