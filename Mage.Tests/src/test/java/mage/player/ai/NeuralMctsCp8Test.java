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
import java.util.*;
import mage.util.RandomUtil;

import static org.junit.Assert.*;

/** Exercise CP8's actual live priority path, not an argmax search ablation. */
public class NeuralMctsCp8Test extends CardTestPlayerBase {
    static class Probe extends ComputerPlayerNeuralMCTS {
        int searches;
        boolean enabled = true;
        boolean failSearch = true;
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
            if (!failSearch) return super.search(game, kind);
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

    @Test public void successfulSearchWritesCorrelatedDecisionAndSnapshot() throws Exception {
        AtomicInteger evaluations = new AtomicInteger(), proposals = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/mcts/evaluate_batch", exchange -> {
            org.json.JSONArray positions = new JSONObject(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8))
                    .getJSONArray("positions"), results = new org.json.JSONArray();
            for (Object item : positions) {
                JSONObject position = (JSONObject) item;
                assertFalse(position.getBoolean("log_trajectory"));
                org.json.JSONArray actions = position.getJSONArray("all_actions"), ids = new org.json.JSONArray(), priors = new org.json.JSONArray();
                for (Object action : actions) {
                    ids.put(((JSONObject) action).getString("fingerprint"));
                    priors.put(1.0 / actions.length());
                }
                results.put(new JSONObject().put("checkpoint_id", "arm20_step17176").put("representation_version", "stable-relations-v1")
                        .put("value_player_id", position.getString("player_id")).put("value_win_prob", .75).put("value_signed", .5)
                        .put("action_fingerprints", ids).put("priors", priors));
            }
            evaluations.addAndGet(positions.length());
            byte[] body = new JSONObject().put("results", results).toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.createContext("/choose_from_all_actions", exchange -> {
            JSONObject payload = new JSONObject(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            assertFalse(payload.getBoolean("logTrajectory"));
            proposals.incrementAndGet();
            byte[] body = "{\"chosen_idx\":1,\"reason\":\"original_cp8_land\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        java.nio.file.Path directory = Files.createTempDirectory("mcts-success-diagnostics-");
        String url = "http://127.0.0.1:" + server.getAddress().getPort();
        Map<String, String> settings = new LinkedHashMap<>();
        settings.put("magellmfast.url", url); settings.put("neuralMcts.url", url);
        settings.put("magellmfast.strategyId", "research"); settings.put("MAGELLM_STRATEGY", "rl");
        settings.put("magellmfast.logTrajectory", "false"); settings.put("magellm.frozenBenchmark", "true");
        settings.put("neuralMcts.hiddenStateMode", "oracle"); settings.put("neuralMcts.diagnostics", "decisions");
        settings.put("neuralMcts.decisionLog", directory.resolve("decisions.jsonl").toString());
        settings.put("neuralMcts.detailDir", directory.resolve("details").toString());
        settings.put("neuralMcts.log", directory.resolve("search.jsonl").toString());
        settings.put("neuralMcts.strict", "true"); settings.put("MAGELLM_STRICT_DECISIONS", "1");
        settings.put("neuralMcts.simulations", "16"); settings.put("neuralMcts.inferenceBatchSize", "2");
        settings.put("neuralMcts.maxDepth", "2"); settings.put("neuralMcts.maxThinkTimeSeconds", "30");
        Map<String, String> previous = new LinkedHashMap<>();
        settings.forEach((key, value) -> { previous.put(key, System.getProperty(key)); System.setProperty(key, value); });
        addCard(Zone.HAND, playerA, "Mountain", 1);
        addCard(Zone.LIBRARY, playerA, "Mountain", 60); addCard(Zone.LIBRARY, playerB, "Forest", 60);
        try {
            runCode("successful search diagnostics", 1, PhaseStep.PRECOMBAT_MAIN, playerA, (info, player, game) -> {
                Game copy = game.copy();
                assertFalse(copy.isSimulation());
                Probe searcher = new Probe(copy.getPlayer(player.getId()));
                searcher.failSearch = false;
                copy.getState().getPlayers().put(searcher.getId(), searcher);
                assertTrue(searcher.priority(copy));
                assertEquals(1, searcher.searches);
                try {
                    JSONObject decision = new JSONObject(Files.readString(directory.resolve("decisions.jsonl")).trim());
                    assertFalse(decision.toString(), decision.has("diagnostic_failure"));
                    assertFalse(decision.toString(), decision.has("failure"));
                    assertEquals("mcts", decision.getString("owner"));
                    assertEquals("executed", decision.getString("execution_status"));
                    assertEquals(16, decision.getInt("simulations"));
                    assertEquals(copy.getId().toString(), decision.getString("game_id"));
                    assertEquals(searcher.getId().toString(), decision.getString("player_id"));
                    assertEquals(searcher.getId().toString(), decision.getString("value_player_id"));
                    for (String receipt : new String[]{"live_state_unchanged", "live_rng_unchanged",
                            "search_live_state_unchanged", "search_live_rng_unchanged"}) assertTrue(receipt, decision.getBoolean(receipt));
                    assertEquals(evaluations.get(), decision.getInt("evaluated_positions"));
                    assertEquals(1, proposals.get());
                    assertTrue(decision.has("cp8"));
                    JSONObject detail = new JSONObject(Files.readString(directory.resolve("details").resolve(decision.getString("detail_file"))));
                    JSONObject summary = new JSONObject(Files.readString(directory.resolve("search.jsonl")).trim());
                    for (String key : new String[]{"game_id", "decision_id"}) {
                        assertEquals(decision.getString(key), detail.getString(key));
                        assertEquals(decision.getString(key), summary.getString(key));
                    }
                    assertTrue(detail.getBoolean("privileged_oracle"));
                    assertFalse(detail.getBoolean("training_eligible"));
                    assertTrue(detail.has("root"));
                    assertTrue(detail.getJSONArray("paths").getJSONObject(0).getBoolean("available"));
                } catch (java.io.IOException exc) { throw new AssertionError(exc); }
            });
            setStopAt(1, PhaseStep.POSTCOMBAT_MAIN); execute();
        } finally {
            server.stop(0);
            previous.forEach(NeuralMctsCp8Test::restoreProperty);
        }
    }

    @Test public void diagnosticProbeUsesOriginalCp8PathWithoutMutationOrRecursiveSearch() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        AtomicInteger evaluations = new AtomicInteger();
        java.util.concurrent.atomic.AtomicBoolean failProbe = new java.util.concurrent.atomic.AtomicBoolean();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/mcts/evaluate_batch", exchange -> {
            JSONObject request = new JSONObject(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8))
                    .getJSONArray("positions").getJSONObject(0);
            org.json.JSONArray actions = request.getJSONArray("all_actions"), ids = new org.json.JSONArray(), priors = new org.json.JSONArray();
            for (int i = 0; i < actions.length(); i++) { ids.put(actions.getJSONObject(i).get("fingerprint")); priors.put(1.0 / actions.length()); }
            JSONObject value = new JSONObject().put("checkpoint_id", "arm20_step17176").put("representation_version", "stable-relations-v1")
                    .put("value_player_id", request.get("player_id")).put("value_win_prob", .75).put("value_signed", .5)
                    .put("action_fingerprints", ids).put("priors", priors);
            byte[] body = new JSONObject().put("results", new org.json.JSONArray().put(value)).toString().getBytes(StandardCharsets.UTF_8);
            evaluations.incrementAndGet();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.createContext("/choose_from_all_actions", exchange -> {
            JSONObject payload = new JSONObject(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            assertFalse(payload.getBoolean("logTrajectory"));
            assertEquals("research", payload.getString("strategyId"));
            requests.incrementAndGet();
            byte[] body = "{\"chosen_idx\":1,\"reason\":\"original_cp8_land\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(failProbe.get() ? 503 : 200, body.length);
            exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        String[] keys = {"magellmfast.url", "magellmfast.strategyId", "MAGELLM_STRATEGY", "magellmfast.logTrajectory",
                "magellm.frozenBenchmark", "neuralMcts.hiddenStateMode", "neuralMcts.decisionLog", "neuralMcts.detailDir", "neuralMcts.url", "MAGELLM_STRICT_DECISIONS"};
        String[] old = Arrays.stream(keys).map(System::getProperty).toArray(String[]::new);
        java.nio.file.Path directory = Files.createTempDirectory("mcts-diagnostics-");
        System.setProperty(keys[0], "http://127.0.0.1:" + server.getAddress().getPort());
        System.setProperty(keys[1], "research"); System.setProperty(keys[2], "rl");
        System.setProperty(keys[3], "false"); System.setProperty(keys[4], "true"); System.setProperty(keys[5], "oracle");
        System.setProperty(keys[6], directory.resolve("decisions.jsonl").toString()); System.setProperty(keys[7], directory.resolve("details").toString());
        System.setProperty(keys[8], System.getProperty(keys[0]));
        System.setProperty(keys[9], "1");
        addCard(Zone.HAND, playerA, "Mountain", 1);
        addCard(Zone.LIBRARY, playerA, "Mountain", 60); addCard(Zone.LIBRARY, playerB, "Forest", 60);
        try {
            runCode("probe original cp8", 1, PhaseStep.PRECOMBAT_MAIN, playerA, (info, player, game) -> {
                Game copy = game.createSimulationForAI();
                Probe original = new Probe(copy.getPlayer(player.getId()));
                copy.getState().getPlayers().put(original.getId(), original);
                String before = NeuralMctsDiagnostics.stateReceipt(copy), rng = RandomUtil.liveStreamsReceipt();
                NeuralMctsDiagnostics diagnostic = new NeuralMctsDiagnostics(copy, player.getId(), 1, MCTSPlayer.NextAction.PRIORITY);
                diagnostic.attempt(() -> diagnostic.probe(copy, original, MCTSPlayer.NextAction.PRIORITY));
                assertFalse(diagnostic.event.toString(), diagnostic.event.has("diagnostic_failure"));
                assertEquals(before, NeuralMctsDiagnostics.stateReceipt(copy));
                assertEquals(rng, RandomUtil.liveStreamsReceipt());
                assertEquals(0, original.searches);
                Game reference = copy.createSimulationForAI();
                NeuralMctsDiagnostics.Probe actual = new NeuralMctsDiagnostics.Probe(original);
                reference.getState().getPlayers().put(actual.getId(), actual);
                try (RandomUtil.RandomScope scope = RandomUtil.searchScope(0x435038L + 1)) { actual.priority(reference); }
                assertEquals(actual.choice.fingerprint, diagnostic.proposal.fingerprint);
                assertEquals(2, requests.get());
                diagnostic.selected = diagnostic.proposal;
                diagnostic.finish(copy, "mcts", 2);
                try {
                    JSONObject written = new JSONObject(Files.readString(directory.resolve("decisions.jsonl")).trim());
                    assertFalse(written.toString(), written.has("diagnostic_failure"));
                    assertEquals("unmapped", written.getJSONObject("cp8").getString("mapping"));
                    assertTrue(written.getJSONObject("cp8").isNull("q"));
                    assertTrue(Files.isRegularFile(directory.resolve("details").resolve(written.getString("detail_file"))));
                } catch (java.io.IOException exc) { throw new AssertionError(exc); }
                reference.getBattlefield().getAllActivePermanents(player.getId()).forEach(permanent -> permanent.setTapped(true));
                NeuralMctsDiagnostics control = new NeuralMctsDiagnostics(reference, player.getId(), 2, MCTSPlayer.NextAction.PRIORITY);
                // Model the interface boundary where a genuine original CP8 menu
                // has two choices but only Pass survives the search's legal probes.
                control.attempt(() -> control.evaluateControl(reference, player.getId(), MCTSPlayer.NextAction.PRIORITY,
                        new DecisionHandler(System.getProperty("neuralMcts.url")), 2));
                assertFalse(control.event.toString(), control.event.has("diagnostic_failure"));
                assertEquals(control.event.toString(), 1, control.event.getInt("root_candidate_count"));
                assertEquals(2, control.event.getInt("candidate_count"));
                assertEquals(.5, control.event.getDouble("root_value"), 1e-9);
                assertEquals(1, evaluations.get());
                failProbe.set(true);
                NeuralMctsDiagnostics failed = new NeuralMctsDiagnostics(copy, player.getId(), 3, MCTSPlayer.NextAction.PRIORITY);
                failed.selected = diagnostic.selected;
                String selectedKey = failed.selected.fingerprint;
                failed.attempt(() -> failed.probe(copy, original, MCTSPlayer.NextAction.PRIORITY));
                assertEquals("inference_failure", failed.event.getJSONObject("diagnostic_failure").getString("category"));
                assertEquals(selectedKey, failed.selected.fingerprint);
                assertNull(failed.proposal);
                assertEquals(before, NeuralMctsDiagnostics.stateReceipt(copy));
                assertTrue(failed.event.getBoolean("live_rng_unchanged"));
            });
            setStopAt(1, PhaseStep.POSTCOMBAT_MAIN); execute();
        } finally {
            server.stop(0);
            for (int i = 0; i < keys.length; i++) restoreProperty(keys[i], old[i]);
        }
    }
}
