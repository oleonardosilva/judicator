package com.elless.judicator.menus.screens;

import com.elless.judicator.menus.ListOptions;
import com.elless.judicator.menus.Menu;
import com.elless.judicator.menus.MenuContext;
import com.elless.judicator.menus.MenuIcon;
import com.elless.judicator.menus.MenuItem;
import com.elless.judicator.menus.Screen;
import com.elless.judicator.notes.Note;
import com.elless.judicator.players.PlayerRef;

import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * The staff notes about a player ({@code /notes}), with the whole text; a click deletes one (after a confirmation),
 * and a button writes a new one.
 */
public record NotesScreen(String player, int page, ListOptions options) implements Screen {

    static final ListOptions.Spec SPEC = new ListOptions.Spec(List.of("newest", "oldest"), List.of(), List.of());

    public NotesScreen(String player, int page) {
        this(player, page, SPEC.defaults());
    }

    @Override
    public Menu build(MenuContext context) {
        if (!context.canUse("notes")) return context.denied();
        final PlayerRef ref = context.judicator().getPlayerDirectory().resolve(player);
        // the notes of one player: a few, read at once
        final Comparator<Note> newest = Comparator.comparing(Note::getCreatedAt).reversed();
        final List<Note> notes = context.judicator().getNoteService().findOf(ref, Math.max(1, context.judicator().getNoteService().countOf(ref)), 0)
                .stream()
                .filter(note -> options.matches(note.getContent(), note.getAuthor()))
                .sorted(options.order().equals("oldest") ? newest.reversed() : newest)
                .toList();
        final int shown = MenuContext.clamp(page, notes.size());
        final DateTimeFormatter dates = DateTimeFormatter.ofPattern(context.messages().node("date-format").getString("dd/MM/yyyy HH:mm"));
        final boolean deletes = context.canUse("delnote");
        final List<MenuItem> entries = MenuContext.slice(notes, shown, note -> entry(context, note, dates, deletes));

        // 51: no status filter here
        final Map<Integer, MenuItem> extras = context.canUse("note")
                ? Map.of(51, context.item(MenuIcon.NOTE, Map.of("nickname", player), "notes", "add")
                .onClick(() -> context.menus().askCommand(context.viewer(), context.text(Map.of(), "player", "note-what"),
                        "note", text -> player + " " + text)))
                : Map.of();
        return context.page(context.text(Map.of("nickname", player, "count", String.valueOf(notes.size())), "notes", "title"),
                entries, shown, notes.size(), SPEC, options, (other, number) -> new NotesScreen(player, number, other), extras);
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
