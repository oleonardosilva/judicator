package com.openmc.judicator.commons;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.punish.PunishUtils;
import com.openmc.judicator.punish.PunishmentBuilder;
import com.openmc.judicator.punish.data.cache.PunishCache;
import com.openmc.judicator.punish.handlers.PunishFactory;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.spongepowered.configurate.ConfigurationNode;

import java.util.function.BiConsumer;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChatContext<T> {

    private String operator;
    private T value;
    private BiConsumer<T, PlayerChatEvent> callbackChat;
    private BiConsumer<T, CommandExecuteEvent> callbackCommand;

    public static ChatContext<PunishmentBuilder> buildPunishmentContext(String punisher, PunishmentBuilder builder, Judicator judicator) {
        final PunishCache cache = judicator.getPunishCache();
        final ConfigurationNode messages = judicator.getMessagesConfig();
        final ChatContext<PunishmentBuilder> chatContext = new ChatContext<>();
        chatContext.setOperator(punisher);
        chatContext.setValue(builder);
        chatContext.setCallbackChat((currentBuilder, event) -> {
            final String message = event.getMessage();
            final String readyPrompt = messages.node("prompt", "ready").getString("");
            final String cancelPrompt = messages.node("prompt", "cancel").getString("");
            if (message.equalsIgnoreCase(readyPrompt)) {
                cache.removeContext(currentBuilder.getPunisher());
                new PunishFactory(judicator, currentBuilder).factory();
                return;
            }
            if (message.equalsIgnoreCase(cancelPrompt)) {
                final TextComponent cancelMessage = PunishUtils.getMessage(messages, "success", "cancel");
                event.getPlayer().sendMessage(cancelMessage);
                cache.removeContext(currentBuilder.getPunisher());
                return;
            }

            currentBuilder.appendEvidences(message);
            final Component cancelMessage = PunishUtils.getConfirmationMessage(messages, "prompt", "next-link");
            event.getPlayer().sendMessage(cancelMessage);
        });

        chatContext.setCallbackCommand((currentBuilder, event) -> {
            final TextComponent cancelMessage = PunishUtils.getMessage(messages, "success", "cancel");
            event.getCommandSource().sendMessage(cancelMessage);
            cache.removeContext(currentBuilder.getPunisher());
        });

        return chatContext;
    }

    public void accept(PlayerChatEvent event) {
        if (callbackChat != null) {
            callbackChat.accept(value, event);
        }
    }

    public void accept(CommandExecuteEvent event) {
        if (callbackCommand != null) {
            callbackCommand.accept(value, event);
        }
    }
}
