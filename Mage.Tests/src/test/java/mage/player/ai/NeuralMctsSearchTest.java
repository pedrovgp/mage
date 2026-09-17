package mage.player.ai;

import mage.util.RandomUtil;
import org.json.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class NeuralMctsSearchTest {
    private static final UUID ROOT = new UUID(0, 1), OPP = new UUID(0, 2);
    private static final long DEADLINE = Long.MAX_VALUE;
    static class State implements NeuralMctsSearch.State {
        final String id; final UUID actor; final Double terminal; final double value;
        final State[] children;
        State(String id, UUID actor, Double terminal, double value, State... children) {
            this.id = id; this.actor = actor; this.terminal = terminal; this.value = value; this.children = children;
        }
        public UUID actor() { return actor; }
        public Double terminalValue() { return terminal; }
        public List<String> actions() {
            List<String> out = new ArrayList<>();
            for (State child : children) out.add(child.id);
            return out;
        }
        public State next(int action) { return children[action]; }
        public JSONObject request() { return new JSONObject().put("id", id).put("value", value).put("n", children.length); }
    }
    static State terminal(String id, double value) { return new State(id, ROOT, value, 0); }
    static class Evaluator implements NeuralMctsSearch.Evaluator {
        final List<String> visited = new ArrayList<>();
        int calls;
        public List<NeuralMctsSearch.Evaluation> evaluate(List<JSONObject> positions, long deadline) {
            calls++;
            List<NeuralMctsSearch.Evaluation> out = new ArrayList<>();
            for (JSONObject p : positions) {
                visited.add(p.getString("id"));
                double[] priors = new double[p.getInt("n")]; Arrays.fill(priors, 1.0 / priors.length);
                out.add(new NeuralMctsSearch.Evaluation(p.getDouble("value"), priors));
            }
            return out;
        }
    }
    @Test public void floatingPointScoreAndPerspective() {
        assertEquals(.25, NeuralMctsSearch.score(1, 4, 0, 5, 1.5, true), 1e-10);
        assertEquals(-.25, NeuralMctsSearch.score(1, 4, 0, 5, 1.5, false), 1e-10);
    }
    @Test public void simulatedFutureValuesDriveChoiceNotRootValue() {
        State good = new State("good", ROOT, null, .8, terminal("later", 0));
        State bad = new State("bad", ROOT, null, -.8, terminal("later", 0));
        State root = new State("root", ROOT, null, -.99, good, bad);
        Evaluator evaluator = new Evaluator();
        NeuralMctsSearch.Result result = new NeuralMctsSearch(evaluator, 1.5, 1)
                .search(Arrays.asList(root, root), ROOT, 64, DEADLINE);
        assertEquals("good", result.action);
        assertTrue(evaluator.visited.containsAll(Arrays.asList("root", "good", "bad")));
        assertEquals(64, result.simulations);
        assertTrue(evaluator.calls > 1);
        assertEquals(2, result.diagnostics.getInt("determinizations"));
    }
    @Test public void opponentValuesAreConvertedByActorNotDepthParity() {
        State good = new State("good", OPP, null, -.8, terminal("later", 0));
        State bad = new State("bad", OPP, null, .8, terminal("later", 0));
        State root = new State("root", ROOT, null, 0, good, bad);
        assertEquals("good", new NeuralMctsSearch(new Evaluator(), 1.5, 1)
                .search(Collections.singletonList(root), ROOT, 64, DEADLINE).action);
    }
    @Test public void opponentChoosesRootLossAndTerminalOverridesNetwork() {
        State trap = new State("trap", OPP, null, -.9, terminal("win", 1), terminal("loss", -1));
        State root = new State("root", ROOT, null, 0, trap, terminal("draw", 0));
        assertEquals("draw", new NeuralMctsSearch(new Evaluator(), 1.5, 8)
                .search(Collections.singletonList(root), ROOT, 256, DEADLINE).action);
    }
    @Test public void deadlineKeepsCompletedEvidence() {
        State later = new State("later", ROOT, null, .4, terminal("end", 0));
        State root = new State("root", ROOT, null, 0, later);
        Evaluator evaluator = new Evaluator() {
            @Override public List<NeuralMctsSearch.Evaluation> evaluate(List<JSONObject> p, long d) {
                if (calls == 2) throw new NeuralMctsSearch.DeadlineExceeded();
                return super.evaluate(p, d);
            }
        };
        // A second nonterminal frontier forces the simulated deadline exception.
        later.children[0] = new State("deeper", ROOT, null, .5, terminal("end", 0));
        NeuralMctsSearch.Result result = new NeuralMctsSearch(evaluator, 1.5, 8)
                .search(Collections.singletonList(root), ROOT, 16, DEADLINE);
        assertEquals(1, result.simulations);
        assertTrue(result.diagnostics.getBoolean("deadline_reached"));
    }
    @Test(expected = IllegalStateException.class) public void serviceFailureNeverFabricatesValue() {
        State root = new State("root", ROOT, null, 0, terminal("end", 1));
        new NeuralMctsSearch((p, d) -> { throw new IllegalStateException("offline"); }, 1.5, 8)
                .search(Collections.singletonList(root), ROOT, 8, DEADLINE);
    }
    @Test(expected = IllegalStateException.class) public void rootOrderingMustMatchAcrossWorlds() {
        State first = new State("root", ROOT, null, 0, terminal("a", 1), terminal("b", -1));
        State second = new State("root", ROOT, null, 0, terminal("b", -1), terminal("a", 1));
        new NeuralMctsSearch(new Evaluator(), 1.5, 8).search(Arrays.asList(first, second), ROOT, 8, DEADLINE);
    }
    @Test public void rngScopesDoNotAdvanceLiveStreamsAndRestoreAfterException() {
        RandomUtil.setSeed(19); RandomUtil.registerPlayer(ROOT, 23);
        Random global = new Random(19), player = new Random(23);
        assertEquals(global.nextInt(), RandomUtil.nextInt());
        try (RandomUtil.RandomScope scope = RandomUtil.searchScope(31)) {
            Random expected = new Random(31);
            assertEquals(expected.nextInt(), RandomUtil.getRandom().nextInt());
            try (RandomUtil.RandomScope nested = RandomUtil.searchScope(77)) { RandomUtil.nextInt(); }
            assertEquals(expected.nextInt(100), RandomUtil.playerNextInt(ROOT, 100));
        }
        assertEquals(global.nextInt(), RandomUtil.nextInt());
        assertEquals(player.nextInt(100), RandomUtil.playerNextInt(ROOT, 100));
    }
    @Test public void responseRejectsPermutationAndWrongPerspective() {
        JSONObject request = new JSONObject().put("player_id", ROOT.toString())
                .put("all_actions", new JSONArray().put(new JSONObject().put("fingerprint", "a")));
        JSONObject response = new JSONObject().put("checkpoint_id", "frozen")
                .put("representation_version", "stable-relations-v1").put("value_player_id", ROOT.toString())
                .put("action_fingerprints", new JSONArray().put("a")).put("priors", new JSONArray().put(1.0))
                .put("value_win_prob", .75).put("value_signed", .5);
        assertEquals(.5, NeuralMctsInferenceClient.validate(request, response, "frozen").value, 1e-10);
        response.put("value_player_id", OPP.toString());
        assertThrows(IllegalStateException.class, () -> NeuralMctsInferenceClient.validate(request, response, "frozen"));
        response.put("value_player_id", ROOT.toString()).put("action_fingerprints", new JSONArray().put("b"));
        assertThrows(IllegalStateException.class, () -> NeuralMctsInferenceClient.validate(request, response, "frozen"));
    }
}
