package com.openmc.judicator.punish.listeners;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.punish.PunishUtils;
import net.kyori.adventure.text.Component;

import java.util.Optional;
import java.util.UUID;

public class ConnectionHandler {

    private final Judicator judicator;

    public ConnectionHandler(Judicator judicator) {
        this.judicator = judicator;
    }

    public Optional<Component> checkPreLogin(String username, String address) {
        return judicator.getPunishService().searchOnPreLogin(username, address)
                .map(punishment -> PunishUtils.getMessageList(judicator.getMessagesConfig(), punishment, "runners", "ban-kick"));
    }

    public Optional<Component> checkLogin(UUID uuid, String username, String address) {
        if (!judicator.getPlatform().isOnlineMode()) return Optional.empty();
        return judicator.getPunishService().searchOnLogin(uuid, username, address)
                .map(punishment -> PunishUtils.getMessageList(judicator.getMessagesConfig(), punishment, "runners", "ban-kick"));
    }

    public void onLoginAllowed(String username, String address) {
        judicator.getAddressService().updateUserIP(username, address);
    }

    public void onDisconnect(String username, String address) {
        judicator.getPunishService().cleanupMutePunishmentCache(username, address);
    }

}
