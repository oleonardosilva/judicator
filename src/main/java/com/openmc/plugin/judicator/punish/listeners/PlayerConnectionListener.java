package com.openmc.plugin.judicator.punish.listeners;

import com.openmc.plugin.judicator.Judicator;
import com.openmc.plugin.judicator.punish.PunishCache;
import com.openmc.plugin.judicator.punish.PunishUtils;
import com.openmc.plugin.judicator.punish.Punishment;
import com.openmc.plugin.judicator.punish.db.PunishmentRepository;
import com.openmc.plugin.judicator.punish.types.PunishType;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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

        final PunishmentRepository repository = judicator.getPunishmentRepository();
        final ConfigurationNode messagesNode = judicator.getMessagesConfig();
        final PunishCache cache = judicator.getPunishCache();

        final String username = event.getUsername().toLowerCase();
        final String ipAddress = event.getConnection().getRemoteAddress().getAddress().getHostAddress();

        final List<Punishment> punishments = repository.findAllActiveByUsernameOrIpAndTypes(username, ipAddress, PunishType.values());
        if (punishments.isEmpty()) return;

        final Optional<Punishment> banPunishmentQuery = punishments.stream().filter(Punishment::isBanType).findFirst();
        if (banPunishmentQuery.isPresent()) {
            final Punishment punishment = banPunishmentQuery.get();
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
            return;
        }

        // Permanentes vêm primeiro (empty)
        // Ambos temporários: ordenar por data decrescente
        final Optional<Punishment> mutePunishmentQuery = punishments.stream()
                .filter(Punishment::isMuteType).min((o1, o2) -> {
                    final Optional<LocalDateTime> f1 = o1.getFinishAt();
                    final Optional<LocalDateTime> f2 = o2.getFinishAt();
                    if (f1.isEmpty() && f2.isPresent()) return -1;
                    if (f1.isPresent() && f2.isEmpty()) return 1;
                    return f1.map(localDateTime -> f2.get().compareTo(localDateTime)).orElse(0);
                });

        if (mutePunishmentQuery.isPresent()) {
            final Punishment punishment = mutePunishmentQuery.get();
            final Optional<String> address = punishment.getIpAddress();
            final String key = address.orElseGet(punishment::getNickname);
            cache.putMutePunishment(key, punishment);
        }
    }

    @Subscribe
    public void onLeave(DisconnectEvent event) {
        final PunishCache cache = judicator.getPunishCache();
        final ProxyServer server = judicator.getServer();

        final String username = event.getPlayer().getUsername();
        final String address = event.getPlayer().getRemoteAddress().getAddress().getHostAddress();

        cache.getPunishment(username)
                .or(() -> cache.getPunishment(address))
                .ifPresent(punishment -> {
                    if (punishment.getIpAddress().isEmpty()) {
                        cache.removeMutePunishment(username);
                        return;
                    }

                    if (server.getAllPlayers().stream()
                            .noneMatch(p -> !p.getUsername().equals(username) &&
                                            p.getRemoteAddress().getAddress().getHostAddress().equals(address))
                    ) {
                        cache.removeMutePunishment(punishment.getIpAddress().get());
                    }
                });
    }

    // verify with player uuid
    @Subscribe
    public void onLogin(LoginEvent event) {
        final ProxyServer server = judicator.getServer();
        if (!server.getConfiguration().isOnlineMode()) {
            return;
        }

        final PunishmentRepository repository = judicator.getPunishmentRepository();
        final ConfigurationNode messagesNode = judicator.getMessagesConfig();

        final Player player = event.getPlayer();
        final UUID uuid = player.getUniqueId();
        final String address = player.getRemoteAddress().getAddress().getHostAddress();

        final List<Punishment> punishments = repository.findAllActiveByUUIDAndTypes(uuid, PunishType.values());
        if (punishments.isEmpty()) return;

        final Optional<Punishment> banPunishmentQuery = punishments.stream().filter(Punishment::isBanType).findFirst();
        if (banPunishmentQuery.isPresent()) {
            final Punishment punishment = banPunishmentQuery.get();
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
            return;
        }

        final PunishCache cache = judicator.getPunishCache();
        final String username = player.getUsername();

        if (cache.getPunishment(username).or(() -> cache.getPunishment(address)).isPresent()) {
            return;
        }

        final Optional<Punishment> mutePunishmentQuery = punishments.stream().filter(Punishment::isMuteType).min((o1, o2) -> {
            final Optional<LocalDateTime> f1 = o1.getFinishAt();
            final Optional<LocalDateTime> f2 = o2.getFinishAt();
            if (f1.isEmpty() && f2.isPresent()) return -1;
            if (f1.isPresent() && f2.isEmpty()) return 1;
            return f1.map(localDateTime -> f2.get().compareTo(localDateTime)).orElse(0);
        });

        if (mutePunishmentQuery.isPresent()) {
            final Punishment punishment = mutePunishmentQuery.get();
            cache.putMutePunishment(username, punishment);
        }
    }


}
