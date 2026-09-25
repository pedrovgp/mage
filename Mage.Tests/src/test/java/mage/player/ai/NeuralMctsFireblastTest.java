package mage.player.ai;

import com.sun.net.httpserver.HttpServer;
import mage.abilities.Ability;
import mage.constants.*;
import mage.game.Game;
import mage.game.match.MatchPlayer;
import mage.players.Player;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.mage.test.serverside.base.CardTestPlayerBase;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/** Actual CP8 payment routing, including the proposal and live search paths. */
public class NeuralMctsFireblastTest extends CardTestPlayerBase {
    static class FireblastPlayer extends NeuralMctsCp8Test.Probe {
        NeuralMctsState.Move fixedSearchMove;
        FireblastPlayer(Player player) {
            super(player);
            enabled = false;
            setMatchPlayer(new MatchPlayer(player.getMatchPlayer(), this));
        }
        @Override protected NeuralMctsState.Move search(Game game, MCTSPlayer.NextAction kind) {
            if (fixedSearchMove == null) return super.search(game, kind);
            return fixedSearchMove;
        }
    }

    @Test public void unpayableRegularCostIsExcludedFromControlProbeAndSearchExecution() throws Exception {
        fireblastFixture(2, true, null, true, true);
    }

    @Test public void payableRegularCostRemainsAModelChoice() throws Exception {
        fireblastFixture(6, false, null, false, false);
    }

    @Test public void regularCostCheckIncludesCostReduction() throws Exception {
        fireblastFixture(5, false, "Ruby Medallion", false, false);
    }

    @Test public void regularCostCheckIncludesCostIncrease() throws Exception {
        fireblastFixture(6, false, "Sphere of Resistance", true, false);
    }

    @Test public void multipleAlternativesRemainAModelChoice() throws Exception {
        fireblastFixture(2, true, "Omniscience", false, false);
    }

