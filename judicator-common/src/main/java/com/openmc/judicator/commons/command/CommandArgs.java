package com.openmc.judicator.commons.command;

import java.util.Optional;

public final class CommandArgs {

    private final String input;
    private int cursor;

    private CommandArgs(String input) {
        this.input = input;
        this.cursor = 0;
    }

    public static CommandArgs of(String[] rawArgs) {
        return new CommandArgs(String.join(" ", rawArgs).trim());
    }

    public static CommandArgs of(String rawInput) {
        return new CommandArgs(rawInput == null ? "" : rawInput.trim());
    }

    public Optional<String> word() {
        skipSpaces();
        if (cursor >= input.length()) return Optional.empty();
        final int start = cursor;
        while (cursor < input.length() && input.charAt(cursor) != ' ') cursor++;
        return Optional.of(input.substring(start, cursor));
    }

    public Optional<String> quotableString() {
        skipSpaces();
        if (cursor >= input.length()) return Optional.empty();
        final char first = input.charAt(cursor);
        if (first == '"' || first == '\'') {
            final int end = input.indexOf(first, cursor + 1);
            if (end > cursor) {
                final String value = input.substring(cursor + 1, end);
                cursor = end + 1;
                return Optional.of(value);
            }
        }
        return word();
    }

    public String remaining() {
        skipSpaces();
        if (cursor >= input.length()) return "";
        final String value = input.substring(cursor);
        cursor = input.length();
        return value.trim();
    }

    private void skipSpaces() {
        while (cursor < input.length() && input.charAt(cursor) == ' ') cursor++;
    }

}
