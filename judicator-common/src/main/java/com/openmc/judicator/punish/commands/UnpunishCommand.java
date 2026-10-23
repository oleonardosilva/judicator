package com.openmc.judicator.punish.commands;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.commons.command.CommandArgs;
import com.openmc.judicator.commons.command.CoreCommand;
import com.openmc.judicator.platform.JudicatorPlayer;
import com.openmc.judicator.platform.JudicatorSender;
import com.openmc.judicator.punish.PunishService;
import com.openmc.judicator.punish.PunishUtils;
import com.openmc.judicator.punish.Punishment;
import com.openmc.judicator.punish.data.cache.PunishCache;
import com.openmc.judicator.punish.rules.PunishCooldown;
import com.openmc.judicator.punish.types.PunishPermissions;
import com.openmc.judicator.punish.types.PunishType;
import org.spongepowered.configurate.ConfigurationNode;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class UnpunishCommand extends CoreCommand {

    private final PunishType[] types;
    private final boolean mute;

    private UnpunishCommand(Judicator judicator, String name, PunishPermissions permission, boolean mute, PunishType... types) {
        super(judicator, name, List.of(), permission.getPermission(), PunishPermissions.ADMIN.getPermission());
        this.types = types;
        this.mute = mute;
    }

    public static List<CoreCommand> all(Judicator judicator) {
        return List.of(
                new UnpunishCommand(judicator, "unban", PunishPermissions.UNBAN, false, PunishType.BAN, PunishType.TEMPBAN),
                new UnpunishCommand(judicator, "unmute", PunishPermissions.UNMUTE, true, PunishType.MUTE, PunishType.TEMPMUTE)
        );
    }

    @Override
    public void execute(JudicatorSender source, CommandArgs args) {
        final Optional<String> optTarget = args.word();
        if (optTarget.isEmpty()) {
            sendUsage(source, getName());
            return;
        }
        final String targetName = optTarget.get();
        final boolean silent = args.consumeFlag(PunishTypeCommand.SILENT_FLAG);
        final String reason = args.remaining();
        if (silent && !silentAllowed(source)) return;

        final ConfigurationNode messages = messages(source);
        final PunishService punishService = judicator.getPunishService();
        final List<Punishment> active = punishService.findAllActiveByUsernameAndTypes(targetName, types);
        if (active.isEmpty()) {
            source.sendMessage(PunishUtils.getMessage(messages, "error", mute ? "not-muted" : "not-banned"));
            return;
        }

        int revoked = 0;
        for (Punishment punishment : active) {
            if (punishService.revokeNow(punishment.getId(), reason)) revoked++;
        }

        if (revoked == 0) {
            source.sendMessage(PunishUtils.getMessage(messages, "error", "unknown"));
            return;
        }

        if (mute) clearMuteCache(active);
        // a mistaken punishment, undone, can be applied again right away
        judicator.getPunishCooldown().release(mute ? PunishCooldown.MUTE : PunishCooldown.BAN, targetName);

        final String nickname = active.get(0).getNickname();
        source.sendMessage(PunishUtils.format(messages, Map.of("nickname", nickname, "count", String.valueOf(revoked)),
                "success", getName()));
        judicator.getPunishmentEffects().pardoned(active);

        final String author = source.asPlayer().map(JudicatorPlayer::getName).orElse("Console");
        judicator.getStaffNotifier().notifyUnpunish(author, nickname, mute ? "MUTE" : "BAN", revoked, silent);
    }

    private void clearMuteCache(List<Punishment> revoked) {
        final PunishCache cache = judicator.getPunishCache();
        revoked.forEach(punishment -> cache.forgetMute(punishment.getId()));
    }

}
