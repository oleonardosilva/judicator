package com.elless.judicator.jails;

import com.elless.judicator.Judicator;
import com.elless.judicator.commons.DateTimeOffsetParser;
import com.elless.judicator.jails.repository.JailRepository;
import com.elless.judicator.platform.JudicatorPlayer;
import com.elless.judicator.platform.Location;
import com.elless.judicator.platform.Platform;
import com.elless.judicator.platform.PlatformTask;
import com.elless.judicator.players.PlayerRef;
import com.elless.judicator.punish.PunishUtils;
import com.elless.judicator.punish.Punishment;
import com.elless.judicator.punish.types.PunishPermissions;
import com.elless.judicator.punish.types.PunishStatus;
import com.elless.judicator.punish.types.PunishType;
import com.elless.judicator.sync.SyncKind;
import org.spongepowered.configurate.ConfigurationNode;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The jails ({@code /jails}) and their prisoners ({@code /jail}, {@code /tempjail}).
 * <p>
 * The jails live in the database, so every server of a network and the proxy know them; each is on one server
 * ({@code scopes.server-name} of the server it was made on). On a game server (Spigot/Paper, the mod loaders), the
 * prisoners online whose jail is on it are checked every {@code jail.check-interval} milliseconds, on their own thread:
 * one who reaches a secret {@link EscapeRoute} escapes (or is caught, by its chance), one who walks or digs out of the
 * area is caught and gets {@code jail.escape.caught-penalty} more, one who got out in any other way (a teleport, a
 * respawn) is just brought back. An escaped prisoner is a fugitive for {@code jail.escape.fugitive-time}: a guard
 * ({@code judicator.jail.guard}) who hits them takes them back with what was left of their time and the penalty;
 * after it they are free and the jail is over.
 * <p>
 * On a proxy the prisoners are only sent to their jail's server and kept there ({@link #prisonServer}); the game server
 * holds them. Escapes and catches reach the other instances through {@code sync}.
 */
public class JailService {

    /** blocks between two checks a prisoner can cover by walking or digging; farther away they were teleported */
    private static final double WALK_STEP = 8;

    private final Judicator judicator;
    private final JailRepository repository;
    private final Map<UUID, Inmate> inmates = new ConcurrentHashMap<>();
    private volatile Map<String, Jail> jails = Map.of();
    private volatile Settings settings;
    private PlatformTask checkTask;
    private long checkInterval;

    public JailService(Judicator judicator, JailRepository repository) {
        this.judicator = judicator;
        this.repository = repository;
        this.settings = Settings.read(judicator.getConfig().node("jail"), judicator);
    }

    public void start() {
        repository.initialize();
        loadJails();
        reload();
        // a plugin reload (Bukkit /reload): the prisoners still online are held again
        if (holdsPrisoners()) platform().getOnlinePlayers().forEach(player -> platform().runAsync(() -> joined(player)));
        if (holdsPrisoners() && !jails.isEmpty() && !judicator.isProxyMode() && judicator.getConfig().node("trust-uuids").getBoolean(false)) {
            judicator.getLogger().warn("This server is behind a proxy (trust-uuids) without proxy-mode: its jails only hold prisoners while they "
                    + "are on it, and switching servers sets them free. Run Judicator on the proxy too (see the jail section of config.yml).");
        }
    }

    public void stop() {
        if (checkTask != null) checkTask.cancel();
        checkTask = null;
        inmates.clear();
    }

    public void reload() {
        this.settings = Settings.read(judicator.getConfig().node("jail"), judicator);
        if (!holdsPrisoners() || settings.interval() == checkInterval) return;
        if (checkTask != null) checkTask.cancel();
        this.checkInterval = settings.interval();
        this.checkTask = platform().runAsyncRepeating(this::checkAllSafely, checkInterval, TimeUnit.MILLISECONDS);
    }

    private Platform platform() {
        return judicator.getPlatform();
    }

    /**
     * Whether this instance keeps prisoners in their cell: a game server, not a proxy.
     */
    public boolean holdsPrisoners() {
        return platform().hasWorlds() && !platform().getType().isProxy();
    }

