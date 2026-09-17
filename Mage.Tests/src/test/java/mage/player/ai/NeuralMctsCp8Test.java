package mage.player.ai;

import com.sun.net.httpserver.HttpServer;
import mage.constants.*;
import mage.game.Game;
import mage.players.Player;
import org.json.JSONObject;
import org.junit.Test;
import org.mage.test.serverside.base.CardTestPlayerBase;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/** Exercise CP8's actual live priority path, not an argmax search ablation. */
public class NeuralMctsCp8Test extends CardTestPlayerBase {
    static class Probe extends ComputerPlayerNeuralMCTS {
        int searches;
        boolean enabled = true;
        Probe(Player original) {
            super(original.getName(), RangeOfInfluence.ONE, 8);
            // Test-only injection into an existing scripted engine seat.
            try {
                java.lang.reflect.Field id = mage.players.PlayerImpl.class.getDeclaredField("playerId");
                id.setAccessible(true);
                id.set(this, original.getId());
            } catch (ReflectiveOperationException exc) { throw new AssertionError(exc); }
            restore(original.getRealPlayer());
        }
        @Override protected boolean searchEnabled() { return enabled; }
        @Override protected NeuralMctsState.Move search(Game game, MCTSPlayer.NextAction kind) {
            searches++;
            throw new IllegalStateException("injected search failure");
        }
    }

    @Test public void forcedPassNeverSearchesOrCallsCp8AndCopiesDoNotShareActionCache() throws Exception {
        addCard(Zone.LIBRARY, playerA, "Mountain", 60);
        addCard(Zone.LIBRARY, playerB, "Forest", 60);
        String previousLog = System.getProperty("neuralMcts.log");
        System.setProperty("neuralMcts.log", Files.createTempFile("mcts-forced-", ".jsonl").toString());
        try {
            runCode("forced", 1, PhaseStep.PRECOMBAT_MAIN, playerA, (info, player, game) -> {
                Game copy = game.createSimulationForAI();
                Probe probe = new Probe(copy.getPlayer(player.getId()));
                copy.getState().getPlayers().put(probe.getId(), probe);
                assertTrue(probe.priority(copy));
                assertEquals(0, probe.searches);
                assertEquals("forced", probe.priorityOwner);
                assertEquals(1, probe.priorityCandidates);
                probe.actionCache.add("live");
                ComputerPlayerNeuralMCTS cloned = probe.copy();
                cloned.actionCache.add("simulation");
                assertFalse(probe.actionCache.contains("simulation"));
            });
            setStopAt(1, PhaseStep.POSTCOMBAT_MAIN);
            execute();
        } finally { restoreProperty("neuralMcts.log", previousLog); }
    }

    @Test public void searchFailureAndDisabledSearchUseActualCp8HttpButStrictAborts() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/choose_from_all_actions", exchange -> {
            JSONObject payload = new JSONObject(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            assertEquals("research", payload.getString("strategyId"));
            assertFalse(payload.getBoolean("logTrajectory"));
            requests.incrementAndGet();
            byte[] body = "{\"chosen_idx\":0,\"reason\":\"frozen_cp8_test\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        String[] keys = {"magellmfast.url", "magellmfast.strategyId", "MAGELLM_STRATEGY",
                "magellmfast.logTrajectory", "neuralMcts.strict", "neuralMcts.log"};
        String[] old = new String[keys.length];
        for (int i = 0; i < keys.length; i++) old[i] = System.getProperty(keys[i]);
        System.setProperty(keys[0], "http://127.0.0.1:" + server.getAddress().getPort());
        System.setProperty(keys[1], "research"); System.setProperty(keys[2], "rl");
        System.setProperty(keys[3], "false"); System.setProperty(keys[4], "false");
        System.setProperty(keys[5], Files.createTempFile("mcts-fallback-", ".jsonl").toString());
        addCard(Zone.HAND, playerA, "Mountain", 1);
        addCard(Zone.LIBRARY, playerA, "Mountain", 60);
        addCard(Zone.LIBRARY, playerB, "Forest", 60);
        try {
            runCode("fallback", 1, PhaseStep.PRECOMBAT_MAIN, playerA, (info, player, game) -> {
                for (boolean enabled : new boolean[]{true, false}) {
                    Game copy = game.createSimulationForAI();
                    Probe probe = new Probe(copy.getPlayer(player.getId()));
                    probe.enabled = enabled;
                    copy.getState().getPlayers().put(probe.getId(), probe);
                    assertTrue(probe.priority(copy));
                    assertEquals(enabled ? 1 : 0, probe.searches);
                    assertEquals(enabled ? "cp8_fallback" : "cp8", probe.priorityOwner);
                }
                assertEquals(2, requests.get());
                System.setProperty("neuralMcts.strict", "true");
                Game copy = game.createSimulationForAI();
                Probe probe = new Probe(copy.getPlayer(player.getId()));
                copy.getState().getPlayers().put(probe.getId(), probe);
                assertThrows(NeuralMctsState.SearchAbort.class, () -> probe.priority(copy));
                assertEquals(2, requests.get());
            });
            setStopAt(1, PhaseStep.POSTCOMBAT_MAIN);
            execute();
        } finally {
            server.stop(0);
            for (int i = 0; i < keys.length; i++) restoreProperty(keys[i], old[i]);
        }
    }

    private static void restoreProperty(String key, String value) {
        if (value == null) System.clearProperty(key); else System.setProperty(key, value);
    }
}
