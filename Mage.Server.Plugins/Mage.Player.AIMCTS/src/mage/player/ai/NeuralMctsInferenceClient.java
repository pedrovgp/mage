package mage.player.ai;

import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** Bounded inference RPC. Failures are never converted into fabricated values. */
public final class NeuralMctsInferenceClient implements NeuralMctsSearch.Evaluator {
    // 32 positions * 4096 fingerprints/scores/priors fits within this bound.
    private static final int MAX_RESPONSE_BYTES = 32 * 1024 * 1024;
    private final String baseUrl;
    private final String checkpoint;
    public NeuralMctsInferenceClient(String baseUrl, String checkpoint) {
        this.baseUrl = baseUrl; this.checkpoint = checkpoint;
    }
    @Override
    public List<NeuralMctsSearch.Evaluation> evaluate(List<JSONObject> positions, long deadline) {
        HttpURLConnection connection = null;
        try {
            long remaining = (deadline - System.nanoTime()) / 1_000_000;
            if (remaining <= 0) throw new NeuralMctsSearch.DeadlineExceeded();
            int timeout = (int) Math.min(remaining, 10000);
            connection = (HttpURLConnection) new URL(baseUrl + "/mcts/evaluate_batch").openConnection();
            connection.setConnectTimeout(timeout); connection.setReadTimeout(timeout);
            connection.setRequestMethod("POST"); connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            byte[] body = new JSONObject().put("positions", new JSONArray(positions)).toString().getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(body.length);
            try (OutputStream stream = connection.getOutputStream()) { stream.write(body); }
            if (connection.getResponseCode() != 200) throw new IOException("inference HTTP " + connection.getResponseCode());
            String response;
            try (InputStream stream = connection.getInputStream()) {
                byte[] bytes = stream.readNBytes(MAX_RESPONSE_BYTES + 1);
                if (bytes.length > MAX_RESPONSE_BYTES) throw new IOException("oversized response");
                response = new String(bytes, StandardCharsets.UTF_8);
            }
            JSONArray results = new JSONObject(response).getJSONArray("results");
            if (results.length() != positions.size()) throw new IOException("result batch mismatch");
            List<NeuralMctsSearch.Evaluation> out = new ArrayList<>();
            for (int i = 0; i < positions.size(); i++) out.add(validate(positions.get(i), results.getJSONObject(i), checkpoint));
            return out;
        } catch (SocketTimeoutException exc) {
            if (System.nanoTime() >= deadline) throw new NeuralMctsSearch.DeadlineExceeded();
            throw new IllegalStateException("inference timed out before search deadline", exc);
        } catch (IOException | JSONException exc) {
            throw new IllegalStateException("neural MCTS inference failed", exc);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
    static NeuralMctsSearch.Evaluation validate(JSONObject request, JSONObject response, String checkpoint) {
        if (!checkpoint.equals(response.getString("checkpoint_id"))
                || !"stable-relations-v1".equals(response.getString("representation_version"))
                || !request.getString("player_id").equals(response.getString("value_player_id")))
            throw new IllegalStateException("checkpoint/representation/perspective mismatch");
        JSONArray candidates = request.getJSONArray("all_actions");
        JSONArray fingerprints = response.getJSONArray("action_fingerprints");
        JSONArray probabilities = response.getJSONArray("priors");
        if (candidates.length() != fingerprints.length() || candidates.length() != probabilities.length())
            throw new IllegalStateException("candidate count mismatch");
        double[] priors = new double[candidates.length()];
        for (int i = 0; i < priors.length; i++) {
            if (!candidates.getJSONObject(i).getString("fingerprint").equals(fingerprints.getString(i)))
                throw new IllegalStateException("candidate order mismatch");
            priors[i] = probabilities.getDouble(i);
        }
        double win = response.getDouble("value_win_prob"), signed = response.getDouble("value_signed");
        if (!Double.isFinite(win) || win < 0 || win > 1 || Math.abs(signed - (2 * win - 1)) > 1e-6)
            throw new IllegalStateException("inconsistent value");
        return new NeuralMctsSearch.Evaluation(signed, priors);
    }
}
