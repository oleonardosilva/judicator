package com.elless.judicator.menus;

import com.elless.judicator.Judicator;
import com.elless.judicator.platform.JudicatorPlayer;
import com.elless.judicator.punish.PunishUtils;
import com.elless.judicator.punish.Punishment;
import com.elless.judicator.punish.types.PunishPermissions;
import com.elless.judicator.warns.Warn;
import com.elless.judicator.warns.WarnUtils;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.IntFunction;

/**
 * What a screen is built with: the viewer, their messages and helpers for the texts, permissions and navigation.
 */
public final class MenuContext {

    /** a list's entries: seven columns of four rows, from the second slot of the second row (a margin around them) */
    public static final int ENTRY_COLUMNS = 7;
    public static final int ENTRY_ROWS = 4;
    /** the first slot of a list's navigation row, the bottom one */
    public static final int NAVIGATION = 45;

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
     * A list of up to {@link MenuService#PAGE_SIZE} entries in the middle ({@link #entrySlot}) and the navigation row
     * at the bottom: back, previous page, the page (click to refresh), next page, close, and {@code extras} in the free
     * slots 46, 47, 51 and 52 of that row.
     *
     * @param pageScreen the same list on another page
     */
    public Menu page(String title, List<MenuItem> entries, int page, int total, IntFunction<Screen> pageScreen,
                     Map<Integer, MenuItem> extras) {
        return page(title, entries, page, total, pageScreen, extras, null);
    }

    /**
     * A list with its order, filters and search: the buttons for each (46 order, 47 filter, 51 status, 52 search;
     * {@code extras} may take a slot a list does not use) and, while anything differs from the defaults, the page
     * button clears it all.
     *
     * @param screen the same list with other options, on a page
     */
    public Menu page(String title, List<MenuItem> entries, int page, int total, ListOptions.Spec spec, ListOptions options,
                     BiFunction<ListOptions, Integer, Screen> screen, Map<Integer, MenuItem> extras) {
        final Map<Integer, MenuItem> controls = new HashMap<>(extras);
        if (spec.orders().size() > 1) {
            controls.put(46, cycle(MenuIcon.SORT, "sort", spec.orders(), options.order(), value -> screen.apply(options.withOrder(value), 1)));
        }
        if (spec.filters().size() > 1) {
            controls.put(47, cycle(MenuIcon.FILTER, "filter", spec.filters(), options.filter(), value -> screen.apply(options.withFilter(value), 1)));
        }
        if (spec.statuses().size() > 1) {
            controls.put(51, cycle(MenuIcon.STATUS, "status", spec.statuses(), options.status(), value -> screen.apply(options.withStatus(value), 1)));
        }
        final Map<String, String> search = Map.of("value", options.search());
        controls.put(52, item(MenuIcon.SEARCH, search, "lists", options.searches() ? "search-active" : "search")
                .onClick(() -> askOptions(spec, options, screen)));
        final boolean changed = !options.equals(spec.defaults());
        return page(title, entries, page, total, number -> screen.apply(options, number), controls,
                changed ? screen.apply(spec.defaults(), 1) : null);
    }

    /**
     * A form with the order, the filters and the search where the renderer has forms (dialogs); otherwise only the
     * search, asked in the chat (the order and the filters have their buttons).
     */
    private void askOptions(ListOptions.Spec spec, ListOptions options, BiFunction<ListOptions, Integer, Screen> screen) {
        final List<MenuRenderer.Field> fields = new ArrayList<>();
        if (spec.orders().size() > 1) fields.add(choice("order", "sort", spec.orders(), options.order()));
        if (spec.filters().size() > 1) fields.add(choice("filter", "filter", spec.filters(), options.filter()));
        if (spec.statuses().size() > 1) fields.add(choice("status", "status", spec.statuses(), options.status()));
        fields.add(new MenuRenderer.Text("search", text(Map.of(), "lists", "form", "search"), options.search()));
        final MenuRenderer.Form form = new MenuRenderer.Form(text(Map.of(), "lists", "form", "title"), fields,
                text(Map.of(), "lists", "form", "apply"), text(Map.of(), "lists", "form", "cancel"));
        final boolean asked = menus.askForm(viewer, form, answers -> screen.apply(new ListOptions(
                answers.getOrDefault("order", options.order()),
                answers.getOrDefault("filter", options.filter()),
                answers.getOrDefault("status", options.status()),
                answers.getOrDefault("search", "")), 1));
        if (!asked) {
            menus.askReplacing(viewer, text(Map.of(), "lists", "search-what"), answer -> screen.apply(options.withSearch(answer), 1));
        }
    }

    private MenuRenderer.Choice choice(String key, String kind, List<String> values, String selected) {
        return new MenuRenderer.Choice(key, text(Map.of(), "lists", kind, "label"),
                values.stream().map(value -> new MenuRenderer.Option(value, value(value))).toList(), selected);
    }

    /**
     * A button that goes to the next value; its lore lists them all, the current one marked.
     */
    private MenuItem cycle(MenuIcon icon, String kind, List<String> values, String current, Function<String, Screen> screen) {
        final List<String> lore = new ArrayList<>();
        for (String value : values) {
            lore.add(text(Map.of("value", value(value)), "lists", value.equals(current) ? "option-current" : "option"));
        }
        lore.add("");
        lore.add(text(Map.of("value", value(ListOptions.next(values, current))), "lists", "next"));
        return MenuItem.of(icon, text(Map.of("value", value(current)), "lists", kind, "name"), lore)
                .onClick(replace(screen.apply(ListOptions.next(values, current))));
    }

    /**
     * The name of a value of {@code menu.lists.values}.
     */
    public String value(String key) {
        return text(Map.of(), "lists", "values", key);
    }

    private Menu page(String title, List<MenuItem> entries, int page, int total, IntFunction<Screen> pageScreen,
                      Map<Integer, MenuItem> extras, Screen clear) {
        final int pages = pages(total);
        final Menu.Builder menu = Menu.builder(title, 6).list();
        if (entries.isEmpty()) {
            menu.set(22, item(MenuIcon.EMPTY, Map.of(), "empty"));
        }
        for (int i = 0; i < entries.size() && i < MenuService.PAGE_SIZE; i++) {
            menu.set(entrySlot(i), entries.get(i));
        }
        final Map<String, String> placeholders = Map.of(
                "page", String.valueOf(page), "pages", String.valueOf(pages), "count", String.valueOf(total));
        menu.set(NAVIGATION, item(MenuIcon.BACK, placeholders, "navigation", "back").onClick(back()));
        if (page > 1) {
            menu.set(48, item(MenuIcon.PREVIOUS, Map.of("page", String.valueOf(page - 1), "pages", String.valueOf(pages)),
                    "navigation", "previous").onClick(replace(pageScreen.apply(page - 1))));
        }
        menu.set(49, clear == null
                ? item(MenuIcon.PAGE, placeholders, "navigation", "page").amount(page).onClick(replace(pageScreen.apply(page)))
                : item(MenuIcon.PAGE, placeholders, "navigation", "page-filtered").amount(page).onClick(replace(clear)));
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
     * Where the entry at that index of a page goes: row by row in the seven middle columns of the four middle rows, so
     * no entry touches the edges of the inventory.
     */
    public static int entrySlot(int index) {
        return (1 + index / ENTRY_COLUMNS) * Menu.COLUMNS + 1 + index % ENTRY_COLUMNS;
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
