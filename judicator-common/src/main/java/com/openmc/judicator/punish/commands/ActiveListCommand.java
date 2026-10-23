package com.openmc.judicator.punish.commands;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.commons.Pagination;
import com.openmc.judicator.commons.command.CommandArgs;
import com.openmc.judicator.commons.command.CoreCommand;
import com.openmc.judicator.platform.JudicatorSender;
import com.openmc.judicator.punish.PunishUtils;
import com.openmc.judicator.punish.Punishment;
import com.openmc.judicator.punish.types.PunishPermissions;
import com.openmc.judicator.punish.types.PunishType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.spongepowered.configurate.ConfigurationNode;

import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

/**
 * {@code /banlist [page]} and {@code /mutelist [page]}: punishments in force, newest first.
 */
public class ActiveListCommand extends CoreCommand {

    private final PunishType[] types;

    private ActiveListCommand(Judicator judicator, String name, PunishPermissions permission, PunishType... types) {
        super(judicator, name, List.of(), permission.getPermission(), PunishPermissions.ADMIN.getPermission());
        this.types = types;
    }

    public static List<CoreCommand> all(Judicator judicator) {
        return List.of(
                new ActiveListCommand(judicator, "banlist", PunishPermissions.BANLIST, PunishType.BAN, PunishType.TEMPBAN),
                new ActiveListCommand(judicator, "mutelist", PunishPermissions.MUTELIST, PunishType.MUTE, PunishType.TEMPMUTE));
    }

    @Override
    public void execute(JudicatorSender source, CommandArgs args) {
        final OptionalInt requested = Pagination.parsePage(args.word());
        if (requested.isEmpty()) {
            sendUsage(source, getName());
            return;
        }

        final ConfigurationNode messages = messages(source);
        final int total = judicator.getPunishService().countActive(types);
        if (total == 0) {
            source.sendMessage(PunishUtils.getMessage(messages, getName(), "none"));
            return;
        }

        final int pages = Pagination.pages(total);
        final int page = Pagination.clamp(requested.getAsInt(), pages);
        final List<Punishment> shown = judicator.getPunishService().findActive(Pagination.PAGE_SIZE, Pagination.offset(page), types);

        final TextComponent.Builder text = Component.text().append(PunishUtils.format(messages, Map.of(
                "count", String.valueOf(total),
                "page", String.valueOf(page),
                "pages", String.valueOf(pages)), getName(), "title"));
        for (Punishment punishment : shown) {
            text.append(PunishUtils.getMessageList(messages, punishment, getName(), "line")
                    .hoverEvent(HoverEvent.showText(PunishUtils.getMessageList(messages, punishment, getName(), "hover")))
                    .clickEvent(ClickEvent.runCommand("/pview " + punishment.getId())));
        }
        text.append(Pagination.footer(messages, page, pages, next -> "/" + getName() + " " + next));
        source.sendMessage(text.build());
    }

    @Override
    public List<String> suggest(JudicatorSender sender, String[] args) {
        return List.of();
    }

}
