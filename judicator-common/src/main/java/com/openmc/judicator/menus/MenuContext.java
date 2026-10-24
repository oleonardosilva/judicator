package com.openmc.judicator.menus;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.platform.JudicatorPlayer;
import com.openmc.judicator.punish.PunishUtils;
import com.openmc.judicator.punish.Punishment;
import com.openmc.judicator.punish.types.PunishPermissions;
import com.openmc.judicator.warns.Warn;
import com.openmc.judicator.warns.WarnUtils;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.IntFunction;

/**
 * What a screen is built with: the viewer, their messages and helpers for the texts, permissions and navigation.
 */
public final class MenuContext {

    private final Judicator judicator;
    private final MenuService menus;
    private final JudicatorPlayer viewer;
    private final ConfigurationNode messages;

    MenuContext(Judicator judicator, MenuService menus, JudicatorPlayer viewer) {
        this.judicator = judicator;
        this.menus = menus;
        this.viewer = viewer;
        this.messages = judicator.messagesFor(viewer);
    }

    public Judicator judicator() {
        return judicator;
    }

    public MenuService menus() {
        return menus;
    }

    public JudicatorPlayer viewer() {
        return viewer;
    }

    public ConfigurationNode messages() {
        return messages;
    }

    /**
     * A text of the {@code menu} section, e.g. {@code text(Map.of(), "main", "title")}.
     */
    public String text(Map<String, String> placeholders, Object... path) {
        return PunishUtils.fill(messages.node(menuPath(path)).getString("§cFind Message Error"), placeholders);
    }

    /**
     * A list of lines of the {@code menu} section; a value with line breaks (a note) takes several lines.
     */
    public List<String> lines(Map<String, String> placeholders, Object... path) {
        return split(raw(path).stream().map(line -> PunishUtils.fill(line, placeholders)).toList());
    }

    public List<String> lines(Punishment punishment, Object... path) {
        return split(PunishUtils.applyPlaceHolders(messages, raw(path), punishment));
    }

    public List<String> lines(Warn warn, Object... path) {
        return split(WarnUtils.applyPlaceHolders(messages, raw(path), warn));
    }

    /**
     * An item with the name and lore at {@code <path>.name} and {@code <path>.lore}.
     */
    public MenuItem item(MenuIcon icon, Map<String, String> placeholders, Object... path) {
        return MenuItem.of(icon, text(placeholders, append(path, "name")), lines(placeholders, append(path, "lore")));
    }

    public MenuItem item(MenuIcon icon, Punishment punishment, Object... path) {
        return MenuItem.of(icon, lines(punishment, append(path, "name")).stream().findFirst().orElse(""),
                lines(punishment, append(path, "lore")));
    }

    public MenuItem item(MenuIcon icon, Warn warn, Object... path) {
        return MenuItem.of(icon, lines(warn, append(path, "name")).stream().findFirst().orElse(""),
                lines(warn, append(path, "lore")));
    }

    /**
     * A text outside the {@code menu} section, with {@code &} as {@code §} (a status name of the chat messages).
     */
    public String message(String fallback, Object... path) {
        return messages.node(path).getString(fallback).replace("&", "§");
    }

    /**
     * Whether the viewer may use a command; a button for it only appears then (and the command checks it again).
     */
    public boolean canUse(String command) {
        return menus.command(command).map(registered -> registered.canUse(viewer)).orElse(false);
    }

    public boolean has(PunishPermissions permission) {
        return viewer.hasPermission(permission.getPermission()) || viewer.hasPermission(PunishPermissions.ADMIN.getPermission());
    }

    public Runnable open(Screen screen) {
        return () -> menus.push(viewer, screen);
    }

    public Runnable replace(Screen screen) {
        return () -> menus.replace(viewer, screen);
    }

    public Runnable back() {
        return () -> menus.back(viewer);
    }

    /**
     * A menu saying the viewer may no longer see this screen, with a way back.
     */
    public Menu denied() {
        return notice(MenuIcon.CANCEL, "denied");
    }

    /**
     * A menu saying what was asked for no longer exists (a punishment deleted meanwhile).
     */
    public Menu gone() {
        return notice(MenuIcon.EMPTY, "gone");
    }

    private Menu notice(MenuIcon icon, String key) {
        final Menu.Builder menu = Menu.builder(text(Map.of(), "main", "title"), 3);
        menu.set(13, item(icon, Map.of(), key));
        menu.set(18, item(MenuIcon.BACK, Map.of(), "navigation", "back").onClick(back()));
        menu.set(26, item(MenuIcon.CLOSE, Map.of(), "navigation", "close").onClick(() -> menus.close(viewer)));
        return menu.build();
    }

