package mage.player.ai;

import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
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
 *
 * <p>This is the single funnel every decision request passes through, so it is also
 * where a failed request is turned into something the game can continue with. That
 * matters more than it sounds: a degraded result here does not raise, it comes back
 * with a reason that makes {@link DecisionResult#isSuccessful()} false, and the caller
 * quietly lets ComputerPlayer7 decide instead. A serving path can be completely broken
 * and still produce a full, plausible-looking benchmark.
 *
 * <p>So two things live here. Transient failures are retried, because a blip should not
 * cost a decision. Whatever survives the retries is counted, and under
 * MAGELLM_STRICT_DECISIONS it raises instead of degrading.
 */
public class LlmDecisionClient {
    private static final Logger logger = Logger.getLogger(LlmDecisionClient.class);

    /** Attempts per decision, not retries: 3 means one try plus two retries. */
    private static final int DEFAULT_ATTEMPTS = 3;
    private static final long RETRY_BASE_SLEEP_MS = 100L;

    private final String baseUrl;

    public LlmDecisionClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /**
     * How many times to try a single decision before giving up.
     *
     * <p>Kept small: a decision blocks the game, so the budget trades a bounded latency
     * cost against losing the decision entirely.
     */
    public static int maxAttempts() {
        String raw = System.getProperty("MAGELLM_DECISION_ATTEMPTS",
                System.getenv("MAGELLM_DECISION_ATTEMPTS"));
        if (raw != null) {
            try {
                int parsed = Integer.parseInt(raw.trim());
                if (parsed >= 1) {
                    return parsed;
                }
            } catch (NumberFormatException ignored) {
                // fall through to the default
            }
        }
        return DEFAULT_ATTEMPTS;
    }

    /**
     * 5xx and 429 are worth another attempt; other 4xx are our own bug and will fail
     * again identically, so retrying them only multiplies the log noise.
     */
    public static boolean isRetryable(int httpCode) {
        return httpCode == 429 || httpCode >= 500;
    }

    /** Seam for tests: override to supply a connection without a real socket. */
    protected HttpURLConnection openConnection(String url) throws IOException {
        return (HttpURLConnection) new URL(url).openConnection();
    }

    public DecisionResult requestDecision(DecisionPayload payload) {
        int attempts = Math.max(1, maxAttempts());
        RequestFailure last = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                return sendOnce(payload);
            } catch (RequestFailure failure) {
                last = failure;
                if (!failure.retryable || attempt == attempts) {
                    break;
                }
                DecisionStats.INSTANCE.recordDecisionRetry();
                logger.warn("decision " + payload.getEndpointPath() + " attempt " + attempt + "/"
                        + attempts + " failed (" + failure.getMessage() + "); retrying");
                sleepBeforeRetry(attempt);
            }
        }
        return degrade(payload, last, attempts);
    }

    private DecisionResult sendOnce(DecisionPayload payload) throws RequestFailure {
        String url = baseUrl + payload.getEndpointPath();
        HttpURLConnection conn = null;
        try {
            conn = openConnection(url);
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
                throw new RequestFailure("server_error", isRetryable(code),
                        "HTTP " + code + ": " + sb, null);
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
            // A 200 we cannot read is a contract break, not a blip.
            throw new RequestFailure("unknown_response", false,
                    "200 with no usable field: " + sb, null);
        } catch (RequestFailure rethrow) {
            throw rethrow;
        } catch (Exception e) {
            // Transport-level: connection refused, reset, timeout, a pod being replaced.
            throw new RequestFailure("exception", true, String.valueOf(e), e);
        } finally {
            if (conn != null)
                conn.disconnect();
        }
    }

    private DecisionResult degrade(DecisionPayload payload, RequestFailure failure, int attempts) {
        String kind = failure == null ? "exception" : failure.kind;
        String detail = failure == null ? "unknown" : failure.getMessage();
        DecisionStats.INSTANCE.recordDecisionFallback();
        if (DecisionHandler.strictDecisionsEnabled()) {
            throw new IllegalStateException(
                    "MAGELLM_STRICT_DECISIONS is set and " + payload.getEndpointPath()
                    + " failed after " + attempts + " attempt(s) (" + kind + "); failing the "
                    + "game rather than letting ComputerPlayer7 decide for the agent under "
                    + "measurement: " + detail, failure);
        }
        logger.error("decision " + payload.getEndpointPath() + " failed after " + attempts
                + " attempt(s) (" + kind + "): " + detail);
        return new DecisionResult(0, new ArrayList<>(), kind);
    }

    private static void sleepBeforeRetry(int attempt) {
        try {
            Thread.sleep(RETRY_BASE_SLEEP_MS * attempt);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    /** One failed attempt, and whether trying again could plausibly help. */
    private static final class RequestFailure extends Exception {
        final String kind;
        final boolean retryable;

        RequestFailure(String kind, boolean retryable, String message, Throwable cause) {
            super(message, cause);
            this.kind = kind;
            this.retryable = retryable;
        }
    }
}
