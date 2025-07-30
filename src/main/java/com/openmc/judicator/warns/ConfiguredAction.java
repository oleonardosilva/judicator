package com.openmc.judicator.warns;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class ConfiguredAction {

    private Long count;
    private List<String> commands;

    public static ConfiguredAction from(ConfigurationNode node) throws SerializationException {
        final ConfiguredAction reason = new ConfiguredAction();
        reason.count = node.node("warns").getLong(0L);
        reason.commands = node.node("commands").getList(String.class);
        return reason;
    }

}
