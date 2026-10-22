package com.openmc.judicator.punish.hierarchy;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.platform.JudicatorPlayer;
import com.openmc.judicator.platform.JudicatorSender;
import com.openmc.judicator.punish.types.PunishPermissions;

import java.util.Optional;

public class HierarchyService {

    private final Judicator judicator;
    private final PlayerLevelRepository repository;

    public HierarchyService(Judicator judicator, PlayerLevelRepository repository) {
        this.judicator = judicator;
        this.repository = repository;
        this.repository.initialize();
    }

    public boolean isEnabled() {
        return judicator.getConfig().node("hierarchy", "enabled").getBoolean(true);
    }

    public int levelOf(JudicatorSender sender) {
        if (!sender.isPlayer()) return Integer.MAX_VALUE;
        final int maxLevel = Math.max(0, judicator.getConfig().node("hierarchy", "max-level").getInt(100));
        for (int level = maxLevel; level > 0; level--) {
            if (sender.hasExplicitPermission(PunishPermissions.LEVEL_PREFIX + level)) return level;
        }
        return 0;
    }

    public int targetLevel(String targetName) {
        final Optional<JudicatorPlayer> online = judicator.getPlatform().getPlayer(targetName);
        if (online.isPresent()) return levelOf(online.get());
        return repository.findLevel(targetName).orElse(0);
    }

    public boolean canPunish(JudicatorSender source, String targetName) {
        if (!isEnabled() || !source.isPlayer()) return true;
        final int targetLevel = targetLevel(targetName);
        return targetLevel <= 0 || levelOf(source) > targetLevel;
    }

    public void recordLevel(JudicatorPlayer player) {
        if (!isEnabled()) return;
        repository.saveLevel(player.getName(), player.getUniqueId(), levelOf(player));
    }

}
