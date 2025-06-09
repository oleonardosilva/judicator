package com.openmc.plugin.judicator.punish.listeners;

import com.openmc.plugin.judicator.Judicator;
import com.openmc.plugin.judicator.punish.PunishService;
import com.openmc.plugin.judicator.punish.PunishUtils;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

import java.util.List;

public class PlayerConnectionListener {

    private final Judicator judicator;

    public PlayerConnectionListener(Judicator judicator) {
        this.judicator = judicator;
    }

    public void register() {
        judicator.getServer().getEventManager().register(judicator, this);
    }

    @Subscribe
    public void onPreLogin(PreLoginEvent event) {
        if (!event.getResult().isAllowed()) return;

        final ConfigurationNode messagesNode = judicator.getMessagesConfig();
        final PunishService service = judicator.getPunishService();

        service.searchOnPreLogin(event,
                punishment -> {
                    final TextComponent.Builder text = Component.text();
                    try {
                        final List<String> list = messagesNode.node("runners", "ban-kick").getList(String.class);
                        if (list != null)
                            PunishUtils.applyPlaceHolders(messagesNode, list, punishment).forEach(s -> text.append(Component.text(s)));
                    } catch (SerializationException e) {
                        throw new RuntimeException(e);
                    }
                    final TextComponent kickMessage = text.build();
                    event.setResult(PreLoginEvent.PreLoginComponentResult.denied(kickMessage));
                }, null);
    }

    @Subscribe
    public void onLeave(DisconnectEvent event) {
        final PunishService service = judicator.getPunishService();
        service.cleanupMutePunishment(event);
    }

    // online mode only
    // check the uuid
    @Subscribe
    public void onLogin(LoginEvent event) {
        final ProxyServer server = judicator.getServer();
        if (!server.getConfiguration().isOnlineMode() || !event.getResult().isAllowed()) return;

        final ConfigurationNode messagesNode = judicator.getMessagesConfig();
        final PunishService service = judicator.getPunishService();

        service.searchOnLogin(event, punishment -> {
            final TextComponent.Builder text = Component.text();
            try {
                final List<String> list = messagesNode.node("runners", "ban-kick").getList(String.class);
                if (list != null)
                    PunishUtils.applyPlaceHolders(messagesNode, list, punishment).forEach(s -> text.append(Component.text(s)));
            } catch (SerializationException e) {
                throw new RuntimeException(e);
            }
            final TextComponent kickMessage = text.build();
            event.setResult(ResultedEvent.ComponentResult.denied(kickMessage));
        }, null);

    }


}
