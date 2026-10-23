package com.openmc.judicator.punish.listeners;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.platform.JudicatorPlayer;
import com.openmc.judicator.players.PlayerRef;
import com.openmc.judicator.punish.PunishUtils;
import com.openmc.judicator.punish.Punishment;
import com.openmc.judicator.punish.data.cache.ImmuneCache;
import com.openmc.judicator.punish.types.PunishPermissions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

public class ConnectionHandler {

    private final Judicator judicator;

    public ConnectionHandler(Judicator judicator) {
        this.judicator = judicator;
    }

    /**
     * Before authentication: bans on the address (and on the name in offline mode).
     */
    public Optional<Component> checkPreLogin(String username, String address) {
        return judicator.getPunishService().searchOnPreLogin(username, address)
                .map(punishment -> PunishUtils.getMessageList(judicator.getMessagesConfig(), punishment, "runners", "ban-kick"));
    }

    /**
     * Runs after authentication (Velocity and BungeeCord {@code LoginEvent}; Bukkit pre-login): the account is recorded (records kept
     * under its name without a UUID become its own), then its bans, the account limits of the address
     * ({@code ip-limits}: a banned account sees its ban, not the limit) and, with {@code alts.deny-if-banned}, the
     * ban-evasion check.
     */
    public Optional<Component> checkLogin(UUID uuid, String username, String address) {
        judicator.getPlayerDirectory().joined(uuid, username);
        final PlayerRef player = judicator.getPlayerDirectory().refOf(uuid, username);
        final Optional<Component> ban = judicator.getPunishService().searchOnLogin(player, address)
                .map(punishment -> PunishUtils.getMessageList(judicator.getMessagesConfig(), punishment, "runners", "ban-kick"));
        if (ban.isPresent()) return ban;
        // behind a proxy in proxy mode, the proxy counts the accounts and checks the alts of the whole network
        if (judicator.isProxyMode()) return Optional.empty();
        final Optional<Component> tooMany = judicator.getAddressPolicy().checkLogin(username, address);
        if (tooMany.isPresent()) return tooMany;
        return checkBannedAlts(player, address);
    }

    private Optional<Component> checkBannedAlts(PlayerRef player, String address) {
        if (!judicator.getConfig().node("alts", "deny-if-banned").getBoolean(false)) return Optional.empty();
        if (judicator.getAddressPolicy().isExempt(address)) return Optional.empty();
        if (judicator.getImmuneCache().isExempt(player.name(), player.uuid(), ImmuneCache.ALTS)) return Optional.empty();
        final List<PlayerRef> others = judicator.getAddressService().findAccountsByIp(address, player);
        if (others.isEmpty()) return Optional.empty();
        // a ban for some servers of the network does not keep the player's other accounts out of it
        final Optional<Punishment> ban = judicator.getPunishService().findLoginBans(others).stream().findFirst();
        if (ban.isEmpty()) return Optional.empty();
        judicator.getStaffNotifier().sendToPermission(PunishPermissions.ALTS_NOTIFY.getPermission(),
                messages -> PunishUtils.format(messages, Map.of("nickname", player.name(), "account", ban.get().getNickname(),
                        "id", String.valueOf(ban.get().getId())), "alts", "denied"));
        return Optional.of(PunishUtils.getMessageList(judicator.getMessagesConfig(), ban.get(), "runners", "alt-ban-kick"));
    }

    /**
     * Where the proxy sends a player who just logged in (Velocity {@code PlayerChooseInitialServerEvent}, BungeeCord
     * {@code ServerConnectEvent} on joining).
     *
     * @param server the server to connect to, or null when {@code kick} is set
     * @param kick   the screen to disconnect with: every server the player could join has a ban for it
     */
    public record InitialServer(String server, Component kick) {
    }

    /**
     * Proxy: the server chosen for a player at login (the first of the try list, or a forced host). A ban for that
     * server sends them to the first server of the try list it does not cover.
     */
    public InitialServer chooseInitialServer(JudicatorPlayer player, String chosen) {
        final List<Punishment> bans = scopedBans(player);
        final Optional<Punishment> ban = bannedFrom(bans, chosen);
        if (ban.isEmpty()) return new InitialServer(chosen, null);
        return judicator.getPlatform().fallbackServers().stream()
                .filter(server -> bannedFrom(bans, server).isEmpty())
                .findFirst()
                .map(server -> new InitialServer(server, null))
                .orElseGet(() -> new InitialServer(null,
                        PunishUtils.getMessageList(judicator.getMessagesConfig(), ban.get(), "runners", "ban-kick")));
    }

    /**
     * Proxy: a player connecting to another server (Velocity {@code ServerPreConnectEvent}, BungeeCord
     * {@code ServerConnectEvent}).
     *
     * @return the message to send when a ban covers that server (the connection is refused)
     */
    public Optional<Component> checkServerSwitch(JudicatorPlayer player, String server) {
        return bannedFrom(scopedBans(player), server)
                .map(ban -> PunishUtils.getMessageList(judicator.messagesFor(player), ban, "runners", "ban-server"));
    }

