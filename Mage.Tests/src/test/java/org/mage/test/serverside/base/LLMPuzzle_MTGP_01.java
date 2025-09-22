package org.mage.test.serverside.base;

import mage.constants.PhaseStep;
import mage.constants.Zone;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

public class LLMPuzzle_MTGP_01 extends CardTestPlayerBaseAI {

    @Test
    public void test_MTGP_01_puzzle_llm_metrics() {
        // Reset counters before run
        httpPost("http://localhost:9000/api/mtg_llm/__test__/reset_counters", "{}");

        // Setup MTGP_01 puzzle scenario (see https://mtgpuzzles.com/puzzle/1)
        // PlayerA (active): 5 life, hand: Banners Raised; Electrickery; Temporary
        // Insanity
        // Graveyard: Opt; Volcanic Spray
        // Battlefield: Mizzix of the Izmagnus (Tapped), Mountain, Mountain, Sulfur
        // Falls, Sulfur Falls
        // PlayerB: 4 life, battlefield: Nebelgast Herald, Treetop Ambusher

        // Set up PlayerA
        setLife(playerA, 5);
        addCard(Zone.HAND, playerA, "Banners Raised");
        addCard(Zone.HAND, playerA, "Electrickery");
        addCard(Zone.HAND, playerA, "Temporary Insanity");
        addCard(Zone.GRAVEYARD, playerA, "Opt");
        addCard(Zone.GRAVEYARD, playerA, "Volcanic Spray");
        addCard(Zone.BATTLEFIELD, playerA, "Mizzix of the Izmagnus", 1, true); // tapped
        addCard(Zone.BATTLEFIELD, playerA, "Mountain");
        addCard(Zone.BATTLEFIELD, playerA, "Mountain");
        addCard(Zone.BATTLEFIELD, playerA, "Sulfur Falls");
        addCard(Zone.BATTLEFIELD, playerA, "Sulfur Falls");

        // Set up PlayerB
        setLife(playerB, 4);
        addCard(Zone.BATTLEFIELD, playerB, "Nebelgast Herald");
        addCard(Zone.BATTLEFIELD, playerB, "Treetop Ambusher");

        setStrictChooseMode(false);

        // Run for one turn (as puzzle specifies "Win in 1 turn")
        setStopAt(1, PhaseStep.END_TURN);
        execute();

        // Wait for async ops
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        // Query metrics from magellmfast
        JSONObject metrics = httpGetJson("http://localhost:9000/api/mtg_llm/__test__/metrics");
        int actions = metrics.optInt("choose_from_all_actions", 0);
        int choices = metrics.optInt("choose_from_choices", 0);
        int attackers = metrics.optInt("choose_attackers", 0);
        int targets = metrics.optInt("choose_targets", 0);

        System.out.println("MTGP_01 metrics: " + metrics.toString());

        // Assert at least one decision was made
        assertTrue("Expected at least one external decision call", (actions + choices + attackers + targets) > 0);

        // Collect performance metrics
        int lifeA = playerA.getLife();
        int lifeB = playerB.getLife();
        boolean won = lifeB <= 0;

        System.out.println("Puzzle result: PlayerA life=" + lifeA + ", PlayerB life=" + lifeB + ", PlayerA won=" + won);

        // Save metrics to file (for aggregator script)
        saveMetricsJson("tests/metrics.json", new JSONObject()
                .put("puzzle_id", "MTGP_01")
                .put("agent", "ComputerPlayer8")
                .put("actions", actions)
                .put("choices", choices)
                .put("attackers", attackers)
                .put("targets", targets)
                .put("playerA_life", lifeA)
                .put("playerB_life", lifeB)
                .put("won", won)
                .put("turns_taken", 1));

    }

    // Helper to POST JSON to file (simple, not robust)
    private static void saveMetricsJson(String path, JSONObject obj) {
        try (java.io.FileWriter fw = new java.io.FileWriter(path, true)) {
            fw.write(obj.toString() + "\n");
        } catch (Exception e) {
            System.err.println("Failed to save metrics: " + e.getMessage());
        }
    }

    // HTTP helpers (copied from LLMIntegrationSmokeTest)
    private static void httpPost(String urlString, String body) {
        try {
            java.net.URL url = new java.net.URL(urlString);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(3000);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setDoOutput(true);
            try (java.io.OutputStream os = conn.getOutputStream()) {
                byte[] input = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
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
            java.net.URL url = new java.net.URL(urlString);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(3000);
            int code = conn.getResponseCode();
            if (code != 200) {
                throw new RuntimeException("GET failed: " + urlString + ", code=" + code);
            }
            try (java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(conn.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
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
