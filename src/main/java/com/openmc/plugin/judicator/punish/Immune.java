package com.openmc.plugin.judicator.punish;

import com.openmc.plugin.judicator.Judicator;
import com.velocitypowered.api.command.CommandSource;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.spongepowered.configurate.ConfigurationNode;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Immune {

    private final Set<String> usersImmune;
    private final ConfigurationNode messages;

    public Immune(Judicator judicator) {
        this.usersImmune = new HashSet<>();
        this.messages = judicator.getMessagesConfig();
        try {
            List<String> list = judicator.getImmuneConfig().node("users").getList(String.class);
            if (list != null) usersImmune.addAll(list);
        } catch (Exception e) {
            judicator.getLogger().error(e.getMessage(), e);
        }
    }

    public boolean canPunish(CommandSource source, String target) {
        boolean can = usersImmune.stream().noneMatch(s -> s.equalsIgnoreCase(target));
        if (!can) {
            final TextComponent text = Component.text(messages.node("immune").getString("message"));
            source.sendMessage(text);
        }
        return can;
    }
}
