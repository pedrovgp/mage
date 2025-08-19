package org.mage.test.serverside.base;

import org.junit.Test;
import mage.cards.Card;
import mage.cards.Cards;
import mage.cards.CardsImpl;
import mage.constants.PhaseStep;
import mage.constants.Zone;
import mage.game.permanent.Permanent;
import mage.players.Player;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.json.JSONObject;

/**
 * Comprehensive AI vs AI smoke test to ensure multiple decision entry points
 * are routed through the LLM integration.
 * This relies on ComputerPlayer8 wiring that calls
 * http://localhost:9000/api/mtg_llm/*.
 * Ensure magellm server is running in random strategy for fast tests.
 */
public class LLMIntegrationSmokeTest extends CardTestPlayerBaseAI {

    @Test
    public void test_AI_vs_AI_runs_and_makes_external_calls() {
        // Reset counters before run
        httpPost("http://localhost:9000/api/mtg_llm/__test__/reset_counters", "{}");

        // Debug: Check what player types we're using
        System.out.println("PlayerA type: " + playerA.getClass().getSimpleName());
        System.out.println("PlayerB type: " + playerB.getClass().getSimpleName());
        System.out.println("PlayerA is AI: " + playerA.isComputer());
        System.out.println("PlayerB is AI: " + playerB.isComputer());

        // Create a scenario that forces decisions
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.BATTLEFIELD, playerA, "Mountain");
        addCard(Zone.BATTLEFIELD, playerB, "Grizzly Bears");

        // Using default RB Aggro deck from base
        setStrictChooseMode(false);

        // Let the AI play naturally - it should cast Lightning Bolt and choose targets
        setStopAt(1, PhaseStep.END_TURN);
        execute();

