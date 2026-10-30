package com.elless.judicator.commons.db;

import com.elless.judicator.players.PlayerRef;

import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * The {@code WHERE} part that finds the records of a player, the SQL twin of {@link PlayerRef#matches}: by UUID, and by
 * name only for records stored without one; or only by name when the name is the identity (offline mode).
 */
public final class IdentitySql {

    private IdentitySql() {
    }

    /**
     * @param uuidColumn the column with the UUID, e.g. {@code player_uuid}
     * @param nameColumn the lower-case name, e.g. {@code lower_nickname} or {@code LOWER(punisher)}
     */
    public static String of(PlayerRef player, String uuidColumn, String nameColumn) {
        if (!player.identifiedByUuid()) return nameColumn + " = ?";
        return "(" + uuidColumn + " = ? OR (" + uuidColumn + " IS NULL AND " + nameColumn + " = ?))";
    }

    /**
     * Binds the parameters of {@link #of}.
     *
     * @return the index of the next parameter
     */
    public static int bind(PreparedStatement statement, int index, PlayerRef player) throws SQLException {
        if (player.identifiedByUuid()) statement.setString(index++, player.uuid().toString());
        statement.setString(index++, player.lowerName());
        return index;
    }

}
