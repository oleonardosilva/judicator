package com.elless.judicator.warns.commands;

import com.elless.judicator.Judicator;
import com.elless.judicator.punish.types.PunishPermissions;

public class TempWarnCommand extends WarnCommand {

    public TempWarnCommand(Judicator judicator) {
        super(judicator, "tempwarn", true, PunishPermissions.TEMPWARN);
    }

}