    // ---- the jails ------------------------------------------------------------------------------------------------

    /**
     * Reads the jails from the database again (at startup, and when another instance changed them).
     */
    public void loadJails() {
        final Map<String, Jail> loaded = new TreeMap<>();
        repository.findAll().forEach(jail -> loaded.put(jail.name(), jail));
        this.jails = Collections.unmodifiableMap(loaded);
    }

    public List<Jail> all() {
        return List.copyOf(jails.values());
    }

    public Optional<Jail> find(String name) {
        return name == null ? Optional.empty() : Optional.ofNullable(jails.get(name.toLowerCase(Locale.ROOT)));
    }

    /**
     * Stores a new or changed jail and tells the other instances.
     *
     * @return false when the database refused it
     */
    public boolean save(Jail jail, String author) {
        if (!repository.save(jail, author)) return false;
        final Map<String, Jail> changed = new TreeMap<>(jails);
        changed.put(jail.name(), jail);
        this.jails = Collections.unmodifiableMap(changed);
        judicator.getSyncService().publish(SyncKind.JAILS_CHANGED, 0L);
        return true;
    }

    public boolean delete(String name) {
        if (!repository.delete(name)) return false;
        final Map<String, Jail> changed = new TreeMap<>(jails);
        changed.remove(name);
        this.jails = Collections.unmodifiableMap(changed);
        judicator.getSyncService().publish(SyncKind.JAILS_CHANGED, 0L);
        return true;
    }

    /**
     * How many prisoners the jail holds now (fugitives left it). Reads the database.
     */
    public int occupants(Jail jail) {
        return judicator.getPunishService().countInJail(jail.name(), false);
    }

    /**
     * Whether the jail is on this server: one of this server's name ({@code scopes.server-name}), or one without a
     * server on a server that is not behind a proxy ({@code proxy-mode}: every backend would take it for its own).
     * Never on a proxy.
     */
    public boolean isLocal(Jail jail) {
        if (platform().getType().isProxy()) return false;
        if (jail.server().isEmpty()) return !judicator.isProxyMode();
        return judicator.getScopeService().localName().map(jail.server()::equalsIgnoreCase).orElse(false);
    }

    /**
     * Whether any jail in force is in it, fugitives' included (they would come back to it if caught). Reads the database.
     */
    public boolean isInUse(Jail jail) {
        return judicator.getPunishService().countInJail(jail.name(), true) > 0;
    }

    /**
     * Why a new prisoner cannot go to the jail picked for them, checked again when the jail is stored (the evidence
     * prompt may have taken a while, or someone else was jailed meanwhile).
     *
     * @return the key of the {@code error} message; empty when they can go
     */
    public Optional<String> refusalOnArrival(PlayerRef player, String jail) {
        final boolean jailed = judicator.getPunishService().findAllActiveOf(player, PunishType.JAIL, PunishType.TEMPJAIL).stream()
                .anyMatch(punishment -> !punishment.isEscaped());
        if (jailed) return Optional.of("already-jailed");
        final Optional<Jail> found = find(jail);
        if (found.isEmpty()) return Optional.of("jail-unknown");
        return occupants(found.get()) < found.get().room() ? Optional.empty() : Optional.of("jail-full");
    }

    /**
     * Why a jail could not be picked for a new prisoner.
     */
    public enum Refusal {
        /** no jail was made with {@code /jails create} */
        NO_JAILS,
        /** no jail of that name */
        UNKNOWN,
        /** the jail asked for, or every jail, is full */
        FULL
    }

    /**
     * @param jail    the jail picked; null when refused
     * @param refusal why none was picked; null when one was
     */
    public record Choice(Jail jail, Refusal refusal) {
    }

