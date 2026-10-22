package com.openmc.judicator.punish.commands;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.commons.command.CommandArgs;
import com.openmc.judicator.commons.command.CoreCommand;
import com.openmc.judicator.platform.JudicatorSender;
import com.openmc.judicator.punish.ConfiguredReason;
import com.openmc.judicator.punish.PunishUtils;
import com.openmc.judicator.punish.PunishmentBuilder;
import com.openmc.judicator.punish.data.cache.ReasonCache;
import com.openmc.judicator.punish.types.PunishPermissions;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class PunishCommand extends CoreCommand {

    public PunishCommand(Judicator judicator) {
        super(judicator, "punish", List.of("punir", "castigar"), PunishPermissions.PUNISH.getPermission(), PunishPermissions.ADMIN.getPermission());
    }

    @Override
    public void execute(JudicatorSender source, CommandArgs args) {
        final Optional<String> optTarget = args.word();
        if (optTarget.isEmpty()) {
            sendUsage(source, "punish");
            return;
        }
        final String targetName = optTarget.get();
        if (!judicator.getImmuneCache().canPunish(source, targetName)) return;
        final String reason = args.remaining();
        final ReasonCache reasonCache = judicator.getReasonCache();

        if (reason.isBlank()) {
            if (source.isPlayer()) {
                source.sendMessage(PunishUtils.getPunishmentsMessage(messages(), targetName, reasonCache.getReasons()));
            }
            return;
        }

        final Optional<ConfiguredReason> optReason = reasonCache.getReason(reason);
        if (optReason.isEmpty()) {
            source.sendMessage(PunishUtils.getMessage(messages(), "error", "reason-not-found"));
            return;
        }

        final ConfiguredReason configuredReason = optReason.get();

        final PunishmentBuilder builder = new PunishmentBuilder()
                .type(configuredReason.getType())
                .reason(reason);

        if (!configuredReason.isPermanent()) {
            builder.duration(configuredReason.getDuration());
        }

        PunishmentCommandSupport.resolveTarget(judicator, source, builder, targetName, configuredReason.isIp());
        PunishmentCommandSupport.submit(judicator, source, builder);
    }

    @Override
    public List<String> suggest(JudicatorSender sender, String[] args) {
        if (args.length >= 2) {
            final String input = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).toLowerCase();
            return judicator.getReasonCache().getReasons().stream()
                    .map(ConfiguredReason::getReason)
                    .filter(reason -> reason.toLowerCase().contains(input))
                    .toList();
        }
        return super.suggest(sender, args);
    }

}
