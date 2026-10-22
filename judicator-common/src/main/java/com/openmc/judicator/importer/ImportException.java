package com.openmc.judicator.importer;

/**
 * A problem the server owner has to fix (configuration, missing file or driver); the message is shown as is.
 */
public class ImportException extends Exception {

    public ImportException(String message) {
        super(message);
    }

    public ImportException(String message, Throwable cause) {
        super(message, cause);
    }
}
