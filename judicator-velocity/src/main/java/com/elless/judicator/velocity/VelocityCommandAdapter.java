package com.elless.judicator.velocity;

import com.elless.judicator.Judicator;
import com.elless.judicator.commons.command.CommandArgs;
import com.elless.judicator.commons.command.CoreCommand;
import com.elless.judicator.platform.JudicatorSender;
import com.velocitypowered.api.command.SimpleCommand;

import java.util.List;

public class VelocityCommandAdapter implements SimpleCommand {

    private final Judicator judicator;
    private final CoreCommand command;

    public VelocityCommandAdapter(Judicator judicator, CoreCommand command) {
        this.judicator = judicator;
        this.command = command;
    }

    @Override
    public void execute(Invocation invocation) {
        final JudicatorSender sender = VelocitySender.of(invocation.source());
        final CommandArgs args = CommandArgs.of(invocation.arguments());
        judicator.getPlatform().runAsync(() -> {
            try {
                command.execute(sender, args);
            } catch (RuntimeException e) {
                judicator.getLogger().error("Error while executing /{}", command.getName(), e);
            }
        });
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        return command.suggest(VelocitySender.of(invocation.source()), invocation.arguments());
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return command.canUse(VelocitySender.of(invocation.source()));
    }

}
