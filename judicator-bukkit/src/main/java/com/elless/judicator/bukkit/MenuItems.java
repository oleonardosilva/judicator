package com.elless.judicator.bukkit;

import com.elless.judicator.menus.Menu;
import com.elless.judicator.menus.MenuIcon;
import com.elless.judicator.menus.MenuItem;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The item of each {@link MenuIcon} on this server's version. Materials are looked up by name — the names of 1.13+
 * first, then the 1.8–1.12 name and data value — and never through a {@code Material} constant, which would not exist
 * on the other side of the 1.13 rename.
 */
final class MenuItems {

    /** {@code SkullMeta#setOwningPlayer(OfflinePlayer)}, 1.12.1+; before it, {@code setOwner(String)} */
    private static final MethodHandle SET_OWNING_PLAYER = BukkitCompat.find(SkullMeta.class, "setOwningPlayer",
            MethodType.methodType(boolean.class, org.bukkit.OfflinePlayer.class));
    /** Paper: {@code Bukkit.createProfile(UUID, String)} and {@code SkullMeta#setPlayerProfile} */
    private static final MethodHandle PAPER_PROFILE = staticMethod(Bukkit.class, "createProfile", "com.destroystokyo.paper.profile.PlayerProfile");
    private static final MethodHandle SET_PAPER_PROFILE = virtualMethod(SkullMeta.class, "setPlayerProfile", "com.destroystokyo.paper.profile.PlayerProfile");
    /** Spigot 1.18.1+: {@code Bukkit.createPlayerProfile(UUID, String)} and {@code SkullMeta#setOwnerProfile} */
    private static final MethodHandle SPIGOT_PROFILE = staticMethod(Bukkit.class, "createPlayerProfile", "org.bukkit.profile.PlayerProfile");
    private static final MethodHandle SET_SPIGOT_PROFILE = virtualMethod(SkullMeta.class, "setOwnerProfile", "org.bukkit.profile.PlayerProfile");
    /** {@code Material#isItem()}, 1.13+: a block such as water has no item */
    private static final MethodHandle IS_ITEM = BukkitCompat.find(Material.class, "isItem", MethodType.methodType(boolean.class));

    private record Look(Material material, short data) {
    }

    /** whether materials have the names of 1.13+ (no data values) */
    private final boolean modern = Material.getMaterial("PLAYER_HEAD") != null;
    private final Map<MenuIcon, Look> looks = new EnumMap<>(MenuIcon.class);
    /** the items asked for by id ({@link MenuItem#material()}): empty when this version has no such item */
    private final Map<String, Optional<Material>> materials = new ConcurrentHashMap<>();

    MenuItems() {
        look(MenuIcon.PLAYER_HEAD, "PLAYER_HEAD", "SKULL_ITEM", 3);
        look(MenuIcon.BAN, "BARRIER", "BARRIER", 0);
        look(MenuIcon.TEMPBAN, "CLOCK", "WATCH", 0);
        look(MenuIcon.MUTE, "WRITABLE_BOOK", "BOOK_AND_QUILL", 0);
        look(MenuIcon.TEMPMUTE, "MAP", "EMPTY_MAP", 0);
        look(MenuIcon.KICK, "LEATHER_BOOTS", "LEATHER_BOOTS", 0);
        look(MenuIcon.WARN, "ORANGE_DYE", "INK_SACK", 14);
        look(MenuIcon.NOTE, "BOOK", "BOOK", 0);
        look(MenuIcon.ALTS, "NAME_TAG", "NAME_TAG", 0);
        look(MenuIcon.REPORT, "OAK_SIGN|SIGN", "SIGN", 0);
        look(MenuIcon.HISTORY, "BOOKSHELF", "BOOKSHELF", 0);
        look(MenuIcon.ONLINE, "EMERALD", "EMERALD", 0);
        look(MenuIcon.SEARCH, "COMPASS", "COMPASS", 0);
        look(MenuIcon.PUNISH, "IRON_SWORD", "IRON_SWORD", 0);
        look(MenuIcon.PARDON, "LIME_DYE", "INK_SACK", 10);
        look(MenuIcon.EXTEND, "CLOCK", "WATCH", 0);
        look(MenuIcon.EDIT, "WRITABLE_BOOK", "BOOK_AND_QUILL", 0);
        look(MenuIcon.TELEPORT, "ENDER_PEARL", "ENDER_PEARL", 0);
        look(MenuIcon.ASSIGN, "NAME_TAG", "NAME_TAG", 0);
        look(MenuIcon.FILTER, "HOPPER", "HOPPER", 0);
        look(MenuIcon.SORT, "COMPARATOR", "REDSTONE_COMPARATOR", 0);
        look(MenuIcon.STATUS, "LEVER", "LEVER", 0);
        look(MenuIcon.INFO, "PAPER", "PAPER", 0);
        look(MenuIcon.CONFIRM, "LIME_WOOL", "WOOL", 5);
        look(MenuIcon.CANCEL, "RED_WOOL", "WOOL", 14);
        look(MenuIcon.TOGGLE_ON, "LIME_DYE", "INK_SACK", 10);
        look(MenuIcon.TOGGLE_OFF, "GRAY_DYE", "INK_SACK", 8);
        look(MenuIcon.BACK, "ARROW", "ARROW", 0);
        look(MenuIcon.CLOSE, "BARRIER", "BARRIER", 0);
        look(MenuIcon.PREVIOUS, "ARROW", "ARROW", 0);
        look(MenuIcon.NEXT, "ARROW", "ARROW", 0);
        look(MenuIcon.PAGE, "PAPER", "PAPER", 0);
        look(MenuIcon.EMPTY, "GLASS_BOTTLE", "GLASS_BOTTLE", 0);
        look(MenuIcon.FILLER, "GRAY_STAINED_GLASS_PANE", "STAINED_GLASS_PANE", 7);
    }

