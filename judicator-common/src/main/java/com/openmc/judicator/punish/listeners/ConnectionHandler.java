package com.openmc.judicator.punish.listeners;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.platform.JudicatorPlayer;
import com.openmc.judicator.punish.PunishUtils;
import com.openmc.judicator.punish.types.PunishPermissions;
import org.spongepowered.configurate.ConfigurationNode;
import net.kyori.adventure.text.Component;

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
        return judicator.getAddressPolicy().checkLogin(username, address);
    }

    public Optional<Component> checkLogin(UUID uuid, String username, String address) {
        if (!judicator.getPlatform().isOnlineMode()) return Optional.empty();
        return judicator.getPunishService().searchOnLogin(uuid, username, address)
                .map(punishment -> PunishUtils.getMessageList(judicator.getMessagesConfig(), punishment, "runners", "ban-kick"));
    }

    public void onLoginAllowed(String username, String address) {
        judicator.getAddressPolicy().recordLogin(username, address);
        judicator.getAddressService().updateUserIP(username, address);
        notifyAlts(username, address);
    }

    public void onJoin(JudicatorPlayer player) {
        judicator.getUuidManager().remember(player.getName(), player.getUniqueId());
        judicator.getHierarchyService().recordLevel(player);
    }

    private void notifyAlts(String username, String address) {
        if (!judicator.getConfig().node("alts", "notify-on-join").getBoolean(true)) return;
        if (judicator.getAddressPolicy().isExempt(address)) return;
        final Set<String> flagged = new TreeSet<>();
        for (String account : judicator.getAddressService().findAccountsByIp(address, username)) {
            if (!"clean".equals(judicator.getPunishService().statusOf(account))) flagged.add(account);
        }
        if (flagged.isEmpty()) return;
        final ConfigurationNode messages = judicator.getMessagesConfig();
        judicator.getStaffNotifier().sendToPermission(PunishPermissions.ALTS_NOTIFY.getPermission(),
                PunishUtils.format(messages, Map.of("nickname", username, "accounts", String.join(", ", flagged)), "alts", "notify"));
    }

    public void onDisconnect(String username, String address) {
        judicator.getPunishService().cleanupMutePunishmentCache(username, address);
    }

}
