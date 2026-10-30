package com.elless.judicator.warns.commands;

import com.elless.judicator.Judicator;
import com.elless.judicator.commons.command.CommandArgs;
import com.elless.judicator.commons.command.CoreCommand;
import com.elless.judicator.platform.JudicatorSender;
import com.elless.judicator.punish.types.PunishPermissions;
import com.elless.judicator.warns.Warn;
import com.elless.judicator.warns.WarnUtils;

import java.util.List;
import java.util.Optional;

public class WarnHistoryCommand extends CoreCommand {

    public WarnHistoryCommand(Judicator judicator) {
        super(judicator, "warnhistory", List.of("whistory"), PunishPermissions.HISTORY.getPermission(), PunishPermissions.ADMIN.getPermission());
    }

    @Override
    public void execute(JudicatorSender source, CommandArgs args) {
        final Optional<String> optTarget = args.word();
        if (optTarget.isEmpty()) {
            sendUsage(source, "warnhistory");
            return;
        }
        final String targetName = optTarget.get();

        final List<Warn> warns = judicator.getWarnService().findAllOf(judicator.getPlayerDirectory().resolve(targetName));
        if (warns.isEmpty()) {
            source.sendMessage(WarnUtils.getMessage(messages(source), "error", "angel"));
            return;
        }

        source.sendMessage(WarnUtils.getWarnHistoryMessage(messages(source), targetName, warns));
    }

}
