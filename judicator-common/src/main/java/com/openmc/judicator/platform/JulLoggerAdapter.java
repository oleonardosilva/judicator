package com.openmc.judicator.platform;

import org.slf4j.helpers.FormattingTuple;
import org.slf4j.helpers.MarkerIgnoringBase;
import org.slf4j.helpers.MessageFormatter;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * An slf4j logger over the java.util.logging one Spigot/Paper and BungeeCord give each plugin.
 */
public final class JulLoggerAdapter extends MarkerIgnoringBase {

    private final transient Logger delegate;

    public JulLoggerAdapter(Logger delegate) {
        this.delegate = delegate;
        this.name = delegate.getName();
    }

    private void log(Level level, String format, Object[] arguments) {
        if (!delegate.isLoggable(level)) return;
        final FormattingTuple tuple = arguments == null
                ? new FormattingTuple(format)
                : MessageFormatter.arrayFormat(format, arguments);
        delegate.log(level, tuple.getMessage(), tuple.getThrowable());
    }

    @Override
    public boolean isTraceEnabled() {
        return delegate.isLoggable(Level.FINEST);
    }

    @Override
    public void trace(String msg) {
        log(Level.FINEST, msg, null);
    }

    @Override
    public void trace(String format, Object arg) {
        log(Level.FINEST, format, new Object[]{arg});
    }

    @Override
    public void trace(String format, Object arg1, Object arg2) {
        log(Level.FINEST, format, new Object[]{arg1, arg2});
    }

    @Override
    public void trace(String format, Object... arguments) {
        log(Level.FINEST, format, arguments);
    }

    @Override
    public void trace(String msg, Throwable t) {
        if (delegate.isLoggable(Level.FINEST)) delegate.log(Level.FINEST, msg, t);
    }

    @Override
    public boolean isDebugEnabled() {
        return delegate.isLoggable(Level.FINE);
    }

    @Override
    public void debug(String msg) {
        log(Level.FINE, msg, null);
    }

    @Override
    public void debug(String format, Object arg) {
        log(Level.FINE, format, new Object[]{arg});
    }

    @Override
    public void debug(String format, Object arg1, Object arg2) {
        log(Level.FINE, format, new Object[]{arg1, arg2});
    }

    @Override
    public void debug(String format, Object... arguments) {
        log(Level.FINE, format, arguments);
    }

    @Override
    public void debug(String msg, Throwable t) {
        if (delegate.isLoggable(Level.FINE)) delegate.log(Level.FINE, msg, t);
    }

    @Override
    public boolean isInfoEnabled() {
        return delegate.isLoggable(Level.INFO);
    }

    @Override
    public void info(String msg) {
        log(Level.INFO, msg, null);
    }

    @Override
    public void info(String format, Object arg) {
        log(Level.INFO, format, new Object[]{arg});
    }

    @Override
    public void info(String format, Object arg1, Object arg2) {
        log(Level.INFO, format, new Object[]{arg1, arg2});
    }

    @Override
    public void info(String format, Object... arguments) {
        log(Level.INFO, format, arguments);
    }

    @Override
    public void info(String msg, Throwable t) {
        if (delegate.isLoggable(Level.INFO)) delegate.log(Level.INFO, msg, t);
    }

    @Override
    public boolean isWarnEnabled() {
        return delegate.isLoggable(Level.WARNING);
    }

    @Override
    public void warn(String msg) {
        log(Level.WARNING, msg, null);
    }

    @Override
    public void warn(String format, Object arg) {
        log(Level.WARNING, format, new Object[]{arg});
    }

    @Override
    public void warn(String format, Object arg1, Object arg2) {
        log(Level.WARNING, format, new Object[]{arg1, arg2});
    }

    @Override
    public void warn(String format, Object... arguments) {
        log(Level.WARNING, format, arguments);
    }

    @Override
    public void warn(String msg, Throwable t) {
        if (delegate.isLoggable(Level.WARNING)) delegate.log(Level.WARNING, msg, t);
    }

    @Override
    public boolean isErrorEnabled() {
        return delegate.isLoggable(Level.SEVERE);
    }

    @Override
    public void error(String msg) {
        log(Level.SEVERE, msg, null);
    }

    @Override
    public void error(String format, Object arg) {
        log(Level.SEVERE, format, new Object[]{arg});
    }

    @Override
    public void error(String format, Object arg1, Object arg2) {
        log(Level.SEVERE, format, new Object[]{arg1, arg2});
    }

    @Override
    public void error(String format, Object... arguments) {
        log(Level.SEVERE, format, arguments);
    }

    @Override
    public void error(String msg, Throwable t) {
        if (delegate.isLoggable(Level.SEVERE)) delegate.log(Level.SEVERE, msg, t);
    }
}
