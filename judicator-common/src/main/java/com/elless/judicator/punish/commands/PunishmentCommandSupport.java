package com.elless.judicator.punish.commands;

import com.elless.judicator.players.PlayerRef;
import com.elless.judicator.Judicator;
import com.elless.judicator.commons.ChatContext;
import com.elless.judicator.menus.MenuRenderer;
import com.elless.judicator.platform.JudicatorPlayer;
import com.elless.judicator.platform.JudicatorSender;
import com.elless.judicator.punish.PlayerAddress;
import com.elless.judicator.punish.PunishUtils;
import com.elless.judicator.punish.PunishmentBuilder;
import com.elless.judicator.punish.handlers.PunishFactory;
import com.elless.judicator.punish.rules.PunishCooldown;
import com.elless.judicator.punish.scopes.ScopeService;
import org.spongepowered.configurate.ConfigurationNode;

import java.util.ArrayList;
import java.util.List;
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

    /**
     * Applies the punishment at once for the console and for an action already confirmed
     * ({@link com.elless.judicator.commons.command.CommandArgs#confirmed}); a staff member otherwise gets the evidence
     * prompt: a dialog when the platform has one for them (Paper, 1.21.6+ clients), the chat otherwise.
     */
    static void submit(Judicator judicator, JudicatorSender source, PunishmentBuilder builder, boolean confirmed) {
        final Optional<JudicatorPlayer> optPlayer = source.asPlayer();
        if (optPlayer.isEmpty()) {
            new PunishFactory(judicator, builder).factory();
            return;
        }

        final JudicatorPlayer player = optPlayer.get();
        builder.punisher(player);
        if (confirmed) {
            new PunishFactory(judicator, builder).factory();
            return;
        }
        final Optional<MenuRenderer> renderer = judicator.getPlatform().menus();
        if (renderer.isEmpty()) {
            promptInChat(judicator, player, builder);
            return;
        }
        // on the player's thread, like the menus
        judicator.getPlatform().runFor(player, () -> {
            if (!askEvidence(judicator, renderer.get(), player, builder)) promptInChat(judicator, player, builder);
        });
    }

    private static void promptInChat(Judicator judicator, JudicatorPlayer player, PunishmentBuilder builder) {
        player.sendMessage(PunishUtils.getConfirmationMessage(judicator.messagesFor(player)));
        final ChatContext<PunishmentBuilder> chatContext = ChatContext.buildPunishmentContext(player.getName(), builder, judicator);
        judicator.getPunishCache().putContext(player.getName(), chatContext);
    }

    /**
     * The evidence on a dialog of its own: the links typed in one field (none applies without evidence).
     *
     * @return false when the renderer cannot ask this player
     */
    private static boolean askEvidence(Judicator judicator, MenuRenderer renderer, JudicatorPlayer player, PunishmentBuilder builder) {
        final ConfigurationNode messages = judicator.messagesFor(player);
        final MenuRenderer.Question question = new MenuRenderer.Question(
                PunishUtils.fill(messages.node("prompt", "dialog", "title").getString("&8Evidence"), Map.of()),
                PunishUtils.fill(messages.node("prompt", "dialog", "label").getString("Links of the evidence:"), Map.of()),
                PunishUtils.fill(messages.node("prompt", "dialog", "confirm").getString("&aApply"), Map.of()),
                PunishUtils.fill(messages.node("prompt", "dialog", "cancel").getString("&cCancel"), Map.of()),
                true);
        return renderer.ask(player, question, answer -> {
            builder.evidences(fitting(answer));
            judicator.getPlatform().runAsync(() -> new PunishFactory(judicator, builder).factory());
        }, () -> player.sendMessage(PunishUtils.getMessage(messages, "success", "cancel")));
    }

    /**
     * The links of the answer that fit in the stored evidence, in order (the field takes 255 characters; a client could
     * send more).
     */
    private static List<String> fitting(String answer) {
        final List<String> links = new ArrayList<>();
        for (String link : answer.trim().split("\\s+")) {
            if (link.isEmpty()) continue;
            links.add(link);
            if (String.join(",", links).length() > ChatContext.EVIDENCE_MAX_LENGTH) {
                links.remove(links.size() - 1);
                break;
            }
        }
        return links;
    }

}
