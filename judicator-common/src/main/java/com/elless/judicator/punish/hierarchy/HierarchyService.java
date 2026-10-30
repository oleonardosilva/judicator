package com.elless.judicator.punish.hierarchy;

import com.elless.judicator.players.PlayerRef;
import com.elless.judicator.Judicator;
import com.elless.judicator.platform.JudicatorPlayer;
import com.elless.judicator.platform.JudicatorSender;
import com.elless.judicator.punish.types.PunishPermissions;

import java.util.Optional;

public class HierarchyService {

    private final Judicator judicator;
    private final PlayerLevelRepository repository;

    private static final String CONSOLE = "Console";

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
        return targetLevel(judicator.getPlayerDirectory().resolveKnown(targetName));
    }

    public int targetLevel(PlayerRef target) {
        final Optional<JudicatorPlayer> online = target.uuid() == null ? judicator.getPlatform().getPlayer(target.name())
                : judicator.getPlatform().getPlayer(target.uuid());
        if (online.isPresent()) return levelOf(online.get());
        return repository.findLevel(target).orElse(0);
    }

    public boolean canPunish(JudicatorSender source, String targetName) {
        if (!isEnabled() || !source.isPlayer()) return true;
        return canPunish(source, judicator.getPlayerDirectory().resolveKnown(targetName));
    }

    public boolean canPunish(JudicatorSender source, PlayerRef target) {
        if (!isEnabled() || !source.isPlayer()) return true;
        final int targetLevel = targetLevel(target);
        return targetLevel <= 0 || levelOf(source) > targetLevel;
    }

    /**
     * Whether the source may act on what a staff member applied (change or roll back their punishments): the console,
     * {@code judicator.admin}, the staff member themselves, or, with the hierarchy on, someone of a higher level. What
     * the console applied is only for the console and admins.
     */
    public boolean canActOnStaff(JudicatorSender source, String staff) {
        if (!source.isPlayer() || source.hasPermission(PunishPermissions.ADMIN.getPermission())) return true;
        // what the console applied (warn tree, anticheat, imports) has no level: only the console or an admin changes it
        if (CONSOLE.equalsIgnoreCase(staff)) return false;
        if (source.getName().equalsIgnoreCase(staff)) return true;
        if (!isEnabled()) return true;
        final int staffLevel = targetLevel(staff);
        return staffLevel <= 0 || levelOf(source) > staffLevel;
    }

    public void recordLevel(JudicatorPlayer player) {
        if (!isEnabled()) return;
        repository.saveLevel(player.getName(), player.getUniqueId(), levelOf(player));
    }

}
