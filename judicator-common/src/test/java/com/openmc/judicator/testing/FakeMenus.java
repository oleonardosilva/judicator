package com.openmc.judicator.testing;

import com.openmc.judicator.menus.Menu;
import com.openmc.judicator.menus.MenuItem;
import com.openmc.judicator.menus.MenuRenderer;
import com.openmc.judicator.menus.MenuService;
import com.openmc.judicator.platform.JudicatorPlayer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Menus as a Spigot/Paper server shows them, without the inventory: the menu each player has open, clicks by slot or
 * by item name, and the close events an inventory sends (also when the next menu replaces it).
 */
public final class FakeMenus implements MenuRenderer {

    private final Map<UUID, Menu> open = new HashMap<>();
    private Supplier<MenuService> service = () -> null;

    void bind(Supplier<MenuService> service) {
        this.service = service;
    }

    @Override
    public void open(JudicatorPlayer viewer, Menu menu) {
        final Menu previous = open.put(viewer.getUniqueId(), menu);
        if (previous != null) service.get().closed(viewer.getUniqueId(), previous);
    }

    @Override
    public void close(JudicatorPlayer viewer) {
        final Menu previous = open.remove(viewer.getUniqueId());
        if (previous != null) service.get().closed(viewer.getUniqueId(), previous);
    }

    /**
     * The player closes the menu (Esc).
     */
    public void escape(JudicatorPlayer viewer) {
        close(viewer);
    }

    public Optional<Menu> of(JudicatorPlayer viewer) {
        return Optional.ofNullable(open.get(viewer.getUniqueId()));
    }

    public String title(JudicatorPlayer viewer) {
        return of(viewer).map(menu -> plain(menu.title())).orElse(null);
    }

    /**
     * The names of the items of the open menu, without colour codes, in slot order.
     */
    public List<String> names(JudicatorPlayer viewer) {
        return of(viewer).map(menu -> menu.items().values().stream().map(item -> plain(item.name())).toList()).orElse(List.of());
    }

    /**
     * The item with that name (without colour codes), or else the first whose name contains it.
     */
    public Optional<MenuItem> item(JudicatorPlayer viewer, String name) {
        return of(viewer).flatMap(menu -> menu.items().values().stream().filter(item -> plain(item.name()).equals(name)).findFirst()
                .or(() -> menu.items().values().stream().filter(item -> plain(item.name()).contains(name)).findFirst()));
    }

    public List<String> lore(JudicatorPlayer viewer, String name) {
        return item(viewer, name).map(item -> item.lore().stream().map(FakeMenus::plain).toList())
                .orElseThrow(() -> new AssertionError("no item '" + name + "' in " + names(viewer)));
    }

    public void click(JudicatorPlayer viewer, int slot) {
        final Menu menu = of(viewer).orElseThrow(() -> new AssertionError(viewer.getName() + " has no menu open"));
        service.get().click(viewer.getUniqueId(), menu, slot);
    }

    /**
     * Clicks the item with that name, or else the first whose name contains it.
     */
    public void click(JudicatorPlayer viewer, String name) {
        final Menu menu = of(viewer).orElseThrow(() -> new AssertionError(viewer.getName() + " has no menu open"));
        final int slot = menu.items().entrySet().stream()
                .filter(entry -> plain(entry.getValue().name()).equals(name))
                .map(Map.Entry::getKey).findFirst()
                .or(() -> menu.items().entrySet().stream()
                        .filter(entry -> plain(entry.getValue().name()).contains(name))
                        .map(Map.Entry::getKey).findFirst())
                .orElseThrow(() -> new AssertionError("no item '" + name + "' in " + names(viewer)));
        service.get().click(viewer.getUniqueId(), menu, slot);
    }

    static String plain(String text) {
        return text.replaceAll("§.", "");
    }
}
