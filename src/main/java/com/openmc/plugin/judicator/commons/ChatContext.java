package com.openmc.plugin.judicator.commons;

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
    private BiConsumer<T, PlayerChatEvent> callback;

    public void accept(PlayerChatEvent event) {
        if (callback != null) {
            callback.accept(value, event);
        }
    }
}
