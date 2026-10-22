package com.openmc.judicator.warns.commands;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.punish.types.PunishPermissions;

public class TempWarnCommand extends WarnCommand {

    public TempWarnCommand(Judicator judicator) {
        super(judicator, "tempwarn", true, PunishPermissions.TEMPWARN);
    }

}
