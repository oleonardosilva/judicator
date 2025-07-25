package com.openmc.plugin.judicator.punish.data.cache;

import com.openmc.plugin.judicator.Judicator;
import com.openmc.plugin.judicator.punish.PunishUtils;
import com.velocitypowered.api.command.CommandSource;
import net.kyori.adventure.text.TextComponent;
import org.spongepowered.configurate.ConfigurationNode;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ImmuneCache {

    private final Set<String> usersImmune;
    private final ConfigurationNode messages;

    public ImmuneCache(Judicator judicator) {
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
            final TextComponent text = PunishUtils.getMessage(messages, "immune");

            source.sendMessage(text);
        }
        return can;
    }
}
