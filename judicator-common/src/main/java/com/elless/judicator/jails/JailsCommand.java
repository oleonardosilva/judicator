package com.elless.judicator.jails;

import com.elless.judicator.Judicator;
import com.elless.judicator.commons.command.CommandArgs;
import com.elless.judicator.commons.command.CoreCommand;
import com.elless.judicator.platform.JudicatorPlayer;
import com.elless.judicator.platform.JudicatorSender;
import com.elless.judicator.platform.Location;
import com.elless.judicator.platform.Platform;
import com.elless.judicator.punish.PunishUtils;
import com.elless.judicator.punish.types.PunishPermissions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.spongepowered.configurate.ConfigurationNode;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Stream;

/**
 * {@code /jails}: the jails of {@code /jail}. Making one and setting its places is done standing there, on the
 * server it is on; the rest works anywhere, the proxy included.
 * <ul>
 *     <li>{@code list}, {@code info <jail>}</li>
 *     <li>{@code create <jail> [radius]}: here, with an area of {@code radius} blocks around ({@value Jail#DEFAULT_RADIUS})</li>
 *     <li>{@code spawn|pos1|pos2|release <jail>}: where prisoners are put, the corners of the area, where they go when
 *     freed ({@code release <jail> clear} for the world's spawn)</li>
 *     <li>{@code shared <jail> <true|false>}, {@code capacity <jail> <n>} (0: no limit), {@code server <jail> <server|none>}</li>
 *     <li>{@code escape <jail> add [chance] [radius]|list|remove <n>|clear}: the secret ways out</li>
 *     <li>{@code tp <jail>}, {@code delete <jail>}</li>
 * </ul>
 */
public class JailsCommand extends CoreCommand {

    private static final List<String> SUBCOMMANDS = List.of("list", "info", "create", "delete", "spawn", "pos1", "pos2",
            "release", "shared", "capacity", "server", "escape", "tp");

    public JailsCommand(Judicator judicator) {
        super(judicator, "jails", List.of(), PunishPermissions.JAILS.getPermission(), PunishPermissions.ADMIN.getPermission());
    }

    private JailService jails() {
        return judicator.getJailService();
    }

    @Override
    public void execute(JudicatorSender source, CommandArgs args) {
        final String sub = args.word().map(word -> word.toLowerCase(Locale.ROOT)).orElse("list");
        if (sub.equals("list")) {
            list(source);
            return;
        }
        if (!SUBCOMMANDS.contains(sub)) {
            sendUsage(source, getName());
            return;
        }
        final Optional<String> name = args.word().map(word -> word.toLowerCase(Locale.ROOT));
        if (name.isEmpty()) {
            usage(source, sub);
            return;
        }
        if (sub.equals("create")) {
            create(source, name.get(), args.word());
            return;
        }
        final Optional<Jail> found = jails().find(name.get());
        if (found.isEmpty()) {
            send(source, "error", "jail-unknown", Map.of("jail", name.get()));
            return;
        }
        final Jail jail = found.get();
        switch (sub) {
            case "info" -> info(source, jail);
            case "delete" -> delete(source, jail);
            case "spawn" -> atPosition(source, jail, (here, current) -> current.area().contains(here) && current.escapeAt(here).isEmpty()
                    ? current.withSpawn(here) : null, "spawn-set");
            case "pos1" -> atPosition(source, jail, (here, current) -> area(current, current.area().withCorner(here, true)), "area-set");
            case "pos2" -> atPosition(source, jail, (here, current) -> area(current, current.area().withCorner(here, false)), "area-set");
            case "release" -> release(source, jail, args.word());
            case "shared" -> shared(source, jail, args.word());
            case "capacity" -> capacity(source, jail, args.word());
            case "server" -> server(source, jail, args.word());
            case "escape" -> escape(source, jail, args);
            case "tp" -> teleport(source, jail);
            default -> sendUsage(source, getName());
        }
    }

    /**
     * The jail with a new area, unless its spawn would be left out of it.
     */
    private static Jail area(Jail jail, JailArea area) {
        return area.contains(jail.spawn()) ? jail.withArea(area) : null;
    }

    private void list(JudicatorSender source) {
        final ConfigurationNode messages = messages(source);
        final List<Jail> all = jails().all();
        if (all.isEmpty()) {
            source.sendMessage(PunishUtils.getMessage(messages, "jails", "none"));
            return;
        }
        final TextComponent.Builder text = Component.text().append(PunishUtils.format(messages, Map.of("count", String.valueOf(all.size())), "jails", "title"));
        for (Jail jail : all) {
            text.append(PunishUtils.format(messages, Map.of(
                    "jail", jail.name(),
                    "server", serverName(messages, jail),
                    "prisoners", String.valueOf(jails().occupants(jail)),
                    "room", room(messages, jail)), "jails", "line"));
        }
        source.sendMessage(text.build());
    }

