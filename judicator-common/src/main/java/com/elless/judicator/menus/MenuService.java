package com.elless.judicator.menus;

import com.elless.judicator.Judicator;
import com.elless.judicator.commons.ChatContext;
import com.elless.judicator.commons.command.CommandArgs;
import com.elless.judicator.commons.command.CoreCommand;
import com.elless.judicator.menus.screens.AltsScreen;
import com.elless.judicator.menus.screens.NotesScreen;
import com.elless.judicator.menus.screens.PlayerScreen;
import com.elless.judicator.menus.screens.PunishmentScreen;
import com.elless.judicator.menus.screens.PunishmentsScreen;
import com.elless.judicator.menus.screens.ReasonsScreen;
import com.elless.judicator.menus.screens.ReportScreen;
import com.elless.judicator.menus.screens.ReportsScreen;
import com.elless.judicator.menus.screens.WarnScreen;
import com.elless.judicator.menus.screens.WarnsScreen;
import com.elless.judicator.platform.JudicatorPlayer;
import com.elless.judicator.platform.JudicatorSender;
import com.elless.judicator.platform.Platform;
import com.elless.judicator.punish.Evidence;
import com.elless.judicator.punish.PunishUtils;
import com.elless.judicator.reports.commands.ReportsCommand;
import org.spongepowered.configurate.ConfigurationNode;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * The menus each staff member has open: a stack of screens per player (the last one shown, the ones "back" returns to).
 * A screen is built off the game thread and shown on it; a viewer's session is only touched on the game thread (on
 * Folia, the viewer's region: {@link Platform#runFor}).
 * Actions run the same commands a staff member would type, so the same permissions and staff rules apply.
 */
public class MenuService {

    /** entries per page of a list: {@link MenuContext#entrySlot} puts them in the middle, a margin around them */
    public static final int PAGE_SIZE = MenuContext.ENTRY_COLUMNS * MenuContext.ENTRY_ROWS;

    private final Judicator judicator;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    private static final class Session {
        private final JudicatorPlayer viewer;
        private final Deque<Screen> stack;
        private Menu current;
        private long generation;
        private boolean building;

        private Session(JudicatorPlayer viewer, Deque<Screen> stack) {
            this.viewer = viewer;
            this.stack = stack;
        }
    }

    public MenuService(Judicator judicator) {
        this.judicator = judicator;
    }

    /**
     * Whether this platform draws menus (Spigot/Paper; not the proxies).
     */
    public boolean isAvailable() {
        return platform().menus().isPresent();
    }

    /**
     * Opens a screen as the first of a new stack (what {@code /jmenu} does).
     */
    public void open(JudicatorPlayer viewer, Screen screen) {
        final Deque<Screen> stack = new ArrayDeque<>();
        stack.push(screen);
        sync(viewer, () -> start(viewer, stack));
    }

    /**
     * Goes to a screen; "back" returns to the current one.
     */
    public void push(JudicatorPlayer viewer, Screen screen) {
        sync(viewer, () -> {
            final Session session = sessions.get(viewer.getUniqueId());
            if (session == null) {
                open(viewer, screen);
                return;
            }
            session.stack.push(screen);
            build(session);
        });
    }

    /**
     * Shows a screen in place of the current one (the same screen with another page or option).
     */
    public void replace(JudicatorPlayer viewer, Screen screen) {
        sync(viewer, () -> {
            final Session session = sessions.get(viewer.getUniqueId());
            if (session == null) {
                open(viewer, screen);
                return;
            }
            session.stack.poll();
            session.stack.push(screen);
            build(session);
        });
    }

    /**
     * Returns to the screen before the current one (built again, with the data as it is now); closes the menu when
     * there is none.
     */
    public void back(JudicatorPlayer viewer) {
        back(viewer, 1);
    }

