package com.openmc.judicator.warns.commands;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.commons.command.CommandArgs;
import com.openmc.judicator.commons.command.CoreCommand;
import com.openmc.judicator.platform.JudicatorSender;
import com.openmc.judicator.punish.PunishUtils;
import com.openmc.judicator.punish.types.PunishPermissions;
import com.openmc.judicator.warns.Warn;
import com.openmc.judicator.warns.WarnService;
import com.openmc.judicator.warns.WarnUtils;
import org.spongepowered.configurate.ConfigurationNode;

import java.util.List;
import java.util.Optional;

public class UnwarnCommand extends CoreCommand {

    public UnwarnCommand(Judicator judicator) {
        super(judicator, "unwarn", List.of(), PunishPermissions.ADMIN.getPermission());
    }

    @Override
    public void execute(JudicatorSender source, CommandArgs args) {
        final Optional<Long> optId = args.word().flatMap(PunishUtils::parseId);
        if (optId.isEmpty()) {
            sendUsage(source, "unwarn");
            return;
        }
        final Long id = optId.get();
        final ConfigurationNode messages = messages();
        final WarnService warnService = judicator.getWarnService();

        final Optional<Warn> optWarn = warnService.findById(id);
        if (optWarn.isEmpty()) {
            source.sendMessage(WarnUtils.getMessage(messages, "error", "warn-not-found"));
            return;
        }

        final Warn warn = optWarn.get();
        warnService.deleteById(id);

        source.sendMessage(WarnUtils.getMessage(messages, "success", "unwarn"));

        judicator.getPlatform().getPlayer(warn.getNickname())
                .ifPresent(player -> player.sendMessage(WarnUtils.getMessage(messages, "success", "got-unwarn")));
    }

    @Override
    public List<String> suggest(JudicatorSender sender, String[] args) {
        return List.of();
    }

}
