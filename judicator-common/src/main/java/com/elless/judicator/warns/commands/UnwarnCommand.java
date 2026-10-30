package com.elless.judicator.warns.commands;

import com.elless.judicator.Judicator;
import com.elless.judicator.commons.command.CommandArgs;
import com.elless.judicator.commons.command.CoreCommand;
import com.elless.judicator.platform.JudicatorPlayer;
import com.elless.judicator.platform.JudicatorSender;
import com.elless.judicator.punish.PunishUtils;
import com.elless.judicator.punish.types.PunishPermissions;
import com.elless.judicator.warns.Warn;
import com.elless.judicator.warns.WarnService;
import com.elless.judicator.warns.WarnUtils;
import com.elless.judicator.warns.handlers.UnwarnHandler;
import org.spongepowered.configurate.ConfigurationNode;

import java.util.List;
import java.util.Optional;

public class UnwarnCommand extends CoreCommand {

    public UnwarnCommand(Judicator judicator) {
        super(judicator, "unwarn", List.of(), PunishPermissions.UNWARN.getPermission(), PunishPermissions.ADMIN.getPermission());
    }

    @Override
    public void execute(JudicatorSender source, CommandArgs args) {
        final Optional<Long> optId = args.word().flatMap(PunishUtils::parseId);
        if (optId.isEmpty()) {
            sendUsage(source, "unwarn");
            return;
        }
        final Long id = optId.get();
        final ConfigurationNode messages = messages(source);
        final WarnService warnService = judicator.getWarnService();

        final Optional<Warn> optWarn = warnService.findById(id);
        if (optWarn.isEmpty()) {
            source.sendMessage(WarnUtils.getMessage(messages, "error", "warn-not-found"));
            return;
        }

        final String author = source.asPlayer().map(JudicatorPlayer::getName).orElse("Console");
        new UnwarnHandler(judicator).remove(optWarn.get(), author, true);

        source.sendMessage(WarnUtils.getMessage(messages, "success", "unwarn"));
    }

    @Override
    public List<String> suggest(JudicatorSender sender, String[] args) {
        return List.of();
    }

}
