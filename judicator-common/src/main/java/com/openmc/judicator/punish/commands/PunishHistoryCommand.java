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

public class PunishHistoryCommand extends CoreCommand {

    public PunishHistoryCommand(Judicator judicator) {
        super(judicator, "punishhistory", List.of("phistory"), PunishPermissions.HISTORY.getPermission(), PunishPermissions.ADMIN.getPermission());
    }

    @Override
    public void execute(JudicatorSender source, CommandArgs args) {
        final Optional<String> optTarget = args.word();
        if (optTarget.isEmpty()) {
            sendUsage(source, "punishhistory");
            return;
        }
        final String targetName = optTarget.get();

        final List<Punishment> punishments = judicator.getPunishService().findAllByUsername(targetName);
        if (punishments.isEmpty()) {
            source.sendMessage(PunishUtils.getMessage(messages(), "error", "angel"));
            return;
        }

        source.sendMessage(PunishUtils.getPunishmentHistoryMessage(messages(), targetName, punishments));
    }

}
