package com.openmc.judicator.punish.commands;

import java.util.List;
import com.openmc.judicator.players.PlayerRef;
import com.openmc.judicator.Judicator;
import com.openmc.judicator.commons.ChatContext;
import com.openmc.judicator.platform.JudicatorPlayer;
import com.openmc.judicator.platform.JudicatorSender;
import com.openmc.judicator.punish.PlayerAddress;
import com.openmc.judicator.punish.PunishUtils;
import com.openmc.judicator.punish.PunishmentBuilder;
import com.openmc.judicator.punish.handlers.PunishFactory;
import com.openmc.judicator.punish.rules.PunishCooldown;
import com.openmc.judicator.punish.scopes.ScopeService;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

final class PunishmentCommandSupport {

    private PunishmentCommandSupport() {
    }

    /**
     * Rules that depend on who punishes: the sender's time limit and the cooldown on the target. Checked before the
     * target is resolved; the cooldown is checked again when the punishment is confirmed.
     *
     * @return false when the punishment must not go on (the reason was already sent to the source)
     */
    static boolean passesStaffRules(Judicator judicator, JudicatorSender source, PunishmentBuilder builder, String targetName) {
        if (!judicator.getTimeLimitService().allows(source, builder.getType(), builder.getStartedAt(), builder.getFinishAt())) {
            return false;
        }
        return judicator.getPunishCooldown().allows(source, PunishCooldown.categoryOf(builder.getType()), targetName);
    }

    /**
     * {@code -scope=<server or group>}: where the punishment applies.
     *
     * @param typed what followed {@code -scope=}; null when the flag was not given (everywhere)
     * @return false when the scope is refused: no {@code scopes.server-name} on this Spigot/Paper server, or neither a
     * group nor a server of the network (the reason was already sent to the source)
     */
    static boolean applyScope(Judicator judicator, JudicatorSender source, PunishmentBuilder builder, String typed) {
        if (typed == null) return true;
        final ScopeService scopes = judicator.getScopeService();
        if (!scopes.canScope()) {
            source.sendMessage(PunishUtils.getMessage(judicator.messagesFor(source), "error", "scope-not-configured"));
            return false;
        }
        final Optional<String> scope = scopes.parse(typed);
        if (scope.isEmpty()) {
            source.sendMessage(PunishUtils.format(judicator.messagesFor(source), Map.of("scope", typed), "error", "scope-unknown"));
            return false;
        }
        builder.scope(scope.get());
        return true;
    }

    /**
     * Tab completion of {@code -scope=}: the groups and servers that start with what was typed.
     */
    static List<String> suggestScopes(Judicator judicator, String typed) {
        final String prefix = typed.substring(PunishmentFlags.SCOPE.length()).toLowerCase(Locale.ROOT);
        return judicator.getScopeService().suggestions().stream()
                .filter(name -> name.startsWith(prefix))
                .map(name -> PunishmentFlags.SCOPE + name)
                .toList();
    }

    /**
     * Fills the target (and, for IP punishments, the address) of the builder.
     *
     * @return false when the punishment must not go on: the address is in {@code exempt-addresses}, or an IP
     * punishment would reach an account the source cannot punish (the reason was already sent to the source).
     */
    static boolean resolveTarget(Judicator judicator, JudicatorSender source, PunishmentBuilder builder, PlayerRef player, boolean byAddress) {
        final Optional<JudicatorPlayer> online = player.uuid() == null ? judicator.getPlatform().getPlayer(player.name())
                : judicator.getPlatform().getPlayer(player.uuid());
        builder.target(player);
        String address = null;
        if (online.isPresent()) {
            if (byAddress) address = online.get().getHostAddress();
        } else if (byAddress) {
            address = judicator.getAddressService().findLast(player).map(PlayerAddress::hostAddress).orElse(null);
            if (address == null) {
                source.sendMessage(PunishUtils.getMessage(judicator.messagesFor(source), "error", "player-ip-not-found"));
            }
        }

        if (address == null) return true;
        if (judicator.getAddressPolicy().isExempt(address)) {
            source.sendMessage(PunishUtils.getMessage(judicator.messagesFor(source), "error", "address-exempt"));
            return false;
        }
        if (!judicator.getImmuneCache().canPunishAddress(source, address, PunishCooldown.categoryOf(builder.getType()))) return false;
        builder.ipAddress(address);
        return true;
    }

    static void submit(Judicator judicator, JudicatorSender source, PunishmentBuilder builder) {
        final Optional<JudicatorPlayer> optPlayer = source.asPlayer();
        if (optPlayer.isEmpty()) {
            new PunishFactory(judicator, builder).factory();
            return;
        }

        final JudicatorPlayer player = optPlayer.get();
        final String punisher = player.getName();
        builder.punisher(player);
        player.sendMessage(PunishUtils.getConfirmationMessage(judicator.messagesFor(player)));

        final ChatContext<PunishmentBuilder> chatContext = ChatContext.buildPunishmentContext(punisher, builder, judicator);
        judicator.getPunishCache().putContext(punisher, chatContext);
    }

}
