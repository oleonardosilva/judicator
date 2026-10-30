package com.elless.judicator.hytale;

import com.hypixel.hytale.logger.HytaleLogger;

import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * A java.util.logging logger writing to the plugin's Hytale logger, for {@code JulLoggerAdapter}: the core logs
 * through slf4j, which the Hytale server does not have.
 */
final class HytaleLog extends Logger {

    private final HytaleLogger delegate;

    HytaleLog(HytaleLogger delegate) {
        super("Judicator", null);
        this.delegate = delegate;
        setLevel(Level.INFO);
    }

    @Override
    public void log(LogRecord record) {
        if (!isLoggable(record.getLevel())) return;
        final HytaleLogger.Api api = delegate.at(record.getLevel());
        (record.getThrown() == null ? api : api.withCause(record.getThrown())).log("%s", record.getMessage());
    }
}
