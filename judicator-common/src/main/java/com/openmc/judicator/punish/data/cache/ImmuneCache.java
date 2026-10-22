package com.openmc.judicator.punish.data.cache;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.platform.JudicatorPlayer;
import com.openmc.judicator.platform.JudicatorSender;
import com.openmc.judicator.punish.PunishUtils;
import org.spongepowered.configurate.ConfigurationNode;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
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
            return false;
        }

        if (!judicator.getHierarchyService().canPunish(source, target)) {
            source.sendMessage(PunishUtils.getMessage(messages, "error", "hierarchy"));
            return false;
        }
        return true;
    }

    /**
     * An IP punishment reaches every account that used the address, so each of them must be punishable by the
     * source (self, immune list and hierarchy), not only the account that was named in the command.
     */
    public boolean canPunishAddress(JudicatorSender source, String address) {
        final Set<String> accounts = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        accounts.addAll(judicator.getAddressService().findAccountsByIp(address, ""));
        judicator.getPlatform().getOnlinePlayers().stream()
                .filter(player -> address.equals(player.getHostAddress()))
                .map(JudicatorPlayer::getName)
                .forEach(accounts::add);
        for (String account : accounts) {
            if (!canPunish(source, account)) return false;
        }
        return true;
    }
}
