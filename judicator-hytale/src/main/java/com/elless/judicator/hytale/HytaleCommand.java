package com.elless.judicator.hytale;

import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.ParseResult;
import com.hypixel.hytale.server.core.command.system.ParserContext;
import com.elless.judicator.Judicator;
import com.elless.judicator.commons.command.CommandArgs;
import com.elless.judicator.commons.command.CoreCommand;
import com.elless.judicator.platform.JudicatorSender;

import java.util.concurrent.CompletableFuture;

/**
 * A core command as a Hytale command that takes the rest of the line as it is; it runs off the world threads, like on
 * the other platforms, and only who may use it can.
 */
final class HytaleCommand extends AbstractCommand {

    private final Judicator judicator;
    private final HytalePlatform platform;
    private final CoreCommand command;

    HytaleCommand(Judicator judicator, HytalePlatform platform, CoreCommand command) {
        super(command.getName(), "Judicator /" + command.getName());
        this.judicator = judicator;
        this.platform = platform;
        this.command = command;
        if (!command.getAliases().isEmpty()) addAliases(command.getAliases().toArray(String[]::new));
        setAllowsExtraArguments(true);
    }

    @Override
    public CompletableFuture<Void> acceptCall(CommandSender sender, ParserContext context, ParseResult result) {
        final JudicatorSender wrapped = HytaleSender.of(sender, platform);
        // the input is the whole line: the label first
        final String[] line = context.getInputString().trim().split(" ", 2);
        final CommandArgs args = CommandArgs.of(line.length > 1 ? line[1] : "");
        return CompletableFuture.runAsync(() -> {
            try {
                command.execute(wrapped, args);
            } catch (RuntimeException e) {
                judicator.getLogger().error("Error while executing /{}", command.getName(), e);
            }
        }, platform::runAsync);
    }

    @Override
    protected CompletableFuture<Void> execute(CommandContext context) {
        throw new UnsupportedOperationException("acceptCall takes the whole line");
    }

    @Override
    public String getPermission() {
        return null;
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return command.canUse(HytaleSender.of(sender, platform));
    }
}
