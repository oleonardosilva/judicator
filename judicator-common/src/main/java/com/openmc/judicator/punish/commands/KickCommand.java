package com.openmc.judicator.punish.commands;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.commons.command.CommandArgs;
import com.openmc.judicator.commons.command.CoreCommand;
import com.openmc.judicator.platform.JudicatorPlayer;
import com.openmc.judicator.platform.JudicatorSender;
import com.openmc.judicator.punish.PunishUtils;
import com.openmc.judicator.punish.Punishment;
import com.openmc.judicator.punish.ReasonShortcuts;
import com.openmc.judicator.punish.data.cache.ImmuneCache;
import com.openmc.judicator.punish.rules.PunishCooldown;
import com.openmc.judicator.punish.types.PunishPermissions;
import com.openmc.judicator.punish.types.PunishType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.spongepowered.configurate.ConfigurationNode;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class KickCommand extends CoreCommand {

    public KickCommand(Judicator judicator) {
        super(judicator, "kick", List.of(), PunishPermissions.KICK.getPermission(), PunishPermissions.ADMIN.getPermission());
    }

    @Override
    public void execute(JudicatorSender source, CommandArgs args) {
        final Optional<String> optTarget = args.word();
        if (optTarget.isEmpty()) {
            sendUsage(source, "kick");
            return;
        }
        final String targetName = optTarget.get();
        final boolean silent = args.consumeFlag(PunishTypeCommand.SILENT_FLAG);
        final ConfigurationNode messages = messages(source);
        if (silent && !silentAllowed(source)) return;
        if (!judicator.getImmuneCache().canPunish(source, targetName, ImmuneCache.KICK)) return;
        final Optional<String> remaining = ReasonShortcuts.expand(judicator, source, args.remaining());
        if (remaining.isEmpty()) return;
        final String reason = remaining.get().isEmpty() ? "None" : remaining.get();

        final Optional<JudicatorPlayer> optPlayer = judicator.getPlatform().getPlayer(targetName);
        if (optPlayer.isEmpty()) {
            source.sendMessage(PunishUtils.getMessage(messages, "error", "player-offline"));
            return;
        }

        if (!judicator.getPunishCooldown().acquire(source, PunishCooldown.KICK, targetName)) return;
        final String punisher = source.asPlayer().map(JudicatorPlayer::getName).orElse("Console");
        final JudicatorPlayer player = optPlayer.get();

        judicator.getPlatform().runSync(() -> {
            final TextComponent kickMessage = kickText(judicator.messagesFor(player), "runners", targetName, punisher, reason);
            if (!silent && judicator.getConfig().node("announce").getBoolean(true)) {
                judicator.getPlatform().announceToServerOf(player,
                        recipient -> kickText(judicator.messagesFor(recipient), "announcements", targetName, punisher, reason));
            }
            player.disconnect(kickMessage);
        });
        judicator.getStaffNotifier().notifyKick(punisher, targetName, reason, silent);
        if (judicator.getConfig().node("log-kicks").getBoolean(false)) record(player, punisher, reason);
    }

    private static TextComponent kickText(ConfigurationNode messages, String section, String nickname, String author, String reason) {
        return Component.text(PunishUtils.getMessageList(messages, section, "kick").content()
                .replace("{nickname}", nickname)
                .replace("{author}", author)
                .replace("{reason}", reason));
    }

    /**
     * Stores the kick in the punishment history. It is already over when it is stored (end = start), so it never
     * counts as an active punishment.
     */
    private void record(JudicatorPlayer player, String punisher, String reason) {
        final LocalDateTime now = LocalDateTime.now();
        // reason is VARCHAR(255): a longer one would make the database reject the row.
        final Punishment kick = Punishment.create(player.getUniqueId(), reason.length() <= 255 ? reason : reason.substring(0, 255),
                punisher, player.getName(), null,
                now, now, new ArrayList<>(), PunishType.KICK);
        kick.setPermanent(false);
        judicator.getPunishService().save(kick);
    }

    @Override
    public List<String> suggest(JudicatorSender sender, String[] args) {
        final List<String> shortcuts = ReasonShortcuts.suggest(judicator, sender, args, 1);
        return shortcuts.isEmpty() ? super.suggest(sender, args) : shortcuts;
    }

}