    /**
     * The jail for a new prisoner: the one asked for when it has room, else the first with room, those that can hold
     * prisoners from here first (on this server; on a proxy, on a server it knows). Reads the database.
     *
     * @param requested a jail name; null to pick one
     */
    public Choice choose(String requested) {
        if (requested != null) {
            final Optional<Jail> jail = find(requested);
            if (jail.isEmpty()) return new Choice(null, Refusal.UNKNOWN);
            return occupants(jail.get()) < jail.get().room() ? new Choice(jail.get(), null) : new Choice(null, Refusal.FULL);
        }
        if (jails.isEmpty()) return new Choice(null, Refusal.NO_JAILS);
        final List<Jail> candidates = new ArrayList<>(jails.values());
        candidates.sort(Comparator.comparing((Jail jail) -> !reachable(jail)));
        for (Jail jail : candidates) {
            if (occupants(jail) < jail.room()) return new Choice(jail, null);
        }
        return new Choice(null, Refusal.FULL);
    }

    private boolean reachable(Jail jail) {
        return platform().getType().isProxy() ? knownServer(jail).isPresent() : isLocal(jail);
    }

    /**
     * The jail's server as the proxy names it, when the proxy has it.
     */
    private Optional<String> knownServer(Jail jail) {
        if (jail.server().isEmpty()) return Optional.empty();
        return platform().serverNames().stream().filter(jail.server()::equalsIgnoreCase).findFirst();
    }

    // ---- the proxy ------------------------------------------------------------------------------------------------

    /**
     * Proxy: the server a player must stay on, their jail's, while they are in jail (not while a fugitive). Empty when
     * they are not in jail, or the proxy does not know their jail's server. Reads the database.
     */
    public Optional<String> prisonServer(PlayerRef player) {
        return judicator.getPunishService().findAllActiveOf(player, PunishType.JAIL, PunishType.TEMPJAIL).stream()
                .filter(punishment -> !punishment.isEscaped())
                .map(punishment -> find(punishment.getJail().orElse(null)))
                .flatMap(Optional::stream)
                .map(this::serverForProxy)
                .flatMap(Optional::stream)
                .findFirst();
    }

    private Optional<String> serverForProxy(Jail jail) {
        final Optional<String> server = knownServer(jail);
        if (server.isEmpty()) {
            judicator.getLogger().warn("Jail '{}' is on '{}', a server this proxy does not have: its prisoners are not sent there. "
                    + "Set scopes.server-name on the server the jail is on (as the proxy calls it) and run /jails server {} <server>.",
                    jail.name(), jail.server(), jail.name());
        }
        return server;
    }

    /**
     * Proxy: takes a prisoner online on the network to their jail's server, unless they are on it.
     */
    public void sendToPrison(JudicatorPlayer player) {
        platform().runAsync(() -> prisonServer(judicator.getPlayerDirectory().refOf(player)).ifPresent(server -> {
            final boolean there = platform().serverOf(player).map(server::equalsIgnoreCase).orElse(false);
            if (!there) platform().connect(player, server);
        }));
    }

    // ---- prisoners on this server ----------------------------------------------------------------------------------

    /**
     * A jail was applied, here or (sync) on another instance: its prisoner, when online here, is taken to it.
     */
    public void punished(Punishment punishment) {
        if (!holdsPrisoners()) return;
        judicator.getPunishmentEffects().onlineTarget(punishment).ifPresent(player -> track(player, punishment));
    }

    /**
     * A player joined this server (off the game thread): their jail in force, if any, takes them; a player who is
     * standing in a jail without being in one (it ended while they were away) is let out.
     */
    public void joined(JudicatorPlayer player) {
        if (!holdsPrisoners()) return;
        final Optional<Punishment> jail = judicator.getPunishService()
                .findAllActiveOf(judicator.getPlayerDirectory().refOf(player), PunishType.JAIL, PunishType.TEMPJAIL).stream()
                .min(Comparator.comparing(Punishment::isEscaped));
        if (jail.isPresent()) {
            track(player, jail.get());
            return;
        }
        if (player.hasPermission(PunishPermissions.JAILS.getPermission())) return;
        platform().runFor(player, () -> platform().locationOf(player).flatMap(this::localJailAt).flatMap(this::releasePoint)
                .ifPresent(point -> platform().teleport(player, point)));
    }

    private Optional<Jail> localJailAt(Location location) {
        return jails.values().stream().filter(this::isLocal).filter(jail -> jail.area().contains(location)).findFirst();
    }

