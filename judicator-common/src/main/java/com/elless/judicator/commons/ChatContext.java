package com.elless.judicator.commons;

import com.elless.judicator.Judicator;
import com.elless.judicator.platform.JudicatorPlayer;
import com.elless.judicator.punish.commands.PromptCommand;
import com.elless.judicator.punish.PunishUtils;
import com.elless.judicator.punish.PunishmentBuilder;
import com.elless.judicator.punish.data.cache.PunishCache;
import com.elless.judicator.punish.handlers.PunishFactory;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.spongepowered.configurate.ConfigurationNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChatContext<T> {

    /** evidence is stored as one comma-separated VARCHAR(255) */
    public static final int EVIDENCE_MAX_LENGTH = 255;

    private String operator;
    private T value;
    private BiConsumer<T, ChatInput> callbackChat;
    private BiConsumer<T, ChatInput> callbackCommand;

    public static ChatContext<PunishmentBuilder> buildPunishmentContext(String punisher, PunishmentBuilder builder, Judicator judicator) {
        final PunishCache cache = judicator.getPunishCache();
        final ChatContext<PunishmentBuilder> chatContext = new ChatContext<>();
        chatContext.setOperator(punisher);
        chatContext.setValue(builder);
        chatContext.setCallbackChat((currentBuilder, input) -> {
            final ConfigurationNode messages = judicator.messagesFor(input.player());
            final String message = input.message();
            if (isPromptWord(judicator, messages, "ready", message)) {
                cache.removeContext(currentBuilder.getPunisher());
                judicator.getPlatform().runAsync(() -> new PunishFactory(judicator, currentBuilder).factory());
                return;
            }
            if (isPromptWord(judicator, messages, "cancel", message)) {
                input.player().sendMessage(PunishUtils.getMessage(messages, "success", "cancel"));
                cache.removeContext(currentBuilder.getPunisher());
                return;
            }

            // Evidence is stored as one comma-separated VARCHAR(255): more than that and the database rejects the
            // whole punishment. Refuse the link that does not fit instead.
            final List<String> withNew = new ArrayList<>(currentBuilder.getEvidences());
            withNew.addAll(Arrays.asList(message.split(" ")));
            if (String.join(",", withNew).length() > EVIDENCE_MAX_LENGTH) {
                input.player().sendMessage(PunishUtils.getConfirmationMessage(messages, "prompt", "evidence-limit"));
                return;
            }

            currentBuilder.appendEvidences(message);
            input.player().sendMessage(PunishUtils.getConfirmationMessage(messages, "prompt", "next-link"));
        });

        chatContext.setCallbackCommand((currentBuilder, input) -> {
            final ConfigurationNode messages = judicator.messagesFor(input.player());
            final String answer = commandAnswer(input.message());
            if (isPromptWord(judicator, messages, "ready", answer) || isPromptWord(judicator, messages, "cancel", answer)) {
                chatContext.acceptChat(input.player(), answer);
                return;
            }
            input.player().sendMessage(PunishUtils.getMessage(messages, "success", "cancel"));
            cache.removeContext(currentBuilder.getPunisher());
        });

        return chatContext;
    }

    /**
     * The next chat line of a staff member answers a question of the menus (a reason, a note): {@code onText} gets it,
     * unless it is the cancel word ({@code prompt.cancel}), which runs {@code onCancel}. Another command typed meanwhile
     * cancels the question.
     */
    public static ChatContext<String> textPrompt(Judicator judicator, JudicatorPlayer player, Consumer<String> onText,
                                                 Runnable onCancel) {
        final PunishCache cache = judicator.getPunishCache();
        final ChatContext<String> chatContext = new ChatContext<>();
        chatContext.setOperator(player.getName());
        chatContext.setValue(player.getName());
        chatContext.setCallbackChat((operator, input) -> {
            cache.removeContext(operator);
            final ConfigurationNode messages = judicator.messagesFor(input.player());
            if (isPromptWord(judicator, messages, "cancel", input.message())) {
                input.player().sendMessage(PunishUtils.getMessage(messages, "success", "cancel"));
                onCancel.run();
                return;
            }
            onText.accept(input.message());
        });
        chatContext.setCallbackCommand((operator, input) -> {
            final String answer = commandAnswer(input.message());
            if (isPromptWord(judicator, judicator.messagesFor(input.player()), "cancel", answer)) {
                chatContext.acceptChat(input.player(), answer);
                return;
            }
            cache.removeContext(operator);
            input.player().sendMessage(PunishUtils.getMessage(judicator.messagesFor(input.player()), "success", "cancel"));
        });
        return chatContext;
    }

    /**
     * What a command typed during a prompt answers: the word of {@code /jprompt (word)} (the buttons of the chat, which
     * clients since 1.19.1 send as commands) or the whole command ({@code /confirm}); a prompt word or not.
     */
    private static String commandAnswer(String commandLine) {
        final String line = (commandLine.startsWith("/") ? commandLine.substring(1) : commandLine).trim();
        final String[] parts = line.split(" ", 2);
        return parts[0].equalsIgnoreCase(PromptCommand.NAME) && parts.length > 1 ? parts[1].trim() : line;
    }

    /**
     * The word in the staff member's language, or in the server language (what they may have learned to type).
     */
    private static boolean isPromptWord(Judicator judicator, ConfigurationNode own, String key, String message) {
        return message.equalsIgnoreCase(PunishUtils.withoutColors(own.node("prompt", key).getString("")))
                || message.equalsIgnoreCase(PunishUtils.withoutColors(judicator.getMessagesConfig().node("prompt", key).getString("")));
    }

    public void acceptChat(JudicatorPlayer player, String message) {
        if (callbackChat != null) {
            callbackChat.accept(value, new ChatInput(player, message));
        }
    }

    public void acceptCommand(JudicatorPlayer player, String command) {
        if (callbackCommand != null) {
            callbackCommand.accept(value, new ChatInput(player, command));
        }
    }

    public record ChatInput(JudicatorPlayer player, String message) {
    }

}
