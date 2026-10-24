package com.openmc.judicator.commons;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.platform.JudicatorPlayer;
import com.openmc.judicator.punish.PunishUtils;
import com.openmc.judicator.punish.PunishmentBuilder;
import com.openmc.judicator.punish.data.cache.PunishCache;
import com.openmc.judicator.punish.handlers.PunishFactory;
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

    static final int EVIDENCE_MAX_LENGTH = 255;

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
            input.player().sendMessage(PunishUtils.getMessage(judicator.messagesFor(input.player()), "success", "cancel"));
            cache.removeContext(currentBuilder.getPunisher());
        });

        return chatContext;
    }

    /**
     * The next chat line of a staff member answers a question of the menus (a reason, a note): {@code onText} gets it,
     * unless it is the cancel word ({@code prompt.cancel}), which runs {@code onCancel}. A command typed meanwhile
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
            cache.removeContext(operator);
            input.player().sendMessage(PunishUtils.getMessage(judicator.messagesFor(input.player()), "success", "cancel"));
        });
        return chatContext;
    }

    /**
     * The word in the staff member's language, or in the server language (what they may have learned to type).
     */
    private static boolean isPromptWord(Judicator judicator, ConfigurationNode own, String key, String message) {
        return message.equalsIgnoreCase(own.node("prompt", key).getString(""))
                || message.equalsIgnoreCase(judicator.getMessagesConfig().node("prompt", key).getString(""));
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
