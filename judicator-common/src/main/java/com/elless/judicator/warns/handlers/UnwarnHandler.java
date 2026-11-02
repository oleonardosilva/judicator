package com.elless.judicator.warns.handlers;

import com.elless.judicator.Judicator;
import com.elless.judicator.api.event.WarnRevokedEvent;
import com.elless.judicator.integration.ApiViews;
import com.elless.judicator.warns.Warn;
import com.elless.judicator.warns.WarnUtils;

/**
 * Takes a warn back ({@code /unwarn}, {@code /staffrollback} and the API): warns cannot be revoked, so it is deleted,
 * and an API event carries it as it was.
 */
public class UnwarnHandler {

    private final Judicator judicator;

    public UnwarnHandler(Judicator judicator) {
        this.judicator = judicator;
    }

    /**
     * {@code /unwarn} and the API: the player, if online here, is told, and the removal is posted to Discord
     * ({@code events.unwarn}), the only record left of a deleted warn.
     */
    public void remove(Warn warn, String author) {
        delete(warn, author);
        judicator.getPlatform().getPlayer(warn.getNickname())
                .ifPresent(player -> player.sendMessage(WarnUtils.getMessage(judicator.messagesFor(player), "success", "got-unwarn")));
        judicator.getDiscordWebhookService().sendUnwarn(warn, author);
    }

    /**
     * {@code /staffrollback}: neither the player nor Discord hears of each one (the rollback is posted once).
     */
    public void removeInBulk(Warn warn, String author) {
        delete(warn, author);
    }

    private void delete(Warn warn, String author) {
        judicator.getWarnService().deleteById(warn.getId());
        judicator.getEventBus().post(() -> new WarnRevokedEvent(ApiViews.warn(warn), author));
    }

}
