package com.openmc.judicator.commons.command;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.platform.JudicatorSender;
import com.openmc.judicator.punish.PunishUtils;
import com.openmc.judicator.punish.types.PunishPermissions;
import lombok.Getter;
import org.spongepowered.configurate.ConfigurationNode;

import java.util.Arrays;
import java.util.List;

@Getter
public abstract class CoreCommand {

    protected final Judicator judicator;
    private final String name;
    private final List<String> aliases;
    private final List<String> permissions;

    protected CoreCommand(Judicator judicator, String name, List<String> aliases, String... permissions) {
        this.judicator = judicator;
        this.name = name;
        this.aliases = List.copyOf(aliases);
        this.permissions = Arrays.asList(permissions);
    }

    public boolean canUse(JudicatorSender sender) {
        return permissions.stream().anyMatch(sender::hasPermission);
    }

    public abstract void execute(JudicatorSender sender, CommandArgs args);

    public List<String> suggest(JudicatorSender sender, String[] args) {
        if (args.length <= 1) {
            final String prefix = args.length == 0 ? "" : args[0];
            return judicator.getPlatform().matchPlayerNames(prefix);
        }
        return List.of();
    }

    /**
     * @param recipient who the messages are for (their language with {@code per-player-language})
     */
    protected ConfigurationNode messages(JudicatorSender recipient) {
        return judicator.messagesFor(recipient);
    }

    protected boolean silentAllowed(JudicatorSender sender) {
        if (sender.hasPermission(PunishPermissions.SILENT.getPermission())
                || sender.hasPermission(PunishPermissions.ADMIN.getPermission())) {
            return true;
        }
        sender.sendMessage(PunishUtils.getMessage(messages(sender), "error", "silent-not-allowed"));
        return false;
    }

    protected void sendUsage(JudicatorSender sender, String usageKey) {
        sender.sendMessage(PunishUtils.getMessage(messages(sender), "usages", usageKey));
    }

}
