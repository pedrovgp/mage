package mage.player.ai;

import mage.abilities.Ability;
import mage.abilities.common.PassAbility;
import mage.constants.RangeOfInfluence;
import mage.game.Game;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Opt-in frozen neural search player. Existing random MCTS remains unchanged. */
public class ComputerPlayerNeuralMCTS extends ComputerPlayerMCTS {
    private long decisions;
    private transient DecisionHandler serializer;
    public ComputerPlayerNeuralMCTS(String name, RangeOfInfluence range, int skill) { super(name, range, skill); }
    protected ComputerPlayerNeuralMCTS(ComputerPlayerNeuralMCTS other) { super(other); decisions = other.decisions; }
    @Override public ComputerPlayerNeuralMCTS copy() { return new ComputerPlayerNeuralMCTS(this); }

    private NeuralMctsState.Move search(Game game, MCTSPlayer.NextAction kind) {
        if (!Boolean.getBoolean("neuralMcts.knownDecks"))
            throw new IllegalStateException("uniform determinization requires explicit neuralMcts.knownDecks=true; unknown decks need belief sampling");
        if (serializer == null) serializer = new DecisionHandler(System.getProperty("neuralMcts.url", "http://localhost:9310"));
        String checkpoint = System.getProperty("neuralMcts.checkpoint", "arm20_step17176");
        int budget = Integer.getInteger("neuralMcts.simulations", 256);
        int batch = Integer.getInteger("neuralMcts.inferenceBatchSize", 8);
        double seconds = Double.parseDouble(System.getProperty("neuralMcts.maxThinkTimeSeconds", "2"));
        if (budget < 1 || batch < 1 || batch > 32 || !Double.isFinite(seconds) || seconds <= 0 || seconds > 60)
            throw new IllegalArgumentException("invalid neural MCTS budget");
        batch = Math.min(batch, budget);
        boolean policyOnly = Boolean.getBoolean("neuralMcts.policyOnly");
        if (policyOnly) batch = 1;
        long start = System.nanoTime(), deadline = start + (long) (seconds * 1_000_000_000L);
        long seed = Long.getLong("neuralMcts.seed", 42L) + 104729L * decisions++;
        List<NeuralMctsSearch.State> worlds = new ArrayList<>();
        for (int i = 0; i < batch; i++) worlds.add(NeuralMctsState.root(game, playerId, kind, seed + i,
                deadline, checkpoint, serializer));
        NeuralMctsInferenceClient client = new NeuralMctsInferenceClient(System.getProperty("neuralMcts.url", "http://localhost:9310"), checkpoint);
        NeuralMctsSearch engine = new NeuralMctsSearch(client,
                Double.parseDouble(System.getProperty("neuralMcts.cPuct", "1.5")),
                Integer.getInteger("neuralMcts.maxDepth", 32));
        NeuralMctsState first = (NeuralMctsState) worlds.get(0);
        NeuralMctsSearch.Result result;
        if (policyOnly) {
            NeuralMctsSearch.Evaluation e = client.evaluate(Collections.singletonList(first.request()), deadline).get(0);
            int best = 0;
            for (int i = 1; i < e.priors.length; i++) if (e.priors[i] > e.priors[best]) best = i;
            result = new NeuralMctsSearch.Result(first.actions().get(best), 0,
                    new JSONObject().put("simulations", 0).put("evaluated_positions", 1)
                            .put("max_visited_depth", 0).put("inference_batches", 1));
        } else result = engine.search(worlds, playerId, budget, deadline);
        record(result.diagnostics.put("checkpoint_id", checkpoint).put("chosen", result.action)
                .put("seed", seed).put("decision_type", kind).put("game_id", game.getId())
                .put("player_id", playerId).put("turn", game.getTurnNum()).put("step", game.getTurnStepType())
                .put("elapsed_ms", (System.nanoTime() - start) / 1e6).put("fallback", false)
                .put("policy_only", policyOnly)
                .put("rejected_root_candidates", first.rejectedCandidates)
                .put("hidden_state_mode", "known_deck_uniform"));
        for (NeuralMctsState.Move move : first.moves()) if (move.fingerprint.equals(result.action)) return move;
        throw new IllegalStateException("chosen action was not legal at root");
    }
    private void failure(Game game, Throwable error) {
        boolean strict = Boolean.parseBoolean(System.getProperty("neuralMcts.strict", "true"));
        record(new JSONObject().put("game_id", game.getId()).put("failed", true)
                .put("fallback", !strict).put("error", error.toString()));
        if (strict)
            throw new NeuralMctsState.SearchAbort("neural MCTS failed: " + error);
    }
    private static synchronized void record(JSONObject event) {
        try {
            Path path = Paths.get(System.getProperty("neuralMcts.log", "logs/neural_mcts.jsonl"));
            if (path.getParent() != null) Files.createDirectories(path.getParent());
            Files.write(path, (event.toString() + "\n").getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception exc) { throw new IllegalStateException("cannot write search diagnostics", exc); }
    }
    @Override public boolean priority(Game game) {
        game.getState().setPriorityPlayerId(playerId);
        game.firePriorityEvent(playerId);
        NeuralMctsState.Move move;
        try { move = search(game, MCTSPlayer.NextAction.PRIORITY); }
        catch (Exception | NeuralMctsState.SearchAbort | NeuralMctsSearch.DeadlineExceeded exc) { failure(game, exc); return super.priority(game); }
        move.apply(game, playerId); // never fall back after a partially applied action
        return !(move.ability instanceof PassAbility);
    }
    @Override public void selectAttackers(Game game, UUID attackingPlayerId) {
        NeuralMctsState.Move move;
        try { move = search(game, MCTSPlayer.NextAction.SELECT_ATTACKERS); }
        catch (Exception | NeuralMctsState.SearchAbort | NeuralMctsSearch.DeadlineExceeded exc) { failure(game, exc); super.selectAttackers(game, attackingPlayerId); return; }
        move.apply(game, playerId);
    }
    @Override public void selectBlockers(Ability source, Game game, UUID defendingPlayerId) {
        NeuralMctsState.Move move;
        try { move = search(game, MCTSPlayer.NextAction.SELECT_BLOCKERS); }
        catch (Exception | NeuralMctsState.SearchAbort | NeuralMctsSearch.DeadlineExceeded exc) { failure(game, exc); super.selectBlockers(source, game, defendingPlayerId); return; }
        move.apply(game, playerId);
    }
}
