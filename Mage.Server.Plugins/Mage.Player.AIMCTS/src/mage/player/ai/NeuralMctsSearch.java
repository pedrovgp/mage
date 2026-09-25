package mage.player.ai;

import java.util.*;
import org.json.JSONArray;
import org.json.JSONObject;

/** Batched, root-parallel PUCT. Each tree owns one determinization. */
public final class NeuralMctsSearch {
    /** Escapes the rules engine's catch(Exception); only search consumes it. */
    public static final class DeadlineExceeded extends Error {
        public DeadlineExceeded() { super("search deadline exceeded"); }
    }
    public interface State {
        UUID actor();
        /** null for nonterminal; terminal utility is always ROOT relative. */
        Double terminalValue();
        List<String> actions();
        State next(int action);
        JSONObject request();
        default JSONObject snapshot() { return new JSONObject(); }
        default JSONObject actionDescription(int index) { return new JSONObject().put("fingerprint", actions().get(index)); }
    }
    public interface Evaluator {
        List<Evaluation> evaluate(List<JSONObject> positions, long deadlineNanos);
    }
    public static final class Evaluation {
        public final double value; // ACTOR relative
        public final double[] priors;
        public Evaluation(double value, double[] priors) {
            if (!Double.isFinite(value) || Math.abs(value) > 1) throw new IllegalArgumentException("invalid value");
            double sum = 0;
            for (double p : priors) {
                if (!Double.isFinite(p) || p < 0) throw new IllegalArgumentException("invalid prior");
                sum += p;
            }
            if (priors.length > 0 && Math.abs(sum - 1) > 1e-5) throw new IllegalArgumentException("priors not normalized");
            this.value = value;
            this.priors = priors.clone();
        }
    }
    static final class Node {
        final State state;
        final Node parent;
        final List<String> keys;
        Node[] children;
        double[] priors;
        double valueSum;
        double leafValue;
        int visits;
        Node(State state, Node parent) {
            this.state = state;
            this.parent = parent;
            keys = state.terminalValue() == null ? state.actions() : Collections.emptyList();
            children = new Node[keys.size()];
        }
    }
    public static final class Result {
        public final String action;
        public final int simulations;
        public final JSONObject diagnostics;
        private final List<Node> roots;
        private final UUID rootPlayer;
        Result(String action, int simulations, JSONObject diagnostics, List<Node> roots, UUID rootPlayer) {
            this.action = action; this.simulations = simulations; this.diagnostics = diagnostics;
            this.roots = roots; this.rootPlayer = rootPlayer;
        }
        /** Only existing, backed-up edges. No expansion or evaluation for display. */
        public JSONArray paths(String cp8Action) {
            return paths(action, cp8Action);
        }
        public JSONArray paths(String selectedAction, String cp8Action) {
            LinkedHashMap<String, String> wanted = new LinkedHashMap<>();
            wanted.put(selectedAction, "selected");
            if (cp8Action != null && !cp8Action.equals(selectedAction)) wanted.put(cp8Action, "cp8");
            JSONObject best = null;
            JSONArray candidates = diagnostics.getJSONArray("actions");
            for (int i = 0; i < candidates.length(); i++) {
                JSONObject candidate = candidates.getJSONObject(i);
                if (candidate.getInt("visits") > 0 && !wanted.containsKey(candidate.getString("fingerprint"))
                        && (best == null || candidate.getDouble("q") > best.getDouble("q"))) best = candidate;
            }
            if (best != null) wanted.put(best.getString("fingerprint"), "alternative");
            JSONArray paths = new JSONArray();
            for (Map.Entry<String, String> entry : wanted.entrySet()) {
                int index = roots.get(0).keys.indexOf(entry.getKey());
                Node parent = null, child = null;
                int world = -1;
                if (index >= 0) for (int r = 0; r < roots.size(); r++) {
                    Node candidate = roots.get(r).children[index];
                    if (candidate != null && candidate.visits > 0 && (child == null || candidate.visits > child.visits)) {
                        parent = roots.get(r); child = candidate; world = r;
                    }
                }
                JSONArray plies = new JSONArray();
                String key = entry.getKey();
                while (child != null && plies.length() < 8) {
                    plies.put(new JSONObject().put("actor", parent.state.actor())
                            .put("action", key).put("visits", child.visits)
                            .put("action_description", parent.state.actionDescription(parent.keys.indexOf(key)))
                            .put("q", child.valueSum / child.visits).put("value_player_id", rootPlayer)
                            .put("terminal_value", child.state.terminalValue() == null ? JSONObject.NULL : child.state.terminalValue())
                            .put("state", child.state.snapshot()));
                    parent = child; child = null;
                    for (int i = 0; i < parent.children.length; i++) {
                        Node candidate = parent.children[i];
                        if (candidate != null && candidate.visits > 0 && (child == null || candidate.visits > child.visits)) {
                            child = candidate; key = parent.keys.get(i);
                        }
                    }
                }
                paths.put(new JSONObject().put("role", entry.getValue()).put("root_action", entry.getKey())
                        .put("world", world).put("available", plies.length() > 0)
                        .put("truncated", child != null).put("plies", plies));
            }
            return paths;
        }
    }
    private final Evaluator evaluator;
    private final double cPuct;
    private final int maxDepth;
    public long inferenceNanos;
    public final JSONArray batchSizes = new JSONArray();
    public final JSONArray completedBatchSizes = new JSONArray();
    public JSONObject failureContext = new JSONObject();
    private int completedSimulations, terminalEvaluations, maxVisitedDepth;
    private final Map<Integer, Integer> visitedDepths = new TreeMap<>();
    private final JSONArray evaluatedRootValues = new JSONArray();
    public JSONObject progressDiagnostics() {
        int positions = 0;
        for (Object size : completedBatchSizes) positions += ((Number) size).intValue();
        double rootValue = 0;
        for (Object value : evaluatedRootValues) rootValue += ((Number) value).doubleValue() / evaluatedRootValues.length();
        return new JSONObject().put("simulations", completedSimulations).put("terminal_evaluations", terminalEvaluations)
                .put("max_visited_depth", maxVisitedDepth).put("visited_depths", new JSONObject(visitedDepths))
                .put("inference_ms", inferenceNanos / 1e6).put("batch_sizes", batchSizes).put("completed_batch_sizes", completedBatchSizes)
                .put("inference_batches", completedBatchSizes.length()).put("evaluated_positions", positions)
                .put("evaluated_leaf_positions", positions - evaluatedRootValues.length()).put("root_values", evaluatedRootValues)
                .put("root_value", evaluatedRootValues.length() == 0 ? JSONObject.NULL : rootValue);
    }
    public NeuralMctsSearch(Evaluator evaluator, double cPuct, int maxDepth) {
        if (!Double.isFinite(cPuct) || cPuct <= 0 || maxDepth < 1) throw new IllegalArgumentException("invalid search config");
        this.evaluator = evaluator; this.cPuct = cPuct; this.maxDepth = maxDepth;
    }
    static double score(double sum, int n, double prior, int parentN, double c, boolean rootActs) {
        double q = n == 0 ? 0 : sum / n;
        return (rootActs ? q : -q) + c * prior * Math.sqrt(Math.max(1, parentN)) / (1 + n);
    }
    private Node select(Node node, UUID rootPlayer) {
        int best = -1;
        double score = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < node.children.length; i++) {
            Node child = node.children[i];
            double s = score(child == null ? 0 : child.valueSum, child == null ? 0 : child.visits,
                    node.priors[i], node.visits, cPuct, node.state.actor().equals(rootPlayer));
            if (s > score) { best = i; score = s; }
        }
        if (best < 0) throw new IllegalStateException("nonterminal state has no actions");
        if (node.children[best] == null) {
            JSONArray path = new JSONArray();
            List<String> keys = new ArrayList<>();
            for (Node n = node; n.parent != null; n = n.parent)
                keys.add(n.parent.keys.get(Arrays.asList(n.parent.children).indexOf(n)));
            Collections.reverse(keys);
            for (String key : keys) path.put(key);
            path.put(node.keys.get(best));
            failureContext = new JSONObject().put("stage", "transition").put("action", node.keys.get(best)).put("path", path).put("actor", node.state.actor());
            node.children[best] = new Node(node.state.next(best), node);
        }
        return node.children[best];
    }
    static void backup(Node node, double rootValue) {
        for (Node n = node; n != null; n = n.parent) { n.visits++; n.valueSum += rootValue; }
    }
    public Result search(List<State> worlds, UUID rootPlayer, int budget, long deadline) {
        completedSimulations = terminalEvaluations = maxVisitedDepth = 0;
        inferenceNanos = 0;
        batchSizes.clear(); completedBatchSizes.clear(); evaluatedRootValues.clear(); visitedDepths.clear();
        if (worlds.isEmpty() || budget < worlds.size()) throw new IllegalArgumentException("insufficient simulation budget");
        List<Node> roots = new ArrayList<>();
        for (State world : worlds) roots.add(new Node(world, null));
        List<String> rootKeys = roots.get(0).keys;
        if (rootKeys.isEmpty()) throw new IllegalArgumentException("root has no actions");
        for (int i = 0; i < roots.size(); i++) if (!roots.get(i).keys.equals(rootKeys)) {
            failureContext = new JSONObject().put("stage", "root_invariant").put("world", i)
                    .put("expected", new JSONArray(rootKeys)).put("actual", new JSONArray(roots.get(i).keys));
            throw new IllegalStateException("root actions differ across determinizations");
        }
        evaluate(roots, rootPlayer, deadline, false);
        for (Node root : roots) evaluatedRootValues.put(root.leafValue);
        Map<Integer, Integer> depths = visitedDepths;
        int evaluatedPositions = roots.size(), batches = 1;
        boolean timedOut = false;
        double leafValueSum = 0;
        try {
            while (completedSimulations < budget && System.nanoTime() < deadline) {
                List<Node> leaves = new ArrayList<>();
                for (Node root : roots) {
                    if (completedSimulations + leaves.size() >= budget || System.nanoTime() >= deadline) break;
                    Node leaf = root;
                    int depth = 0;
                    while (leaf.priors != null && leaf.state.terminalValue() == null && depth < maxDepth) {
                        if (System.nanoTime() >= deadline) throw new DeadlineExceeded();
                        leaf = select(leaf, rootPlayer); depth++;
                    }
                    maxVisitedDepth = Math.max(maxVisitedDepth, depth);
                    depths.merge(depth, 1, Integer::sum);
                    Double terminal = leaf.state.terminalValue();
                    if (terminal != null) { backup(leaf, terminal); completedSimulations++; terminalEvaluations++; }
                    else if (leaf.priors != null) { backup(leaf, leaf.leafValue); completedSimulations++; }
                    else leaves.add(leaf);
                }
                if (!leaves.isEmpty()) {
                    evaluate(leaves, rootPlayer, deadline, true);
                    completedSimulations += leaves.size();
                    evaluatedPositions += leaves.size(); batches++;
                    for (Node leaf : leaves) leafValueSum += leaf.leafValue;
                }
            }
        } catch (DeadlineExceeded exhausted) { timedOut = true; }
        if (completedSimulations == 0 && (timedOut || System.nanoTime() >= deadline)) throw new DeadlineExceeded();
        if (completedSimulations == 0) throw new IllegalStateException("no completed simulations");
        JSONArray stats = new JSONArray();
        JSONArray rootValues = new JSONArray();
        for (Node root : roots) rootValues.put(root.leafValue);
        String chosen = null;
        int best = -1;
        double bestValue = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < rootKeys.size(); i++) {
            int n = 0; double w = 0, p = 0;
            for (Node root : roots) {
                Node child = root.children[i];
                if (child != null) { n += child.visits; w += child.valueSum; }
                p += root.priors[i];
            }
            double q = n == 0 ? 0 : w / n;
            stats.put(new JSONObject().put("fingerprint", rootKeys.get(i)).put("visits", n)
                    .put("q", n == 0 ? JSONObject.NULL : q).put("prior", p / roots.size()));
            if (n > best || (n == best && q > bestValue)) { best = n; bestValue = q; chosen = rootKeys.get(i); }
        }
        int visited = 0; double maxPrior = 0, concentration = 0, entropy = 0, rootValue = 0;
        for (Node root : roots) rootValue += root.leafValue / roots.size();
        for (int i = 0; i < stats.length(); i++) {
            JSONObject candidate = stats.getJSONObject(i);
            if (candidate.getInt("visits") > 0) visited++;
            double prior = candidate.getDouble("prior");
            maxPrior = Math.max(maxPrior, prior); concentration += prior * prior;
            if (prior > 0) entropy -= prior * Math.log(prior);
        }
        return new Result(chosen, completedSimulations, new JSONObject().put("actions", stats).put("simulations", completedSimulations)
                .put("root_value", rootValue).put("value_player_id", rootPlayer.toString())
                .put("exploration_coverage", (double) visited / stats.length())
                .put("max_prior", maxPrior).put("prior_concentration", concentration).put("prior_entropy", entropy)
                .put("visited_depths", new JSONObject(depths)).put("terminal_evaluations", terminalEvaluations)
                .put("inference_ms", inferenceNanos / 1e6).put("batch_sizes", batchSizes)
                .put("completed_batch_sizes", completedBatchSizes)
                .put("determinizations", roots.size()).put("max_visited_depth", maxVisitedDepth)
                .put("evaluated_positions", evaluatedPositions).put("inference_batches", batches)
                .put("root_values", rootValues).put("evaluated_leaf_positions", evaluatedPositions - roots.size())
                .put("mean_evaluated_leaf_value", evaluatedPositions == roots.size() ? JSONObject.NULL
                        : leafValueSum / (evaluatedPositions - roots.size()))
                .put("deadline_reached", timedOut || System.nanoTime() >= deadline), roots, rootPlayer);
    }
    private void evaluate(List<Node> nodes, UUID rootPlayer, long deadline, boolean backup) {
        long started = System.nanoTime();
        failureContext = new JSONObject().put("stage", "inference").put("batch_size", nodes.size());
        List<JSONObject> requests = new ArrayList<>();
        for (Node n : nodes) requests.add(n.state.request());
        batchSizes.put(nodes.size());
        List<Evaluation> values;
        try { values = evaluator.evaluate(requests, deadline); }
        finally { inferenceNanos += System.nanoTime() - started; }
        if (values.size() != nodes.size()) throw new IllegalStateException("batch response count mismatch");
        // Validate the complete response before committing any backup.
        for (int i = 0; i < nodes.size(); i++)
            if (values.get(i).priors.length != nodes.get(i).keys.size())
                throw new IllegalStateException("action response count mismatch");
        for (int i = 0; i < nodes.size(); i++) {
            Node n = nodes.get(i); Evaluation e = values.get(i);
            n.priors = e.priors;
            n.leafValue = n.state.actor().equals(rootPlayer) ? e.value : -e.value;
            if (backup) backup(n, n.leafValue);
        }
        completedBatchSizes.put(nodes.size());
    }
}
