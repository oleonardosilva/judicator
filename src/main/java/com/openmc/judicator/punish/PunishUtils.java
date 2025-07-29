package com.openmc.judicator.punish;

import com.openmc.judicator.commons.DateTimeOffsetParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class PunishUtils {

    public static List<String> applyPlaceHolders(ConfigurationNode messagesNode, List<String> messages, Punishment punishment) {
        final String dateFormat = messagesNode.node("date-format").getString("dd/MM/yyyy-HH:mm:ss");
        final String permanent = messagesNode.node("permanent").getString("§cPermanente.").replace("&", "§");
        final String yes = messagesNode.node("yes").getString("§aYes.").replace("&", "§");
        final String no = messagesNode.node("no").getString("§cNo.").replace("&", "§");
        final String status = messagesNode.node("status", punishment.getStatus().name().toLowerCase()).getString(punishment.getStatus().name()).replace("&", "§");

        final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern(dateFormat);
        return messages.stream().map(s -> s
                        .replace("&", "§")
                        .replace("{finishAt}", punishment.getFinishAt().map(timeFormatter::format).orElse(permanent))
                        .replace("{author}", punishment.getPunisher())
                        .replace("{status}", status)
                        .replace("{id}", punishment.getId().toString())
                        .replace("{evidence}", punishment.getEvidences().isEmpty() ? "None" : String.join(", ", punishment.getEvidences()))
                        .replace("{nickname}", punishment.getNickname())
                        .replace("{type}", punishment.getType().name())
                        .replace("{startedAt}", timeFormatter.format(punishment.getStartedAt()))
                        .replace("{revoked}", punishment.isRevoked() ? yes : no)
                        .replace("{revokedReason}", punishment.getRevokedReason().orElse("None"))
                        .replace("{reason}", punishment.getReason().orElse("None"))
                        .replace("{addressIP}", punishment.getIpAddress().orElse("None"))
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

    public static TextComponent getPunishmentsMessage(ConfigurationNode node, String target, List<ConfiguredReason> reasons) {
        final String yes = node.node("yes").getString("&aSim").replace("&", "§");
        final String no = node.node("no").getString("&cNão").replace("&", "§");
        final String permanent = node.node("permanent").getString("&cPermanente").replace("&", "§");
        final TextComponent title = Component.text(getMessageList(node, "punish", "title").content());

        final TextComponent.Builder reasonsText = Component.text();

        for (ConfiguredReason reason : reasons) {
            final String message = node.node("punish", "line", "message").getString("").replace("&", "§").replace("{reason}", reason.getReason());
            String hoverMessage;
            try {
                hoverMessage = String.join("", Objects.requireNonNull(node.node("punish", "line", "hoverMessage").getList(String.class)))
                        .replace("&", "§")
                        .replace("{type}", reason.getType().name())
                        .replace("{toggle}", reason.isIp() ? yes : no)
                        .replace("{permission}", reason.getPermission());

            } catch (SerializationException e) {
                throw new RuntimeException(e);
            }

            if (reason.isPermanent()) {
                hoverMessage = hoverMessage.replace("{duration}", permanent);
            } else {
                final long timestamp = DateTimeOffsetParser.getMillisFromDuration(reason.getDuration());
                hoverMessage = hoverMessage.replace("{duration}", DateTimeOffsetParser.format(node, timestamp));
            }
            final String suggests = "/punish " + target + " " + reason.getReason();

            reasonsText.append(
                    Component.text()
                            .content(message)
                            .hoverEvent(HoverEvent.showText(Component.text(hoverMessage)))
                            .clickEvent(ClickEvent.suggestCommand(suggests))
                            .build());
        }

        final TextComponent footer = getMessageList(node, "punish", "footer");
        return Component.text().append(title).append(reasonsText).append(footer).build();
    }

    public static TextComponent getPunishmentHistoryMessage(ConfigurationNode node, String target, List<Punishment> punishments) {
        final TextComponent title = Component.text(getMessageList(node, "history", "title").content().replace("{nickname}", target));
        final TextComponent.Builder reasonsText = Component.text();

        for (Punishment punishment : punishments) {
            final String command = "/pview " + punishment.getId();

            reasonsText.append(
                    getMessageList(node, punishment, "history", "line", "message")
                            .hoverEvent(HoverEvent.showText(getMessageList(node, punishment, "history", "line", "hoverMessage")))
                            .clickEvent(ClickEvent.runCommand(command))
            );
        }

        final TextComponent footer = getMessageList(node, "history", "footer");
        return Component.text().append(title).append(reasonsText).append(footer).build();
    }

    public static Component getConfirmationMessage(ConfigurationNode node, Object... path) {
        final String readyPrompt = node.node("prompt", "ready").getString("confirmar").replace("&", "§");
        final String cancelPrompt = node.node("prompt", "cancel").getString("cancelar").replace("&", "§");

        final Component ready = Component.text(readyPrompt)
                .clickEvent(ClickEvent.runCommand(readyPrompt))
                .hoverEvent(HoverEvent.showText(Component.text(readyPrompt)));

        final Component cancel = Component.text(cancelPrompt)
                .clickEvent(ClickEvent.runCommand(cancelPrompt))
                .hoverEvent(HoverEvent.showText(Component.text(cancelPrompt)));

        final TextComponent message;
        if (path == null || path.length == 0) {
            message = getMessage(node, "prompt", "write-evidences");
        } else {
            message = getMessage(node, path);
        }

        return message
                .replaceText(builder -> builder
                        .matchLiteral("{ready-prompt}")
                        .replacement(ready))
                .replaceText(builder -> builder
                        .matchLiteral("{cancel-prompt}")
                        .replacement(cancel));

    }

}