    private void info(JudicatorSender source, Jail jail) {
        final ConfigurationNode messages = messages(source);
        final Map<String, String> values = new HashMap<>();
        values.put("jail", jail.name());
        values.put("server", serverName(messages, jail));
        values.put("world", jail.spawn().world());
        values.put("spawn", jail.spawn().blockX() + " " + jail.spawn().blockY() + " " + jail.spawn().blockZ());
        values.put("area", jail.area().describe());
        values.put("release", jail.release().map(point -> point.world() + " " + point.blockX() + " " + point.blockY() + " " + point.blockZ())
                .orElseGet(() -> messages.node("jails", "world-spawn").getString("world spawn")));
        values.put("prisoners", String.valueOf(jails().occupants(jail)));
        values.put("room", room(messages, jail));
        values.put("escapes", String.valueOf(jail.escapes().size()));
        source.sendMessage(Component.text(PunishUtils.fill(String.join("", list(messages, "jails", "info")), values)));
    }

    private static List<String> list(ConfigurationNode messages, Object... path) {
        try {
            return messages.node(path).getList(String.class, List.of());
        } catch (Exception e) {
            return List.of();
        }
    }

    private static String serverName(ConfigurationNode messages, Jail jail) {
        return jail.server().isEmpty() ? messages.node("jails", "no-server").getString("this server") : jail.server();
    }

    private static String room(ConfigurationNode messages, Jail jail) {
        if (!jail.shared()) return messages.node("jails", "solitary").getString("1 (not shared)");
        return jail.capacity() == 0 ? messages.node("jails", "no-limit").getString("no limit") : String.valueOf(jail.capacity());
    }

    private void create(JudicatorSender source, String name, Optional<String> radiusText) {
        if (!Jail.VALID_NAME.matcher(name).matches()) {
            send(source, "error", "jail-name", Map.of("jail", name));
            return;
        }
        if (jails().find(name).isPresent()) {
            send(source, "error", "jail-exists", Map.of("jail", name));
            return;
        }
        int radius = Jail.DEFAULT_RADIUS;
        if (radiusText.isPresent()) {
            try {
                radius = Math.max(1, Math.min(64, Integer.parseInt(radiusText.get())));
            } catch (NumberFormatException e) {
                usage(source, "create");
                return;
            }
        }
        final Optional<String> serverName = judicator.getScopeService().localName();
        // behind a proxy, the jail must have the name the proxy sends prisoners to
        if (judicator.isProxyMode() && serverName.isEmpty()) {
            send(source, "error", "jail-server-name", Map.of());
            return;
        }
        final int size = radius;
        here(source).ifPresent(at -> at.thenRun(player -> {
            final Jail jail = Jail.create(name, serverName.orElse(""), player.at()).withArea(JailArea.around(player.at(), size));
            if (!jails().save(jail, player.player().getName())) {
                send(source, "error", "unknown", Map.of());
                return;
            }
            send(source, "jails", "created", Map.of("jail", name, "radius", String.valueOf(size)));
        }));
    }

    private void delete(JudicatorSender source, Jail jail) {
        if (jails().isInUse(jail)) {
            send(source, "error", "jail-occupied", Map.of("jail", jail.name()));
            return;
        }
        if (!jails().delete(jail.name())) {
            send(source, "error", "unknown", Map.of());
            return;
        }
        send(source, "jails", "deleted", Map.of("jail", jail.name()));
    }

    /**
     * Changes the jail with where the sender stands; the change is null when the place is refused (the spawn out of
     * the area).
     */
    private void atPosition(JudicatorSender source, Jail jail, BiFunction<Location, Jail, Jail> change, String done) {
        if (!editableHere(source, jail)) return;
        here(source).ifPresent(at -> at.thenRun(player -> {
            if (!player.at().world().equals(jail.spawn().world())) {
                send(source, "error", "jail-other-world", Map.of("jail", jail.name()));
                return;
            }
            final Jail current = jails().find(jail.name()).orElse(jail);
            final Jail changed = change.apply(player.at(), current);
            if (changed == null) {
                send(source, "error", "jail-spawn-outside", Map.of("jail", jail.name()));
                return;
            }
            save(source, changed, done, Map.of("jail", jail.name(), "area", changed.area().describe()));
        }));
    }

    private void release(JudicatorSender source, Jail jail, Optional<String> option) {
        if (option.map("clear"::equalsIgnoreCase).orElse(false)) {
            save(source, jail.withRelease(null), "release-cleared", Map.of("jail", jail.name()));
            return;
        }
        if (!editableHere(source, jail)) return;
        here(source).ifPresent(at -> at.thenRun(player ->
                save(source, jails().find(jail.name()).orElse(jail).withRelease(player.at()), "release-set", Map.of("jail", jail.name()))));
    }

