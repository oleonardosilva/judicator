package com.elless.judicator.reports.commands;

import com.elless.judicator.Judicator;
import com.elless.judicator.commons.command.CommandArgs;
import com.elless.judicator.commons.command.CoreCommand;
import com.elless.judicator.platform.JudicatorPlayer;
import com.elless.judicator.platform.JudicatorSender;
import com.elless.judicator.punish.PunishUtils;
import com.elless.judicator.punish.types.PunishPermissions;
import com.elless.judicator.reports.ReportService;
import org.spongepowered.configurate.ConfigurationNode;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@code /report <player> <reason>}: any player tells the staff about another one. With
 * {@code reports.require-permission: true} only players with {@code judicator.report} may.
 */
public class ReportCommand extends CoreCommand {

    public ReportCommand(Judicator judicator) {
        super(judicator, "report", List.of(), PunishPermissions.REPORT.getPermission(), PunishPermissions.ADMIN.getPermission());
    }

    @Override
    public boolean canUse(JudicatorSender sender) {
        return !judicator.getConfig().node("reports", "require-permission").getBoolean(false) || super.canUse(sender);
    }

    @Override
    public void execute(JudicatorSender source, CommandArgs args) {
        final ConfigurationNode messages = messages(source);
        final Optional<JudicatorPlayer> reporter = source.asPlayer();
        if (reporter.isEmpty()) {
            source.sendMessage(PunishUtils.getMessage(messages, "reports", "only-players"));
            return;
        }
        final Optional<String> target = args.word();
        final String reason = args.remaining().trim();
        if (target.isEmpty() || reason.isEmpty()) {
            sendUsage(source, getName());
            return;
        }
        final ReportService.Creation creation = judicator.getReportService().report(reporter.get(), target.get(), reason);
        final Map<String, String> placeholders = Map.of(
                "target", creation.report() == null ? target.get() : creation.report().getTargetName(),
                "id", creation.report() == null ? "" : String.valueOf(creation.report().getId()),
                "seconds", String.valueOf(creation.secondsLeft()));
        final String key = switch (creation.outcome()) {
            case CREATED -> "created";
            case SELF -> "self";
            case EXEMPT -> "exempt";
            case UNKNOWN_PLAYER -> "unknown";
            case OFFLINE -> "offline";
            case COOLDOWN -> "cooldown";
            case DUPLICATE -> "duplicate";
            case FAILED -> "failed";
        };
        source.sendMessage(PunishUtils.format(messages, placeholders, "reports", key));
    }

}
