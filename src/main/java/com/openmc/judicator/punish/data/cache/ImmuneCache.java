package com.openmc.judicator.punish.data.cache;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.punish.PunishUtils;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.TextComponent;
import org.spongepowered.configurate.ConfigurationNode;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ImmuneCache {

    private Set<String> usersImmune;
    private ConfigurationNode messages;

    public ImmuneCache() {
    }

    public void initialize(Judicator judicator) {
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
        if (source instanceof Player player && player.getUsername().equalsIgnoreCase(target)) {
            player.sendMessage(PunishUtils.getMessage(messages, "error", "self-punish"));
            return false;
        }

        boolean can = usersImmune.stream().noneMatch(s -> s.equalsIgnoreCase(target));
        if (!can) {
            final TextComponent text = PunishUtils.getMessage(messages, "error", "immune");

            source.sendMessage(text);
        }
        return can;
    }
}
