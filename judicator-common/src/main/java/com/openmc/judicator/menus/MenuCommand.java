package com.openmc.judicator.menus;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.commons.command.CommandArgs;
import com.openmc.judicator.commons.command.CoreCommand;
import com.openmc.judicator.platform.JudicatorPlayer;
import com.openmc.judicator.platform.JudicatorSender;
import com.openmc.judicator.punish.PunishUtils;
import com.openmc.judicator.punish.types.PunishPermissions;
import com.openmc.judicator.menus.screens.MainScreen;
import com.openmc.judicator.menus.screens.PlayerScreen;

import java.util.List;
import java.util.Optional;

/**
 * {@code /jmenu [player]}: the main menu, or a player's. Only registered where the platform draws menus.
 */
public class MenuCommand extends CoreCommand {

    public MenuCommand(Judicator judicator) {
        super(judicator, "jmenu", List.of("judicatormenu"), PunishPermissions.MENU.getPermission(), PunishPermissions.ADMIN.getPermission());
    }

    @Override
    public void execute(JudicatorSender source, CommandArgs args) {
        final Optional<JudicatorPlayer> viewer = source.asPlayer();
        if (viewer.isEmpty()) {
            source.sendMessage(PunishUtils.getMessage(messages(source), "menu", "only-players"));
            return;
        }
        final Optional<String> target = args.word();
        judicator.getMenuService().open(viewer.get(), target.<Screen>map(PlayerScreen::new).orElseGet(MainScreen::new));
    }

}
