package com.openmc.judicator.database;

import com.openmc.judicator.testing.InMemoryAccessAddressRepository;
import com.openmc.judicator.testing.InMemoryNoteRepository;
import com.openmc.judicator.testing.InMemoryPlayerLevelRepository;
import com.openmc.judicator.testing.InMemoryPunishmentRepository;
import com.openmc.judicator.testing.InMemoryWarnRepository;

import java.nio.file.Path;

/**
 * The in-memory repositories behind {@code TestServer} follow the same contract as the database.
 */
class InMemoryRepositoryContractTest extends RepositoryContract {

    @Override
    protected Store open(Path folder) {
        return new Store(new InMemoryPunishmentRepository(), new InMemoryWarnRepository(),
                new InMemoryAccessAddressRepository(), new InMemoryPlayerLevelRepository(), new InMemoryNoteRepository(), () -> {
        });
    }
}
