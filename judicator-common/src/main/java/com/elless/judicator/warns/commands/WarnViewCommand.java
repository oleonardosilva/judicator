package com.elless.judicator.warns.commands;

import com.elless.judicator.Judicator;
import com.elless.judicator.commons.command.CommandArgs;
import com.elless.judicator.commons.command.CoreCommand;
import com.elless.judicator.platform.JudicatorSender;
import com.elless.judicator.punish.PunishUtils;
import com.elless.judicator.punish.types.PunishPermissions;
import com.elless.judicator.warns.Warn;
import com.elless.judicator.warns.WarnUtils;

import java.util.List;
import java.util.Optional;

public class WarnViewCommand extends CoreCommand {

    public WarnViewCommand(Judicator judicator) {
        super(judicator, "warnview", List.of("wview"), PunishPermissions.VIEW.getPermission(), PunishPermissions.ADMIN.getPermission());
    }

    @Override
    public void execute(JudicatorSender source, CommandArgs args) {
        final Optional<Long> optId = args.word().flatMap(PunishUtils::parseId);
        if (optId.isEmpty()) {
            sendUsage(source, "warnview");
            return;
        }

        final Optional<Warn> optWarn = judicator.getWarnService().findById(optId.get());
        if (optWarn.isEmpty()) {
            source.sendMessage(WarnUtils.getMessage(messages(source), "error", "warn-not-found"));
            return;
        }

        source.sendMessage(WarnUtils.getMessageList(messages(source), optWarn.get(), "runners", "warn-view"));
    }

    @Override
    public List<String> suggest(JudicatorSender sender, String[] args) {
        return List.of();
    }

}
