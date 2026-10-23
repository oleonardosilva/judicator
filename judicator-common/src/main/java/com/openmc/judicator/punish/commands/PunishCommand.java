package com.openmc.judicator.punish.commands;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.commons.command.CommandArgs;
import com.openmc.judicator.commons.command.CoreCommand;
import com.openmc.judicator.platform.JudicatorSender;
import com.openmc.judicator.punish.ConfiguredReason;
import com.openmc.judicator.punish.PunishUtils;
import com.openmc.judicator.punish.Punishment;
import com.openmc.judicator.punish.PunishmentBuilder;
import com.openmc.judicator.punish.data.cache.ReasonCache;
import com.openmc.judicator.punish.rules.PunishCooldown;
import com.openmc.judicator.punish.types.PunishPermissions;
import com.openmc.judicator.punish.types.PunishType;

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
        final boolean silent = args.consumeFlag(PunishTypeCommand.SILENT_FLAG);
        if (silent && !silentAllowed(source)) return;
        if (!judicator.getImmuneCache().canPunish(source, targetName, null)) return;
        final String reason = args.remaining();
        final ReasonCache reasonCache = judicator.getReasonCache();

        if (reason.isBlank()) {
            if (source.isPlayer()) {
                final List<Punishment> history = judicator.getPunishService().findAllByUsername(targetName);
                final List<ConfiguredReason> usable = reasonCache.getReasons().stream()
                        .filter(configured -> canUseReason(source, configured))
                        .toList();
                source.sendMessage(PunishUtils.getPunishmentsMessage(messages(source), targetName, usable,
                        configured -> configured.stepFor(countPrevious(history, configured))));
            }
            return;
        }

        final Optional<ConfiguredReason> optReason = reasonCache.getReason(reason);
        if (optReason.isEmpty()) {
            source.sendMessage(PunishUtils.getMessage(messages(source), "error", "reason-not-found"));
            return;
        }

        final ConfiguredReason configuredReason = optReason.get();
        if (!canUseReason(source, configuredReason)) {
            source.sendMessage(PunishUtils.getMessage(messages(source), "error", "reason-no-permission"));
            return;
        }

        final ConfiguredReason.Step step = configuredReason.isProgressive()
                ? configuredReason.stepFor(countPrevious(judicator.getPunishService().findAllByUsername(targetName), configuredReason))
                : configuredReason.stepFor(0);

        if (!judicator.getImmuneCache().canPunish(source, targetName, PunishCooldown.categoryOf(step.type()))) return;

        final PunishmentBuilder builder = new PunishmentBuilder()
                .type(step.type())
                .reason(reason)
                .silent(silent);

        if (!step.isPermanent()) {
            builder.duration(step.duration());
        }

        if (!PunishmentCommandSupport.passesStaffRules(judicator, source, builder, targetName)) return;
        if (!PunishmentCommandSupport.resolveTarget(judicator, source, builder, targetName, configuredReason.isIp())) return;
        PunishmentCommandSupport.submit(judicator, source, builder);
    }

    @Override
    public List<String> suggest(JudicatorSender sender, String[] args) {
        final int reasonStart = args.length >= 2 && args[1].equalsIgnoreCase(PunishTypeCommand.SILENT_FLAG) ? 2 : 1;
        if (args.length > reasonStart) {
            final String input = String.join(" ", Arrays.copyOfRange(args, reasonStart, args.length)).toLowerCase();
            return judicator.getReasonCache().getReasons().stream()
                    .filter(reason -> canUseReason(sender, reason))
                    .map(ConfiguredReason::getReason)
                    .filter(reason -> reason.toLowerCase().contains(input))
                    .toList();
        }
        return super.suggest(sender, args);
    }

    private boolean canUseReason(JudicatorSender sender, ConfiguredReason reason) {
        return judicator.getReasonCache().canUse(sender, reason);
    }

    private static int countPrevious(List<Punishment> history, ConfiguredReason reason) {
        return (int) history.stream()
                .filter(punishment -> !punishment.isRevoked())
                .filter(punishment -> punishment.getType() != PunishType.KICK)
                .filter(punishment -> punishment.getReason().map(r -> r.equalsIgnoreCase(reason.getReason())).orElse(false))
                .count();
    }

}
