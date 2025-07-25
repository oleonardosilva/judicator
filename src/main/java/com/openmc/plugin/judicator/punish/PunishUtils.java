package com.openmc.plugin.judicator.punish;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.apache.commons.validator.routines.InetAddressValidator;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

public class PunishUtils {

    public static List<String> applyPlaceHolders(ConfigurationNode messagesNode, List<String> messages, Punishment punishment) {
        final String dateFormat = messagesNode.node("date-format").getString("dd/MM/yyyy-HH:mm:ss");
        final String permanent = messagesNode.node("permanent").getString("§cPermanente.");
        final String yes = messagesNode.node("yes").getString("§aYes.");
        final String no = messagesNode.node("no").getString("§cNo.");
        final String status = messagesNode.node(punishment.getStatus().name().toLowerCase()).getString("");

        final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern(dateFormat);
        return messages.stream().map(s -> s
                        .replace("&", "§")
                        .replace("{finishAt}", punishment.getFinishAt().isEmpty() ? permanent : timeFormatter.format(punishment.getFinishAt().get()))
                        .replace("{author}", punishment.getPunisher())
                        .replace("{status}", status)
                        .replace("{id}", punishment.getId().toString())
                        .replace("{evidence}", punishment.getEvidences().stream().findFirst().orElse("None"))
                        .replace("{nickname}", punishment.getNickname())
                        .replace("{startedAt}", timeFormatter.format(punishment.getStartedAt()))
                        .replace("{revoked}", punishment.isRevoked() ? yes : no)
                        .replace("{revokedReason}", punishment.getRevokedReason())
                        .replace("{reason}", punishment.getReason()))
                .collect(Collectors.toList());
    }

    public static boolean isValidIP(String ip) {
        final InetAddressValidator validator = InetAddressValidator.getInstance();
        return validator.isValid(ip);
    }

    public static TextComponent getMessage(ConfigurationNode node, Object... path) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(node.node(path).getString("§cFind Message Error"));
    }

    public static TextComponent getMessageList(ConfigurationNode node, Object... path) {
        final TextComponent.Builder text = Component.text();
        try {
            final List<String> list = node.node(path).getList(String.class);
            if (list != null) {
                text.content(String.join("", list));
            }
        } catch (SerializationException e) {
            throw new RuntimeException(e);
        }
        return text.build();
    }

    public static TextComponent getMessageList(ConfigurationNode node, Punishment punishment, Object... path) {
        final TextComponent.Builder text = Component.text();
        try {
            final List<String> list = node.node(path).getList(String.class);
            if (list != null) {
                text.content(String.join("", PunishUtils.applyPlaceHolders(node, list, punishment)));
            }
        } catch (SerializationException e) {
            throw new RuntimeException(e);
        }
        return text.build();
    }

}
