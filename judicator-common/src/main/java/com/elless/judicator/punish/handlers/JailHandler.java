package com.elless.judicator.punish.handlers;

import com.elless.judicator.Judicator;
import com.elless.judicator.players.PlayerRef;
import com.elless.judicator.punish.Punishment;
import com.elless.judicator.punish.PunishmentBuilder;
import com.elless.judicator.punish.types.PunishType;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

public class JailHandler implements PunishHandler {

    /** one new prisoner at a time on this instance: the checks of a full jail and of a player already jailed hold */
    private static final Object ADMISSION = new Object();

    private final Judicator judicator;
    private final PunishmentBuilder punishmentBuilder;

    public JailHandler(Judicator judicator, PunishmentBuilder punishmentBuilder) {
        this.judicator = judicator;
        this.punishmentBuilder = punishmentBuilder;
    }

    /**
     * @throws Refused when the player was jailed meanwhile, or the jail filled up
     */
    public Punishment handle() {
        final Punishment built = punishmentBuilder.build();
        final Punishment punishment;
        synchronized (ADMISSION) {
            final PlayerRef player = judicator.getPlayerDirectory().resolve(built.getNickname());
            final Optional<String> refusal = judicator.getJailService().refusalOnArrival(player, built.getJail().orElse(null));
            if (refusal.isPresent()) throw new Refused(refusal.get());
            // A fugitive jailed again: the hunt is over, the new jail takes its place.
            final LocalDateTime now = LocalDateTime.now();
            judicator.getPunishService().findAllActiveOf(player, PunishType.JAIL, PunishType.TEMPJAIL)
                    .stream().filter(Punishment::isEscaped)
                    .forEach(fugitive -> judicator.getPunishService().changeEnd(fugitive, now, null));
            punishment = store(built);
        }
        judicator.getDiscordWebhookService().sendPunishment(punishment);
        judicator.getPunishmentEffects().punished(punishment);
        return punishment;
    }

    private Punishment store(Punishment built) {
        // As an online-only mute: the time of a prisoner who is not online starts running at their next login.
        if (built.isOnlineOnly() && built.getFinishAt().isPresent()
                && judicator.getPlatform().getPlayer(built.getNickname()).isEmpty()) {
            built.setPausedRemaining(Duration.between(built.getStartedAt(), built.getFinishAt().get()).toMillis());
            built.setFinishAt(null);
        }
        return judicator.getPunishService().save(built);
    }

    /**
     * The jail cannot be applied any more; {@code key} is the {@code error} message that says why.
     */
    public static final class Refused extends RuntimeException {

        private final String key;

        Refused(String key) {
            super(key, null, false, false);
            this.key = key;
        }

        public String key() {
            return key;
        }
    }
}
