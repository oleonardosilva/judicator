package com.elless.judicator.minecraft;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import com.elless.judicator.Judicator;
import com.elless.judicator.commons.command.CommandArgs;
import com.elless.judicator.commons.command.CoreCommand;
import com.elless.judicator.platform.JudicatorSender;
import com.elless.judicator.punish.PunishUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * The core's commands as Brigadier commands: the name and each alias, with the rest of the line as one argument, in
 * place of a vanilla command of the same name. Who cannot use a command does not see it ({@code requires}); it runs off
 * the game thread, like on the other platforms.
 */
final class MinecraftCommands {

    private static final String ARGUMENTS = "arguments";

    private final Judicator judicator;
    private final MinecraftPlatform platform;

    MinecraftCommands(Judicator judicator, MinecraftPlatform platform) {
        this.judicator = judicator;
        this.platform = platform;
    }

    void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        for (CoreCommand command : judicator.getCommands()) {
            final List<String> labels = new ArrayList<>();
            labels.add(command.getName());
            labels.addAll(command.getAliases());
            for (String label : labels) {
                // Judicator's /ban, /kick, /banlist and /pardon take the place of the vanilla ones
                if (!remove(dispatcher.getRoot(), label)) continue;
                final LiteralCommandNode<CommandSourceStack> node = Commands.literal(label)
                        .requires(source -> command.canUse(sender(source)))
                        .executes(context -> run(command, context, ""))
                        .then(Commands.argument(ARGUMENTS, StringArgumentType.greedyString())
                                .suggests((context, builder) -> suggest(command, context, builder))
                                .executes(context -> run(command, context, StringArgumentType.getString(context, ARGUMENTS))))
                        .build();
                dispatcher.getRoot().addChild(node);
            }
        }
    }

    /**
     * Brigadier cannot remove a command: the node leaves the maps of the root.
     *
     * @return whether the label is free now
     */
    private boolean remove(RootCommandNode<CommandSourceStack> root, String label) {
        if (root.getChild(label) == null) return true;
        try {
            for (String field : List.of("children", "literals", "arguments")) {
                final Field maps = CommandNode.class.getDeclaredField(field);
                maps.setAccessible(true);
                ((Map<?, ?>) maps.get(root)).remove(label);
            }
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            judicator.getLogger().warn("Could not replace the server's /{}; Judicator's is not registered", label, e);
            return false;
        }
    }

    private JudicatorSender sender(CommandSourceStack source) {
        return MinecraftSender.of(source, platform);
    }

    private int run(CoreCommand command, CommandContext<CommandSourceStack> context, String arguments) {
        final JudicatorSender sender = sender(context.getSource());
        if (!command.canUse(sender)) {
            sender.sendMessage(PunishUtils.getMessage(judicator.getMessagesConfig(), "error", "no-permission"));
            return 0;
        }
        final CommandArgs args = CommandArgs.of(arguments);
        platform.runAsync(() -> {
            try {
                command.execute(sender, args);
            } catch (RuntimeException e) {
                judicator.getLogger().error("Error while executing /{}", command.getName(), e);
            }
        });
        return 1;
    }

    /**
     * The core suggests the word being typed; the suggestions replace it.
     */
    private CompletableFuture<Suggestions> suggest(CoreCommand command, CommandContext<CommandSourceStack> context,
                                                   SuggestionsBuilder builder) {
        final JudicatorSender sender = sender(context.getSource());
        if (!command.canUse(sender)) return builder.buildFuture();
        final String typed = builder.getRemaining();
        final String[] args = typed.split(" ", -1);
        final SuggestionsBuilder lastWord = builder.createOffset(builder.getStart() + typed.lastIndexOf(' ') + 1);
        for (String suggestion : command.suggest(sender, args)) {
            lastWord.suggest(suggestion);
        }
        return lastWord.buildFuture();
    }
}
