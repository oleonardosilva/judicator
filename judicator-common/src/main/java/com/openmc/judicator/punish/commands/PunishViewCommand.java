package com.openmc.judicator.punish.commands;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.commons.command.CommandArgs;
import com.openmc.judicator.commons.command.CoreCommand;
import com.openmc.judicator.platform.JudicatorSender;
import com.openmc.judicator.punish.PunishUtils;
import com.openmc.judicator.punish.Punishment;
import com.openmc.judicator.punish.types.PunishPermissions;

import java.util.List;
import java.util.Optional;

public class PunishViewCommand extends CoreCommand {

    public PunishViewCommand(Judicator judicator) {
        super(judicator, "punishview", List.of("pview"), PunishPermissions.VIEW.getPermission(), PunishPermissions.ADMIN.getPermission());
    }

    @Override
    public void execute(JudicatorSender source, CommandArgs args) {
        final Optional<Long> optId = args.word().flatMap(PunishUtils::parseId);
        if (optId.isEmpty()) {
            sendUsage(source, "punishview");
            return;
        }

        final Optional<Punishment> optPunishment = judicator.getPunishService().findById(optId.get());
        if (optPunishment.isEmpty()) {
            source.sendMessage(PunishUtils.getMessage(messages(), "error", "punish-not-found"));
            return;
        }

        source.sendMessage(PunishUtils.getMessageList(messages(), optPunishment.get(), "runners", "punishment-view"));
    }

    @Override
    public List<String> suggest(JudicatorSender sender, String[] args) {
        return List.of();
    }

}
