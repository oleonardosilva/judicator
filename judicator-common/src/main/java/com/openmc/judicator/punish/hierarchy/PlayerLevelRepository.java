package com.openmc.judicator.punish.hierarchy;

import java.util.OptionalInt;
import java.util.UUID;

public interface PlayerLevelRepository {

    void initialize();

    void saveLevel(String nickname, UUID uuid, int level);

    OptionalInt findLevel(String nickname);

    /**
     * @return whether there was a level to delete
     */
    boolean delete(String nickname);

}
