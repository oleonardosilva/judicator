package com.elless.judicator.database;

import com.elless.judicator.commons.db.RelationalDBManager;
import com.elless.judicator.jails.repository.dao.JailRelationalDAO;
import com.elless.judicator.notes.repository.dao.NoteRelationalDAO;
import com.elless.judicator.players.repository.dao.PlayerRelationalDAO;
import com.elless.judicator.punish.data.repository.dao.PlayerAddressRelationalDAO;
import com.elless.judicator.punish.data.repository.dao.PunishmentRelationalDAO;
import com.elless.judicator.punish.hierarchy.PlayerLevelRelationalDAO;
import com.elless.judicator.reports.repository.dao.ReportRelationalDAO;
import com.elless.judicator.sync.repository.dao.SyncRelationalDAO;
import com.elless.judicator.warns.repository.dao.WarnRelationalDAO;
import org.slf4j.Logger;

/**
 * Every relational DAO on one database, its tables created as at startup.
 */
final class RelationalStores {

    private RelationalStores() {
    }

    static RepositoryContract.Store open(RelationalDBManager manager, Logger logger) {
        final PunishmentRelationalDAO punishments = new PunishmentRelationalDAO(manager, logger);
        final WarnRelationalDAO warns = new WarnRelationalDAO(manager, logger);
        final PlayerAddressRelationalDAO addresses = new PlayerAddressRelationalDAO(manager, logger);
        final PlayerLevelRelationalDAO levels = new PlayerLevelRelationalDAO(manager, logger);
        final NoteRelationalDAO notes = new NoteRelationalDAO(manager, logger);
        final SyncRelationalDAO sync = new SyncRelationalDAO(manager, logger);
        final PlayerRelationalDAO players = new PlayerRelationalDAO(manager, logger);
        final ReportRelationalDAO reports = new ReportRelationalDAO(manager, logger);
        final JailRelationalDAO jails = new JailRelationalDAO(manager, logger);
        punishments.initialize();
        warns.initialize();
        addresses.initialize();
        levels.initialize();
        notes.initialize();
        sync.initialize();
        players.initialize();
        reports.initialize();
        jails.initialize();
        return new RepositoryContract.Store(punishments, warns, addresses, levels, notes, sync, players, reports, jails, manager::shutdown);
    }
}