    private void fireblastFixture(int mountains, boolean tapped, String modifier,
                                 boolean sacrifice, boolean checkSearch) throws Exception {
        AtomicInteger costChoices = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            JSONObject payload = new JSONObject(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            assertFalse(payload.getBoolean("logTrajectory"));
            String path = exchange.getRequestURI().getPath();
            int index;
            if (path.equals("/choose_from_all_actions")) {
                index = 1; // Pass then Fireblast.
            } else if (path.equals("/choose_from_choices")) {
                costChoices.incrementAndGet();
                JSONArray choices = payload.getJSONArray("allChoices");
                index = choices.length() - 1;
                if ("Omniscience".equals(modifier)) {
                    assertEquals(2, choices.length());
                    for (Object choice : choices) assertFalse(choice.toString().startsWith("Cast with no alternative cost:"));
                } else {
                    assertTrue(choices.getString(index).startsWith("Cast with no alternative cost:"));
                }
            } else if (path.equals("/choose_targets")) {
                index = 0;
                JSONArray candidates = payload.getJSONArray("targetCandidateIds");
                String opponent = payload.getJSONObject("gameView").getJSONObject("opponentPlayer").getString("id");
                for (int i = 0; i < candidates.length(); i++) {
                    if (opponent.equals(candidates.getString(i))) index = i;
                }
            } else {
                throw new AssertionError("unexpected inference route " + path);
            }
            byte[] response = new JSONObject().put("chosen_idx", index).put("reason", "prefer_regular_payment")
                    .toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        Map<String, String> settings = new LinkedHashMap<>();
        settings.put("magellmfast.url", "http://127.0.0.1:" + server.getAddress().getPort());
        settings.put("magellmfast.strategyId", "research");
        settings.put("MAGELLM_STRATEGY", "rl");
        settings.put("magellmfast.logTrajectory", "false");
        settings.put("MAGELLM_STRICT_DECISIONS", "1");
        settings.put("magellm.frozenBenchmark", "true");
        settings.put("neuralMcts.diagnostics", "summary");
        Map<String, String> previous = new LinkedHashMap<>();
        settings.forEach((key, value) -> { previous.put(key, System.getProperty(key)); System.setProperty(key, value); });
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", mountains);
        addCard(Zone.HAND, playerA, "Fireblast", 1);
        if (modifier != null) addCard(Zone.BATTLEFIELD, playerA, modifier, 1);
        addCard(Zone.LIBRARY, playerA, "Mountain", 60);
        addCard(Zone.LIBRARY, playerB, "Forest", 60);
        try {
            runCode("Fireblast payment", 1, PhaseStep.PRECOMBAT_MAIN, playerA, (info, player, game) -> {
                Game control = game.copy();
                FireblastPlayer cp8 = new FireblastPlayer(control.getPlayer(player.getId()));
                control.getState().getPlayers().put(cp8.getId(), cp8);
                if (tapped) control.getBattlefield().getAllActivePermanents(player.getId())
                        .forEach(p -> p.setTapped(true));
                if (checkSearch) {
                    String before = NeuralMctsDiagnostics.stateReceipt(control);
                    NeuralMctsDiagnostics diagnostic = new NeuralMctsDiagnostics(control, player.getId(), 67, MCTSPlayer.NextAction.PRIORITY);
                    diagnostic.attempt(() -> diagnostic.probe(control, cp8, MCTSPlayer.NextAction.PRIORITY));
                    assertFalse(diagnostic.event.toString(), diagnostic.event.has("diagnostic_failure"));
                    assertNotNull(diagnostic.proposal);
                    assertEquals("Fireblast", control.getCard(diagnostic.proposal.ability.getSourceId()).getName());
                    assertTrue(diagnostic.event.getBoolean("live_rng_unchanged"));
                    assertTrue(diagnostic.event.getBoolean("live_state_unchanged"));
                    assertEquals(before, NeuralMctsDiagnostics.stateReceipt(control));

                    Ability action = control.getPlayer(player.getId()).getPlayable(control, false).stream()
                            .filter(a -> "Fireblast".equals(control.getObject(a.getSourceId()).getName()))
                            .findFirst().orElseThrow().copy();
                    action.getTargets().get(0).addTarget(playerB.getId(), action, control);
                    NeuralMctsState.Move move = new NeuralMctsState.Move(action, null, null);
                    assertTrue(move.legalPriority(control, player.getId(), move.activationSeed()));
                    assertEquals(before, NeuralMctsDiagnostics.stateReceipt(control));
                    Game searched = control.copy();
                    FireblastPlayer searcher = new FireblastPlayer(searched.getPlayer(player.getId()));
                    searched.getState().getPlayers().put(searcher.getId(), searcher);
                    searcher.enabled = true;
                    searcher.fixedSearchMove = move;
                    assertTrue(searcher.priority(searched));
                    assertEquals("mcts", searcher.priorityOwner);
                    assertEquals(1, searcher.getActionsTaken());
                    assertEquals(1, searched.getStack().size());
                    assertEquals(0, mountainCount(searched, player));

                    // Full-game benchmarks retain TestPlayer around CP8. The
                    // engine must forward payment context through that wrapper.
                    Game wrappedGame = control.createSimulationForAI();
                    org.mage.test.player.TestComputerPlayerNeuralMCTS wrappedCp8 =
                            new org.mage.test.player.TestComputerPlayerNeuralMCTS(player.getName(), RangeOfInfluence.ONE, 8);
                    try {
                        java.lang.reflect.Field id = mage.players.PlayerImpl.class.getDeclaredField("playerId");
                        id.setAccessible(true);
                        id.set(wrappedCp8, player.getId());
                    } catch (ReflectiveOperationException exc) { throw new AssertionError(exc); }
                    wrappedCp8.restore(wrappedGame.getPlayer(player.getId()).getRealPlayer());
                    wrappedCp8.setTestMode(true);
                    wrappedCp8.setMatchPlayer(new MatchPlayer(cp8.getMatchPlayer(), wrappedCp8));
                    org.mage.test.player.TestPlayer wrapper = new org.mage.test.player.TestPlayer(wrappedCp8);
                    wrapper.setAIPlayer(true);
                    wrappedGame.getState().getPlayers().put(wrapper.getId(), wrapper);
                    String previousEnabled = System.getProperty("neuralMcts.enabled");
                    System.setProperty("neuralMcts.enabled", "false");
                    try { wrapper.priority(wrappedGame); }
                    finally {
                        if (previousEnabled == null) System.clearProperty("neuralMcts.enabled");
                        else System.setProperty("neuralMcts.enabled", previousEnabled);
                    }
                    assertEquals("Wrapped CP8 must cast Fireblast", 1, wrappedGame.getStack().size());
                    assertEquals(0, mountainCount(wrappedGame, player));
                }
                assertTrue(cp8.priority(control));
                assertTrue("Fireblast must activate", cp8.priorityActivated);
                assertEquals(1, control.getStack().size());
                assertEquals(mountains - (sacrifice ? 2 : 0), mountainCount(control, player));
                assertEquals(sacrifice ? 0 : 1, costChoices.get());
            });
            setStopAt(1, PhaseStep.POSTCOMBAT_MAIN);
            execute();
        } finally {
            server.stop(0);
            previous.forEach((key, value) -> {
                if (value == null) System.clearProperty(key); else System.setProperty(key, value);
            });
        }
    }

    private static long mountainCount(Game game, Player player) {
        return game.getBattlefield().getAllActivePermanents(player.getId()).stream()
                .filter(p -> p.getName().equals("Mountain")).count();
    }
}
