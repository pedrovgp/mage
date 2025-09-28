package org.mage.test.serverside.base;

import mage.constants.PhaseStep;
import mage.constants.Zone;
import mage.game.permanent.Permanent;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.Assume;
import java.util.Set;
import java.util.HashSet;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

public class LLMPuzzlesBase extends CardTestPlayerBaseAI {

    // Read system properties for reproducible runs
    private static final String STRATEGY = System.getProperty("strategy", "rl");
    private static final String SEED = System.getProperty("seed", "");
    private static final String LORA_PATH = System.getProperty("lora.path", "");
    private static final String LORA_HASH = System.getProperty("lora.hash", "");
    private static final String COMMIT = System.getProperty("commit", "");
    private static final String METADATA = System.getProperty("metadata", "{}");

    // Tests selection: provide a comma-separated list via -Dtests="test_A,test_B".
    // If empty, all tests run. This lets the runner invoke the full test class once
    // and each test decide whether to skip itself.
    private static Set<String> getSelectedTests() {
        String raw = System.getProperty("tests", "").trim();
        if (raw.isEmpty()) {
            return java.util.Collections.emptySet();
        }
        java.util.Set<String> s = new java.util.HashSet<>();
        for (String p : raw.split(",")) {
            s.add(p.trim());
        }
        return s;
    }

    private static final java.util.Set<String> SELECTED_TESTS = getSelectedTests();

    private static boolean shouldRun(String testName) {
        // If no explicit selection provided, run everything.
        return SELECTED_TESTS.isEmpty() || SELECTED_TESTS.contains(testName);
    }

    /*
     * Test helpers to reduce repeated boilerplate across puzzle tests.
     * Usage in a test:
     * beginPuzzle("test_ID_puzzle_llm_metrics", 1);
     * // test-specific setup...
     * execute();
     * finishAndSave("ID", 1);
     */
    private void beginPuzzle(String testName, int stopTurn) {
        // Reset counters and gate execution based on -Dtests
        httpPost("http://localhost:9000/api/mtg_llm/__test__/reset_counters", "{}");
        System.out.println("[RUNNING] " + testName);
        org.junit.Assume.assumeTrue("Skipping " + testName, shouldRun(testName));
        setStrictChooseMode(false);
        setStopAt(stopTurn, PhaseStep.END_TURN);
    }

    private void finishAndSave(String puzzleId, int turnsTaken) {
        // Query metrics from magellmfast
        JSONObject metrics = httpGetJson("http://localhost:9000/api/mtg_llm/__test__/metrics");
        int actions = metrics.optInt("choose_from_all_actions", 0);
        int choices = metrics.optInt("choose_from_choices", 0);
        int attackers = metrics.optInt("choose_attackers", 0);
        int targets = metrics.optInt("choose_targets", 0);

        System.out.println(puzzleId + " metrics: " + metrics.toString());

        // Assert at least one decision was made (skip when using local mageai agent
        // that doesn't call external API)
        if (!"mageai".equalsIgnoreCase(STRATEGY)) {
            assertTrue("Expected at least one external decision call", (actions + choices + attackers + targets) > 0);
        } else {
            System.out.println("Skipping external decision assertion for strategy=mageai");
        }

        // Collect performance metrics
        int lifeA = playerA.getLife();
        int lifeB = playerB.getLife();
        boolean won = lifeB <= 0;

        System.out.println("Puzzle result: PlayerA life=" + lifeA + ", PlayerB life=" + lifeB + ", PlayerA won=" + won);

        // Save metrics to file (for aggregator script)
        String artifacts = System.getProperty("artifact.dir", "tests");
        String agent = ("mageai".equalsIgnoreCase(STRATEGY) ? "ComputerPlayer7" : "ComputerPlayer8");
        saveMetricsJson(artifacts + "/metrics.json", new JSONObject()
                .put("puzzle_id", puzzleId)
                .put("agent", agent)
                .put("strategy", STRATEGY)
                .put("seed", SEED.isEmpty() ? JSONObject.NULL : Integer.parseInt(SEED))
                .put("commit", COMMIT)
                .put("lora_path", LORA_PATH.isEmpty() ? JSONObject.NULL : LORA_PATH)
                .put("lora_hash", LORA_HASH.isEmpty() ? JSONObject.NULL : LORA_HASH)
                .put("metadata", new JSONObject(METADATA))
                .put("actions", actions)
                .put("choices", choices)
                .put("attackers", attackers)
                .put("targets", targets)
                .put("playerA_life", lifeA)
                .put("playerB_life", lifeB)
                .put("won", won)
                .put("turns_taken", turnsTaken));
    }

    // Concurrency-safe helper to POST JSON to file
    private static final Object METRICS_FILE_LOCK = new Object();

    private static void saveMetricsJson(String path, JSONObject obj) {
        try {
            java.io.File f = new java.io.File(path);
            java.io.File parent = f.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            synchronized (METRICS_FILE_LOCK) {
                try (java.io.FileWriter fw = new java.io.FileWriter(f, true)) {
                    fw.write(obj.toString() + System.lineSeparator());
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to save metrics: " + path + " (" + e.getMessage() + ")");
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
