package com.openmc.judicator.features;

import com.openmc.judicator.testing.FakePlayer;
import com.openmc.judicator.testing.TestServer;
import com.openmc.judicator.testing.TestServer.Outcome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * per-player-language: each player gets the messages in the language of their game client (en, es, pt); everyone
 * else, and every screen shown before the client says its language, gets the server language.
 */
class PlayerLanguageTest {

    @TempDir
    Path folder;

    private TestServer server;

    @AfterEach
    void stop() {
        if (server != null) server.close();
    }

    private TestServer perPlayer() {
        return TestServer.in(folder).config("per-player-language: false", "per-player-language: true").start();
    }

    @Test
    void announcement_isBuiltForEachRecipient() {
        server = perPlayer();
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1").locale("pt_BR"));
        final FakePlayer ana = server.online(new FakePlayer("Ana", "10.0.0.2").locale("pt_br"));
        final FakePlayer juan = server.online(new FakePlayer("Juan", "10.0.0.3").locale("es_ES"));
        final FakePlayer bob = server.online(new FakePlayer("Bob", "10.0.0.4").locale("en_US"));

        server.command(server.console, "ban Steve xray");

        assertTrue(ana.received("O usuário Steve foi banido por Console."), ana.messages().toString());
        assertTrue(juan.received("El usuario Steve fue baneado por Console."), juan.messages().toString());
        assertTrue(bob.received("User Steve was banned by Console."), bob.messages().toString());
        assertTrue(steve.kickMessage().contains("Você foi banido do servidor."), steve.kickMessage());
    }

    @Test
    void staffMember_getsPromptUsageAndNotificationsInTheirLanguage() {
        server = perPlayer();
        server.online(new FakePlayer("Steve", "10.0.0.1"));
        final FakePlayer mod = server.online(new FakePlayer("Mod", "10.0.0.2", "judicator.ban", "judicator.notify").locale("pt_BR"));

        server.command(mod, "ban");
        assertTrue(mod.received("Comando incorreto. Use: /ban (jogador) [motivo]."), mod.messages().toString());

        server.command(mod, "ban Steve xray");
        assertEquals(Outcome.TAKEN_BY_PROMPT, server.chat(mod, "confirmar"));
        assertTrue(mod.received("A punição foi aplicada com sucesso ao jogador."), mod.messages().toString());
        assertTrue(mod.received("[Staff] Mod aplicou BAN em Steve: xray"), mod.messages().toString());
    }

    @Test
    void staffMember_canStillConfirmWithTheServerLanguageWord() {
        server = perPlayer();
        server.online(new FakePlayer("Steve", "10.0.0.1"));
        final FakePlayer mod = server.online(new FakePlayer("Mod", "10.0.0.2", "judicator.ban").locale("pt_BR"));

        server.command(mod, "ban Steve xray");
        server.chat(mod, "confirm");

        assertTrue(mod.received("A punição foi aplicada com sucesso ao jogador."), mod.messages().toString());
    }

    @Test
    void unknownLanguageAndConsole_getTheServerLanguage() {
        server = perPlayer();
        final FakePlayer hans = server.online(new FakePlayer("Hans", "10.0.0.5").locale("de_DE"));
        final FakePlayer noLocale = server.online(new FakePlayer("Quiet", "10.0.0.6"));
        server.online(new FakePlayer("Steve", "10.0.0.1"));

        server.command(server.console, "ban");
        server.command(server.console, "ban Steve xray");

        assertTrue(server.console.received("Incorrect command. Use: /ban (player) [reason]."));
        assertTrue(hans.received("User Steve was banned by Console."));
        assertTrue(noLocale.received("User Steve was banned by Console."));
    }

    @Test
    void loginScreen_usesTheServerLanguage() {
        server = perPlayer();
        server.online(new FakePlayer("Steve", "10.0.0.1").locale("pt_BR"));
        server.command(server.console, "ban Steve xray");

        final Optional<String> kick = server.join(new FakePlayer("Steve", "10.0.0.1").locale("pt_BR"));

        assertTrue(kick.isPresent());
        assertTrue(kick.get().contains("You have been banned from the server."), kick.get());
    }

    @Test
    void off_everyoneGetsTheServerLanguage() {
        server = TestServer.in(folder).start();
        final FakePlayer ana = server.online(new FakePlayer("Ana", "10.0.0.2").locale("pt_BR"));
        server.online(new FakePlayer("Steve", "10.0.0.1"));

        server.command(server.console, "ban Steve xray");

        assertTrue(ana.received("User Steve was banned by Console."), ana.messages().toString());
        assertFalse(ana.received("foi banido"));
    }
}
