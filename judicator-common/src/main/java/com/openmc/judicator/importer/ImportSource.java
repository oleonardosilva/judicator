package com.openmc.judicator.importer;

/**
 * A plugin or file whose punishments can be copied into Judicator. Implementations only read the source.
 */
public interface ImportSource {

    /**
     * @return name used in {@code /judicator import <name>}
     */
    String name();

    ImportResult read(ImportContext context) throws Exception;
}