    public void back(JudicatorPlayer viewer, int screens) {
        sync(viewer, () -> {
            final Session session = sessions.get(viewer.getUniqueId());
            if (session == null) return;
            for (int i = 0; i < screens; i++) session.stack.poll();
            if (session.stack.isEmpty()) {
                close(viewer);
                return;
            }
            build(session);
        });
    }

    public void refresh(JudicatorPlayer viewer) {
        back(viewer, 0);
    }

    public void close(JudicatorPlayer viewer) {
        sync(viewer, () -> {
            sessions.remove(viewer.getUniqueId());
            platform().menus().ifPresent(renderer -> renderer.close(viewer));
        });
    }

    /**
     * A click on a slot of the menu the player sees (game thread). Ignored while the next screen is being built or an
     * action of this menu is running (a double click must not run a command twice), and when the menu is not the one
     * of the session (a click arriving after the screen changed). Every action ends in a new screen, a close or a
     * question, which releases the menu.
     */
    public void click(UUID viewer, Menu menu, int slot) {
        final Session session = sessions.get(viewer);
        if (session == null || session.building || session.current != menu) return;
        final Optional<Runnable> action = menu.item(slot).map(MenuItem::action);
        if (action.isEmpty()) return;
        session.building = true;
        action.get().run();
    }

    /**
     * The player closed the menu (game thread), also while the next screen was being built: it is then not shown.
     * Opening the next screen closes the previous one too: that menu is no longer the current one and the session
     * stays.
     */
    public void closed(UUID viewer, Menu menu) {
        final Session session = sessions.get(viewer);
        if (session != null && session.current == menu) sessions.remove(viewer);
    }

    /**
     * The player left.
     */
    public void forget(UUID viewer) {
        sessions.remove(viewer);
    }

    /**
     * Closes every menu open (the plugin is being disabled: nothing would handle the clicks any more).
     */
    public void closeAll() {
        platform().menus().ifPresent(renderer -> sessions.values().forEach(session -> renderer.close(session.viewer)));
        sessions.clear();
    }

    /**
     * Whether the player has a menu of Judicator open (or one being built).
     */
    public boolean hasOpen(UUID viewer) {
        return sessions.containsKey(viewer);
    }

    /**
     * Runs a command as the viewer off the game thread, checking its permission as typing it would, then {@code after}
     * on the game thread.
     *
     * @param confirmed the viewer confirmed it on a confirmation screen (a punishment then skips the evidence prompt)
     */
    public void run(JudicatorPlayer viewer, String command, String arguments, boolean confirmed, Runnable after) {
        run(viewer, command, confirmed ? CommandArgs.confirmed(arguments) : CommandArgs.of(arguments), after);
    }

    private void run(JudicatorPlayer viewer, String command, CommandArgs args, Runnable after) {
        platform().runAsync(() -> {
            final Optional<CoreCommand> found = command(command);
            if (found.isEmpty() || !found.get().canUse(viewer)) {
                viewer.sendMessage(PunishUtils.getMessage(judicator.messagesFor(viewer), "menu", "no-permission"));
            } else {
                try {
                    found.get().execute(viewer, args);
                } catch (RuntimeException e) {
                    judicator.getLogger().error("Error while running /{} from a menu", command, e);
                }
            }
            sync(viewer, after);
        });
    }

    /**
     * Closes the menu and asks something in the chat ({@code menu.prompt}); the answer runs a command, then the menu
     * comes back where it was. The cancel word brings the menu back without running anything.
     *
     * @param what      what to type, shown in the question
     * @param arguments the arguments of the command for an answer
     */
    public void askCommand(JudicatorPlayer viewer, String what, String command, Function<String, String> arguments) {
        askCommand(viewer, what, command, arguments, 0);
    }

    /**
     * @param leave how many screens the menu goes back after the command (1 from a confirmation screen)
     */
    public void askCommand(JudicatorPlayer viewer, String what, String command, Function<String, String> arguments, int leave) {
        ask(viewer, what, (answer, saved) -> run(viewer, command, arguments.apply(answer), false, () -> resume(viewer, saved, leave)));
    }

