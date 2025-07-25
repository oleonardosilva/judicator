package com.openmc.plugin.judicator.punish.listeners;

import com.openmc.plugin.judicator.Judicator;
import com.openmc.plugin.judicator.punish.AccessAddressService;
import com.openmc.plugin.judicator.punish.PunishService;
import com.openmc.plugin.judicator.punish.PunishUtils;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.TextComponent;
import org.spongepowered.configurate.ConfigurationNode;

public class OnPlayerConnectionListener {

    private final Judicator judicator;
    private final ConfigurationNode messagesNode;
    private final PunishService punishService;
    private final AccessAddressService addressService;

    public OnPlayerConnectionListener(Judicator judicator) {
        this.judicator = judicator;
        this.messagesNode = judicator.getMessagesConfig();
        this.punishService = judicator.getPunishService();
        this.addressService = judicator.getAddressService();
    }

    public void register() {
        judicator.getServer().getEventManager().register(judicator, this);
    }

    @Subscribe(async = false, priority = -10)
    public void onPreLogin(PreLoginEvent event) {
        if (!event.getResult().isAllowed()) return;

        punishService.searchOnPreLogin(event,
                punishment -> {
                    final TextComponent kickMessage = PunishUtils.getMessageList(messagesNode, punishment, "runners", "ban-kick");
                    event.setResult(PreLoginEvent.PreLoginComponentResult.denied(kickMessage));
                }, null);
    }

    @Subscribe
    public void onLeave(DisconnectEvent event) {
        final PunishService service = judicator.getPunishService();
        service.cleanupMutePunishmentCache(event);
    }

    // online mode only
    // check the uuid
    @Subscribe(async = false, priority = -10)
    public void onLogin(LoginEvent event) {
        if (!event.getResult().isAllowed()) return;

        final ProxyServer server = judicator.getServer();

        if (server.getConfiguration().isOnlineMode())
            punishService.searchOnLogin(event, punishment -> {
                final TextComponent kickMessage = PunishUtils.getMessageList(messagesNode, punishment, "runners", "ban-kick");
                event.setResult(ResultedEvent.ComponentResult.denied(kickMessage));
            }, null);

        if (event.getResult().isAllowed()) {
            addressService.updateUserIP(event.getPlayer().getUsername(), event.getPlayer().getRemoteAddress().getAddress().getHostAddress());
        }
    }

}
