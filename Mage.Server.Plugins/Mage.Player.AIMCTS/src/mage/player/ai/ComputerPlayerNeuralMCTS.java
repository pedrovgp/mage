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
public class ComputerPlayerNeuralMCTS extends ComputerPlayer8 {
    private long decisions;
    private transient DecisionHandler serializer;
    public ComputerPlayerNeuralMCTS(String name, RangeOfInfluence range, int skill) { super(name, range, skill); }
    protected ComputerPlayerNeuralMCTS(ComputerPlayerNeuralMCTS other) { super(other); decisions = other.decisions; }
    @Override public ComputerPlayerNeuralMCTS copy() { return new ComputerPlayerNeuralMCTS(this); }

    protected NeuralMctsState.Move search(Game game, MCTSPlayer.NextAction kind) {
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
        long start = System.nanoTime(), deadline = start + (long) (seconds * 1_000_000_000L);
        long seed = Long.getLong("neuralMcts.gameSeed", Long.getLong("neuralMcts.seed", 42L)) + 104729L * decisions++;
        List<NeuralMctsSearch.State> worlds = new ArrayList<>();
        NeuralMctsState first = NeuralMctsState.root(game, playerId, kind, seed, deadline, checkpoint, serializer);
        if (first.moves().size() == 1) {
            record(context(game, kind).put("owner", "forced").put("fast_path", true)
                    .put("simulations", 0).put("evaluated_positions", 0).put("inference_batches", 0)
                    .put("chosen", first.moves().get(0).fingerprint).put("candidate_count", 1)
                    .put("elapsed_ms", (System.nanoTime() - start) / 1e6).put("fallback", false));
            return first.moves().get(0);
        }
        worlds.add(first);
        for (int i = 1; i < batch; i++) worlds.add(NeuralMctsState.root(game, playerId, kind, seed + i,
                deadline, checkpoint, serializer));
        NeuralMctsInferenceClient client = new NeuralMctsInferenceClient(System.getProperty("neuralMcts.url", "http://localhost:9310"), checkpoint);
        NeuralMctsSearch engine = new NeuralMctsSearch(client,
                Double.parseDouble(System.getProperty("neuralMcts.cPuct", "1.5")),
                Integer.getInteger("neuralMcts.maxDepth", 32));
        NeuralMctsSearch.Result result = engine.search(worlds, playerId, budget, deadline);
        record(result.diagnostics.put("checkpoint_id", checkpoint).put("chosen", result.action)
                .put("seed", seed).put("decision_type", kind).put("game_id", game.getId())
                .put("player_id", playerId).put("turn", game.getTurnNum()).put("step", game.getTurnStepType())
                .put("elapsed_ms", (System.nanoTime() - start) / 1e6).put("fallback", false)
                .put("owner", "mcts").put("candidate_count", first.moves().size())
                .put("rejected_root_candidates", first.rejectedCandidates)
                .put("hidden_state_mode", "known_deck_uniform"));
        for (NeuralMctsState.Move move : first.moves()) if (move.fingerprint.equals(result.action)) return move;
        throw new IllegalStateException("chosen action was not legal at root");
    }
    protected void failure(Game game, Throwable error) {
        boolean strict = Boolean.parseBoolean(System.getProperty("neuralMcts.strict", "false"));
        record(new JSONObject().put("game_id", game.getId()).put("failed", true)
                .put("fallback", !strict).put("fallback_owner", strict ? "none" : "cp8")
                .put("error", error.toString()));
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
    private JSONObject context(Game game, MCTSPlayer.NextAction kind) {
        return new JSONObject().put("game_id", game.getId()).put("player_id", playerId)
                .put("turn", game.getTurnNum()).put("step", game.getTurnStepType()).put("decision_type", kind);
    }
    protected boolean searchEnabled() {
        return !Boolean.getBoolean("neuralMcts.policyOnly")
                && Boolean.parseBoolean(System.getProperty("neuralMcts.enabled", "true"));
    }
    @Override protected void onForcedPriority(Game game) {
        record(context(game, MCTSPlayer.NextAction.PRIORITY).put("owner", "forced").put("fast_path", true)
                .put("candidate_count", 1).put("simulations", 0).put("evaluated_positions", 0)
                .put("inference_batches", 0).put("fallback", false));
    }
    @Override protected boolean tryPriorityOverride(Game game, List<Ability> allActions) {
        if (!searchEnabled()) return false;
        NeuralMctsState.Move move;
        try {
            move = search(game, MCTSPlayer.NextAction.PRIORITY);
            // Unsearched live cost/choice callbacks belong to CP8. Verify the
            // candidate with that SAME player on a disposable copy before commit.
            if (!move.legalPriority(game, playerId, move.activationSeed()))
                throw new IllegalStateException("selected action unavailable with CP8 auxiliary choices");
        } catch (Exception | NeuralMctsState.SearchAbort | NeuralMctsSearch.DeadlineExceeded exc) {
            priorityOwner = "cp8_fallback";
            failure(game, exc);
            return false; // CP8's existing llmPlay continues, without another priority event.
        }
        priorityOwner = "mcts";
        if (move.ability instanceof PassAbility) pass(game);
        else {
            this.actions = new LinkedList<>();
            this.actions.add(move.ability.copy());
            int before = getActionsTaken();
            act(game); // CP8/CP6 activation, callbacks and anti-stall accounting.
            if (!game.isSimulation() && getActionsTaken() == before)
                throw new NeuralMctsState.SearchAbort("selected action failed during live CP8 activation");
        }
        return true; // never retry/fall back after a partially applied live action.
    }
    @Override public void selectAttackers(Game game, UUID attackingPlayerId) {
        if (!searchEnabled() || !Boolean.parseBoolean(System.getProperty("neuralMcts.searchCombat", "true"))) {
            super.selectAttackers(game, attackingPlayerId); return;
        }
        NeuralMctsState.Move move;
        try { move = search(game, MCTSPlayer.NextAction.SELECT_ATTACKERS); }
        catch (Exception | NeuralMctsState.SearchAbort | NeuralMctsSearch.DeadlineExceeded exc) { failure(game, exc); super.selectAttackers(game, attackingPlayerId); return; }
        move.apply(game, playerId);
    }
    @Override public void selectBlockers(Ability source, Game game, UUID defendingPlayerId) {
        if (!searchEnabled() || !Boolean.parseBoolean(System.getProperty("neuralMcts.searchCombat", "true"))) {
            super.selectBlockers(source, game, defendingPlayerId); return;
        }
        NeuralMctsState.Move move;
        try { move = search(game, MCTSPlayer.NextAction.SELECT_BLOCKERS); }
        catch (Exception | NeuralMctsState.SearchAbort | NeuralMctsSearch.DeadlineExceeded exc) { failure(game, exc); super.selectBlockers(source, game, defendingPlayerId); return; }
        move.apply(game, playerId);
    }
}
