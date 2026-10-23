package com.openmc.judicator.punish.hierarchy;


import com.openmc.judicator.players.PlayerRef;
import java.util.OptionalInt;
import java.util.UUID;

public interface PlayerLevelRepository {

    void initialize();

    /**
     * Stores the level of a staff member who joined; a row of the same account under an older name is dropped.
     */
    void saveLevel(String nickname, UUID uuid, int level);

    /**
     * The level last recorded for a player ({@link PlayerRef#matches}).
     */
    OptionalInt findLevel(PlayerRef player);

    /**
     * @return whether there was a level to delete
     */
    boolean delete(PlayerRef player);

}