    /**
     * A list of up to {@link MenuService#PAGE_SIZE} entries in the five top rows and the navigation row below: back,
     * previous page, the page (click to refresh), next page, close, and {@code extras} in the free slots 46, 47, 51
     * and 52 of that row.
     *
     * @param pageScreen the same list on another page
     */
    public Menu page(String title, List<MenuItem> entries, int page, int total, IntFunction<Screen> pageScreen,
                     Map<Integer, MenuItem> extras) {
        final int pages = pages(total);
        final Menu.Builder menu = Menu.builder(title, 6);
        if (entries.isEmpty()) {
            menu.set(22, item(MenuIcon.EMPTY, Map.of(), "empty"));
        }
        for (int i = 0; i < entries.size() && i < MenuService.PAGE_SIZE; i++) {
            menu.set(i, entries.get(i));
        }
        final Map<String, String> placeholders = Map.of(
                "page", String.valueOf(page), "pages", String.valueOf(pages), "count", String.valueOf(total));
        menu.set(45, item(MenuIcon.BACK, placeholders, "navigation", "back").onClick(back()));
        if (page > 1) {
            menu.set(48, item(MenuIcon.PREVIOUS, Map.of("page", String.valueOf(page - 1), "pages", String.valueOf(pages)),
                    "navigation", "previous").onClick(replace(pageScreen.apply(page - 1))));
        }
        menu.set(49, item(MenuIcon.PAGE, placeholders, "navigation", "page").amount(page).onClick(replace(pageScreen.apply(page))));
        if (page < pages) {
            menu.set(50, item(MenuIcon.NEXT, Map.of("page", String.valueOf(page + 1), "pages", String.valueOf(pages)),
                    "navigation", "next").onClick(replace(pageScreen.apply(page + 1))));
        }
        menu.set(53, item(MenuIcon.CLOSE, placeholders, "navigation", "close").onClick(() -> menus.close(viewer)));
        extras.forEach((slot, item) -> {
            if (slot == 46 || slot == 47 || slot == 51 || slot == 52) menu.set(slot, item);
        });
        return menu.build();
    }

    /**
     * The entries of one page of a list already in memory.
     */
    public static <T> List<MenuItem> slice(List<T> all, int page, Function<T, MenuItem> toItem) {
        final int from = Math.min(all.size(), offset(page));
        return all.subList(from, Math.min(all.size(), from + MenuService.PAGE_SIZE)).stream().map(toItem).toList();
    }

    public static int pages(int total) {
        return Math.max(1, (total + MenuService.PAGE_SIZE - 1) / MenuService.PAGE_SIZE);
    }

    /**
     * @return the page, moved into 1..pages
     */
    public static int clamp(int page, int total) {
        return Math.max(1, Math.min(page, pages(total)));
    }

    public static int offset(int page) {
        return (page - 1) * MenuService.PAGE_SIZE;
    }

    /**
     * Wraps a long text (a note, a reason) into lines of about 36 characters.
     */
    public static String wrap(String text) {
        final StringBuilder wrapped = new StringBuilder();
        int line = 0;
        for (String word : text.split(" ")) {
            if (line > 0 && line + word.length() > 36) {
                wrapped.append('\n');
                line = 0;
            } else if (line > 0) {
                wrapped.append(' ');
                line++;
            }
            wrapped.append(word);
            line += word.length();
        }
        return wrapped.toString();
    }

    /**
     * The lines at the path: a list, or one text.
     */
    private List<String> raw(Object... path) {
        final ConfigurationNode node = messages.node(menuPath(path));
        if (!node.isList()) return node.virtual() ? List.of() : List.of(node.getString(""));
        try {
            final List<String> list = node.getList(String.class);
            return list == null ? List.of() : list;
        } catch (SerializationException e) {
            return List.of();
        }
    }

    /**
     * Each line break of a filled line starts a new line, in the colour the line started with.
     */
    private static List<String> split(List<String> filled) {
        final List<String> lines = new ArrayList<>();
        for (String line : filled) {
            final String[] parts = line.split("\n", -1);
            final String colour = line.startsWith("§") && line.length() > 1 ? line.substring(0, 2) : "";
            for (int i = 0; i < parts.length; i++) lines.add(i == 0 ? parts[i] : colour + parts[i]);
        }
        return lines;
    }

    private static Object[] menuPath(Object... path) {
        final Object[] full = new Object[path.length + 1];
        full[0] = "menu";
        System.arraycopy(path, 0, full, 1, path.length);
        return full;
    }

    private static Object[] append(Object[] path, Object last) {
        final Object[] full = Arrays.copyOf(path, path.length + 1);
        full[path.length] = last;
        return full;
    }
}