    /**
     * Proxy: the player got to a server (Velocity {@code ServerPostConnectEvent}, BungeeCord {@code ServerSwitchEvent}). A ban for it applied while they were on
     * their way (after {@link #checkServerSwitch} let them through) sends them on to the first server of the try list
     * it does not cover, or disconnects them.
     */
    public void checkArrival(JudicatorPlayer player) {
        final Optional<String> server = judicator.getScopeService().serverOf(player);
        if (server.isEmpty()) return;
        final List<Punishment> bans = scopedBans(player);
        final Optional<Punishment> ban = bannedFrom(bans, server.get());
        if (ban.isEmpty()) return;
        final Optional<String> fallback = fallbackFor(bans, server.get());
        final Component kick = PunishUtils.getMessageList(judicator.messagesFor(player), ban.get(), "runners", "ban-kick");
        if (fallback.isEmpty()) {
            player.disconnect(kick);
            return;
        }
        player.sendMessage(PunishUtils.getMessageList(judicator.messagesFor(player), ban.get(), "runners", "ban-server"));
        judicator.getPlatform().connect(player, fallback.get()).thenAccept(connected -> {
            if (!connected) player.disconnect(kick);
        });
    }

    /**
     * The first server of the try list that no ban keeps the player out of, other than the one they are leaving.
     */
    public Optional<String> fallbackFor(JudicatorPlayer player, String leaving) {
        return fallbackFor(scopedBans(player), leaving);
    }

    private Optional<String> fallbackFor(List<Punishment> bans, String leaving) {
        return judicator.getPlatform().fallbackServers().stream()
                .filter(server -> !server.equalsIgnoreCase(leaving))
                .filter(server -> bannedFrom(bans, server).isEmpty())
                .findFirst();
    }

    private List<Punishment> scopedBans(JudicatorPlayer player) {
        return judicator.getPunishService().findScopedBans(judicator.getPlayerDirectory().refOf(player), player.getHostAddress());
    }

    private Optional<Punishment> bannedFrom(List<Punishment> bans, String server) {
        return bans.stream().filter(ban -> judicator.getScopeService().appliesTo(ban, server)).findFirst();
    }

    public void onLoginAllowed(UUID uuid, String username, String address) {
        // the proxy records the address and warns about alts once for the network, not at every server switch
        if (judicator.isProxyMode()) return;
        final PlayerRef player = judicator.getPlayerDirectory().refOf(uuid, username);
        judicator.getAddressPolicy().recordLogin(username, address);
        judicator.getAddressService().updateUserIP(player, address);
        notifyAlts(player, address);
    }

    /**
     * The player is in the game (Velocity: login allowed; BungeeCord: {@code PostLoginEvent}; Bukkit:
     * {@code PlayerJoinEvent}, after whitelist and full server checks): paused online-only mutes run again from now.
     */
    public void onJoin(JudicatorPlayer player) {
        judicator.getUuidManager().remember(player.getName(), player.getUniqueId());
        // Proxy mode: switching servers is not joining the network; the proxy keeps the online time, staff levels and notices.
        if (judicator.isProxyMode()) return;
        judicator.getPunishService().resumeOnlineOnlyMutes(judicator.getPlayerDirectory().refOf(player));
        judicator.getHierarchyService().recordLevel(player);
        notifyNotes(player);
        // the proxy can only talk to the player once they are on a server: its listener does it then
        if (!judicator.getPlatform().getType().isProxy()) judicator.getReportService().notifyOnJoin(player);
    }

    /**
     * Tells the staff with {@code judicator.notes.notify} (other than the player) that someone with notes joined.
     */
    private void notifyNotes(JudicatorPlayer joined) {
        if (!judicator.getConfig().node("notes", "notify-on-join").getBoolean(true)) return;
        final int count = judicator.getNoteService().countOf(judicator.getPlayerDirectory().refOf(joined));
        if (count == 0) return;
        final String command = "/notes " + joined.getName();
        judicator.getPlatform().getOnlinePlayers().stream()
                .filter(staff -> !staff.getUniqueId().equals(joined.getUniqueId()))
                .filter(staff -> staff.hasPermission(PunishPermissions.NOTES_NOTIFY.getPermission()))
                .forEach(staff -> staff.sendMessage(PunishUtils.format(judicator.messagesFor(staff),
                                Map.of("nickname", joined.getName(), "count", String.valueOf(count)), "notes", "notify")
                        .hoverEvent(HoverEvent.showText(Component.text(command)))
                        .clickEvent(ClickEvent.runCommand(command))));
    }

    private void notifyAlts(PlayerRef player, String address) {
        if (!judicator.getConfig().node("alts", "notify-on-join").getBoolean(true)) return;
        if (judicator.getAddressPolicy().isExempt(address)) return;
        final Set<String> flagged = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (PlayerRef account : judicator.getAddressService().findAccountsByIp(address, player)) {
            if (!"clean".equals(judicator.getPunishService().statusOf(account))) flagged.add(account.name());
        }
        if (flagged.isEmpty()) return;
        judicator.getStaffNotifier().sendToPermission(PunishPermissions.ALTS_NOTIFY.getPermission(),
                messages -> PunishUtils.format(messages, Map.of("nickname", player.name(), "accounts", String.join(", ", flagged)), "alts", "notify"));
    }

    public void onDisconnect(UUID uuid, String username, String address) {
        if (!judicator.isProxyMode()) judicator.getPunishService().pauseOnlineOnlyMutes(judicator.getPlayerDirectory().refOf(uuid, username));
        judicator.getPunishService().cleanupMutePunishmentCache(uuid, address);
    }

}
