package com.openmc.judicator.punish.listeners;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.platform.JudicatorPlayer;
import com.openmc.judicator.punish.PunishUtils;
import com.openmc.judicator.punish.Punishment;
import com.openmc.judicator.punish.data.cache.ImmuneCache;
import com.openmc.judicator.punish.types.PunishPermissions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;

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

    public Optional<Component> checkPreLogin(String username, String address) {
        final Optional<Component> ban = judicator.getPunishService().searchOnPreLogin(username, address)
                .map(punishment -> PunishUtils.getMessageList(judicator.getMessagesConfig(), punishment, "runners", "ban-kick"));
        if (ban.isPresent()) return ban;
        // behind a proxy in proxy mode, the proxy counts the accounts of the whole network
        if (judicator.isProxyMode()) return Optional.empty();
        return judicator.getAddressPolicy().checkLogin(username, address);
    }

    /**
     * Runs after authentication (Velocity {@code LoginEvent}; Bukkit pre-login): the UUID ban check in online mode
     * and, with {@code alts.deny-if-banned}, the ban-evasion check.
     */
    public Optional<Component> checkLogin(UUID uuid, String username, String address) {
        if (judicator.trustsUuids()) {
            final Optional<Component> ban = judicator.getPunishService().searchOnLogin(uuid, username, address)
                    .map(punishment -> PunishUtils.getMessageList(judicator.getMessagesConfig(), punishment, "runners", "ban-kick"));
            if (ban.isPresent()) return ban;
        }
        if (judicator.isProxyMode()) return Optional.empty();
        return checkBannedAlts(uuid, username, address);
    }

    /**
     * With {@code alts.deny-if-banned: true}, refuses an account when another account that used the same IP has a ban
     * in force (evading a ban with an alt). Exempt addresses and players exempt from {@code alts} pass. The screen shows
     * the ban's id, not the other account's name.
     */
    private Optional<Component> checkBannedAlts(UUID uuid, String username, String address) {
        if (!judicator.getConfig().node("alts", "deny-if-banned").getBoolean(false)) return Optional.empty();
        if (judicator.getAddressPolicy().isExempt(address)) return Optional.empty();
        if (judicator.getImmuneCache().isExempt(username, uuid, ImmuneCache.ALTS)) return Optional.empty();
        final Set<String> others = judicator.getAddressService().findAccountsByIp(address, username);
        if (others.isEmpty()) return Optional.empty();
        final Optional<Punishment> ban = judicator.getPunishService().findActiveBans(others).stream().findFirst();
        if (ban.isEmpty()) return Optional.empty();
        judicator.getStaffNotifier().sendToPermission(PunishPermissions.ALTS_NOTIFY.getPermission(),
                messages -> PunishUtils.format(messages, Map.of("nickname", username, "account", ban.get().getNickname(),
                        "id", String.valueOf(ban.get().getId())), "alts", "denied"));
        return Optional.of(PunishUtils.getMessageList(judicator.getMessagesConfig(), ban.get(), "runners", "alt-ban-kick"));
    }

    public void onLoginAllowed(String username, String address) {
        // the proxy records the address and warns about alts once for the network, not at every server switch
        if (judicator.isProxyMode()) return;
        judicator.getAddressPolicy().recordLogin(username, address);
        judicator.getAddressService().updateUserIP(username, address);
        notifyAlts(username, address);
    }

    /**
     * The player is in the game (Velocity: login allowed; Bukkit: {@code PlayerJoinEvent}, after whitelist and full
     * server checks): paused online-only mutes run again from now.
     */
    public void onJoin(JudicatorPlayer player) {
        judicator.getUuidManager().remember(player.getName(), player.getUniqueId());
        // Proxy mode: switching servers is not joining the network; the proxy keeps the online time, staff levels and notices.
        if (judicator.isProxyMode()) return;
        judicator.getPunishService().resumeOnlineOnlyMutes(player.getName());
        judicator.getHierarchyService().recordLevel(player);
        notifyNotes(player);
    }

    /**
     * Tells the staff with {@code judicator.notes.notify} (other than the player) that someone with notes joined.
     */
    private void notifyNotes(JudicatorPlayer joined) {
        if (!judicator.getConfig().node("notes", "notify-on-join").getBoolean(true)) return;
        final int count = judicator.getNoteService().countByUsername(joined.getName());
        if (count == 0) return;
        final String command = "/notes " + joined.getName();
        judicator.getPlatform().getOnlinePlayers().stream()
                .filter(staff -> !staff.getName().equalsIgnoreCase(joined.getName()))
                .filter(staff -> staff.hasPermission(PunishPermissions.NOTES_NOTIFY.getPermission()))
                .forEach(staff -> staff.sendMessage(PunishUtils.format(judicator.messagesFor(staff),
                                Map.of("nickname", joined.getName(), "count", String.valueOf(count)), "notes", "notify")
                        .hoverEvent(HoverEvent.showText(Component.text(command)))
                        .clickEvent(ClickEvent.runCommand(command))));
    }

    private void notifyAlts(String username, String address) {
        if (!judicator.getConfig().node("alts", "notify-on-join").getBoolean(true)) return;
        if (judicator.getAddressPolicy().isExempt(address)) return;
        final Set<String> flagged = new TreeSet<>();
        for (String account : judicator.getAddressService().findAccountsByIp(address, username)) {
            if (!"clean".equals(judicator.getPunishService().statusOf(account))) flagged.add(account);
        }
        if (flagged.isEmpty()) return;
        judicator.getStaffNotifier().sendToPermission(PunishPermissions.ALTS_NOTIFY.getPermission(),
                messages -> PunishUtils.format(messages, Map.of("nickname", username, "accounts", String.join(", ", flagged)), "alts", "notify"));
    }

    public void onDisconnect(String username, String address) {
        if (!judicator.isProxyMode()) judicator.getPunishService().pauseOnlineOnlyMutes(username);
        judicator.getPunishService().cleanupMutePunishmentCache(username, address);
    }

}
