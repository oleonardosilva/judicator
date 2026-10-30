package com.elless.judicator.punish.commands;

import com.elless.judicator.Judicator;
import com.elless.judicator.commons.command.CommandArgs;
import com.elless.judicator.commons.command.CoreCommand;
import com.elless.judicator.platform.JudicatorPlayer;
import com.elless.judicator.platform.JudicatorSender;
import com.elless.judicator.punish.PunishUtils;
import com.elless.judicator.punish.ReasonShortcuts;
import com.elless.judicator.punish.data.cache.ImmuneCache;
import com.elless.judicator.punish.handlers.KickHandler;
import com.elless.judicator.punish.rules.PunishCooldown;
import com.elless.judicator.punish.types.PunishPermissions;
import org.spongepowered.configurate.ConfigurationNode;

import java.util.List;
import java.util.Optional;

public class KickCommand extends CoreCommand {

    public KickCommand(Judicator judicator) {
        super(judicator, "kick", List.of(), PunishPermissions.KICK.getPermission(), PunishPermissions.ADMIN.getPermission());
    }

    @Override
    public void execute(JudicatorSender source, CommandArgs args) {
        final Optional<String> optTarget = args.word();
        if (optTarget.isEmpty()) {
            sendUsage(source, "kick");
            return;
        }
        final String targetName = optTarget.get();
        final boolean silent = args.consumeFlag(PunishmentFlags.SILENT);
        final ConfigurationNode messages = messages(source);
        if (silent && !silentAllowed(source)) return;
        if (!judicator.getImmuneCache().canPunish(source, targetName, ImmuneCache.KICK)) return;
        final Optional<String> remaining = ReasonShortcuts.expand(judicator, source, args.remaining());
        if (remaining.isEmpty()) return;
        final String reason = remaining.get().isEmpty() ? "None" : remaining.get();

        final Optional<JudicatorPlayer> optPlayer = judicator.getPlatform().getPlayer(targetName);
        if (optPlayer.isEmpty()) {
            source.sendMessage(PunishUtils.getMessage(messages, "error", "player-offline"));
            return;
        }

        if (!judicator.getPunishCooldown().acquire(source, PunishCooldown.KICK, targetName)) return;
        final String punisher = source.asPlayer().map(JudicatorPlayer::getName).orElse("Console");
        new KickHandler(judicator).handle(optPlayer.get(), punisher, reason, silent);
    }

    @Override
    public List<String> suggest(JudicatorSender sender, String[] args) {
        final List<String> shortcuts = ReasonShortcuts.suggest(judicator, sender, args, 1);
        return shortcuts.isEmpty() ? super.suggest(sender, args) : shortcuts;
    }

}