    private void shared(JudicatorSender source, Jail jail, Optional<String> value) {
        if (value.isEmpty()) {
            send(source, "jails", "shared-now", Map.of("jail", jail.name(), "room", room(messages(source), jail)));
            return;
        }
        final Optional<Boolean> shared = value.map(text -> text.toLowerCase(Locale.ROOT)).flatMap(text -> switch (text) {
            case "true", "yes", "on" -> Optional.of(true);
            case "false", "no", "off" -> Optional.of(false);
            default -> Optional.empty();
        });
        if (shared.isEmpty()) {
            usage(source, "shared");
            return;
        }
        final Jail changed = jail.withShared(shared.get());
        save(source, changed, "shared-set", Map.of("jail", jail.name(), "room", room(messages(source), changed)));
    }

    private void capacity(JudicatorSender source, Jail jail, Optional<String> value) {
        if (value.isEmpty()) {
            send(source, "jails", "capacity-now", Map.of("jail", jail.name(), "room", room(messages(source), jail)));
            return;
        }
        final int capacity;
        try {
            capacity = Integer.parseInt(value.get());
        } catch (NumberFormatException e) {
            usage(source, "capacity");
            return;
        }
        if (capacity < 0) {
            usage(source, "capacity");
            return;
        }
        final Jail changed = jail.withCapacity(capacity).withShared(capacity != 1 || jail.shared());
        save(source, changed, "capacity-set", Map.of("jail", jail.name(), "room", room(messages(source), changed)));
    }

    private void server(JudicatorSender source, Jail jail, Optional<String> value) {
        if (value.isEmpty()) {
            send(source, "jails", "server-now", Map.of("jail", jail.name(), "server", serverName(messages(source), jail)));
            return;
        }
        final String server = value.get().equalsIgnoreCase("none") ? "" : value.get().toLowerCase(Locale.ROOT);
        if (!server.isEmpty() && !Jail.VALID_NAME.matcher(server).matches()) {
            usage(source, "server");
            return;
        }
        final Jail changed = jail.withServer(server);
        save(source, changed, "server-set", Map.of("jail", jail.name(), "server", serverName(messages(source), changed)));
    }

    private void escape(JudicatorSender source, Jail jail, CommandArgs args) {
        final String action = args.word().map(word -> word.toLowerCase(Locale.ROOT)).orElse("list");
        switch (action) {
            case "list" -> {
                final ConfigurationNode messages = messages(source);
                if (jail.escapes().isEmpty()) {
                    source.sendMessage(PunishUtils.format(messages, Map.of("jail", jail.name()), "jails", "no-escapes"));
                    return;
                }
                final TextComponent.Builder text = Component.text()
                        .append(PunishUtils.format(messages, Map.of("jail", jail.name()), "jails", "escapes-title"));
                for (int i = 0; i < jail.escapes().size(); i++) {
                    final EscapeRoute route = jail.escapes().get(i);
                    text.append(PunishUtils.format(messages, Map.of("index", String.valueOf(i + 1), "at", route.describe(),
                            "radius", String.valueOf(route.radius()), "chance", String.valueOf(route.chance())), "jails", "escape-line"));
                }
                source.sendMessage(text.build());
            }
            case "add" -> {
                final int chance;
                final double radius;
                try {
                    chance = args.word().map(Integer::parseInt).orElse(100);
                    radius = args.word().map(Double::parseDouble).orElse(EscapeRoute.DEFAULT_RADIUS);
                } catch (NumberFormatException e) {
                    usage(source, "escape");
                    return;
                }
                if (chance < 0 || chance > 100 || radius <= 0 || radius > EscapeRoute.MAX_RADIUS) {
                    usage(source, "escape");
                    return;
                }
                if (jail.escapes().size() >= Jail.MAX_ESCAPES) {
                    send(source, "error", "jail-escapes-full", Map.of("jail", jail.name(), "max", String.valueOf(Jail.MAX_ESCAPES)));
                    return;
                }
                if (!editableHere(source, jail)) return;
                here(source).ifPresent(at -> at.thenRun(player -> {
                    final EscapeRoute route = new EscapeRoute(new Location(player.at().world(), player.at().x(), player.at().y(),
                            player.at().z(), 0, 0), radius, chance);
                    final Jail current = jails().find(jail.name()).orElse(jail);
                    // a prisoner put back at the spawn would roll again and again
                    if (route.reaches(current.spawn())) {
                        send(source, "error", "jail-escape-spawn", Map.of("jail", jail.name()));
                        return;
                    }
                    save(source, current.withEscape(route), "escape-added", Map.of("jail", jail.name(), "at", route.describe(),
                            "chance", String.valueOf(route.chance()), "radius", String.valueOf(route.radius())));
                }));
            }
            case "remove" -> {
                final int index;
                try {
                    index = Integer.parseInt(args.word().orElse(""));
                } catch (NumberFormatException e) {
                    usage(source, "escape");
                    return;
                }
                if (index < 1 || index > jail.escapes().size()) {
                    usage(source, "escape");
                    return;
                }
                save(source, jail.withoutEscape(index), "escape-removed", Map.of("jail", jail.name(), "index", String.valueOf(index)));
            }
            case "clear" -> save(source, jail.withoutEscapes(), "escapes-cleared", Map.of("jail", jail.name()));
            default -> usage(source, "escape");
        }
    }

