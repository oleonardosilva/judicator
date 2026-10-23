package com.openmc.judicator.warns.handlers;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.api.event.WarnRevokedEvent;
import com.openmc.judicator.integration.ApiViews;
import com.openmc.judicator.warns.Warn;
import com.openmc.judicator.warns.WarnUtils;

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
     * @param tellPlayer whether the player, if online here, is told the warn was removed
     */
    public void remove(Warn warn, String author, boolean tellPlayer) {
        judicator.getWarnService().deleteById(warn.getId());
        if (tellPlayer) {
            judicator.getPlatform().getPlayer(warn.getNickname())
                    .ifPresent(player -> player.sendMessage(WarnUtils.getMessage(judicator.messagesFor(player), "success", "got-unwarn")));
        }
        judicator.getEventBus().post(() -> new WarnRevokedEvent(ApiViews.warn(warn), author));
    }

}