    /**
     * Closes the menu and asks something in the chat; the answer opens a screen on top of the ones the player had.
     */
    public void askScreen(JudicatorPlayer viewer, String what, Function<String, Screen> next) {
        ask(viewer, what, (answer, saved) -> sync(viewer, () -> {
            final Deque<Screen> stack = new ArrayDeque<>(saved);
            stack.push(next.apply(answer));
            start(viewer, stack);
        }));
    }

    /**
     * Closes the menu and asks something in the chat; the answer gives the screen that replaces the current one (the
     * same list searching for it).
     */
    public void askReplacing(JudicatorPlayer viewer, String what, Function<String, Screen> next) {
        ask(viewer, what, (answer, saved) -> sync(viewer, () -> {
            final Deque<Screen> stack = new ArrayDeque<>(saved);
            stack.poll();
            stack.push(next.apply(answer));
            start(viewer, stack);
        }));
    }

    /**
     * Shows a form in place of the current screen; once submitted, {@code next} gives the screen that replaces it (the
     * same list with other options). Cancelling brings the screen back as it was.
     *
     * @return false when the renderer has no forms (nothing was shown)
     */
    public boolean askForm(JudicatorPlayer viewer, MenuRenderer.Form form, Function<Map<String, String>, Screen> next) {
        final Optional<MenuRenderer> renderer = platform().menus();
        final Session session = sessions.get(viewer.getUniqueId());
        if (renderer.isEmpty() || session == null) return false;
        final Deque<Screen> saved = new ArrayDeque<>(session.stack);
        final boolean shown = renderer.get().form(viewer, form, answers -> {
            final Deque<Screen> stack = new ArrayDeque<>(saved);
            stack.poll();
            stack.push(next.apply(answers));
            start(viewer, stack);
        }, () -> resume(viewer, saved, 0));
        if (!shown) {
            session.building = false;
            return false;
        }
        sessions.remove(viewer.getUniqueId());
        return true;
    }

    /**
     * Asks for the evidence of a punishment on the renderer's own dialog, keeping the menu: the answer runs the command
     * as confirmed with those links and goes {@code leave} screens back; cancelling shows the screen it was asked from
     * again, with nothing applied.
     *
     * @return false when the renderer cannot ask this player (no dialogs): nothing was shown
     */
    public boolean askEvidence(JudicatorPlayer viewer, String command, String arguments, int leave) {
        final Optional<MenuRenderer> renderer = platform().menus();
        final Session session = sessions.get(viewer.getUniqueId());
        if (renderer.isEmpty() || session == null) return false;
        final Deque<Screen> saved = new ArrayDeque<>(session.stack);
        final boolean shown = renderer.get().ask(viewer, Evidence.question(judicator.messagesFor(viewer)),
                answer -> run(viewer, command, CommandArgs.confirmed(arguments, Evidence.links(answer)), () -> resume(viewer, saved, leave)),
                () -> resume(viewer, saved, 0));
        if (!shown) {
            session.building = false;
            return false;
        }
        sessions.remove(viewer.getUniqueId());
        return true;
    }

    /**
     * What an answer does, with the screens the player had when asked.
     */
    private interface Answer {
        void accept(String answer, Deque<Screen> saved);
    }