    public void left(UUID player) {
        inmates.remove(player);
    }

    /**
     * Whether the player is in jail here (not a fugitive): what they may do is limited.
     */
    public boolean isPrisonerHere(JudicatorPlayer player) {
        final Inmate inmate = inmates.get(player.getUniqueId());
        return inmate != null && !inmate.punishment.isEscaped() && jailOf(inmate.punishment).map(this::isLocal).orElse(false);
    }

    private void track(JudicatorPlayer player, Punishment punishment) {
        final Inmate inmate = new Inmate(player, punishment);
        inmates.put(player.getUniqueId(), inmate);
        if (!punishment.isEscaped()) confine(inmate);
    }

    /**
     * Takes the prisoner to their cell, when their jail is here.
     */
    private void confine(Inmate inmate) {
        final Optional<Jail> jail = jailOf(inmate.punishment).filter(this::isLocal);
        if (jail.isEmpty() || !inmate.busy.compareAndSet(false, true)) return;
        moveTo(inmate, jail.get().spawn());
    }

    /**
     * Teleports a prisoner whose {@link Inmate#busy} is set and clears it once they got there.
     */
    private void moveTo(Inmate inmate, Location to) {
        platform().teleport(inmate.player, to).whenComplete((moved, error) -> {
            if (error != null) judicator.getLogger().warn("Could not take {} to jail", inmate.player.getName(), error);
            if (Boolean.TRUE.equals(moved)) inmate.lastInside = to;
            inmate.busy.set(false);
        });
    }

    private Optional<Jail> jailOf(Punishment punishment) {
        return find(punishment.getJail().orElse(null));
    }

    /**
     * A jail changed: a new end ({@code /extend}, a pause or resume), an escape or a catch (here or elsewhere).
     */
    public void refreshed(Punishment stored) {
        if (!stored.isJailType() || !holdsPrisoners()) return;
        final Optional<Inmate> inmate = inmateOf(stored);
        if (inmate.isEmpty()) {
            // caught on another server of the network while on this one
            if (stored.getStatus() == PunishStatus.ACTIVE && !stored.isEscaped()) punished(stored);
            return;
        }
        final boolean wasEscaped = inmate.get().punishment.isEscaped();
        inmate.get().punishment = stored;
        if (wasEscaped && !stored.isEscaped()) confine(inmate.get());
    }

    /**
     * A jail was revoked: its prisoner, when online here, goes out.
     */
    public void released(Punishment punishment) {
        if (!holdsPrisoners()) return;
        inmateOf(punishment).ifPresent(inmate -> {
            inmates.remove(inmate.player.getUniqueId(), inmate);
            if (!inmate.punishment.isEscaped()) letOut(inmate);
        });
    }

    /**
     * A punishment was deleted ({@code /judicator clear}, here or elsewhere): a prisoner it held goes out.
     */
    public void deleted(long id) {
        if (!holdsPrisoners()) return;
        inmates.values().stream().filter(inmate -> inmate.punishment.getId() == id).findFirst().ifPresent(inmate -> {
            inmates.remove(inmate.player.getUniqueId(), inmate);
            if (!inmate.punishment.isEscaped()) letOut(inmate);
        });
    }

    private Optional<Inmate> inmateOf(Punishment punishment) {
        return inmates.values().stream().filter(inmate -> inmate.punishment.getId().equals(punishment.getId())).findFirst();
    }

    private void letOut(Inmate inmate) {
        jailOf(inmate.punishment).filter(this::isLocal).flatMap(this::releasePoint)
                .ifPresent(point -> platform().teleport(inmate.player, point));
    }

    private Optional<Location> releasePoint(Jail jail) {
        return jail.release().or(() -> platform().worldSpawn());
    }

    private void checkAllSafely() {
        try {
            checkAll();
        } catch (RuntimeException e) {
            judicator.getLogger().error("Could not check the prisoners", e);
        }
    }

