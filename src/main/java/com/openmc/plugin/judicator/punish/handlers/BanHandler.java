package com.openmc.plugin.judicator.punish.handlers;

import com.openmc.plugin.judicator.Judicator;
import com.openmc.plugin.judicator.punish.Punishment;
import com.openmc.plugin.judicator.punish.PunishmentBuilder;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class BanHandler {

    private final Judicator judicator;
    private final PunishmentBuilder punishmentBuilder;
    private final ConfigurationNode messagesNode;
    private final boolean announce;

    public BanHandler(Judicator judicator, PunishmentBuilder punishmentBuilder) {
        this.judicator = judicator;
        this.punishmentBuilder = punishmentBuilder;
        this.messagesNode = judicator.getMessagesConfig();
        this.announce = judicator.getConfig().node("announce").getBoolean(true);
    }

    public void handle() {
        final Punishment punishment = judicator.getPunishmentRepository().save(punishmentBuilder.build());
        this.kick(punishment);
        this.announce(punishment);
    }

    private void kick(Punishment punishment) {
        final ProxyServer server = judicator.getServer();
        server.getScheduler().buildTask(judicator,
                () -> {
                    final TextComponent.Builder text = Component.text();
                    try {
                        final List<String> list = messagesNode.node("runners", "ban-kick").getList(String.class);
                        if (list != null) {
                            applyPlaceHolders(list, punishment).forEach(
                                    s -> text.append(Component.text(s))
                            );
                        }
                    } catch (SerializationException e) {
                        throw new RuntimeException(e);
                    }
                    final TextComponent kickMessage = text.build();

                    final Optional<String> optIpAddress = punishment.getIpAddress();
                    if (optIpAddress.isPresent()) {
                        server.getAllPlayers().stream()
                                .filter(player -> player.getRemoteAddress().getAddress().getHostAddress().equals(optIpAddress.get()))
                                .forEach(player -> player.disconnect(kickMessage));
                    } else {
                        server.getPlayer(punishment.getNickname())
                                .ifPresent(player -> player.disconnect(kickMessage));
                    }
                }
        ).schedule();
    }

    private void announce(Punishment punishment) {
        if (announce) {
            final ProxyServer server = judicator.getServer();
            final TextComponent.Builder text = Component.text();
            try {
                final List<String> list = messagesNode.node("announcements", "ban").getList(String.class);
                if (list != null) {
                    applyPlaceHolders(list, punishment).forEach(
                            s -> text.append(Component.text(s))
                    );
                }
            } catch (SerializationException e) {
                throw new RuntimeException(e);
            }
            final TextComponent announcement = text.build();
            server.sendMessage(announcement);
        }
    }

    private List<String> applyPlaceHolders(List<String> messages, Punishment punishment) {
        String dateFormat = messagesNode.node("date-format").getString("dd/MM/yyyy-HH:mm:ss");
        String permanent = messagesNode.node("permanent").getString("§cPermanente.");
        final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern(dateFormat);
        return messages.stream().map(s -> s
                        .replace("&", "§")
                        .replace("{finishAt}", punishment.getFinishAt().isEmpty() ? permanent : timeFormatter.format(punishment.getFinishAt().get()))
                        .replace("{author}", punishment.getPunisher())
                        .replace("{id}", punishment.getId().toString())
                        .replace("{evidence}", punishment.getEvidences().stream().findFirst().orElse("None"))
                        .replace("{nickname}", punishment.getNickname())
                        .replace("{startedAt}", timeFormatter.format(punishment.getStartedAt()))
                        .replace("{reason}", punishment.getReason()))
                .collect(Collectors.toList());
    }

}
