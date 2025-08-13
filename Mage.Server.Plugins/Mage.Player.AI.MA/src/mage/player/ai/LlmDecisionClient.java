package mage.player.ai;

import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Unified decision HTTP client.
 */
public class LlmDecisionClient {
    private static final Logger logger = Logger.getLogger(LlmDecisionClient.class);
    private final String baseUrl;

    public LlmDecisionClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    public DecisionResult requestDecision(DecisionPayload payload) {
        String url = baseUrl + payload.getEndpointPath();
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; utf-8");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);
            String json = payload.getBody().toString();
            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }
            int code = conn.getResponseCode();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(
                    code == 200 ? conn.getInputStream() : conn.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null)
                    sb.append(line);
            }
            if (code != 200) {
                logger.error("LLM server error " + code + ": " + sb);
                return new DecisionResult(0, new ArrayList<>(), "server_error");
            }
            JSONObject resp = new JSONObject(sb.toString());
            if (resp.has("chosen_idx")) {
                int idx = resp.optInt("chosen_idx", 0);
                String reason = resp.optString("reason", "");
                return new DecisionResult(idx, null, reason);
            }
            if (resp.has("chosen_attackers")) {
                JSONArray arr = resp.getJSONArray("chosen_attackers");
                List<UUID> uuids = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++)
                    uuids.add(UUID.fromString(arr.getString(i)));
                String reason = resp.optString("reason", "");
                return new DecisionResult(null, uuids, reason);
            }
            return new DecisionResult(0, new ArrayList<>(), "unknown_response");
        } catch (Exception e) {
            logger.error("LLM request failed", e);
            return new DecisionResult(0, new ArrayList<>(), "exception");
        } finally {
            if (conn != null)
                conn.disconnect();
        }
    }
}
