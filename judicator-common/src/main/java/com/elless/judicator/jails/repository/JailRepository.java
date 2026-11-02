package com.elless.judicator.jails.repository;

import com.elless.judicator.jails.Jail;

import java.util.List;

/**
 * The jails, in the database so that every server of a network (and the proxy, which sends prisoners to theirs) sees
 * the same ones.
 */
public interface JailRepository {

    void initialize();

    List<Jail> findAll();

    /**
     * Stores a new jail or replaces the one with its name.
     *
     * @return false when it could not be stored
     */
    boolean save(Jail jail, String author);

    /**
     * @return false when there was no such jail
     */
    boolean delete(String name);

}
