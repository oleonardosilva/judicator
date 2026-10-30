package com.elless.judicator.testing;

import com.elless.judicator.menus.Menu;
import com.elless.judicator.menus.MenuItem;
import com.elless.judicator.menus.MenuRenderer;
import com.elless.judicator.menus.MenuService;
import com.elless.judicator.platform.JudicatorPlayer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Menus as a Spigot/Paper server shows them, without the inventory: the menu each player has open, clicks by slot or
 * by item name, and the close events an inventory sends (also when the next menu replaces it).
 */
public final class FakeMenus implements MenuRenderer {

    private final Map<UUID, Menu> open = new HashMap<>();
    private final Map<UUID, Question> questions = new HashMap<>();
    private final Map<UUID, PendingForm> forms = new HashMap<>();
    private final Supplier<UUID> runningFor;
    private Supplier<MenuService> service = () -> null;
    private boolean dialogs;

    /**
     * @param runningFor the player whose thread runs the current task: a menu is only shown on its viewer's (Folia)
     */
    FakeMenus(Supplier<UUID> runningFor) {
        this.runningFor = runningFor;
    }

    private record Question(String label, boolean optional, Consumer<String> onAnswer, Runnable onCancel) {
    }

    private record PendingForm(MenuRenderer.Form form, Consumer<Map<String, String>> onSubmit, Runnable onCancel) {
    }

    @Override
    public boolean form(JudicatorPlayer viewer, MenuRenderer.Form form, Consumer<Map<String, String>> onSubmit, Runnable onCancel) {
        if (!dialogs) return false;
        open.remove(viewer.getUniqueId());
        forms.put(viewer.getUniqueId(), new PendingForm(form, onSubmit, onCancel));
        return true;
    }

    /**
     * The keys of the fields of the open form, in order, or an empty list.
     */
    public List<String> formFields(JudicatorPlayer viewer) {
        final PendingForm pending = forms.get(viewer.getUniqueId());
        return pending == null ? List.of() : pending.form().fields().stream().map(MenuRenderer.Field::key).toList();
    }

    /**
     * Submits the open form: the given answers, the other fields as they were shown.
     */
    public void submit(JudicatorPlayer viewer, Map<String, String> answers) {
        final PendingForm pending = forms.remove(viewer.getUniqueId());
        if (pending == null) throw new AssertionError(viewer.getName() + " has no form open");
        final Map<String, String> all = new HashMap<>();
        for (MenuRenderer.Field field : pending.form().fields()) {
            if (field instanceof MenuRenderer.Choice choice) all.put(choice.key(), choice.selected());
            if (field instanceof MenuRenderer.Text text) all.put(text.key(), text.initial());
        }
        all.putAll(answers);
        pending.onSubmit().accept(all);
    }

    /**
     * Like dialogs (Paper, 1.21.6+ clients): questions are asked on a screen with a text field instead of the chat.
     */
    public FakeMenus dialogs() {
        this.dialogs = true;
        return this;
    }

    @Override
    public boolean ask(JudicatorPlayer viewer, MenuRenderer.Question question, Consumer<String> onAnswer, Runnable onCancel) {
        if (!dialogs) return false;
        // the question replaces the dialog on screen; a dialog sends no close event
        open.remove(viewer.getUniqueId());
        questions.put(viewer.getUniqueId(), new Question(question.label(), question.optional(), onAnswer, onCancel));
        return true;
    }

    /**
     * The label of the text field of the open question, or null.
     */
    public String question(JudicatorPlayer viewer) {
        final Question question = questions.get(viewer.getUniqueId());
        return question == null ? null : plain(question.label());
    }

    /**
     * Like the dialog: an empty answer cancels, unless the question is optional.
     */
    public void answer(JudicatorPlayer viewer, String text) {
        final Question question = questions.remove(viewer.getUniqueId());
        if (question == null) throw new AssertionError(viewer.getName() + " was asked nothing");
        if (text.isBlank() && !question.optional()) question.onCancel().run();
        else question.onAnswer().accept(text.isBlank() ? "" : text);
    }

    public void cancelQuestion(JudicatorPlayer viewer) {
        final Question question = questions.remove(viewer.getUniqueId());
        if (question == null) throw new AssertionError(viewer.getName() + " was asked nothing");
        question.onCancel().run();
    }

    void bind(Supplier<MenuService> service) {
        this.service = service;
    }

    @Override
    public void open(JudicatorPlayer viewer, Menu menu) {
        if (!viewer.getUniqueId().equals(runningFor.get())) {
            throw new AssertionError("the menu of " + viewer.getName() + " was shown outside of their thread (Platform.runFor)");
        }
        final Menu previous = open.put(viewer.getUniqueId(), menu);
        if (previous != null) service.get().closed(viewer.getUniqueId(), previous);
    }

    @Override
    public void close(JudicatorPlayer viewer) {
        final Menu previous = open.remove(viewer.getUniqueId());
        if (previous != null) service.get().closed(viewer.getUniqueId(), previous);
    }

    /**
     * The player closes the menu (Esc).
     */
    public void escape(JudicatorPlayer viewer) {
        close(viewer);
    }

    public Optional<Menu> of(JudicatorPlayer viewer) {
        return Optional.ofNullable(open.get(viewer.getUniqueId()));
    }

    public String title(JudicatorPlayer viewer) {
        return of(viewer).map(menu -> plain(menu.title())).orElse(null);
    }

    /**
     * The names of the items of the open menu, without colour codes, in slot order.
     */
    public List<String> names(JudicatorPlayer viewer) {
        return of(viewer).map(menu -> menu.items().values().stream().map(item -> plain(item.name())).toList()).orElse(List.of());
    }

    /**
     * The item with that name (without colour codes), or else the first whose name contains it.
     */
    public Optional<MenuItem> item(JudicatorPlayer viewer, String name) {
        return of(viewer).flatMap(menu -> menu.items().values().stream().filter(item -> plain(item.name()).equals(name)).findFirst()
                .or(() -> menu.items().values().stream().filter(item -> plain(item.name()).contains(name)).findFirst()));
    }

    public List<String> lore(JudicatorPlayer viewer, String name) {
        return item(viewer, name).map(item -> item.lore().stream().map(FakeMenus::plain).toList())
                .orElseThrow(() -> new AssertionError("no item '" + name + "' in " + names(viewer)));
    }

    public void click(JudicatorPlayer viewer, int slot) {
        final Menu menu = of(viewer).orElseThrow(() -> new AssertionError(viewer.getName() + " has no menu open"));
        service.get().click(viewer.getUniqueId(), menu, slot);
    }

    /**
     * Clicks the item with that name, or else the first whose name contains it.
     */
    public void click(JudicatorPlayer viewer, String name) {
        final Menu menu = of(viewer).orElseThrow(() -> new AssertionError(viewer.getName() + " has no menu open"));
        final int slot = menu.items().entrySet().stream()
                .filter(entry -> plain(entry.getValue().name()).equals(name))
                .map(Map.Entry::getKey).findFirst()
                .or(() -> menu.items().entrySet().stream()
                        .filter(entry -> plain(entry.getValue().name()).contains(name))
                        .map(Map.Entry::getKey).findFirst())
                .orElseThrow(() -> new AssertionError("no item '" + name + "' in " + names(viewer)));
        service.get().click(viewer.getUniqueId(), menu, slot);
    }

    static String plain(String text) {
        return text.replaceAll("§.", "");
    }
}
