package com.openmc.judicator.warns;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

public class WarnUtils {

    public static List<String> applyPlaceHolders(ConfigurationNode messagesNode, List<String> messages, Warn warn) {
        final String dateFormat = messagesNode.node("date-format").getString("dd/MM/yyyy-HH:mm:ss");
        final String permanent = messagesNode.node("permanent").getString("§cPermanente.").replace("&", "§");
        final String status = messagesNode.node("status", warn.getStatus().name().toLowerCase()).getString(warn.getStatus().name()).replace("&", "§");

        final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern(dateFormat);
        return messages.stream().map(s -> s
                        .replace("&", "§")
                        .replace("{finishAt}", warn.getFinishAt().map(timeFormatter::format).orElse(permanent))
                        .replace("{author}", warn.getPunisher())
                        .replace("{status}", status)
                        .replace("{id}", warn.getId().toString())
                        .replace("{nickname}", warn.getNickname())
                        .replace("{startedAt}", timeFormatter.format(warn.getStartedAt()))
                        .replace("{reason}", warn.getReason())
                )
                .collect(Collectors.toList());
    }

    public static TextComponent getMessage(ConfigurationNode node, Object... path) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(node.node(path).getString("§cFind Message Error"));
    }

    public static TextComponent getMessageList(ConfigurationNode node, Object... path) {
        final TextComponent.Builder text = Component.text();
        try {
            final List<String> list = node.node(path).getList(String.class);
            if (list != null) {
                text.content(String.join("", list).replace("&", "§"));
            }
        } catch (SerializationException e) {
            throw new RuntimeException(e);
        }
        return text.build();
    }

    public static TextComponent getWarnHistoryMessage(ConfigurationNode node, String target, List<Warn> warns) {
        final TextComponent title = Component.text(getMessageList(node, "warn-history", "title").content().replace("{nickname}", target));
        final TextComponent.Builder reasonsText = Component.text();

        for (Warn warn : warns) {
            final String command = "/wview " + warn.getId();

            reasonsText.append(
                    getMessageList(node, warn, "warn-history", "line", "message")
                            .hoverEvent(HoverEvent.showText(getMessageList(node, warn, "warn-history", "line", "hoverMessage")))
                            .clickEvent(ClickEvent.runCommand(command))
            );
        }

        final TextComponent footer = getMessageList(node, "punish-history", "footer");
        return Component.text().append(title).append(reasonsText).append(footer).build();
    }

    public static TextComponent getMessageList(ConfigurationNode node, Warn warn, Object... path) {
        final TextComponent.Builder text = Component.text();
        try {
            final List<String> list = node.node(path).getList(String.class);
            if (list != null) {
                text.content(String.join("", WarnUtils.applyPlaceHolders(node, list, warn)));
            }
        } catch (SerializationException e) {
            throw new RuntimeException(e);
        }
        return text.build();
    }

}
