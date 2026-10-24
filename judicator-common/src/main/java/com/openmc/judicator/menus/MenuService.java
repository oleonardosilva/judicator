package com.openmc.judicator.menus;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.commons.ChatContext;
import com.openmc.judicator.commons.command.CommandArgs;
import com.openmc.judicator.commons.command.CoreCommand;
import com.openmc.judicator.menus.screens.AltsScreen;
import com.openmc.judicator.menus.screens.NotesScreen;
import com.openmc.judicator.menus.screens.PlayerScreen;
import com.openmc.judicator.menus.screens.PunishmentScreen;
import com.openmc.judicator.menus.screens.PunishmentsScreen;
import com.openmc.judicator.menus.screens.ReasonsScreen;
import com.openmc.judicator.menus.screens.ReportScreen;
import com.openmc.judicator.menus.screens.ReportsScreen;
import com.openmc.judicator.menus.screens.WarnScreen;
import com.openmc.judicator.menus.screens.WarnsScreen;
import com.openmc.judicator.platform.JudicatorPlayer;
import com.openmc.judicator.platform.JudicatorSender;
import com.openmc.judicator.platform.Platform;
import com.openmc.judicator.punish.PunishUtils;
import com.openmc.judicator.reports.commands.ReportsCommand;
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
 * A screen is built off the game thread and shown on it; the state of the sessions is only touched on the game thread.
 * Actions run the same commands a staff member would type, so the same permissions and staff rules apply.
 */
public class MenuService {

