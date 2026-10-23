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
     * Runs after authentication (Velocity {@code LoginEvent}; Bukkit pre-login): the account is recorded (records kept
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
        final Optional<Punishment> ban = judicator.getPunishService().findActiveBans(others).stream().findFirst();
        if (ban.isEmpty()) return Optional.empty();
        judicator.getStaffNotifier().sendToPermission(PunishPermissions.ALTS_NOTIFY.getPermission(),
                messages -> PunishUtils.format(messages, Map.of("nickname", player.name(), "account", ban.get().getNickname(),
                        "id", String.valueOf(ban.get().getId())), "alts", "denied"));
        return Optional.of(PunishUtils.getMessageList(judicator.getMessagesConfig(), ban.get(), "runners", "alt-ban-kick"));
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
     * The player is in the game (Velocity: login allowed; Bukkit: {@code PlayerJoinEvent}, after whitelist and full
     * server checks): paused online-only mutes run again from now.
     */
    public void onJoin(JudicatorPlayer player) {
        judicator.getUuidManager().remember(player.getName(), player.getUniqueId());
        // Proxy mode: switching servers is not joining the network; the proxy keeps the online time, staff levels and notices.
        if (judicator.isProxyMode()) return;
        judicator.getPunishService().resumeOnlineOnlyMutes(judicator.getPlayerDirectory().refOf(player));
        judicator.getHierarchyService().recordLevel(player);
        notifyNotes(player);
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