    private void ask(JudicatorPlayer viewer, String what, Answer answer) {
        sync(viewer, () -> {
            final Session session = sessions.remove(viewer.getUniqueId());
            final Deque<Screen> saved = session == null ? new ArrayDeque<>() : new ArrayDeque<>(session.stack);
            final Runnable cancel = () -> resume(viewer, saved, 0);
            final Optional<MenuRenderer> renderer = platform().menus();
            final ConfigurationNode messages = judicator.messagesFor(viewer);
            final MenuRenderer.Question question = new MenuRenderer.Question(
                    PunishUtils.fill(messages.node("menu", "question", "title").getString("&8Judicator"), Map.of("what", what)),
                    PunishUtils.fill(messages.node("menu", "question", "label").getString("Type {what}:"), Map.of("what", what)),
                    PunishUtils.fill(messages.node("menu", "question", "confirm").getString("&aOK"), Map.of()),
                    PunishUtils.fill(messages.node("menu", "question", "cancel").getString("&cCancel"), Map.of()));
            if (renderer.isPresent() && renderer.get().ask(viewer, question, text -> answer.accept(text.trim(), saved), cancel)) return;
            renderer.ifPresent(r -> r.close(viewer));
            viewer.sendMessage(PunishUtils.format(messages, Map.of("what", what,
                    "cancel", messages.node("prompt", "cancel").getString("cancel")), "menu", "prompt"));
            judicator.getPunishCache().putContext(viewer.getName(),
                    ChatContext.textPrompt(judicator, viewer, text -> answer.accept(text.trim(), saved), cancel));
        });
    }

    /**
     * Shows the menu again as it was before a question, {@code leave} screens back.
     */
    private void resume(JudicatorPlayer viewer, Deque<Screen> saved, int leave) {
        sync(viewer, () -> {
            final Deque<Screen> stack = new ArrayDeque<>(saved);
            for (int i = 0; i < leave; i++) stack.poll();
            if (!stack.isEmpty()) start(viewer, stack);
        });
    }

    /**
     * {@code menus.replace-commands} (on by default): a player typing a lookup command gets the menu instead of the
     * chat answer. The adapters call it once the command's permission was checked; the console keeps the chat.
     *
     * @return whether the menu was opened (the command must not run)
     */
    public boolean redirect(JudicatorSender sender, String command, String[] args) {
        if (!isAvailable() || !judicator.getConfig().node("menus", "replace-commands").getBoolean(true)) return false;
        final Optional<JudicatorPlayer> viewer = sender.asPlayer();
        if (viewer.isEmpty()) return false;
        final Optional<Screen> screen = screenFor(command, args);
        screen.ifPresent(found -> open(viewer.get(), found));
        return screen.isPresent();
    }

    /**
     * The screen of a command line in its plain forms; anything else (a period for /blame, a flag) keeps the chat.
     */
    static Optional<Screen> screenFor(String command, String[] args) {
        final Optional<String> player = args.length >= 1 && !args[0].isBlank() ? Optional.of(args[0]) : Optional.empty();
        final Optional<Integer> page = page(args, 1);
        final boolean onePlayer = args.length == 1 && player.isPresent();
        final boolean playerAndPage = player.isPresent() && (args.length == 1 || (args.length == 2 && page.isPresent()));
        return switch (command) {
            case "playerinfo" -> onePlayer ? player.map(PlayerScreen::new) : Optional.empty();
            case "punish" -> onePlayer ? player.map(ReasonsScreen::new) : Optional.empty();
            case "punishhistory" -> onePlayer ? player.map(name -> PunishmentsScreen.history(name, 1)) : Optional.empty();
            case "warnhistory" -> onePlayer ? player.map(name -> new WarnsScreen(name, 1)) : Optional.empty();
            case "alts" -> onePlayer ? player.map(name -> new AltsScreen(name, 1)) : Optional.empty();
            case "notes" -> playerAndPage ? player.map(name -> new NotesScreen(name, page.orElse(1))) : Optional.empty();
            case "blame" -> playerAndPage ? player.map(name -> PunishmentsScreen.blame(name, page.orElse(1))) : Optional.empty();
            case "punishview" -> onePlayer ? player.flatMap(PunishUtils::parseId).map(PunishmentScreen::new) : Optional.empty();
            case "warnview" -> onePlayer ? player.flatMap(PunishUtils::parseId).map(WarnScreen::new) : Optional.empty();
            case "banlist", "mutelist" -> {
                final Optional<Integer> number = args.length == 0 ? Optional.of(1) : args.length == 1 ? page(args, 0) : Optional.empty();
                yield number.map(n -> command.equals("banlist") ? PunishmentsScreen.bans(n) : PunishmentsScreen.mutes(n));
            }
            case "reports" -> reportsScreen(args);
            default -> Optional.empty();
        };
    }

