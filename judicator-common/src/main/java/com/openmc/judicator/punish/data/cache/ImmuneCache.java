package com.openmc.judicator.punish.data.cache;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.platform.JudicatorSender;
import com.openmc.judicator.punish.PunishUtils;
import org.spongepowered.configurate.ConfigurationNode;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ImmuneCache {

    private final Set<String> usersImmune = ConcurrentHashMap.newKeySet();
    private Judicator judicator;

    public ImmuneCache() {
    }

    public void initialize(Judicator judicator) {
        this.judicator = judicator;
        usersImmune.clear();
        try {
            List<String> list = judicator.getImmuneConfig().node("users").getList(String.class);
            if (list != null) usersImmune.addAll(list);
        } catch (Exception e) {
            judicator.getLogger().error(e.getMessage(), e);
        }
    }

    public boolean canPunish(JudicatorSender source, String target) {
        final ConfigurationNode messages = judicator.getMessagesConfig();
        if (source.isPlayer() && source.getName().equalsIgnoreCase(target)) {
            source.sendMessage(PunishUtils.getMessage(messages, "error", "self-punish"));
            return false;
        }

        boolean can = usersImmune.stream().noneMatch(s -> s.equalsIgnoreCase(target));
        if (!can) {
            source.sendMessage(PunishUtils.getMessage(messages, "error", "immune"));
        }
        return can;
    }
}