    /**
     * Every prisoner online here (off the game thread): those whose time is over go free, those in a jail here are
     * checked on their own thread.
     */
    public void checkAll() {
        for (Inmate inmate : inmates.values()) {
            if (inmate.busy.get()) continue;
            final Punishment punishment = inmate.punishment;
            if (punishment.getStatus() != PunishStatus.ACTIVE) {
                if (inmates.remove(inmate.player.getUniqueId(), inmate)) served(inmate);
                continue;
            }
            if (punishment.isEscaped()) continue;
            jailOf(punishment).filter(this::isLocal).ifPresent(jail -> platform().runFor(inmate.player, () -> check(inmate, jail)));
        }
    }

    private void served(Inmate inmate) {
        final ConfigurationNode messages = judicator.messagesFor(inmate.player);
        if (inmate.punishment.isEscaped()) {
            inmate.player.sendMessage(PunishUtils.getMessage(messages, "jail", "hunt-over"));
            return;
        }
        inmate.player.sendMessage(PunishUtils.getMessage(messages, "jail", "served"));
        letOut(inmate);
    }

    /**
     * Where the prisoner is (their thread).
     */
    private void check(Inmate inmate, Jail jail) {
        if (inmate.busy.get() || inmates.get(inmate.player.getUniqueId()) != inmate) return;
        final Optional<Location> at = platform().locationOf(inmate.player);
        if (at.isEmpty()) return;
        final Location location = at.get();
        final Optional<EscapeRoute> route = jail.escapeAt(location);
        if (route.isPresent()) {
            if (!inmate.busy.compareAndSet(false, true)) return;
            if (ThreadLocalRandom.current().nextInt(100) < route.get().chance()) platform().runAsync(() -> escape(inmate));
            else caught(inmate, jail, "escape-failed");
            return;
        }
        if (jail.area().contains(location)) {
            inmate.lastInside = location;
            return;
        }
        if (!inmate.busy.compareAndSet(false, true)) return;
        final Location last = inmate.lastInside;
        final boolean walkedOut = last != null && last.sameWorld(location) && last.distanceSquared(location) <= WALK_STEP * WALK_STEP;
        if (walkedOut) {
            caught(inmate, jail, "caught");
            return;
        }
        inmate.player.sendMessage(PunishUtils.getMessage(judicator.messagesFor(inmate.player), "jail", "back"));
        moveTo(inmate, jail.spawn());
    }

    /**
     * Caught leaving or escaping: back to the cell, with the penalty on a temporary jail.
     */
    private void caught(Inmate inmate, Jail jail, String key) {
        final ConfigurationNode messages = judicator.messagesFor(inmate.player);
        inmate.player.sendMessage(PunishUtils.getMessage(messages, "jail", key));
        moveTo(inmate, jail.spawn());
        final long penalty = settings.caughtPenalty();
        final Punishment punishment = inmate.punishment;
        if (penalty <= 0 || !punishment.getType().isTemp()) return;
        platform().runAsync(() -> {
            final boolean changed = punishment.isPaused()
                    ? judicator.getPunishService().changeEnd(punishment, null, punishment.getPausedRemaining() + penalty)
                    : punishment.getFinishAt().isPresent()
                    && judicator.getPunishService().changeEnd(punishment, punishment.getFinishAt().get().plus(Duration.ofMillis(penalty)), null);
            if (changed) inmate.player.sendMessage(PunishUtils.format(messages,
                    Map.of("time", DateTimeOffsetParser.format(messages, penalty)), "jail", "penalty"));
        });
    }

    /**
     * Through a secret way out (off the game thread): a fugitive for {@code jail.escape.fugitive-time}, free at once
     * without it.
     */
    private void escape(Inmate inmate) {
        try {
            final Punishment punishment = inmate.punishment;
            final LocalDateTime now = LocalDateTime.now();
            Long left = null;
            if (punishment.getType().isTemp()) {
                left = punishment.isPaused() ? punishment.getPausedRemaining()
                        : Math.max(0, Duration.between(now, punishment.getFinishAt().orElse(now)).toMillis());
            }
            final long hunt = settings.fugitiveTime();
            if (!judicator.getPunishService().changeJail(punishment, now.plus(Duration.ofMillis(hunt)), null, now, left, SyncKind.JAIL_ESCAPED)) {
                return;
            }
            inmate.lastInside = null;
            final ConfigurationNode messages = judicator.messagesFor(inmate.player);
            if (hunt > 0) {
                inmate.player.sendMessage(PunishUtils.format(messages, Map.of("time", DateTimeOffsetParser.format(messages, hunt)), "jail", "escaped"));
            } else {
                inmates.remove(inmate.player.getUniqueId(), inmate);
                inmate.player.sendMessage(PunishUtils.getMessage(messages, "jail", "free"));
            }
            notifyStaff(punishment, "jail-escaped", null);
        } finally {
            inmate.busy.set(false);
        }
    }

