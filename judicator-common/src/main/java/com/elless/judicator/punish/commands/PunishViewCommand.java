package com.elless.judicator.punish.commands;

import com.elless.judicator.Judicator;
import com.elless.judicator.commons.command.CommandArgs;
import com.elless.judicator.commons.command.CoreCommand;
import com.elless.judicator.platform.JudicatorSender;
import com.elless.judicator.punish.PunishUtils;
import com.elless.judicator.punish.Punishment;
import com.elless.judicator.punish.types.PunishPermissions;

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
            source.sendMessage(PunishUtils.getMessage(messages(source), "error", "punish-not-found"));
            return;
        }

        source.sendMessage(PunishUtils.getMessageList(messages(source), optPunishment.get(), "runners", "punishment-view"));
    }

    @Override
    public List<String> suggest(JudicatorSender sender, String[] args) {
        return List.of();
    }

}
