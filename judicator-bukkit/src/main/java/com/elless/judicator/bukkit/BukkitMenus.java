package com.elless.judicator.bukkit;

import com.elless.judicator.menus.Menu;
import com.elless.judicator.menus.MenuItem;
import com.elless.judicator.menus.MenuRenderer;
import com.elless.judicator.menus.MenuService;
import com.elless.judicator.platform.JudicatorPlayer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.Plugin;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * The menus as chest inventories. Nothing can be taken from or put into them: every click and drag while one is open
 * is cancelled (shift, number keys, double click, creative included); a click on a slot of the menu goes to the
 * {@link MenuService}. The inventory is found by its holder, never by its title or the {@code InventoryView} (a class
 * up to 1.20, an interface since 1.21).
 */
final class BukkitMenus implements MenuRenderer, Listener {

    /** pre-1.13 servers refuse longer titles */
    private static final int LEGACY_TITLE_LENGTH = 32;
    private static final Set<ClickType> CLICKS = EnumSet.of(ClickType.LEFT, ClickType.RIGHT, ClickType.SHIFT_LEFT, ClickType.SHIFT_RIGHT);

    private final Plugin plugin;
    private final Scheduling scheduling;
    private final MenuItems items = new MenuItems();
    /** the menu each player has open */
    private final Map<UUID, Holder> open = new ConcurrentHashMap<>();
    /** the newest menu asked for each player while its heads are being prepared (1.9-1.12) */
    private final Map<UUID, Menu> preparing = new ConcurrentHashMap<>();
    private Supplier<MenuService> service = () -> null;

    BukkitMenus(Plugin plugin, Scheduling scheduling) {
        this.plugin = plugin;
        this.scheduling = scheduling;
    }

    /**
     * The holder of a menu inventory: which {@link Menu} it shows.
     */
    static final class Holder implements InventoryHolder {
        private final Menu menu;
        private Inventory inventory;

        private Holder(Menu menu) {
            this.menu = menu;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    void bind(Supplier<MenuService> service) {
        this.service = service;
    }

    MenuItems items() {
        return items;
    }

    @Override
    public void open(JudicatorPlayer viewer, Menu menu) {
        final UUID uuid = viewer.getUniqueId();
        if (items.skinsBlockTheGameThread() && menu.items().values().stream().anyMatch(MenuItems::hasOwner)) {
            // 1.9-1.12 look a skin up from Mojang on the thread that puts the head in an inventory: done here off the
            // game thread first, which fills the server's skin cache, then the menu opens on the game thread
            preparing.put(uuid, menu);
            scheduling.async(() -> {
                items.prepareSkins(menu);
                if (!plugin.isEnabled()) return;
                scheduling.player(uuid, () -> {
                    if (preparing.remove(uuid, menu)) show(viewer, menu);
                });
            });
            return;
        }
        preparing.remove(uuid);
        show(viewer, menu);
    }

    private void show(JudicatorPlayer viewer, Menu menu) {
        final Player player = Bukkit.getPlayer(viewer.getUniqueId());
        if (player == null) return;
        final Holder holder = new Holder(menu);
        final Inventory inventory = Bukkit.createInventory(holder, menu.size(), title(menu.title()));
        holder.inventory = inventory;
        for (Map.Entry<Integer, MenuItem> entry : menu.items().entrySet()) {
            inventory.setItem(entry.getKey(), items.create(entry.getValue()));
        }
        open.put(player.getUniqueId(), holder);
        player.openInventory(inventory);
    }

    /**
     * On the next tick: a close often comes from a click, and closing an inventory inside its click event leaves a
     * ghost item on the cursor. While the plugin is being disabled (no more ticks for it), at once.
     */
    @Override
    public void close(JudicatorPlayer viewer) {
        final Holder shown = open.get(viewer.getUniqueId());
        if (shown == null) return;
        final Runnable close = () -> {
            final Player player = Bukkit.getPlayer(viewer.getUniqueId());
            if (player != null && open.get(player.getUniqueId()) == shown) player.closeInventory();
        };
        if (plugin.isEnabled()) scheduling.playerNextTick(viewer.getUniqueId(), close);
        else close.run();
    }

    /**
     * At once, outside of any click event: before a dialog takes the screen of a player with a menu inventory open (a
     * close later would close the dialog on the client).
     */
    void closeNow(JudicatorPlayer viewer) {
        final Player player = Bukkit.getPlayer(viewer.getUniqueId());
        if (player != null && open.containsKey(player.getUniqueId())) player.closeInventory();
    }

    boolean isOpen(UUID player) {
        return open.containsKey(player);
    }

    /**
     * The menu of the clicking player, when the event is about it. Only players with a menu open get their inventory's
     * holder asked for (on Paper, the holder of a chest is a block state snapshot). Compared by holder, never by the
     * inventory object: 1.8 hands events a new wrapper of the same inventory.
     */
    private Holder menuOf(UUID player, Inventory inventory) {
        final Holder shown = open.get(player);
        return shown != null && inventory.getHolder() == shown ? shown : null;
    }

    private String title(String title) {
        if (items.isModern() || title.length() <= LEGACY_TITLE_LENGTH) return title;
        final String cut = title.substring(0, LEGACY_TITLE_LENGTH);
        // a § at the end would be half a colour code
        return cut.endsWith("§") ? cut.substring(0, LEGACY_TITLE_LENGTH - 1) : cut;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        final Inventory top = event.getInventory();
        final Holder holder = menuOf(event.getWhoClicked().getUniqueId(), top);
        if (holder == null) return;
        event.setCancelled(true);
        final int slot = event.getRawSlot();
        if (!(event.getWhoClicked() instanceof Player player) || slot < 0 || slot >= top.getSize()
                || !CLICKS.contains(event.getClick())) return;
        final MenuService menus = service.get();
        if (menus != null) menus.click(player.getUniqueId(), holder.menu, slot);
    }

    /**
     * Again after every other plugin: one that un-cancels inventory clicks must not let items out of a menu.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClickLate(InventoryClickEvent event) {
        if (menuOf(event.getWhoClicked().getUniqueId(), event.getInventory()) != null) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (menuOf(event.getWhoClicked().getUniqueId(), event.getInventory()) != null) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDragLate(InventoryDragEvent event) {
        onDrag(event);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        final UUID uuid = event.getPlayer().getUniqueId();
        final Holder holder = menuOf(uuid, event.getInventory());
        if (holder == null) return;
        open.remove(uuid, holder);
        final MenuService menus = service.get();
        if (menus != null) menus.closed(uuid, holder.menu);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        open.remove(event.getPlayer().getUniqueId());
        preparing.remove(event.getPlayer().getUniqueId());
    }
}
