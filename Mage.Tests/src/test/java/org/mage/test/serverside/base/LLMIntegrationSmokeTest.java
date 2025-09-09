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
    public void test_action_choices_are_processed_through_fastapi_server_integration() {
        // Reset counters before run
        httpPost("http://localhost:9000/api/mtg_llm/__test__/reset_counters", "{}");

        // Create a scenario that forces action decisions
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.BATTLEFIELD, playerA, "Mountain");
        addCard(Zone.BATTLEFIELD, playerB, "Grizzly Bears");

        // Force the AI to make decisions
        setStrictChooseMode(false);

        // Let the AI play and make action choices
        setStopAt(1, PhaseStep.END_TURN);
        execute();

        // Query metrics and assert action choices were processed
        JSONObject metrics = httpGetJson("http://localhost:9000/api/mtg_llm/__test__/metrics");

        // At least one action choice should happen (priority/pass decisions)
        int actions = metrics.optInt("choose_from_all_actions", 0);
        assertTrue("Expected action choices to be processed, got: " + actions, actions > 0);

        // Log the actual counts for debugging
        System.out.println("Action choices test - Actions processed: " + actions);
    }

    @Test
    public void test_llm_integration_calls_are_made_for_gameplay() {
        // Reset counters before run
        httpPost("http://localhost:9000/api/mtg_llm/__test__/reset_counters", "{}");

        // Create a scenario that triggers various LLM integration calls
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.BATTLEFIELD, playerA, "Mountain");
        addCard(Zone.BATTLEFIELD, playerB, "Grizzly Bears");
        addCard(Zone.BATTLEFIELD, playerB, "Elvish Mystic");

        // Force the AI to make decisions
        setStrictChooseMode(false);

        // Let the AI play and potentially make various types of decisions
        setStopAt(1, PhaseStep.END_TURN);
        execute();

        // Query metrics and assert LLM integration calls were made
        JSONObject metrics = httpGetJson("http://localhost:9000/api/mtg_llm/__test__/metrics");

        int actions = metrics.optInt("choose_from_all_actions", 0);
        int choices = metrics.optInt("choose_from_choices", 0);
        int targets = metrics.optInt("choose_targets", 0);
        int attackers = metrics.optInt("choose_attackers", 0);

        System.out.println("LLM integration test - Actions: " + actions +
                ", Choices: " + choices + ", Targets: " + targets + ", Attackers: " + attackers);

        // Ensure some LLM integration calls were made during gameplay
        assertTrue("Expected at least one LLM call during gameplay, got: " + (actions + choices + targets + attackers),
                (actions + choices + targets + attackers) > 0);
    }

    @Test
    public void test_select_attackers_triggers_correct_endpoint() {
        // Reset counters before run
        httpPost("http://localhost:9000/api/mtg_llm/__test__/reset_counters", "{}");

        // Create a scenario with creatures that can attack
        addCard(Zone.BATTLEFIELD, playerA, "Grizzly Bears"); // 2/2 creature
        addCard(Zone.BATTLEFIELD, playerA, "Elvish Mystic"); // 1/1 creature

        // Force the AI to make attack decisions
        setStrictChooseMode(false);

        // Move to combat phase where attackers are selected
        setStopAt(1, PhaseStep.DECLARE_ATTACKERS);
        execute();

        // Query metrics and assert attacker selection was processed
        JSONObject metrics = httpGetJson("http://localhost:9000/api/mtg_llm/__test__/metrics");

        int attackers = metrics.optInt("choose_attackers", 0);
        int actions = metrics.optInt("choose_from_all_actions", 0);

        System.out.println("Attacker selection test - Attackers: " + attackers + ", Actions: " + actions);

        // At minimum, action decisions should be made during combat phase
        assertTrue("Expected at least action decisions during combat, got: " + actions, actions > 0);
        assertTrue("Expected at least attacker selections during combat, got: " + attackers, attackers > 0);

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
