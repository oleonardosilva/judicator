package com.elless.judicator.database;

import com.elless.judicator.testing.InMemoryPlayerAddressRepository;
import com.elless.judicator.testing.InMemoryNoteRepository;
import com.elless.judicator.testing.InMemoryPlayerLevelRepository;
import com.elless.judicator.testing.InMemoryPlayerRepository;
import com.elless.judicator.testing.InMemoryPunishmentRepository;
import com.elless.judicator.testing.InMemoryReportRepository;
import com.elless.judicator.testing.InMemorySyncRepository;
import com.elless.judicator.testing.InMemoryWarnRepository;

import java.nio.file.Path;

/**
 * The in-memory repositories behind {@code TestServer} follow the same contract as the database.
 */
class InMemoryRepositoryContractTest extends RepositoryContract {

    @Override
    protected Store open(Path folder) {
        return new Store(new InMemoryPunishmentRepository(), new InMemoryWarnRepository(),
                new InMemoryPlayerAddressRepository(), new InMemoryPlayerLevelRepository(), new InMemoryNoteRepository(), new InMemorySyncRepository(),
                new InMemoryPlayerRepository(), new InMemoryReportRepository(), () -> {
        });
    }
}
