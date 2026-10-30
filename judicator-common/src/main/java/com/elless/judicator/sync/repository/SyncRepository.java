package com.elless.judicator.sync.repository;

import com.elless.judicator.sync.SyncMessage;

import java.util.List;
import java.util.OptionalLong;

/**
 * The notices the instances sharing one database leave each other ({@code sync_messages}).
 */
public interface SyncRepository {

    void initialize();

    void insert(String sender, String kind, long punishmentId);

    /**
     * @return the highest id stored (0 when the table is empty); empty when the database could not be read
     */
    OptionalLong maxId();

    /**
     * The notices with an id above {@code afterId}, lowest id first, at most {@code limit}.
     */
    List<SyncMessage> findAfter(long afterId, int limit);

    /**
     * @return how many were deleted
     */
    int deleteUpTo(long id);

}
