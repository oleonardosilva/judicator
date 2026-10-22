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

import java.util.function.BiConsumer;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChatContext<T> {

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
            final ConfigurationNode messages = judicator.getMessagesConfig();
            final String message = input.message();
            final String readyPrompt = messages.node("prompt", "ready").getString("");
            final String cancelPrompt = messages.node("prompt", "cancel").getString("");
            if (message.equalsIgnoreCase(readyPrompt)) {
                cache.removeContext(currentBuilder.getPunisher());
                judicator.getPlatform().runAsync(() -> new PunishFactory(judicator, currentBuilder).factory());
                return;
            }
            if (message.equalsIgnoreCase(cancelPrompt)) {
                input.player().sendMessage(PunishUtils.getMessage(messages, "success", "cancel"));
                cache.removeContext(currentBuilder.getPunisher());
                return;
            }

            currentBuilder.appendEvidences(message);
            input.player().sendMessage(PunishUtils.getConfirmationMessage(messages, "prompt", "next-link"));
        });

        chatContext.setCallbackCommand((currentBuilder, input) -> {
            input.player().sendMessage(PunishUtils.getMessage(judicator.getMessagesConfig(), "success", "cancel"));
            cache.removeContext(currentBuilder.getPunisher());
        });

        return chatContext;
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