    private void teleport(JudicatorSender source, Jail jail) {
        if (!editableHere(source, jail)) return;
        final JudicatorPlayer player = source.asPlayer().orElseThrow();
        judicator.getPlatform().teleport(player, jail.spawn()).thenAccept(moved -> {
            if (moved) send(source, "jails", "teleported", Map.of("jail", jail.name()));
            else send(source, "error", "unknown", Map.of());
        });
    }

    private void save(JudicatorSender source, Jail jail, String done, Map<String, String> values) {
        judicator.getPlatform().runAsync(() -> {
            if (!jails().save(jail, source.getName())) {
                send(source, "error", "unknown", Map.of());
                return;
            }
            send(source, "jails", done, values);
        });
    }

    /**
     * Whether the sender can set a place of this jail here: a player on the jail's own server.
     */
    private boolean editableHere(JudicatorSender source, Jail jail) {
        if (!canStandHere(source)) return false;
        if (!jails().isLocal(jail)) {
            send(source, "error", "jail-elsewhere", Map.of("jail", jail.name(), "server", jail.server()));
            return false;
        }
        return true;
    }

    private boolean canStandHere(JudicatorSender source) {
        final Platform platform = judicator.getPlatform();
        if (source.isPlayer() && platform.hasWorlds() && !platform.getType().isProxy()) return true;
        send(source, "error", "jail-needs-position", Map.of());
        return false;
    }

    /**
     * Where the player who sent the command stands, read on their thread.
     */
    private Optional<Here> here(JudicatorSender source) {
        if (!canStandHere(source)) return Optional.empty();
        return Optional.of(new Here(source.asPlayer().orElseThrow()));
    }

    private final class Here {
        private final JudicatorPlayer player;

        private Here(JudicatorPlayer player) {
            this.player = player;
        }

        /**
         * Reads the position on the player's thread, then runs the action off it (it may use the database).
         */
        void thenRun(java.util.function.Consumer<Position> action) {
            final Platform platform = judicator.getPlatform();
            platform.runFor(player, () -> {
                final Optional<Location> at = platform.locationOf(player);
                if (at.isEmpty()) return;
                if (!Location.storable(at.get().world())) {
                    send(player, "error", "jail-world-name", Map.of("world", at.get().world()));
                    return;
                }
                platform.runAsync(() -> action.accept(new Position(player, at.get())));
            });
        }
    }

    private record Position(JudicatorPlayer player, Location at) {
    }

    /**
     * The usage of one subcommand ({@code jails.usage.<sub>}; {@code jails.usage.jail} for those that only take the
     * jail), instead of the whole {@code /jails} usage.
     */
    private void usage(JudicatorSender source, String sub) {
        final ConfigurationNode usages = messages(source).node("jails", "usage");
        final String key = usages.node(sub).virtual() ? "jail" : sub;
        source.sendMessage(PunishUtils.format(messages(source), Map.of("sub", sub), "jails", "usage", key));
    }

    private void send(JudicatorSender source, String section, String key, Map<String, String> values) {
        source.sendMessage(PunishUtils.format(messages(source), values, section, key));
    }

    @Override
    public List<String> suggest(JudicatorSender sender, String[] args) {
        if (args.length <= 1) return startingWith(SUBCOMMANDS.stream(), args.length == 0 ? "" : args[0]);
        if (args.length == 2 && !args[0].equalsIgnoreCase("list") && !args[0].equalsIgnoreCase("create")) {
            return startingWith(jails().all().stream().map(Jail::name), args[1]);
        }
        if (args.length == 3) {
            return switch (args[0].toLowerCase(Locale.ROOT)) {
                case "shared" -> startingWith(Stream.of("true", "false"), args[2]);
                case "escape" -> startingWith(Stream.of("add", "list", "remove", "clear"), args[2]);
                case "release" -> startingWith(Stream.of("clear"), args[2]);
                case "server" -> startingWith(Stream.concat(Stream.of("none"), judicator.getScopeService().suggestions().stream()), args[2]);
                default -> List.of();
            };
        }
        return List.of();
    }

    private static List<String> startingWith(Stream<String> options, String prefix) {
        final String lower = prefix.toLowerCase(Locale.ROOT);
        return options.filter(option -> option.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }
}
