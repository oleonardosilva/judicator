package com.openmc.judicator.punish.commands;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.commons.command.CommandArgs;
import com.openmc.judicator.commons.command.CoreCommand;
import com.openmc.judicator.platform.JudicatorSender;
import com.openmc.judicator.punish.PunishUtils;
import com.openmc.judicator.punish.types.PunishPermissions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.spongepowered.configurate.ConfigurationNode;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class AltsCommand extends CoreCommand {

    public AltsCommand(Judicator judicator) {
        super(judicator, "alts", List.of(), PunishPermissions.ALTS.getPermission(), PunishPermissions.ADMIN.getPermission());
    }

    @Override
    public void execute(JudicatorSender source, CommandArgs args) {
        final Optional<String> optTarget = args.word();
        if (optTarget.isEmpty()) {
            sendUsage(source, "alts");
            return;
        }
        final String targetName = optTarget.get();
        final ConfigurationNode messages = messages();

        final Set<String> alts = judicator.getAddressService().findAlts(targetName, judicator.getAddressPolicy()::isExempt);
        if (alts.isEmpty()) {
            source.sendMessage(PunishUtils.format(messages, Map.of("nickname", targetName), "alts", "none"));
            return;
        }

        final TextComponent.Builder text = Component.text()
                .append(PunishUtils.format(messages, Map.of("nickname", targetName), "alts", "title"));
        for (String alt : alts) {
            final String status = messages.node("alts", "status", judicator.getPunishService().statusOf(alt))
                    .getString("").replace("&", "§");
            text.append(Component.text("\n"))
                    .append(PunishUtils.format(messages, Map.of("account", alt, "status", status), "alts", "line")
                            .hoverEvent(HoverEvent.showText(PunishUtils.format(messages, Map.of("account", alt), "alts", "hover")))
                            .clickEvent(ClickEvent.runCommand("/phistory " + alt)));
        }
        source.sendMessage(text.build());
    }

}
