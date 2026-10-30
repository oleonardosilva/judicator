package com.elless.judicator.menus;

import com.elless.judicator.platform.JudicatorPlayer;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Draws menus for a platform that has them (Spigot/Paper inventories). Called on the game thread; the platform reports
 * clicks and closes back to {@link MenuService#click} and {@link MenuService#closed} with the {@link Menu} it showed.
 */
public interface MenuRenderer {

    /**
     * Shows the menu, replacing the one the player has open.
     */
    void open(JudicatorPlayer viewer, Menu menu);

    /**
     * Closes the menu the player has open, if any.
     */
    void close(JudicatorPlayer viewer);

    /**
     * Asks the viewer to type something (a reason, a note) on a screen of the renderer's own, when it has one (a
     * dialog with a text field). Answers arrive on the game thread.
     *
     * @return false when the renderer cannot ask: the menu then closes and the question goes to the chat
     */
    default boolean ask(JudicatorPlayer viewer, Question question, Consumer<String> onAnswer, Runnable onCancel) {
        return false;
    }

    /**
     * The texts of a question ({@code menu.question}), with § colour codes.
     *
     * @param optional an empty answer is an answer (the evidence of a punishment); otherwise it cancels
     */
    record Question(String title, String label, String confirm, String cancel, boolean optional) {

        public Question(String title, String label, String confirm, String cancel) {
            this(title, label, confirm, cancel, false);
        }
    }

    /**
     * Shows a form (several choices and texts at once, e.g. the order, filters and search of a list), when the
     * renderer has one (a dialog). The answers arrive on the game thread, by field key.
     *
     * @return false when the renderer has no forms: the list then asks only for the search, in the chat
     */
    default boolean form(JudicatorPlayer viewer, Form form, Consumer<Map<String, String>> onSubmit, Runnable onCancel) {
        return false;
    }

    /**
     * A form; texts with § colour codes.
     */
    record Form(String title, List<Field> fields, String confirm, String cancel) {
        public Form {
            fields = List.copyOf(fields);
        }
    }

    sealed interface Field permits Choice, Text {
        String key();
    }

    /**
     * One of several options; {@code selected} is the value chosen now.
     */
    record Choice(String key, String label, List<Option> options, String selected) implements Field {
        public Choice {
            options = List.copyOf(options);
        }
    }

    record Option(String value, String label) {
    }

    record Text(String key, String label, String initial) implements Field {
    }
}
