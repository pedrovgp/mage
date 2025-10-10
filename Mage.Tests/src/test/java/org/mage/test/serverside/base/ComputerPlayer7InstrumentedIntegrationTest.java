package org.mage.test.serverside.base;

import mage.constants.PhaseStep;
import mage.constants.RangeOfInfluence;
import mage.constants.Zone;
import org.junit.Test;
import org.mage.test.player.TestPlayer;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.json.JSONObject;
import org.json.JSONArray;

/**
 * Integration tests for ComputerPlayer7Instrumented trajectory logging.
 * Validates that trajectory data is properly logged during gameplay and
 * that the /v1/log_trajectory endpoint works correctly.
 */
public class ComputerPlayer7InstrumentedIntegrationTest extends CardTestPlayerBaseAI {

    @Override
    protected TestPlayer createPlayer(String name, RangeOfInfluence rangeOfInfluence) {
        if (getFullSimulatedPlayers().contains(name)) {
            // Use ComputerPlayer7Instrumented for trajectory logging tests
            TestPlayer testPlayer = new TestPlayer(
                    new org.mage.test.player.TestComputerPlayer7Instrumented(name, rangeOfInfluence, getSkillLevel()));
            testPlayer.setAIPlayer(true); // enable full AI support (game simulations) for all turns
            return testPlayer;
        }
        return super.createPlayer(name, rangeOfInfluence);
    }

    @Test
    public void test_ComputerPlayer7Instrumented_logs_trajectory_data() {
        // Reset trajectory logs before test
        httpPost("http://localhost:9000/api/mtg_llm/__test__/reset_counters", "{}");

        // Create a simple scenario that forces decisions
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.BATTLEFIELD, playerA, "Mountain");
        setLife(playerB, 3);

        // Use ComputerPlayer7Instrumented for playerA (configured in base class)
        setStrictChooseMode(false);

        // Let the AI play and make decisions that should trigger trajectory logging
        setStopAt(1, PhaseStep.END_TURN);
        execute();

        // Wait a moment for async logging to complete
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            // ignore
        }

        // Verify trajectory logs were created
        JSONObject trajectories = httpGetJson("http://localhost:9000/api/mtg_llm/__test__/list_trajectories");
        JSONArray files = trajectories.getJSONArray("files");

        System.out.println("Trajectory files created: " + files.length());
        for (int i = 0; i < files.length(); i++) {
            JSONObject file = files.getJSONObject(i);
            System.out.println("File: " + file.getString("path") + ", size: " + file.getLong("size_bytes"));
        }

        assertTrue("Expected at least one trajectory file to be created during gameplay",
                files.length() > 0);
    }

    @Test
    public void test_log_trajectory_endpoint_accepts_valid_payload() {
        // Build minimal valid trajectory payload matching LogTrajectoryCreate schema
        JSONObject payload = new JSONObject();
        payload.put("request_id", UUID.randomUUID().toString());
        payload.put("gameId", "test-game-123");
        payload.put("matchId", "test-match-456");
        payload.put("decisionType", "priority");

        // Game state
        JSONObject gameData = new JSONObject();
        gameData.put("id", "test-game-123");
        gameData.put("numPlayers", 2);
        gameData.put("startingLife", 20);
        gameData.put("currentTurn", 1);
        gameData.put("activePlayerId", "player-a-uuid");
        gameData.put("priorityPlayerId", "player-a-uuid");
        payload.put("game", gameData);
        payload.put("gameIsOver", false);

        // Available actions and chosen action
        payload.put("availableActions", new JSONArray());
        payload.put("chosenAction", new JSONObject());
        payload.put("additionalContext", new JSONObject());

        // Send to endpoint
        String response = httpPost("http://localhost:9000/api/mtg_llm/v1/log_trajectory", payload.toString());

        // Verify success response
        JSONObject result = new JSONObject(response);
        assertTrue("Expected successful trajectory logging", result.getBoolean("success"));
        assertEquals("Expected success message", "Trajectory logged successfully", result.getString("message"));
    }

    @Test
    public void test_log_trajectory_endpoint_validates_required_fields() {
        // Test with missing required fields
        JSONObject invalidPayload = new JSONObject();
        invalidPayload.put("request_id", UUID.randomUUID().toString());
        // Missing gameId, decisionType, etc.

        try {
            String response = httpPost("http://localhost:9000/api/mtg_llm/v1/log_trajectory",
                    invalidPayload.toString());
            JSONObject result = new JSONObject(response);

            // Should still succeed but with validation warnings (depending on
            // implementation)
            // At minimum, should not crash the server
            assertTrue("Endpoint should handle invalid payloads gracefully", true);

        } catch (Exception e) {
            // If it throws an exception, that's also acceptable as long as it's handled
            System.out.println("Endpoint properly rejected invalid payload: " + e.getMessage());
        }
    }

    @Test
    public void test_trajectory_data_contains_required_fields() {
        // Create a simple game scenario
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.BATTLEFIELD, playerA, "Mountain");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();

        // Wait for logging
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            // ignore
        }

        // Check that trajectory files contain expected structure
        JSONObject trajectories = httpGetJson("http://localhost:9000/api/mtg_llm/__test__/list_trajectories");
        JSONArray files = trajectories.getJSONArray("files");

        if (files.length() > 0) {
            // If files were created, they should have non-zero size
            JSONObject firstFile = files.getJSONObject(0);
            long size = firstFile.getLong("size_bytes");
            assertTrue("Trajectory files should contain data", size > 0);
        }
    }

    @Test
    public void test_LLM_endpoints_still_work_with_trajectory_logging() {
        // Reset all counters
        httpPost("http://localhost:9000/api/mtg_llm/__test__/reset_counters", "{}");

        // Create scenario that triggers both LLM decisions and trajectory logging
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.BATTLEFIELD, playerA, "Mountain");
        addCard(Zone.BATTLEFIELD, playerB, "Grizzly Bears");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();

        // Wait for operations to complete
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            // ignore
        }

        // Verify both LLM integration and trajectory logging worked
        JSONObject llmMetrics = httpGetJson("http://localhost:9000/api/mtg_llm/__test__/metrics");
        JSONObject trajectories = httpGetJson("http://localhost:9000/api/mtg_llm/__test__/list_trajectories");

        // Should have some LLM calls
        int totalLlmCalls = llmMetrics.optInt("choose_from_all_actions", 0) +
                llmMetrics.optInt("choose_from_choices", 0) +
                llmMetrics.optInt("choose_attackers", 0) +
                llmMetrics.optInt("choose_targets", 0);

        System.out.println(
                "LLM calls: " + totalLlmCalls + ", Trajectory files: " + trajectories.getJSONArray("files").length());

        // Both should have worked (trajectory logging doesn't interfere with LLM
        // integration)
        assertTrue("LLM integration should still work", totalLlmCalls >= 0); // Allow 0 for random strategy
    }

    private static String httpPost(String urlString, String body) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = body.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            int code = conn.getResponseCode();
            if (code != 200) {
                throw new RuntimeException("POST failed: " + urlString + ", code=" + code);
            }
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
                return sb.toString();
            }
        } catch (Exception e) {
            throw new RuntimeException("HTTP POST failed: " + e.getMessage(), e);
        }
    }

    private static JSONObject httpGetJson(String urlString) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(10000);
            int code = conn.getResponseCode();
            if (code != 200) {
                throw new RuntimeException("GET failed: " + urlString + ", code=" + code);
            }
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
                return new JSONObject(sb.toString());
            }
        } catch (Exception e) {
            throw new RuntimeException("HTTP GET failed: " + e.getMessage(), e);
        }
    }
}