    /**
     * A fugitive hit by a guard (off the game thread): back to jail with what was left of their time and the penalty.
     */
    private void capture(Inmate inmate, JudicatorPlayer guard) {
        final Punishment punishment = inmate.punishment;
        LocalDateTime end = null;
        if (punishment.getType().isTemp()) {
            final long left = punishment.getJailLeft() == null ? 0 : punishment.getJailLeft();
            end = LocalDateTime.now().plus(Duration.ofMillis(left + settings.caughtPenalty()));
        }
        final boolean caught = punishment.isEscaped() && punishment.getStatus() == PunishStatus.ACTIVE
                && judicator.getPunishService().changeJail(punishment, end, null, null, null, SyncKind.JAIL_CAUGHT);
        if (!caught) {
            inmate.busy.set(false);
            return;
        }
        // still busy: no check runs before they are back in the cell
        final Optional<Jail> jail = jailOf(punishment).filter(this::isLocal);
        if (jail.isPresent()) moveTo(inmate, jail.get().spawn());
        else inmate.busy.set(false);
        inmate.player.sendMessage(PunishUtils.format(judicator.messagesFor(inmate.player), Map.of("guard", guard.getName()), "jail", "captured"));
        guard.sendMessage(PunishUtils.format(judicator.messagesFor(guard), Map.of("nickname", inmate.player.getName()), "jail", "capture"));
        notifyStaff(punishment, "jail-caught", guard.getName());
    }

    /**
     * An escape or a catch made on another instance (sync): its staff hear of it, and a proxy takes a caught fugitive
     * back to their jail's server.
     */
    public void received(SyncKind kind, Punishment stored) {
        refreshed(stored);
        notifyStaff(stored, kind == SyncKind.JAIL_ESCAPED ? "jail-escaped" : "jail-caught", null);
        if (kind == SyncKind.JAIL_CAUGHT && platform().getType().isProxy()) {
            judicator.getPunishmentEffects().onlineTarget(stored).ifPresent(this::sendToPrison);
        }
    }

    /**
     * Staff with {@code judicator.jail.notify}; behind a proxy running Judicator, only the proxy tells them.
     */
    private void notifyStaff(Punishment punishment, String key, String guard) {
        if (!settings.announceEscapes() || judicator.isProxyMode()) return;
        judicator.getStaffNotifier().sendToPermission(PunishPermissions.JAIL_NOTIFY.getPermission(), messages -> PunishUtils.format(messages,
                Map.of("nickname", punishment.getNickname(), "jail", punishment.getJail().orElse("?"),
                        "guard", guard == null ? "?" : guard, "id", String.valueOf(punishment.getId())), "notifications", key));
    }

    // ---- what prisoners may do -------------------------------------------------------------------------------------

    /**
     * Breaking a block (game thread).
     */
    public boolean mayBreak(JudicatorPlayer player) {
        return settings.breakBlocks() || !isPrisonerHere(player);
    }

    /**
     * Placing a block (game thread).
     */
    public boolean mayPlace(JudicatorPlayer player) {
        return settings.placeBlocks() || !isPrisonerHere(player);
    }

