package com.openmc.judicator.warns.commands;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.commons.command.CommandArgs;
import com.openmc.judicator.commons.command.CoreCommand;
import com.openmc.judicator.platform.JudicatorSender;
import com.openmc.judicator.punish.ReasonShortcuts;
import com.openmc.judicator.punish.data.cache.ImmuneCache;
import com.openmc.judicator.punish.rules.PunishCooldown;
import com.openmc.judicator.punish.types.PunishPermissions;
import com.openmc.judicator.warns.WarnBuilder;
import com.openmc.judicator.warns.handlers.WarnHandler;

import java.util.List;
import java.util.Optional;

public class WarnCommand extends CoreCommand {

    private final boolean temporary;

    public WarnCommand(Judicator judicator) {
        this(judicator, "warn", false, PunishPermissions.WARN);
    }

    protected WarnCommand(Judicator judicator, String name, boolean temporary, PunishPermissions permission) {
        super(judicator, name, List.of(), permission.getPermission(), PunishPermissions.ADMIN.getPermission());
        this.temporary = temporary;
    }

    @Override
    public void execute(JudicatorSender source, CommandArgs args) {
        final Optional<String> optTarget = args.word();
        if (optTarget.isEmpty()) {
            sendUsage(source, getName());
            return;
        }
        final String targetName = optTarget.get();
        boolean silent = args.consumeFlag("-s");

        String duration = null;
        if (temporary) {
            final Optional<String> optDuration = args.quotableString();
            if (optDuration.isEmpty()) {
                sendUsage(source, getName());
                return;
            }
            duration = optDuration.get();
            silent |= args.consumeFlag("-s");
        }

        final String typedReason = args.remaining();
        if (typedReason.isEmpty()) {
            sendUsage(source, getName());
            return;
        }
        final Optional<String> reason = ReasonShortcuts.expand(judicator, source, typedReason);
        if (reason.isEmpty()) return;

        if (silent && !silentAllowed(source)) return;
        if (!judicator.getImmuneCache().canPunish(source, targetName, ImmuneCache.WARN)) return;

        final WarnBuilder builder = new WarnBuilder()
                .reason(reason.get())
                .silent(silent);

        if (duration != null) {
            try {
                builder.duration(duration);
            } catch (IllegalArgumentException e) {
                sendUsage(source, getName());
                return;
            }
        }

        builder.target(judicator.getPlayerDirectory().resolve(targetName));

        source.asPlayer().ifPresent(builder::punisher);
        if (!judicator.getPunishCooldown().acquire(source, PunishCooldown.WARN, targetName)) return;

        if (new WarnHandler(judicator, builder).handle().isEmpty()) {
            judicator.getPunishCooldown().release(PunishCooldown.WARN, targetName);
        }
    }

    @Override
    public List<String> suggest(JudicatorSender sender, String[] args) {
        final List<String> shortcuts = ReasonShortcuts.suggest(judicator, sender, args, temporary ? 2 : 1);
        return shortcuts.isEmpty() ? super.suggest(sender, args) : shortcuts;
    }

}
