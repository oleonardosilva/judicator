package com.openmc.judicator.punish.commands;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.commons.command.CommandArgs;
import com.openmc.judicator.commons.command.CoreCommand;
import com.openmc.judicator.platform.JudicatorSender;
import com.openmc.judicator.punish.PunishUtils;
import com.openmc.judicator.punish.Punishment;
import com.openmc.judicator.punish.types.PunishPermissions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.spongepowered.configurate.ConfigurationNode;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@code /blame <staff> [page]}: punishments applied by a staff member, newest first.
 */
public class BlameCommand extends CoreCommand {

    private static final int PAGE_SIZE = 10;

    public BlameCommand(Judicator judicator) {
        super(judicator, "blame", List.of("staffhistory"), PunishPermissions.BLAME.getPermission(), PunishPermissions.ADMIN.getPermission());
    }

    @Override
    public void execute(JudicatorSender source, CommandArgs args) {
        final Optional<String> optStaff = args.word();
        if (optStaff.isEmpty()) {
            sendUsage(source, "blame");
            return;
        }
        final String staff = optStaff.get();
        int page = 1;
        final Optional<String> optPage = args.word();
        if (optPage.isPresent()) {
            try {
                page = Integer.parseInt(optPage.get());
            } catch (NumberFormatException e) {
                sendUsage(source, "blame");
                return;
            }
        }

        final ConfigurationNode messages = messages();
        final int total = judicator.getPunishService().countByPunisher(staff);
        if (total == 0) {
            source.sendMessage(PunishUtils.format(messages, Map.of("staff", staff), "blame", "none"));
            return;
        }

        final int pages = (total + PAGE_SIZE - 1) / PAGE_SIZE;
        page = Math.max(1, Math.min(page, pages));
        final List<Punishment> shown = judicator.getPunishService().findByPunisher(staff, PAGE_SIZE, (page - 1) * PAGE_SIZE);

        final TextComponent.Builder text = Component.text().append(PunishUtils.format(messages, Map.of(
                "staff", staff,
                "count", String.valueOf(total),
                "page", String.valueOf(page),
                "pages", String.valueOf(pages)), "blame", "title"));
        for (Punishment punishment : shown) {
            text.append(PunishUtils.getMessageList(messages, punishment, "blame", "line")
                    .hoverEvent(HoverEvent.showText(PunishUtils.getMessageList(messages, punishment, "punish-history", "line", "hoverMessage")))
                    .clickEvent(ClickEvent.runCommand("/pview " + punishment.getId())));
        }
        if (page < pages) {
            text.append(PunishUtils.format(messages, Map.of("staff", staff, "next", String.valueOf(page + 1)), "blame", "next-page")
                    .clickEvent(ClickEvent.runCommand("/blame " + staff + " " + (page + 1))));
        }
        source.sendMessage(text.build());
    }

}
