package com.openmc.judicator.punish.commands;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.commons.DateTimeOffsetParser;
import com.openmc.judicator.commons.Pagination;
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

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * {@code /blame <staff> [period] [page]}: punishments applied by a staff member, newest first; with a period
 * ({@code 7:d}, {@code 24:h}), only the ones started within it.
 */
public class BlameCommand extends CoreCommand {

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

        Optional<String> next = args.word();
        String period = null;
        long periodMillis = 0;
        if (next.isPresent() && next.get().contains(":")) {
            final OptionalLong millis = DateTimeOffsetParser.parseMillis(next.get());
            if (millis.isEmpty()) {
                sendUsage(source, "blame");
                return;
            }
            period = next.get();
            periodMillis = millis.getAsLong();
            next = args.word();
        }
        final OptionalInt requested = Pagination.parsePage(next);
        if (requested.isEmpty()) {
            sendUsage(source, "blame");
            return;
        }

        final ConfigurationNode messages = messages(source);
        final LocalDateTime since = period == null ? null : DateTimeOffsetParser.since(periodMillis);
        final Map<String, String> placeholders = new HashMap<>(Map.of("staff", staff));
        if (period != null) placeholders.put("period", DateTimeOffsetParser.format(messages, periodMillis));

        final int total = judicator.getPunishService().countByPunisher(staff, since);
        if (total == 0) {
            source.sendMessage(PunishUtils.format(messages, placeholders, "blame", period == null ? "none" : "none-since"));
            return;
        }

        final int pages = Pagination.pages(total);
        final int page = Pagination.clamp(requested.getAsInt(), pages);
        final List<Punishment> shown = judicator.getPunishService().findByPunisher(staff, since, Pagination.PAGE_SIZE, Pagination.offset(page));

        placeholders.put("count", String.valueOf(total));
        placeholders.put("page", String.valueOf(page));
        placeholders.put("pages", String.valueOf(pages));
        final TextComponent.Builder text = Component.text()
                .append(PunishUtils.format(messages, placeholders, "blame", period == null ? "title" : "title-since"));
        for (Punishment punishment : shown) {
            text.append(PunishUtils.getMessageList(messages, punishment, "blame", "line")
                    .hoverEvent(HoverEvent.showText(PunishUtils.getMessageList(messages, punishment, "blame", "hover")))
                    .clickEvent(ClickEvent.runCommand("/pview " + punishment.getId())));
        }
        final String base = "/blame " + staff + (period == null ? "" : " " + period) + " ";
        text.append(Pagination.footer(messages, page, pages, target -> base + target));
        source.sendMessage(text.build());
    }

}
