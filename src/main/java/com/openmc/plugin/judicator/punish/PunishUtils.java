package com.openmc.plugin.judicator.punish;

import com.openmc.plugin.judicator.commons.DateTimeOffsetParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
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
                        .replace("{evidence}", punishment.getEvidences().isEmpty() ? "None" : String.join(", ", punishment.getEvidences()))
                        .replace("{nickname}", punishment.getNickname())
                        .replace("{startedAt}", timeFormatter.format(punishment.getStartedAt()))
                        .replace("{revoked}", punishment.isRevoked() ? yes : no)
                        .replace("{revokedReason}", punishment.isRevoked() ? punishment.getRevokedReason() : "None")
                        .replace("{reason}", punishment.getReason())
                        .replace("{addressIP}", punishment.getIpAddress().orElse("None"))
                )
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

    public static TextComponent getPunishmentsMessage(ConfigurationNode node, String target, List<ConfiguredReason> reasons) {
        final String yes = node.node("yes").getString("&aSim");
        final String no = node.node("no").getString("&cNão");
        final String permanent = node.node("permanent").getString("&cPermanente");
        final TextComponent title = Component.text(getMessageList(node, "punish", "title").content());

        final TextComponent.Builder reasonsText = Component.text();

        for (ConfiguredReason reason : reasons) {
            final String message = node.node("punish", "line", "message").getString("").replace("&", "§").replace("{reason}", reason.getReason());
            String hoverMessage = node.node("punish", "line", "hoverMessage").getString("")
                    .replace("&", "§")
                    .replace("{type}", reason.getType().name())
                    .replace("{toggle}", reason.isIp() ? yes : no)
                    .replace("{permission}", reason.getPermission());

            if (reason.isPermanent()) {
                hoverMessage = hoverMessage.replace("{duration}", permanent);
            } else {
                final long timestamp = DateTimeOffsetParser.getMillisFromDuration(reason.getDuration());
                hoverMessage = hoverMessage.replace("{duration}", DateTimeOffsetParser.format(node, timestamp));
            }
            final String suggests = "/punir " + target + " " + reason.getReason();

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

    public static Component getConfirmationMessage(ConfigurationNode node, String... path) {
        final String readyPrompt = node.node("ready-prompt").getString("confirmar");
        final String cancelPrompt = node.node("cancel-prompt").getString("cancelar");

        final Component ready = Component.text(readyPrompt)
                .clickEvent(ClickEvent.runCommand(readyPrompt))
                .hoverEvent(HoverEvent.showText(Component.text(readyPrompt)));

        final Component cancel = Component.text(cancelPrompt)
                .clickEvent(ClickEvent.runCommand(cancelPrompt))
                .hoverEvent(HoverEvent.showText(Component.text(cancelPrompt)));


        final TextComponent message = getMessage(node, path == null ? new Object[]{"write-evidences"} : path);

        return message
                .replaceText(builder -> builder
                        .matchLiteral("{ready-prompt}")
                        .replacement(ready))
                .replaceText(builder -> builder
                        .matchLiteral("{cancel-prompt}")
                        .replacement(cancel));

    }

}
