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

        final List<Punishment> punishments = judicator.getPunishService().findAllOf(judicator.getPlayerDirectory().resolve(targetName));
        if (punishments.isEmpty()) {
            source.sendMessage(PunishUtils.getMessage(messages(source), "error", "angel"));
            return;
        }

        source.sendMessage(PunishUtils.getPunishmentHistoryMessage(messages(source), targetName, punishments));
    }

}