    /**
     * A player hitting another (game thread): a prisoner does not hurt anyone ({@code jail.rules.attack}) and is not
     * hurt ({@code jail.rules.protect}); a guard who hits a fugitive catches them.
     *
     * @param victim the player hit; null for anything else
     * @return whether the hit goes on
     */
    public boolean allowAttack(JudicatorPlayer attacker, JudicatorPlayer victim) {
        if (!settings.attack() && isPrisonerHere(attacker)) return false;
        if (victim == null) return true;
        final Inmate attacked = inmates.get(victim.getUniqueId());
        if (attacked == null) return true;
        if (!attacked.punishment.isEscaped()) return !settings.protect() || !isPrisonerHere(victim);
        if (!attacker.hasPermission(PunishPermissions.JAIL_GUARD.getPermission())) return true;
        if (attacked.busy.compareAndSet(false, true)) platform().runAsync(() -> capture(attacked, attacker));
        return false;
    }

    /**
     * A command a prisoner runs: only {@code jail.allowed-commands} while in jail here.
     *
     * @param label the command, lower case, without slash nor namespace
     * @return whether it is blocked (the player was told)
     */
    public boolean blocksCommand(JudicatorPlayer player, String label) {
        if (!isPrisonerHere(player) || settings.allowedCommands().contains(label)) return false;
        player.sendMessage(PunishUtils.getMessage(judicator.messagesFor(player), "jail", "command"));
        return true;
    }

    /**
     * A prisoner online here.
     */
    private static final class Inmate {
        private final JudicatorPlayer player;
        private volatile Punishment punishment;
        /** where they were last seen inside the jail */
        private volatile Location lastInside;
        /** a teleport, an escape or a catch is under way: the checks wait */
        private final AtomicBoolean busy = new AtomicBoolean();

        private Inmate(JudicatorPlayer player, Punishment punishment) {
            this.player = player;
            this.punishment = punishment;
        }
    }

    /**
     * The {@code jail} section of {@code config.yml}.
     */
    record Settings(boolean onlineOnly, long interval, Set<String> allowedCommands, boolean breakBlocks, boolean placeBlocks,
                    boolean attack, boolean protect, long caughtPenalty, long fugitiveTime, boolean announceEscapes) {

        static Settings read(ConfigurationNode node, Judicator judicator) {
            final Set<String> commands = new HashSet<>();
            try {
                for (String command : node.node("allowed-commands").getList(String.class, List.of())) {
                    commands.add(label(command));
                }
            } catch (Exception e) {
                judicator.getLogger().warn("jail.allowed-commands: give a list of commands such as [msg, r]");
            }
            final ConfigurationNode rules = node.node("rules");
            final ConfigurationNode escape = node.node("escape");
            return new Settings(node.node("online-only").getBoolean(true),
                    Math.max(50, Math.min(5000, node.node("check-interval").getLong(250))),
                    Set.copyOf(commands),
                    rules.node("break-blocks").getBoolean(false),
                    rules.node("place-blocks").getBoolean(false),
                    rules.node("attack").getBoolean(false),
                    rules.node("protect").getBoolean(true),
                    duration(judicator, "jail.escape.caught-penalty", escape.node("caught-penalty").getString("10:m")),
                    duration(judicator, "jail.escape.fugitive-time", escape.node("fugitive-time").getString("10:m")),
                    escape.node("notify").getBoolean(true));
        }

        private static long duration(Judicator judicator, String path, String value) {
            if (value == null || value.isBlank() || value.trim().equals("0")) return 0;
            try {
                return Math.max(0, DateTimeOffsetParser.getMillisFromDuration(value.trim()));
            } catch (IllegalArgumentException e) {
                judicator.getLogger().warn("{}: '{}' is not a duration such as 10:m; 0 is used", path, value);
                return 0;
            }
        }

        /**
         * "/Tell", "minecraft:tell" and "tell" all give "tell".
         */
        static String label(String command) {
            String label = command.trim().split(" ", 2)[0].toLowerCase(Locale.ROOT);
            if (label.startsWith("/")) label = label.substring(1);
            final int namespace = label.indexOf(':');
            return namespace >= 0 ? label.substring(namespace + 1) : label;
        }
    }

    /**
     * {@code jail.online-only}: whether {@code /tempjail} counts only online time.
     */
    public boolean onlineOnlyByDefault() {
        return settings.onlineOnly();
    }

}
