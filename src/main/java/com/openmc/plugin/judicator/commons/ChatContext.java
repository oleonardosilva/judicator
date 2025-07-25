package com.openmc.plugin.judicator.commons;

import com.openmc.plugin.judicator.Judicator;
import com.openmc.plugin.judicator.punish.PunishUtils;
import com.openmc.plugin.judicator.punish.PunishmentBuilder;
import com.openmc.plugin.judicator.punish.data.cache.PunishCache;
import com.openmc.plugin.judicator.punish.handlers.BanHandler;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
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
            final String readyPrompt = messages.node("ready-prompt").getString("");
            final String cancelPrompt = messages.node("cancel-prompt").getString("");
            if (message.equalsIgnoreCase(readyPrompt)) {
                cache.removeContext(currentBuilder.getPunisher());
                new BanHandler(judicator, currentBuilder).handle();
                return;
            }
            if (message.equalsIgnoreCase(cancelPrompt)) {
                final TextComponent cancelMessage = PunishUtils.getMessage(messages, "operation-cancel");
                event.getPlayer().sendMessage(cancelMessage);
                cache.removeContext(currentBuilder.getPunisher());
                return;
            }

            currentBuilder.appendEvidences(message);
            final TextComponent cancelMessage = PunishUtils.getMessage(messages, "next-link");
            event.getPlayer().sendMessage(cancelMessage);
        });

        chatContext.setCallbackCommand((currentBuilder, event) -> {
            final TextComponent cancelMessage = PunishUtils.getMessage(messages, "operation-cancel");
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
