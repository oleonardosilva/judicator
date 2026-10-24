package com.openmc.judicator.menus.screens;

import com.openmc.judicator.menus.Menu;
import com.openmc.judicator.menus.MenuContext;
import com.openmc.judicator.menus.MenuIcon;
import com.openmc.judicator.menus.MenuItem;
import com.openmc.judicator.menus.MenuService;
import com.openmc.judicator.menus.Screen;
import com.openmc.judicator.notes.Note;
import com.openmc.judicator.players.PlayerRef;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * The staff notes about a player ({@code /notes}), newest first, with the whole text; a click deletes one (after a
 * confirmation), and a button writes a new one.
 */
public record NotesScreen(String player, int page) implements Screen {

    @Override
    public Menu build(MenuContext context) {
        if (!context.canUse("notes")) return context.denied();
        final PlayerRef ref = context.judicator().getPlayerDirectory().resolve(player);
        final int total = context.judicator().getNoteService().countOf(ref);
        final int shown = MenuContext.clamp(page, total);
        final DateTimeFormatter dates = DateTimeFormatter.ofPattern(context.messages().node("date-format").getString("dd/MM/yyyy HH:mm"));
        final boolean deletes = context.canUse("delnote");
        final List<MenuItem> entries = context.judicator().getNoteService().findOf(ref, MenuService.PAGE_SIZE, MenuContext.offset(shown))
                .stream().map(note -> entry(context, note, dates, deletes)).toList();

        final Map<Integer, MenuItem> extras = context.canUse("note")
                ? Map.of(47, context.item(MenuIcon.NOTE, Map.of("nickname", player), "notes", "add")
                .onClick(() -> context.menus().askCommand(context.viewer(), context.text(Map.of(), "player", "note-what"),
                        "note", text -> player + " " + text)))
                : Map.of();
        return context.page(context.text(Map.of("nickname", player, "count", String.valueOf(total)), "notes", "title"),
                entries, shown, total, next -> new NotesScreen(player, next), extras);
    }

    private static MenuItem entry(MenuContext context, Note note, DateTimeFormatter dates, boolean deletes) {
        final Map<String, String> placeholders = Map.of(
                "id", String.valueOf(note.getId()),
                "author", note.getAuthor(),
                "createdAt", dates.format(note.getCreatedAt()),
                "nickname", note.getNickname(),
                "content", MenuContext.wrap(note.getContent()));
        final MenuItem item = context.item(MenuIcon.NOTE, placeholders, "notes", deletes ? "entry" : "entry-read-only");
        return deletes
                ? item.onClick(context.open(ConfirmScreen.simple(MenuIcon.NOTE, "delnote", placeholders, "delnote", String.valueOf(note.getId()))))
                : item;
    }
}