    private static Optional<Screen> reportsScreen(String[] args) {
        if (args.length == 0) return Optional.of(new ReportsScreen("open", 1));
        if (args.length == 1) return page(args, 0).map(n -> new ReportsScreen("open", n));
        final String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("info") && args.length == 2) return PunishUtils.parseId(args[1]).map(ReportScreen::new);
        if (!sub.equals("list") || args.length > 3) return Optional.empty();
        final String filter = args[1].toLowerCase(Locale.ROOT);
        if (args.length == 2 && page(args, 1).isPresent()) return page(args, 1).map(n -> new ReportsScreen("open", n));
        if (!ReportsCommand.FILTERS.containsKey(filter)) return Optional.empty();
        if (args.length == 2) return Optional.of(new ReportsScreen(filter, 1));
        return page(args, 2).map(n -> new ReportsScreen(filter, n));
    }

    /**
     * A page number at that argument.
     */
    private static Optional<Integer> page(String[] args, int index) {
        if (args.length <= index) return Optional.empty();
        try {
            final int number = Integer.parseInt(args[index]);
            return number >= 1 ? Optional.of(number) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    public Optional<CoreCommand> command(String name) {
        return judicator.getCommands().stream().filter(command -> command.getName().equals(name)).findFirst();
    }

    private void start(JudicatorPlayer viewer, Deque<Screen> stack) {
        final Session session = new Session(viewer, stack);
        sessions.put(viewer.getUniqueId(), session);
        build(session);
    }

    private void build(Session session) {
        final Screen screen = session.stack.peek();
        if (screen == null) return;
        final long generation = ++session.generation;
        session.building = true;
        final JudicatorPlayer viewer = session.viewer;
        final boolean previous = session.stack.size() > 1;
        platform().runAsync(() -> {
            Menu built;
            try {
                final MenuContext context = new MenuContext(judicator, this, viewer);
                final String key = keyOf(screen);
                built = screen.build(context).styled(key, context.lines(Map.of(), "style", key, "header"), previous);
            } catch (RuntimeException e) {
                judicator.getLogger().error("Could not build a menu for {}", viewer.getName(), e);
                built = null;
            }
            final Menu menu = built;
            sync(viewer, () -> {
                if (sessions.get(viewer.getUniqueId()) != session || session.generation != generation) return;
                session.building = false;
                final Optional<MenuRenderer> renderer = platform().menus();
                if (platform().getPlayer(viewer.getUniqueId()).isEmpty()) {
                    // left meanwhile (a question answered just before quitting)
                    sessions.remove(viewer.getUniqueId());
                    return;
                }
                if (menu == null || renderer.isEmpty()) {
                    sessions.remove(viewer.getUniqueId());
                    renderer.ifPresent(r -> r.close(viewer));
                    viewer.sendMessage(PunishUtils.getMessage(judicator.messagesFor(viewer), "menu", "error"));
                    return;
                }
                session.current = menu;
                renderer.get().open(viewer, menu);
            });
        });
    }

    /**
     * {@code MainScreen} -> {@code main}, {@code PunishConfirmScreen} -> {@code punishConfirm}.
     */
    static String keyOf(Screen screen) {
        final String name = screen.getClass().getSimpleName().replaceFirst("Screen$", "");
        return name.isEmpty() ? "screen" : Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    private Platform platform() {
        return judicator.getPlatform();
    }

    /**
     * On the viewer's thread (Folia: their region), where their clicks arrive too.
     */
    private void sync(JudicatorPlayer viewer, Runnable task) {
        platform().runFor(viewer, task);
    }
}
