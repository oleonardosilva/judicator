package com.elless.judicator.punish.commands;

import com.elless.judicator.Judicator;
import com.elless.judicator.commons.command.CommandArgs;
import com.elless.judicator.commons.command.CoreCommand;
import com.elless.judicator.platform.JudicatorPlayer;
import com.elless.judicator.platform.JudicatorSender;
import com.elless.judicator.punish.PunishUtils;

import java.util.List;
import java.util.Optional;

/**
 * {@code /jprompt (word)}: what the confirm and cancel buttons of the evidence prompt run. Clients since 1.19.1 send a
 * clicked word as a command, never as chat, and newer ones question a command they do not know: this one is registered,
 * and the prompt takes it before it runs ({@code ChatHandler.handlePromptCommand}). Run with no prompt open, it says so.
 */
public class PromptCommand extends CoreCommand {

    public static final String NAME = "jprompt";

    public PromptCommand(Judicator judicator) {
        super(judicator, NAME, List.of());
    }

    /**
     * Whoever can apply a punishment that asks for evidence.
     */
    @Override
    public boolean canUse(JudicatorSender sender) {
        return judicator.getCommands().stream()
                .filter(command -> command instanceof PunishTypeCommand || command instanceof PunishCommand)
                .anyMatch(command -> command.canUse(sender));
    }

    @Override
    public void execute(JudicatorSender sender, CommandArgs args) {
        final Optional<JudicatorPlayer> player = sender.asPlayer();
        if (player.isPresent() && judicator.getChatHandler().handlePromptCommand(player.get(), NAME + " " + args.remaining())) return;
        sender.sendMessage(PunishUtils.getMessage(judicator.messagesFor(sender), "prompt", "nothing"));
    }

    @Override
    public List<String> suggest(JudicatorSender sender, String[] args) {
        return List.of();
    }
}
