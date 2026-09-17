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
        Result(String action, int simulations, JSONObject diagnostics) {
            this.action = action; this.simulations = simulations; this.diagnostics = diagnostics;
        }
    }
    private final Evaluator evaluator;
    private final double cPuct;
    private final int maxDepth;
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
        if (node.children[best] == null) node.children[best] = new Node(node.state.next(best), node);
        return node.children[best];
    }
    static void backup(Node node, double rootValue) {
        for (Node n = node; n != null; n = n.parent) { n.visits++; n.valueSum += rootValue; }
    }
    public Result search(List<State> worlds, UUID rootPlayer, int budget, long deadline) {
        if (worlds.isEmpty() || budget < worlds.size()) throw new IllegalArgumentException("insufficient simulation budget");
        List<Node> roots = new ArrayList<>();
        for (State world : worlds) roots.add(new Node(world, null));
        List<String> rootKeys = roots.get(0).keys;
        if (rootKeys.isEmpty()) throw new IllegalArgumentException("root has no actions");
        for (Node root : roots) if (!root.keys.equals(rootKeys)) throw new IllegalStateException("root actions differ across determinizations");
        evaluate(roots, rootPlayer, deadline, false);
        int completed = 0;
        int maxVisitedDepth = 0, evaluatedPositions = roots.size(), batches = 1;
        boolean timedOut = false;
        double leafValueSum = 0;
        try {
            while (completed < budget && System.nanoTime() < deadline) {
                List<Node> leaves = new ArrayList<>();
                for (Node root : roots) {
                    if (completed + leaves.size() >= budget || System.nanoTime() >= deadline) break;
                    Node leaf = root;
                    int depth = 0;
                    while (leaf.priors != null && leaf.state.terminalValue() == null && depth < maxDepth) {
                        if (System.nanoTime() >= deadline) throw new DeadlineExceeded();
                        leaf = select(leaf, rootPlayer); depth++;
                    }
                    maxVisitedDepth = Math.max(maxVisitedDepth, depth);
                    Double terminal = leaf.state.terminalValue();
                    if (terminal != null) { backup(leaf, terminal); completed++; }
                    else if (leaf.priors != null) { backup(leaf, leaf.leafValue); completed++; }
                    else leaves.add(leaf);
                }
                if (!leaves.isEmpty()) {
                    evaluate(leaves, rootPlayer, deadline, true);
                    completed += leaves.size();
                    evaluatedPositions += leaves.size(); batches++;
                    for (Node leaf : leaves) leafValueSum += leaf.leafValue;
                }
            }
        } catch (DeadlineExceeded exhausted) { timedOut = true; }
        if (completed == 0) throw new IllegalStateException("no completed simulations");
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
                    .put("q", q).put("prior", p / roots.size()));
            if (n > best || (n == best && q > bestValue)) { best = n; bestValue = q; chosen = rootKeys.get(i); }
        }
        return new Result(chosen, completed, new JSONObject().put("actions", stats).put("simulations", completed)
                .put("determinizations", roots.size()).put("max_visited_depth", maxVisitedDepth)
                .put("evaluated_positions", evaluatedPositions).put("inference_batches", batches)
                .put("root_values", rootValues).put("evaluated_leaf_positions", evaluatedPositions - roots.size())
                .put("mean_evaluated_leaf_value", evaluatedPositions == roots.size() ? JSONObject.NULL
                        : leafValueSum / (evaluatedPositions - roots.size()))
                .put("deadline_reached", timedOut || System.nanoTime() >= deadline));
    }
    private void evaluate(List<Node> nodes, UUID rootPlayer, long deadline, boolean backup) {
        List<JSONObject> requests = new ArrayList<>();
        for (Node n : nodes) requests.add(n.state.request());
        List<Evaluation> values = evaluator.evaluate(requests, deadline);
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
    }
}
