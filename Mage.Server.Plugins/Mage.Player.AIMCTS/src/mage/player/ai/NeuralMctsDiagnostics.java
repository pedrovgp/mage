package mage.player.ai;

import mage.abilities.Ability;
import mage.abilities.common.PassAbility;
import mage.game.Game;
import mage.game.combat.CombatGroup;
import mage.game.stack.StackObject;
import mage.util.RandomUtil;
import org.json.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Disposable CP8 probes and local, non-training decision artifacts. No policy decisions here. */
final class NeuralMctsDiagnostics {
    final JSONObject event;
    final long started = System.nanoTime();
    long diagnosticNanos;
    final Game live;
    final long[] rpcStart = DecisionHandler.diagnosticRpcSnapshot();
    long diagnosticRpcNanos, diagnosticRpcCount;
    NeuralMctsState root;
    NeuralMctsSearch.Result result;
    NeuralMctsState.Move selected, proposal;
    final JSONObject detail = new JSONObject().put("schema_version", 1).put("diagnostic_only", true)
            .put("training_eligible", false);

    static boolean enabled(Game game) {
        return !game.isSimulation() && "decisions".equals(System.getProperty("neuralMcts.diagnostics", "summary"));
    }
    NeuralMctsDiagnostics(Game game, UUID player, long sequence, MCTSPlayer.NextAction kind) {
        live = game;
        boolean oracle = NeuralMctsState.oracleEnabled();
        if (!Boolean.getBoolean("magellm.frozenBenchmark") || !"false".equals(System.getProperty("magellmfast.logTrajectory")))
            throw new IllegalStateException("decision diagnostics require a frozen benchmark without training logging");
        event = new JSONObject().put("schema_version", 1).put("game_id", game.getId().toString()).put("player_id", player.toString())
                .put("decision_id", player + "_" + sequence).put("sequence", sequence)
                .put("turn", game.getTurnNum()).put("step", game.getTurnStepType()).put("decision_type", kind)
                .put("hidden_state_mode", oracle ? "oracle" : "known_deck_uniform").put("diagnostic_only", oracle)
                .put("value_player_id", player.toString()).put("execution_status", "pending").put("fallback", false)
                .put("inference_ms", 0).put("simulation_ms", 0).put("simulations", 0)
                .put("evaluated_positions", 0).put("evaluated_leaf_positions", 0).put("inference_batches", 0)
                .put("batch_sizes", new JSONArray()).put("root_value", JSONObject.NULL);
    }
    interface Work { void run() throws Exception; }
    void attempt(Work work) {
        long start = System.nanoTime();
        long[] rpcBefore = DecisionHandler.diagnosticRpcSnapshot();
        String stateBefore = null, rngBefore = null;
        Map<String, String> streamsBefore = null;
        try (RandomUtil.RandomScope scope = RandomUtil.searchScope(0x435038L + event.getLong("sequence"))) {
            stateBefore = stateReceipt(live); rngBefore = RandomUtil.liveStreamsReceipt();
            streamsBefore = RandomUtil.liveStreamReceipts();
            work.run();
        } catch (Exception | NeuralMctsState.SearchAbort | NeuralMctsSearch.DeadlineExceeded | StrictDecisionFailure exc) {
            event.put("diagnostic_failure", new JSONObject().put("category", category(exc)).put("error", exc.toString()));
        } finally {
            long[] rpcAfter = DecisionHandler.diagnosticRpcSnapshot();
            diagnosticRpcNanos += rpcAfter[0] - rpcBefore[0]; diagnosticRpcCount += rpcAfter[1] - rpcBefore[1];
            try {
                boolean rngUnchanged = rngBefore != null && rngBefore.equals(RandomUtil.liveStreamsReceipt());
                event.put("live_state_unchanged", event.optBoolean("live_state_unchanged", true) && stateBefore != null && stateBefore.equals(stateReceipt(live)))
                        .put("live_rng_unchanged", event.optBoolean("live_rng_unchanged", true) && rngUnchanged);
                if (!rngUnchanged && streamsBefore != null) event.append("rng_changes", new JSONObject()
                        .put("observer_thread", Thread.currentThread().getName())
                        .put("streams", new JSONObject(RandomUtil.liveStreamChanges(streamsBefore))));
            } catch (Exception exc) {
                event.put("diagnostic_failure", new JSONObject().put("category", "isolation_audit_failure").put("error", exc.toString()));
            }
            diagnosticNanos += System.nanoTime() - start;
        }
    }
    static String stateReceipt(Game game) {
        StringBuilder receipt = new StringBuilder(game.getState().getValue(true, game));
        game.getPlayers().values().forEach(p -> receipt.append(p.getId()).append(p.getHand()).append(p.getLibrary().getCardList()));
        return NeuralMctsState.hash(receipt.toString());
    }
    void acceptSearch(NeuralMctsState first, NeuralMctsSearch.Result searched, long searchNanos) {
        root = first; result = searched;
        event.put("candidate_count", first.moves().size()).put("root_candidate_count", first.moves().size())
                .put("search_proposed_action", searched.action);
        for (String key : searched.diagnostics.keySet()) event.put(key, searched.diagnostics.get(key));
        event.put("simulation_ms", Math.max(0, searchNanos / 1e6 - event.getDouble("inference_ms")));
    }
    void evaluateControl(Game game, UUID player, MCTSPlayer.NextAction kind, DecisionHandler serializer, int liveCandidates) {
        root = NeuralMctsState.root(game, player, kind, 0x435038L + event.getLong("sequence"),
                System.nanoTime() + 30_000_000_000L, System.getProperty("neuralMcts.checkpoint", "arm20_step17176"), serializer);
        event.put("root_candidate_count", root.moves().size())
                .put("candidate_count", liveCandidates >= 0 ? liveCandidates : root.moves().size());
        // CP8's original primary menu and the search's concrete legal variants
        // have different denominators. A genuine CP8 choice still gets a value.
        if (event.getInt("candidate_count") <= 1) return;
        NeuralMctsInferenceClient client = new NeuralMctsInferenceClient(System.getProperty("neuralMcts.url", "http://localhost:9310"), root.checkpoint);
        long start = System.nanoTime();
        NeuralMctsSearch.Evaluation evaluation = client.evaluate(Collections.singletonList(root.request()), root.deadline).get(0);
        event.put("diagnostic_inference_ms", (System.nanoTime() - start) / 1e6)
                .put("diagnostic_batch_sizes", new JSONArray().put(1)).put("root_value", evaluation.value);
        JSONArray actions = new JSONArray();
        for (int i = 0; i < root.moves().size(); i++) actions.put(root.moves().get(i).describe(root.game)
                .put("prior", evaluation.priors[i]).put("visits", JSONObject.NULL).put("q", JSONObject.NULL));
        event.put("actions", actions);
    }
    void probe(Game live, ComputerPlayer8 original, MCTSPlayer.NextAction kind) {
        Game copy = live.createSimulationForAI();
        Probe cp8 = new Probe(original);
        copy.getState().getPlayers().put(cp8.getId(), cp8);
        int errors = copy.getTotalErrorsCount();
        if (kind == MCTSPlayer.NextAction.PRIORITY) cp8.priority(copy);
        else if (kind == MCTSPlayer.NextAction.SELECT_ATTACKERS) cp8.selectAttackers(copy, cp8.getId());
        else cp8.selectBlockers(null, copy, cp8.getId());
        if (copy.getTotalErrorsCount() != errors) throw new IllegalStateException("CP8 probe engine error");
        proposal = kind == MCTSPlayer.NextAction.PRIORITY ? cp8.choice : combatMove(copy, kind);
        if (proposal == null) throw new IllegalStateException("CP8 probe did not record a choice");
    }
    static final class Probe extends ComputerPlayer8 {
        NeuralMctsState.Move choice;
        Probe(ComputerPlayer8 original) { super(original); }
        @Override protected void onPrioritySelected(Game game, Ability action) {
            if (!priorityActivated) throw new IllegalStateException("CP8 probe activation failed");
            choice = actualMove(game, action);
        }
    }
    static NeuralMctsState.Move actualMove(Game game, Ability chosen) {
        if (!(chosen instanceof PassAbility)) for (StackObject stack : game.getStack()) {
            Ability concrete = stack.getStackAbility();
            if (Objects.equals(concrete.getSourceId(), chosen.getSourceId()) && concrete.getId().equals(chosen.getId()))
                return new NeuralMctsState.Move(concrete.copy(), null, null);
        }
        // Non-stack targeted activations cannot be reconstructed from a played ability.
        // Retain their original identity, and explicitly leave exact mapping unavailable.
        return new NeuralMctsState.Move(chosen.copy(), null, null);
    }
    static NeuralMctsState.Move combatMove(Game game, MCTSPlayer.NextAction kind) {
        Map<UUID, UUID> assignments = new LinkedHashMap<>();
        for (CombatGroup group : game.getCombat().getGroups()) {
            if (kind == MCTSPlayer.NextAction.SELECT_ATTACKERS)
                for (UUID id : group.getAttackers()) assignments.put(id, group.getDefenderId());
            else for (UUID id : group.getBlockers()) assignments.put(id, group.getAttackers().get(0));
        }
        return new NeuralMctsState.Move(null, kind == MCTSPlayer.NextAction.SELECT_ATTACKERS ? assignments : null,
                kind == MCTSPlayer.NextAction.SELECT_BLOCKERS ? assignments : null);
    }
    static boolean primaryAgreement(NeuralMctsState.Move a, NeuralMctsState.Move b) {
        if (a.ability == null || b.ability == null) return a.fingerprint.equals(b.fingerprint);
        if (a.ability instanceof PassAbility || b.ability instanceof PassAbility)
            return a.ability instanceof PassAbility && b.ability instanceof PassAbility;
        return Objects.equals(a.ability.getSourceId(), b.ability.getSourceId()) && a.ability.getId().equals(b.ability.getId());
    }
    void failure(Throwable exc) {
        JSONObject failure = new JSONObject().put("category", category(exc)).put("error", exc.toString());
        if (selected != null) failure.put("action", selected.fingerprint);
        event.put("failure", failure);
    }
    static String category(Throwable exc) {
        if (exc instanceof NeuralMctsSearch.DeadlineExceeded) return "deadline_limit";
        String message = exc.toString().toLowerCase(Locale.ROOT);
        if (message.contains("candidate limit")) return "candidate_limit";
        if (message.contains("inference") || exc instanceof StrictDecisionFailure) return "inference_failure";
        if (message.contains("activat") || message.contains("unavailable with cp8")) return "activation_failure";
        if (message.contains("engine error")) return "simulation_failure";
        return "invariant_failure";
    }
    void finish(Game game, String owner, int candidateCount) {
        event.put("owner", owner);
        if (!event.has("candidate_count")) event.put("candidate_count", candidateCount < 0 ? JSONObject.NULL : candidateCount);
        event.put("forced", event.optInt("candidate_count", -1) >= 0 && event.optInt("candidate_count", -1) <= 1);
        attempt(() -> {
            if (selected != null) event.put("selected_action", selected.describe(game)).put("chosen", selected.fingerprint);
            if (root != null) {
                detail.put("root", root.snapshot());
                if (result != null) {
                    JSONArray actions = event.getJSONArray("actions");
                    for (int i = 0; i < actions.length(); i++) {
                        JSONObject description = root.moves().get(i).describe(root.game);
                        for (String key : description.keySet()) actions.getJSONObject(i).put(key, description.get(key));
                    }
                }
            }
            String mapped = null;
            if (proposal != null) {
                JSONObject cp8 = proposal.describe(game).put("mapping", "unmapped").put("q", JSONObject.NULL);
                if (selected != null) cp8.put("primary_agreement", primaryAgreement(selected, proposal));
                if (root != null) for (NeuralMctsState.Move move : root.moves()) {
                    if (move.fingerprint.equals(proposal.fingerprint)) { mapped = move.fingerprint; break; }
                }
                if (mapped != null) {
                    cp8.put("mapping", "mapped").put("exact_agreement", selected != null && selected.fingerprint.equals(mapped));
                    JSONArray actions = event.optJSONArray("actions");
                    if (actions != null) for (int i = 0; i < actions.length(); i++) {
                        JSONObject candidate = actions.getJSONObject(i);
                        if (mapped.equals(candidate.getString("fingerprint"))) cp8.put("q", candidate.opt("q"));
                    }
                }
                event.put("cp8", cp8);
            }
            if (result != null) detail.put("paths", result.paths(selected == null ? result.action : selected.fingerprint, mapped));
            if (event.has("actions") && selected != null) {
                for (Object row : event.getJSONArray("actions")) {
                    JSONObject candidate = (JSONObject) row;
                    if (selected.fingerprint.equals(candidate.getString("fingerprint"))) {
                        event.put("selected_q", candidate.opt("q"));
                        JSONObject cp8 = event.optJSONObject("cp8");
                        if (cp8 != null && !cp8.isNull("q") && !candidate.isNull("q"))
                            event.put("estimated_value_difference", candidate.getDouble("q") - cp8.getDouble("q"));
                    }
                }
            }
            detail.put("game_id", event.get("game_id")).put("decision_id", event.get("decision_id"))
                    .put("privileged_oracle", "oracle".equals(event.getString("hidden_state_mode")));
            Path directory = Paths.get(System.getProperty("neuralMcts.detailDir"));
            Files.createDirectories(directory);
            String name = event.getString("game_id") + "_" + event.getString("decision_id") + ".json";
            Path temporary = directory.resolve(name + ".tmp");
            Files.write(temporary, detail.toString().getBytes(StandardCharsets.UTF_8));
            Files.move(temporary, directory.resolve(name), StandardCopyOption.REPLACE_EXISTING);
            event.put("detail_file", name);
        });
        event.put("diagnostic_ms", diagnosticNanos / 1e6).put("total_callback_ms", (System.nanoTime() - started) / 1e6);
        long[] rpcEnd = DecisionHandler.diagnosticRpcSnapshot();
        event.put("cp8_inference_ms", (rpcEnd[0] - rpcStart[0] - diagnosticRpcNanos) / 1e6)
                .put("cp8_inference_requests", rpcEnd[1] - rpcStart[1] - diagnosticRpcCount)
                .put("diagnostic_cp8_ms", diagnosticRpcNanos / 1e6).put("diagnostic_cp8_requests", diagnosticRpcCount)
                .put("inference_ms", event.getDouble("inference_ms") + (rpcEnd[0] - rpcStart[0] - diagnosticRpcNanos) / 1e6);
        try {
            Path log = Paths.get(System.getProperty("neuralMcts.decisionLog"));
            Files.createDirectories(log.getParent());
            Files.write(log, (event.toString() + "\n").getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception exc) { System.err.println("MCTS diagnostic write failed: " + exc); }
    }
}
