package com.openmc.plugin.judicator.punish;

import org.spongepowered.configurate.ConfigurationNode;

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

}
