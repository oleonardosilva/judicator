package com.openmc.plugin.judicator.commons;

import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
