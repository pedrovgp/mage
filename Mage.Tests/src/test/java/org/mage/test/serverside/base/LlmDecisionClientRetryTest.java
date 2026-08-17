package org.mage.test.serverside.base;

import mage.player.ai.DecisionHandler;
import mage.player.ai.DecisionPayload;
import mage.player.ai.DecisionResult;
import mage.player.ai.LlmDecisionClient;
import mage.player.ai.StrictDecisionFailure;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * The client is where a failed decision becomes a degraded one, so it is where retries
 * and strict mode have to work. A transient blip should cost a retry, not a decision;
 * whatever survives the retries should be countable and, under strict mode, fatal.
 */
public class LlmDecisionClientRetryTest {

    @After
    public void clearSwitches() {
        System.clearProperty("MAGELLM_STRICT_DECISIONS");
        System.clearProperty("MAGELLM_DECISION_ATTEMPTS");
    }

    /** Serves a scripted sequence of responses, one per attempt. */
    private static class ScriptedClient extends LlmDecisionClient {
        private final List<Integer> codes;   // negative -> throw a transport error
        final List<String> attempts = new ArrayList<>();

        ScriptedClient(List<Integer> codes) {
            super("http://stub");
            this.codes = codes;
        }

        @Override
        protected HttpURLConnection openConnection(String url) throws IOException {
            int index = attempts.size();
            attempts.add(url);
            int code = index < codes.size() ? codes.get(index) : codes.get(codes.size() - 1);
            if (code < 0) {
                throw new IOException("connection refused (scripted)");
            }
            return new StubConnection(new URL(url), code);
        }
    }

    private static class StubConnection extends HttpURLConnection {
        private final int code;

        StubConnection(URL url, int code) {
            super(url);
            this.code = code;
        }

        @Override
        public int getResponseCode() {
            return code;
        }

        @Override
        public OutputStream getOutputStream() {
            return new ByteArrayOutputStream();
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(
                    new JSONObject().put("chosen_idx", 7).put("reason", "ok")
                            .toString().getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public InputStream getErrorStream() {
            return new ByteArrayInputStream("boom".getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public void disconnect() {
        }

        @Override
        public boolean usingProxy() {
            return false;
        }

        @Override
        public void connect() {
        }
    }

    private static DecisionPayload payload() {
        return new DecisionPayload("/choose_from_all_actions", new JSONObject().put("x", 1));
    }

    @Test
    public void testATransientBlipCostsARetryNotTheDecision() {
        ScriptedClient client = new ScriptedClient(List.of(-1, 200));

        DecisionResult result = client.requestDecision(payload());

        assertEquals("Should have retried once", 2, client.attempts.size());
        assertTrue("The retried decision must succeed", result.isSuccessful());
        assertEquals(Integer.valueOf(7), result.getChosenIndex());
    }

    @Test
    public void testAServerErrorIsRetried() {
        ScriptedClient client = new ScriptedClient(List.of(503, 503, 200));

        DecisionResult result = client.requestDecision(payload());

        assertEquals(3, client.attempts.size());
        assertTrue(result.isSuccessful());
    }

    @Test
    public void testANonRetryableStatusIsNotRetried() {
        // 400 means we built a bad payload; another identical attempt cannot help.
        ScriptedClient client = new ScriptedClient(List.of(400, 400, 400));

        DecisionResult result = client.requestDecision(payload());

        assertEquals("400 should be attempted exactly once", 1, client.attempts.size());
        assertFalse(result.isSuccessful());
    }

    @Test
    public void testAttemptsAreExhaustedThenTheDecisionDegrades() {
        System.setProperty("MAGELLM_DECISION_ATTEMPTS", "2");
        ScriptedClient client = new ScriptedClient(List.of(500, 500, 500));

        DecisionResult result = client.requestDecision(payload());

        assertEquals(2, client.attempts.size());
        assertFalse("A decision nobody could serve must not look successful",
                result.isSuccessful());
    }

    @Test
    public void testStrictModeFailsInsteadOfDegrading() {
        System.setProperty("MAGELLM_DECISION_ATTEMPTS", "1");
        System.setProperty("MAGELLM_STRICT_DECISIONS", "1");
        ScriptedClient client = new ScriptedClient(List.of(500));

        try {
            client.requestDecision(payload());
            fail("Strict mode should refuse to degrade the decision");
        } catch (StrictDecisionFailure expected) {
            assertTrue("Should name the endpoint, got: " + expected.getMessage(),
                    expected.getMessage().contains("/choose_from_all_actions"));
        }
    }

    /**
     * An {@link Error}, not an exception, because the caller is
     * {@code ComputerPlayer8.choose}/{@code .chooseTarget} and both catch
     * {@code Exception} and fall through to CP7 — which is how two benchmark runs
     * reported {@code strict_decisions: True} alongside five figures of fallbacks.
     */
    @Test
    public void testStrictFailureIsNotAnException() {
        System.setProperty("MAGELLM_DECISION_ATTEMPTS", "1");
        System.setProperty("MAGELLM_STRICT_DECISIONS", "1");
        ScriptedClient client = new ScriptedClient(List.of(500));

        boolean swallowed = false;
        try {
            try {
                client.requestDecision(payload());
            } catch (Exception e) {
                swallowed = true;
            }
            fail("A catch(Exception) caller must not be able to intercept it");
        } catch (StrictDecisionFailure expected) {
            assertFalse("catch(Exception) must not see the strict failure", swallowed);
        }
    }

    /** A strict failure ends the game, so there is no fallback to account for. */
    @Test
    public void testStrictModeDoesNotRecordAFallback() {
        System.setProperty("MAGELLM_DECISION_ATTEMPTS", "1");
        System.setProperty("MAGELLM_STRICT_DECISIONS", "1");
        ScriptedClient client = new ScriptedClient(List.of(500));

        long before = DecisionHandler.decisionFallbackCount();
        try {
            client.requestDecision(payload());
            fail("Strict mode should have thrown");
        } catch (StrictDecisionFailure expected) {
            assertEquals("Strict mode must not record a fallback",
                    before, DecisionHandler.decisionFallbackCount());
        }
    }

    @Test
    public void testStrictModeStillRetriesFirst() {
        System.setProperty("MAGELLM_STRICT_DECISIONS", "1");
        ScriptedClient client = new ScriptedClient(List.of(503, 200));

        DecisionResult result = client.requestDecision(payload());

        assertEquals("A blip must be retried before strict mode fails the game",
                2, client.attempts.size());
        assertTrue(result.isSuccessful());
    }

    @Test
    public void testRetryPolicyClassifiesStatuses() {
        assertTrue(LlmDecisionClient.isRetryable(500));
        assertTrue(LlmDecisionClient.isRetryable(502));
        assertTrue(LlmDecisionClient.isRetryable(429));
        assertFalse(LlmDecisionClient.isRetryable(400));
        assertFalse(LlmDecisionClient.isRetryable(404));
        assertFalse(LlmDecisionClient.isRetryable(200));
    }

    @Test
    public void testAttemptBudgetIsConfigurable() {
        System.clearProperty("MAGELLM_DECISION_ATTEMPTS");
        assertEquals(3, LlmDecisionClient.maxAttempts());

        System.setProperty("MAGELLM_DECISION_ATTEMPTS", "5");
        assertEquals(5, LlmDecisionClient.maxAttempts());

        System.setProperty("MAGELLM_DECISION_ATTEMPTS", "garbage");
        assertEquals("A bad value must not disable retries", 3, LlmDecisionClient.maxAttempts());
    }
}