    /** entries per page of a list: the five rows above the navigation row */
    public static final int PAGE_SIZE = 45;

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
        sync(() -> start(viewer, stack));
    }

    /**
     * Goes to a screen; "back" returns to the current one.
     */
    public void push(JudicatorPlayer viewer, Screen screen) {
        sync(() -> {
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
        sync(() -> {
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
        sync(() -> {
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
        sync(() -> {
            sessions.remove(viewer.getUniqueId());
            platform().menus().ifPresent(renderer -> renderer.close(viewer));
        });
    }

    /**
     * A click on a slot of the menu the player sees (game thread). Ignored while the next screen is being built, and
     * when the menu is not the one of the session (a click arriving after the screen changed).
     */
    public void click(UUID viewer, Menu menu, int slot) {
        final Session session = sessions.get(viewer);
        if (session == null || session.building || session.current != menu) return;
        menu.item(slot).map(MenuItem::action).ifPresent(Runnable::run);
    }

    /**
     * The player closed the menu (game thread). Opening the next screen closes the previous one too: that menu is no
     * longer the current one and the session stays.
     */
    public void closed(UUID viewer, Menu menu) {
        final Session session = sessions.get(viewer);
        if (session != null && session.current == menu && !session.building) sessions.remove(viewer);
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
        platform().runAsync(() -> {
            final Optional<CoreCommand> found = command(command);
            if (found.isEmpty() || !found.get().canUse(viewer)) {
                viewer.sendMessage(PunishUtils.getMessage(judicator.messagesFor(viewer), "menu", "no-permission"));
            } else {
                try {
                    found.get().execute(viewer, confirmed ? CommandArgs.confirmed(arguments) : CommandArgs.of(arguments));
                } catch (RuntimeException e) {
                    judicator.getLogger().error("Error while running /{} from a menu", command, e);
                }
            }
            sync(after);
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
        ask(viewer, what, leave, (answer, resume) -> run(viewer, command, arguments.apply(answer), false, resume));
    }

    /**
     * Closes the menu and asks something in the chat; the answer opens a screen on top of the ones the player had.
     */
    public void askScreen(JudicatorPlayer viewer, String what, Function<String, Screen> next) {
        ask(viewer, what, 0, (answer, resume) -> {
            resume.run();
            push(viewer, next.apply(answer));
        });
    }

    private interface Answer {
        void accept(String answer, Runnable resume);
    }

    private void ask(JudicatorPlayer viewer, String what, int leave, Answer answer) {
        sync(() -> {
            final Session session = sessions.remove(viewer.getUniqueId());
            final Deque<Screen> saved = session == null ? new ArrayDeque<>() : new ArrayDeque<>(session.stack);
            platform().menus().ifPresent(renderer -> renderer.close(viewer));
            final Runnable resume = () -> resume(viewer, saved, leave);
            final ConfigurationNode messages = judicator.messagesFor(viewer);
            viewer.sendMessage(PunishUtils.format(messages, Map.of("what", what,
                    "cancel", messages.node("prompt", "cancel").getString("cancel")), "menu", "prompt"));
            judicator.getPunishCache().putContext(viewer.getName(),
                    ChatContext.textPrompt(judicator, viewer, text -> answer.accept(text.trim(), resume), () -> resume(viewer, saved, 0)));
        });
    }

    /**
     * Shows the menu again as it was before a question, {@code leave} screens back.
     */
    private void resume(JudicatorPlayer viewer, Deque<Screen> saved, int leave) {
        sync(() -> {
            final Deque<Screen> stack = new ArrayDeque<>(saved);
            for (int i = 0; i < leave; i++) stack.poll();
            if (!stack.isEmpty()) start(viewer, stack);
        });
    }

    /**
     * {@code menus.replace-commands: true}: a player typing one of the lookup commands in its plain form gets the menu
     * instead of the chat answer. The adapters call it once the command's permission was checked.
     *
     * @return whether the menu was opened (the command must not run)
     */
    public boolean redirect(JudicatorSender sender, String command, String[] args) {
        if (!isAvailable() || !judicator.getConfig().node("menus", "replace-commands").getBoolean(false)) return false;
        final Optional<JudicatorPlayer> viewer = sender.asPlayer();
        if (viewer.isEmpty()) return false;
        final Optional<Screen> screen = screenFor(command, args);
        screen.ifPresent(found -> open(viewer.get(), found));
        return screen.isPresent();
    }

    static Optional<Screen> screenFor(String command, String[] args) {
        final Optional<String> one = args.length == 1 && !args[0].isBlank() ? Optional.of(args[0]) : Optional.empty();
        return switch (command) {
            case "playerinfo" -> one.map(PlayerScreen::new);
            case "punishhistory" -> one.map(player -> PunishmentsScreen.history(player, 1));
            case "warnhistory" -> one.map(player -> new WarnsScreen(player, 1));
            case "notes" -> one.map(player -> new NotesScreen(player, 1));
            case "alts" -> one.map(player -> new AltsScreen(player, 1));
            case "punish" -> one.map(ReasonsScreen::new);
            case "punishview" -> one.flatMap(PunishUtils::parseId).map(PunishmentScreen::new);
            case "warnview" -> one.flatMap(PunishUtils::parseId).map(WarnScreen::new);
            case "banlist" -> args.length == 0 ? Optional.of(PunishmentsScreen.bans(1)) : Optional.empty();
            case "mutelist" -> args.length == 0 ? Optional.of(PunishmentsScreen.mutes(1)) : Optional.empty();
            case "reports" -> {
                if (args.length == 0) yield Optional.of(new ReportsScreen("open", 1));
                if (args.length == 2 && args[0].equalsIgnoreCase("list") && ReportsCommand.FILTERS.containsKey(args[1].toLowerCase(Locale.ROOT))) {
                    yield Optional.of(new ReportsScreen(args[1].toLowerCase(Locale.ROOT), 1));
                }
                if (args.length == 2 && args[0].equalsIgnoreCase("info")) {
                    yield PunishUtils.parseId(args[1]).map(ReportScreen::new);
                }
                yield Optional.empty();
            }
            default -> Optional.empty();
        };
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
        platform().runAsync(() -> {
            Menu built;
            try {
                built = screen.build(new MenuContext(judicator, this, viewer));
            } catch (RuntimeException e) {
                judicator.getLogger().error("Could not build a menu for {}", viewer.getName(), e);
                built = null;
            }
            final Menu menu = built;
            sync(() -> {
                if (sessions.get(viewer.getUniqueId()) != session || session.generation != generation) return;
                session.building = false;
                final Optional<MenuRenderer> renderer = platform().menus();
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

    private Platform platform() {
        return judicator.getPlatform();
    }

    private void sync(Runnable task) {
        platform().runSync(task);
    }
}
