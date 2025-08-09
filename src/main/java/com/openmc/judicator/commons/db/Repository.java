package com.openmc.judicator.commons.db;

import java.util.Optional;

public interface Repository<O, I> {

    void initialize();

    O save(O o);

    Optional<O> findById(I id);

    void deleteById(I id);

}