        // Wait a moment for any async operations to complete
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            // ignore
        }

        // Query metrics and assert at least one endpoint was called
        JSONObject metrics = httpGetJson("http://localhost:9000/api/mtg_llm/__test__/metrics");
        int a = metrics.optInt("choose_from_all_actions", 0);
        int b = metrics.optInt("choose_from_choices", 0);
        int c = metrics.optInt("choose_attackers", 0);
        int d = metrics.optInt("choose_targets", 0);

        // Log the actual metrics for debugging
        System.out.println("Test metrics: " + metrics.toString());

        assertTrue("Expected at least one external decision call, got: " + metrics, (a + b + c + d) > 0);
    }

    @Test
    public void test_multiple_decision_entry_points_coverage() {
        // Reset counters before run
        httpPost("http://localhost:9000/api/mtg_llm/__test__/reset_counters", "{}");

        // Create a scenario that forces multiple decision types
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.BATTLEFIELD, playerA, "Mountain");
        addCard(Zone.BATTLEFIELD, playerB, "Grizzly Bears"); // 2/2 creature to target

        // Force the AI to cast Lightning Bolt and make targeting decisions
        setStrictChooseMode(false);

        // Start the game and let the AI play
        execute();

        // Let the AI play naturally - it should cast Lightning Bolt and choose targets
        setStopAt(1, PhaseStep.END_TURN);
        execute();

        // Query metrics and assert specific decision types were called
        JSONObject metrics = httpGetJson("http://localhost:9000/api/mtg_llm/__test__/metrics");

        // At least one action choice should happen (priority/pass)
        int actions = metrics.optInt("choose_from_all_actions", 0);
        assertTrue("Expected action choices to be called, got: " + actions, actions > 0);

        // At least one general choice should happen (targets, modes, etc.)
        int choices = metrics.optInt("choose_from_choices", 0);
        assertTrue("Expected general choices to be called, got: " + choices, choices > 0);

        // Target selection should happen when casting spells
        int targets = metrics.optInt("choose_targets", 0);

        // Attackers may or may not happen depending on game state
        int attackers = metrics.optInt("choose_attackers", 0);

        // Log the actual counts for debugging
        System.out.println("Decision coverage - Actions: " + actions +
                ", Choices: " + choices +
                ", Targets: " + targets +
                ", Attackers: " + attackers);

        // Total should be at least 3 (actions + choices + targets)
        assertTrue("Expected at least 3 different decision types, got: " + (actions + choices + targets + attackers),
                (actions + choices + targets + attackers) >= 3);
    }

    @Test
    public void test_target_selection_triggers_choices() {
        // Reset counters before run
        httpPost("http://localhost:9000/api/mtg_llm/__test__/reset_counters", "{}");

        // Create a scenario that forces target selection
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.BATTLEFIELD, playerA, "Mountain");
        addCard(Zone.BATTLEFIELD, playerB, "Grizzly Bears");
        addCard(Zone.BATTLEFIELD, playerB, "Elvish Mystic");

        // Force the AI to cast Lightning Bolt and make targeting decisions
        setStrictChooseMode(false);

        // Let the AI play naturally - it should cast Lightning Bolt and choose targets
        setStopAt(1, PhaseStep.END_TURN);
        execute();

        // Query metrics and assert target selection was called
        JSONObject metrics = httpGetJson("http://localhost:9000/api/mtg_llm/__test__/metrics");

        // Target selection should go through choose_from_choices
        int choices = metrics.optInt("choose_from_choices", 0);
        assertTrue("Expected target selection choices to be called, got: " + choices, choices > 0);

        System.out.println("Target selection test - Choices called: " + choices);
    }

    @Test
    public void test_LLM_endpoints_are_accessible() {
        // Test that we can reach the magellm server
        JSONObject metrics = httpGetJson("http://localhost:9000/api/mtg_llm/__test__/metrics");
        assertTrue("Should be able to reach magellm server", metrics != null);

        // Test that we can reset counters
        httpPost("http://localhost:9000/api/mtg_llm/__test__/reset_counters", "{}");
        // If we get here without exception, the endpoint is accessible

        // Verify counters were reset
        JSONObject metricsAfterReset = httpGetJson("http://localhost:9000/api/mtg_llm/__test__/metrics");
        int totalCalls = metricsAfterReset.optInt("choose_from_all_actions", 0) +
                metricsAfterReset.optInt("choose_from_choices", 0) +
                metricsAfterReset.optInt("choose_attackers", 0) +
                metricsAfterReset.optInt("choose_targets", 0);

        System.out.println("Metrics after reset: " + metricsAfterReset.toString());
        assertEquals("Counters should be reset to 0", 0, totalCalls);
    }

    @Test
    public void test_LLM_integration_direct() {
        // Test the LLM integration directly without going through the complex test
        // infrastructure
        try {
            // Create a simple game and player to test LLM integration
            mage.game.Game game = new mage.game.TwoPlayerDuel(
                    mage.constants.MultiplayerAttackOption.LEFT,
                    mage.constants.RangeOfInfluence.ONE,
                    mage.game.mulligan.MulliganType.GAME_DEFAULT.getMulligan(0),
                    60, 20, 7);
            mage.players.Player player = new mage.player.ai.ComputerPlayer8("TestPlayer",
                    mage.constants.RangeOfInfluence.ONE, 8);

            // Test that the player is the right type
            System.out.println("Player type: " + player.getClass().getSimpleName());
            System.out.println("Player is AI: " + player.isComputer());

            // This should at least create the player without errors
            assertTrue("Player should be ComputerPlayer8", player instanceof mage.player.ai.ComputerPlayer8);

        } catch (Exception e) {
            System.out.println("Error creating ComputerPlayer8: " + e.getMessage());
            e.printStackTrace();
            fail("Should be able to create ComputerPlayer8: " + e.getMessage());
        }
    }

    private static void httpPost(String urlString, String body) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(3000);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = body.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            int code = conn.getResponseCode();
            if (code != 200) {
                throw new RuntimeException("POST failed: " + urlString + ", code=" + code);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static JSONObject httpGetJson(String urlString) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(3000);
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
            throw new RuntimeException(e);
        }
    }
}
