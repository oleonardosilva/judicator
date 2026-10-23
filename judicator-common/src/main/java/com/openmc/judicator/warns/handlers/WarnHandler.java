package com.openmc.judicator.warns.handlers;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.warns.*;
import net.kyori.adventure.text.TextComponent;

import java.util.Optional;

public class WarnHandler {

    private final Judicator judicator;
    private final WarnBuilder warnBuilder;

    public WarnHandler(Judicator judicator, WarnBuilder warnBuilder) {
        this.judicator = judicator;
        this.warnBuilder = warnBuilder;
    }

    public Optional<Warn> handle() {
        try {
            final Warn warn = judicator.getWarnService().save(warnBuilder.build());
            this.announce(warn);
            this.calc(warn);

            judicator.getPlatform().getPlayer(warn.getPunisher()).ifPresent(
                    player -> player
                            .sendMessage(WarnUtils.getMessage(judicator.messagesFor(player), "success", "warn-applied"))
            );

            judicator.getPlatform().getPlayer(warn.getNickname()).ifPresent(player -> {
                final TextComponent warnAlert = WarnUtils.getMessageList(judicator.messagesFor(player), warn, "runners", "warn-alert");
                player.sendMessage(warnAlert);
            });

            return Optional.of(warn);
        } catch (Exception e) {
            judicator.getLogger().error("Failed to handle ban punishment", e);
            judicator.getPlatform().getPlayer(warnBuilder.getPunisher()).ifPresent(
                    player -> player
                            .sendMessage(WarnUtils.getMessage(judicator.messagesFor(player), "error", "unknown"))
            );
            return Optional.empty();
        }
    }

    private void calc(Warn warn) {
        final WarnService warnService = judicator.getWarnService();

        final Long count = warnService.countActiveWarns(warn.getNickname());
        judicator.getActionCache().getAction(count)
                .map(ConfiguredAction::getCommands)
                .ifPresent(actions -> actions.forEach(action ->
                        judicator.getPlatform().dispatchConsoleCommand(action.replace("{player}", warn.getSafeNickname()))));
    }

    private void announce(Warn warn) {
        judicator.getDiscordWebhookService().sendWarn(warn);

        judicator.getStaffNotifier().notifyWarn(warn);
        if (!warn.isSilent() && judicator.getConfig().node("announce").getBoolean(true)) {
            judicator.getPlatform().getPlayer(warn.getNickname()).ifPresent(player -> judicator.getPlatform().announceToServerOf(player,
                    recipient -> WarnUtils.getMessageList(judicator.messagesFor(recipient), warn, "announcements", "warn")));
        }
    }

}
