package com.openmc.judicator.bukkit;

import com.openmc.judicator.menus.Menu;
import com.openmc.judicator.menus.MenuItem;
import com.openmc.judicator.menus.MenuRenderer;
import com.openmc.judicator.menus.MenuService;
import com.openmc.judicator.platform.JudicatorPlayer;
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

    private final MenuItems items = new MenuItems();
    /** the menu inventory each player has open */
    private final Map<UUID, Inventory> open = new ConcurrentHashMap<>();
    private Supplier<MenuService> service = () -> null;

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

    @Override
    public void open(JudicatorPlayer viewer, Menu menu) {
        final Player player = Bukkit.getPlayer(viewer.getUniqueId());
        if (player == null) return;
        final Holder holder = new Holder(menu);
        final Inventory inventory = Bukkit.createInventory(holder, menu.size(), title(menu.title()));
        holder.inventory = inventory;
        for (Map.Entry<Integer, MenuItem> entry : menu.items().entrySet()) {
            inventory.setItem(entry.getKey(), items.create(entry.getValue()));
        }
        open.put(player.getUniqueId(), inventory);
        player.openInventory(inventory);
    }

    @Override
    public void close(JudicatorPlayer viewer) {
        final Player player = Bukkit.getPlayer(viewer.getUniqueId());
        if (player != null && open.containsKey(player.getUniqueId())) player.closeInventory();
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
        if (!(top.getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        final int slot = event.getRawSlot();
        if (!(event.getWhoClicked() instanceof Player player) || slot < 0 || slot >= top.getSize()
                || !CLICKS.contains(event.getClick())) return;
        final MenuService menus = service.get();
        if (menus != null) menus.click(player.getUniqueId(), holder.menu, slot);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)) return;
        final UUID uuid = event.getPlayer().getUniqueId();
        open.remove(uuid, event.getInventory());
        final MenuService menus = service.get();
        if (menus != null) menus.closed(uuid, holder.menu);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        open.remove(event.getPlayer().getUniqueId());
    }
}