    boolean isModern() {
        return modern;
    }

    /**
     * 1.9 to 1.12: putting a head with an owner in an inventory looks the skin up from Mojang right there (1.8 leaves
     * it to the game client, 1.13+ looks it up in the background).
     */
    boolean skinsBlockTheGameThread() {
        return !modern && Material.getMaterial("SHIELD") != null;
    }

    static boolean hasOwner(MenuItem item) {
        return item.icon() == MenuIcon.PLAYER_HEAD && (item.head() != null || item.headName() != null);
    }

    /**
     * Off the game thread: puts each head of the menu in an inventory of its own, which makes the server look its
     * skin up (and keep it in its cache) here instead of when the menu opens.
     */
    void prepareSkins(Menu menu) {
        try {
            final org.bukkit.inventory.Inventory scratch = Bukkit.createInventory(null, 9);
            for (MenuItem item : menu.items().values()) {
                if (hasOwner(item)) scratch.setItem(0, create(item));
            }
        } catch (RuntimeException ignored) {
            // the menu still opens; a head may then wait for its skin
        }
    }

    /**
     * An item by its id, {@code diamond_sword} or {@code minecraft:diamond_sword}.
     */
    private Optional<Material> material(String id) {
        return materials.computeIfAbsent(id.toLowerCase(Locale.ROOT), key -> {
            final Material material = Material.matchMaterial(key.startsWith("minecraft:") ? key.substring(10) : key);
            if (material == null || material == Material.AIR) return Optional.empty();
            try {
                if (IS_ITEM != null && !(boolean) IS_ITEM.invoke(material)) return Optional.empty();
            } catch (Throwable e) {
                return Optional.empty();
            }
            return Optional.of(material);
        });
    }

    /**
     * @param modernNames the 1.13+ names to try in order, separated by | (OAK_SIGN is 1.14+, SIGN on 1.13)
     */
    private void look(MenuIcon icon, String modernNames, String legacyName, int data) {
        Material material = null;
        if (modern) {
            for (String name : modernNames.split("\\|")) {
                material = Material.getMaterial(name);
                if (material != null) break;
            }
        } else {
            material = Material.getMaterial(legacyName);
        }
        short value = modern ? 0 : (short) data;
        if (material == null) {
            material = Material.getMaterial("PAPER");
            value = 0;
        }
        looks.put(icon, new Look(material, value));
    }

    @SuppressWarnings("deprecation")
    ItemStack create(MenuItem item) {
        final Look look = item.material() == null ? looks.get(item.icon())
                : material(item.material()).map(material -> new Look(material, (short) 0)).orElse(looks.get(item.icon()));
        final ItemStack stack = look.data() == 0
                ? new ItemStack(look.material(), item.amount())
                : new ItemStack(look.material(), item.amount(), look.data());
        final ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;
        meta.setDisplayName(item.name());
        meta.setLore(item.lore());
        meta.addItemFlags(ItemFlag.values());
        if (meta instanceof SkullMeta skull && item.head() != null) skin(skull, item);
        stack.setItemMeta(meta);
        return stack;
    }

    /**
     * The skin of any player, without a lookup on the game thread: a player online here gives their complete profile;
     * anyone else is a profile with only the UUID and the name, whose skin the game client fetches itself. On an
     * offline-mode server the UUIDs come from the names and have no skin, so the name alone is sent.
     */
    @SuppressWarnings("deprecation")
    private static void skin(SkullMeta skull, MenuItem item) {
        final Player online = item.head() != null ? Bukkit.getPlayer(item.head())
                : item.headName() != null ? Bukkit.getPlayerExact(item.headName()) : null;
        try {
            if (online != null) {
                if (SET_OWNING_PLAYER != null) SET_OWNING_PLAYER.invoke(skull, online);
                else skull.setOwner(online.getName());
                return;
            }
            final UUID uuid = Bukkit.getOnlineMode() ? item.head() : null;
            final String name = item.headName();
            if (uuid == null && (name == null || name.isEmpty())) return;
            if (PAPER_PROFILE != null && SET_PAPER_PROFILE != null) {
                SET_PAPER_PROFILE.invoke(skull, PAPER_PROFILE.invoke(uuid, name));
            } else if (SPIGOT_PROFILE != null && SET_SPIGOT_PROFILE != null) {
                SET_SPIGOT_PROFILE.invoke(skull, SPIGOT_PROFILE.invoke(uuid, name));
            } else if (SET_OWNING_PLAYER != null && uuid != null) {
                SET_OWNING_PLAYER.invoke(skull, Bukkit.getOfflinePlayer(uuid));
            } else if (name != null) {
                skull.setOwner(name);
            }
        } catch (Throwable ignored) {
            // the default head
        }
    }

    /**
     * {@code owner.name(UUID, String)} returning the class named, when the server has both.
     */
    private static MethodHandle staticMethod(Class<?> owner, String name, String returned) {
        try {
            final Class<?> type = Class.forName(returned, false, owner.getClassLoader());
            return MethodHandles.publicLookup().findStatic(owner, name, MethodType.methodType(type, UUID.class, String.class));
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }

    /**
     * {@code owner#name(the class named)}, returning nothing, when the server has both.
     */
    private static MethodHandle virtualMethod(Class<?> owner, String name, String parameter) {
        try {
            final Class<?> type = Class.forName(parameter, false, owner.getClassLoader());
            return MethodHandles.publicLookup().findVirtual(owner, name, MethodType.methodType(void.class, type));
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }
}
