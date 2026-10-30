package com.elless.judicator.features;

import com.elless.judicator.platform.PlatformType;
import com.elless.judicator.punish.Punishment;
import com.elless.judicator.punish.types.PunishType;
import com.elless.judicator.testing.FakeMenus;
import com.elless.judicator.testing.FakePlayer;
import com.elless.judicator.testing.TestServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The evidence a staff member attaches before a punishment applies: in the chat, where the confirm and cancel buttons
 * reach the server as commands (clients since 1.19.1 send no clicked chat), or on a dialog for 1.21.6+ clients on
 * Paper.
 */
class EvidencePromptTest {

    @TempDir
    Path folder;

    private TestServer server;

    @AfterEach
    void stop() {
        if (server != null) server.close();
    }

    private FakePlayer mod() {
        return server.online(new FakePlayer("Mod", "10.0.0.2", "judicator.ban"));
    }

    private List<Punishment> activeBans() {
        return server.punishments.findAllActiveByUsernameAndTypes("Steve", PunishType.BAN, PunishType.TEMPBAN);
    }

    @Test
    void chatButtons_runACommandTheClientKnows() {
        server = TestServer.in(folder).start();
        final FakePlayer mod = mod();
        server.online(new FakePlayer("Steve", "10.0.0.1"));

        server.command(mod, "ban Steve xray");

        assertTrue(mod.clicks().containsAll(List.of("/jprompt confirm", "/jprompt cancel")), mod.clicks().toString());
    }

    @Test
    void confirmButton_appliesWithTheEvidence() {
        server = TestServer.in(folder).start();
        final FakePlayer mod = mod();
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1"));

        server.command(mod, "ban Steve xray");
        server.chat(mod, "https://imgur.com/proof1");
        assertEquals(TestServer.Outcome.TAKEN_BY_PROMPT, server.playerCommand(mod, "/jprompt confirm"));

        assertEquals(List.of("https://imgur.com/proof1"), activeBans().get(0).getEvidences());
        assertTrue(steve.wasKicked());
        assertFalse(mod.received("Operation successfully canceled."));
    }

    @Test
    void cancelButton_cancels() {
        server = TestServer.in(folder).start();
        final FakePlayer mod = mod();
        server.online(new FakePlayer("Steve", "10.0.0.1"));

        server.command(mod, "ban Steve xray");
        assertEquals(TestServer.Outcome.TAKEN_BY_PROMPT, server.playerCommand(mod, "/jprompt cancel"));

        assertTrue(activeBans().isEmpty());
        assertTrue(mod.received("Operation successfully canceled."));
        assertEquals(TestServer.Outcome.ALLOWED, server.chat(mod, "confirm"), "the prompt is over");
    }

    @Test
    void promptWordSentAsACommand_answersThePrompt() {
        server = TestServer.in(folder).start();
        final FakePlayer mod = mod();
        server.online(new FakePlayer("Steve", "10.0.0.1"));

        server.command(mod, "ban Steve xray");
        assertEquals(TestServer.Outcome.TAKEN_BY_PROMPT, server.playerCommand(mod, "/confirm"));

        assertEquals(1, activeBans().size());
    }

    @Test
    void promptCommand_withoutAPromptSaysSo() {
        server = TestServer.in(folder).start();
        final FakePlayer mod = mod();

        server.command(mod, "jprompt confirm");

        assertTrue(mod.received("There is nothing to confirm or cancel."), mod.messages().toString());
    }

    @Test
    void promptCommand_isForWhoeverCanPunish() {
        server = TestServer.in(folder).start();
        final FakePlayer player = server.online(new FakePlayer("Player", "10.0.0.9"));

        assertFalse(server.command(player, "jprompt confirm"));
        assertTrue(server.command(mod(), "jprompt confirm"));
    }

    private FakeMenus startWithDialogs() {
        server = TestServer.in(folder).platform(PlatformType.BUKKIT).start();
        return server.platform.menus.dialogs();
    }

    @Test
    void dialog_asksForTheEvidenceInsteadOfTheChat() {
        final FakeMenus menus = startWithDialogs();
        final FakePlayer mod = mod();
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1"));

        server.command(mod, "ban Steve xray");

        assertEquals("Links of the evidence, separated by spaces (optional):", menus.question(mod));
        assertFalse(mod.received("Send the evidence one by one in chat."));
        assertEquals(TestServer.Outcome.ALLOWED, server.chat(mod, "hello"), "the chat is not taken");
        menus.answer(mod, "https://imgur.com/a  https://imgur.com/b ");

        assertEquals(List.of("https://imgur.com/a", "https://imgur.com/b"), activeBans().get(0).getEvidences());
        assertTrue(steve.wasKicked());
    }

    @Test
    void dialog_appliesWithoutEvidenceWhenLeftEmpty() {
        final FakeMenus menus = startWithDialogs();
        final FakePlayer mod = mod();
        server.online(new FakePlayer("Steve", "10.0.0.1"));

        server.command(mod, "ban Steve xray");
        menus.answer(mod, "");

        assertEquals(1, activeBans().size());
        assertTrue(activeBans().get(0).getEvidences().isEmpty());
    }

    @Test
    void dialog_cancelAppliesNothing() {
        final FakeMenus menus = startWithDialogs();
        final FakePlayer mod = mod();
        server.online(new FakePlayer("Steve", "10.0.0.1"));

        server.command(mod, "ban Steve xray");
        menus.cancelQuestion(mod);

        assertTrue(activeBans().isEmpty());
        assertTrue(mod.received("Operation successfully canceled."));
        assertNull(menus.question(mod));
    }

    @Test
    void withoutDialogs_bukkitKeepsTheChatPrompt() {
        server = TestServer.in(folder).platform(PlatformType.BUKKIT).start();
        final FakePlayer mod = mod();
        server.online(new FakePlayer("Steve", "10.0.0.1"));

        server.command(mod, "ban Steve xray");

        assertTrue(mod.received("Send the evidence one by one in chat."));
        assertNull(server.platform.menus.question(mod));
    }
}
