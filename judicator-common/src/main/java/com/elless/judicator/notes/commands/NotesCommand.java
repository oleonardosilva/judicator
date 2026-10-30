package com.elless.judicator.notes.commands;

import com.elless.judicator.Judicator;
import com.elless.judicator.commons.Pagination;
import com.elless.judicator.commons.command.CommandArgs;
import com.elless.judicator.commons.command.CoreCommand;
import com.elless.judicator.notes.Note;
import com.elless.judicator.platform.JudicatorSender;
import com.elless.judicator.players.PlayerRef;
import com.elless.judicator.punish.PunishUtils;
import com.elless.judicator.punish.types.PunishPermissions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.spongepowered.configurate.ConfigurationNode;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * {@code /notes <player> [page]}: the staff notes about a player, newest first; hover shows the whole text, a click
 * prepares {@code /delnote}.
 */
public class NotesCommand extends CoreCommand {

    private static final int PREVIEW_LENGTH = 40;

    public NotesCommand(Judicator judicator) {
        super(judicator, "notes", List.of(), PunishPermissions.NOTES.getPermission(), PunishPermissions.ADMIN.getPermission());
    }

    @Override
    public void execute(JudicatorSender source, CommandArgs args) {
        final Optional<String> target = args.word();
        final OptionalInt requested = Pagination.parsePage(args.word());
        if (target.isEmpty() || requested.isEmpty()) {
            sendUsage(source, getName());
            return;
        }
        final ConfigurationNode messages = messages(source);
        final PlayerRef player = judicator.getPlayerDirectory().resolve(target.get());
        final String nickname = player.name();
        final int total = judicator.getNoteService().countOf(player);
        if (total == 0) {
            source.sendMessage(PunishUtils.format(messages, Map.of("nickname", nickname), "notes", "none"));
            return;
        }
        final int pages = Pagination.pages(total);
        final int page = Pagination.clamp(requested.getAsInt(), pages);
        final DateTimeFormatter dates = DateTimeFormatter.ofPattern(messages.node("date-format").getString("dd/MM/yyyy HH:mm"));

        final TextComponent.Builder text = Component.text().append(PunishUtils.format(messages, Map.of(
                "nickname", nickname,
                "count", String.valueOf(total),
                "page", String.valueOf(page),
                "pages", String.valueOf(pages)), "notes", "title"));
        for (Note note : judicator.getNoteService().findOf(player, Pagination.PAGE_SIZE, Pagination.offset(page))) {
            final Map<String, String> placeholders = Map.of(
                    "id", String.valueOf(note.getId()),
                    "author", note.getAuthor(),
                    "createdAt", dates.format(note.getCreatedAt()),
                    "content", note.getContent(),
                    "preview", preview(note.getContent()));
            text.append(PunishUtils.format(messages, placeholders, "notes", "line")
                    .hoverEvent(HoverEvent.showText(PunishUtils.format(messages, placeholders, "notes", "hover")))
                    .clickEvent(ClickEvent.suggestCommand("/delnote " + note.getId())));
        }
        text.append(Pagination.footer(messages, page, pages, p -> "/notes " + nickname + " " + p));
        source.sendMessage(text.build());
    }

    private static String preview(String content) {
        return content.length() <= PREVIEW_LENGTH ? content : content.substring(0, PREVIEW_LENGTH) + "...";
    }

}
