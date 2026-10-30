package com.elless.judicator.menus.screens;

import com.elless.judicator.menus.Menu;
import com.elless.judicator.menus.MenuContext;
import com.elless.judicator.menus.MenuIcon;
import com.elless.judicator.menus.Screen;
import com.elless.judicator.punish.Punishment;
import com.elless.judicator.punish.types.PunishStatus;
import com.elless.judicator.punish.types.PunishType;

import java.util.Map;
import java.util.Optional;

/**
 * One punishment ({@code /pview}): its data, the player, and revoke, add time and change the reason while it is in
 * force.
 */
public record PunishmentScreen(long id) implements Screen {

    @Override
    public Menu build(MenuContext context) {
        if (!context.canUse("punishview")) return context.denied();
        final Optional<Punishment> found = context.judicator().getPunishService().findById(id);
        if (found.isEmpty()) return context.gone();
        final Punishment punishment = found.get();
        final Map<String, String> placeholders = Map.of("id", String.valueOf(id), "nickname", punishment.getNickname());
        final boolean inForce = punishment.getStatus() == PunishStatus.ACTIVE;

        final Menu.Builder menu = Menu.builder(context.text(placeholders, "punishment", "title"), 3);
        menu.set(4, context.item(Icons.of(punishment.getType()), punishment, "punishment", "info"));
        menu.set(10, context.item(MenuIcon.PLAYER_HEAD, placeholders, "punishment", "player")
                .headOf(punishment.getPlayerUUID().orElse(null), punishment.getNickname())
                .onClick(context.open(new PlayerScreen(punishment.getNickname()))));
        if (!punishment.isRevoked() && punishment.getType() != PunishType.KICK && context.canUse("revoke")) {
            menu.set(12, context.item(MenuIcon.PARDON, placeholders, "punishment", "revoke")
                    .onClick(context.open(ConfirmScreen.withReason(MenuIcon.PARDON, "revoke", placeholders, "revoke", String.valueOf(id)))));
        }
        if (inForce && !punishment.isPermanent() && context.canUse("extend")) {
            menu.set(14, context.item(MenuIcon.EXTEND, placeholders, "punishment", "extend")
                    .onClick(() -> context.menus().askCommand(context.viewer(), context.text(Map.of(), "punishment", "extend-what"),
                            "extend", time -> id + " " + time)));
        }
        if (inForce && context.canUse("changereason")) {
            menu.set(16, context.item(MenuIcon.EDIT, placeholders, "punishment", "change-reason")
                    .onClick(() -> context.menus().askCommand(context.viewer(), context.text(Map.of(), "punishment", "change-reason-what"),
                            "changereason", reason -> id + " " + reason)));
        }
        menu.set(18, context.item(MenuIcon.BACK, Map.of(), "navigation", "back").onClick(context.back()));
        menu.set(26, context.item(MenuIcon.CLOSE, Map.of(), "navigation", "close")
                .onClick(() -> context.menus().close(context.viewer())));
        return menu.build();
    }
}
