package com.elless.judicator.bukkit;

import com.elless.judicator.menus.Menu;
import com.elless.judicator.menus.MenuRenderer;
import com.elless.judicator.platform.JudicatorPlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Inventories or dialogs, per player and per menu. Dialogs need the server to have them (Paper with their API) and the
 * player's client to be 1.21.6 or newer (behind ViaVersion an older client can join a newer server); then
 * {@code menus.dialogs} decides: {@code mixed} (lists as inventories, a grid of items to sort and page; everything else,
 * questions and forms as dialogs), {@code all} or {@code none}.
 */
final class BukkitMenuChooser implements MenuRenderer, Listener {

    enum Mode {
        MIXED, ALL, NONE;

        /**
         * @param value {@code menus.dialogs}: mixed, all or none; true and false of the version before mean mixed and none
         */
        static Mode of(String value) {
            if (value == null) return MIXED;
            return switch (value.trim().toLowerCase(java.util.Locale.ROOT)) {
                case "all" -> ALL;
                case "none", "false", "off" -> NONE;
                default -> MIXED;
            };
        }
    }

    /** the protocol of 1.21.6, the first version with dialogs */
    static final int DIALOGS_PROTOCOL = 771;

    private final Scheduling scheduling;
    private final BukkitMenus inventories;
    private final Supplier<Mode> mode;
    private volatile MenuRenderer dialogs;
    /** what shows each player's menu now */
    private final Map<UUID, MenuRenderer> showing = new ConcurrentHashMap<>();

    BukkitMenuChooser(Scheduling scheduling, BukkitMenus inventories, Supplier<Mode> mode) {
        this.scheduling = scheduling;
        this.inventories = inventories;
        this.mode = mode;
    }

    void dialogs(MenuRenderer dialogs) {
        this.dialogs = dialogs;
    }

    @Override
    public void open(JudicatorPlayer viewer, Menu menu) {
        final MenuRenderer renderer = rendererFor(viewer.getUniqueId(), menu.isList());
        final MenuRenderer previous = showing.put(viewer.getUniqueId(), renderer);
        if (previous == inventories && renderer != inventories) inventories.closeNow(viewer);
        else if (previous != null && previous != renderer) previous.close(viewer);
        renderer.open(viewer, menu);
    }

    @Override
    public void close(JudicatorPlayer viewer) {
        final MenuRenderer renderer = showing.remove(viewer.getUniqueId());
        if (renderer != null) renderer.close(viewer);
    }

    /**
     * A question goes to a dialog whenever the player can see one (also in {@code mixed}, from a list).
     */
    @Override
    public boolean ask(JudicatorPlayer viewer, Question question, Consumer<String> onAnswer, Runnable onCancel) {
        return onDialog(viewer, dialogs -> dialogs.ask(viewer, question, onAnswer, onCancel));
    }

    @Override
    public boolean form(JudicatorPlayer viewer, Form form, Consumer<Map<String, String>> onSubmit, Runnable onCancel) {
        return onDialog(viewer, dialogs -> dialogs.form(viewer, form, onSubmit, onCancel));
    }

    /**
     * Shows a dialog of its own (a question, a form). From a menu inventory it comes on the next tick: the click that
     * asked is still being handled, and the inventory closes first.
     */
    private boolean onDialog(JudicatorPlayer viewer, java.util.function.Predicate<MenuRenderer> show) {
        final MenuRenderer available = dialogs;
        if (available == null || mode.get() == Mode.NONE || !clientHasDialogs(viewer.getUniqueId())) return false;
        final MenuRenderer previous = showing.put(viewer.getUniqueId(), available);
        if (previous == inventories && inventories.isOpen(viewer.getUniqueId())) {
            scheduling.playerNextTick(viewer.getUniqueId(), () -> {
                inventories.closeNow(viewer);
                show.test(available);
            });
            return true;
        }
        return show.test(available);
    }

    private MenuRenderer rendererFor(UUID player, boolean list) {
        final MenuRenderer available = dialogs;
        final Mode chosen = mode.get();
        if (available == null || chosen == Mode.NONE || (chosen == Mode.MIXED && list) || !clientHasDialogs(player)) return inventories;
        return available;
    }

    private static boolean clientHasDialogs(UUID player) {
        final int protocol = ViaVersion.protocolOf(player);
        return protocol < 0 || protocol >= DIALOGS_PROTOCOL;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        showing.remove(event.getPlayer().getUniqueId());
    }

    /**
     * The protocol of a player's client as ViaVersion knows it; -1 without ViaVersion (the client is the server's
     * version).
     */
    static final class ViaVersion {

        private static final MethodHandle PLAYER_VERSION = lookup();
        private static final Object API = api();

        private ViaVersion() {
        }

        static int protocolOf(UUID player) {
            if (API == null || PLAYER_VERSION == null) return -1;
            try {
                return (int) PLAYER_VERSION.invoke(API, player);
            } catch (Throwable e) {
                return -1;
            }
        }

        private static Object api() {
            try {
                return Class.forName("com.viaversion.viaversion.api.Via").getMethod("getAPI").invoke(null);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                return null;
            }
        }

        private static MethodHandle lookup() {
            try {
                final Class<?> api = Class.forName("com.viaversion.viaversion.api.ViaAPI");
                return MethodHandles.publicLookup().findVirtual(api, "getPlayerVersion", MethodType.methodType(int.class, UUID.class));
            } catch (ReflectiveOperationException | LinkageError e) {
                return null;
            }
        }
    }
}
