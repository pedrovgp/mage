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

public class LLMPuzzles extends LLMPuzzlesBase {

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

    @Test
    public void test_MTGP_01_puzzle_llm_metrics() {
        beginPuzzle("test_MTGP_01_puzzle_llm_metrics", 1);

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

        finishAndSave("MTGP_01", 1);
    }

    @Test
    public void test_PS_M207_puzzle_llm_metrics() {
        // PS_M207.pzl: Possibility Storm - Magic Core Set 2020 #07
        // Goal: Win this turn. Your Dauntless Bodyguard chose Shanna, Sisay's Legacy
        // when it entered the battlefield.
        // humanlife=20
        // ailife=7
        // turn=1
        // activeplayer=human
        // activephase=MAIN1
        // humanhand=Strength of the Pack;Depose // Deploy;Storm the Citadel;Masterful
        // Replication;Short Sword
        // humanbattlefield=Gideon Blackblade|Counters:LOYALTY=6;Blackblade
        // Reforged;Omnispell Adept;Shanna, Sisay's Legacy|Id:9;Sigiled Sword of
        // Valeron|AttachedTo:9;Dauntless
        // Bodyguard|ChosenCards:9|Id:10|NoETBTrigs;Forebear's
        // Blade|AttachedTo:10;Hallowed Fountain|NoETBTrigs;Hallowed
        // Fountain|NoETBTrigs;Hallowed Fountain|NoETBTrigs;Temple
        // Garden|NoETBTrigs;Temple Garden|NoETBTrigs;Temple Garden|NoETBTrigs
        // aibattlefield=Charity Extractor;Looming Altisaur;Gate Colossus;Looming
        // Altisaur;Charity Extractor

        setLife(playerA, 20);
        setLife(playerB, 7);

        addCard(Zone.HAND, playerA, "Strength of the Pack");
        addCard(Zone.HAND, playerA, "Depose // Deploy");
        addCard(Zone.HAND, playerA, "Storm the Citadel");
        addCard(Zone.HAND, playerA, "Masterful Replication");
        addCard(Zone.HAND, playerA, "Short Sword");

        addCard(Zone.BATTLEFIELD, playerA, "Gideon Blackblade");
        addCounters(Zone.BATTLEFIELD, playerA, "Gideon Blackblade", CounterType.LOYALTY, 6);
        addCard(Zone.BATTLEFIELD, playerA, "Blackblade Reforged");
        addCard(Zone.BATTLEFIELD, playerA, "Omnispell Adept");
        addCard(Zone.BATTLEFIELD, playerA, "Shanna, Sisay's Legacy"); // ID:9
        addCard(Zone.BATTLEFIELD, playerA, "Sigiled Sword of Valeron"); // AttachedTo:9
        addCard(Zone.BATTLEFIELD, playerA, "Dauntless Bodyguard"); // ID:10, ChosenCards:9
        addCard(Zone.BATTLEFIELD, playerA, "Forebear's Blade"); // AttachedTo:10
        addCard(Zone.BATTLEFIELD, playerA, "Hallowed Fountain");
        addCard(Zone.BATTLEFIELD, playerA, "Hallowed Fountain");
        addCard(Zone.BATTLEFIELD, playerA, "Hallowed Fountain");
        addCard(Zone.BATTLEFIELD, playerA, "Temple Garden");
        addCard(Zone.BATTLEFIELD, playerA, "Temple Garden");
        addCard(Zone.BATTLEFIELD, playerA, "Temple Garden");

        addCard(Zone.BATTLEFIELD, playerB, "Charity Extractor");
        addCard(Zone.BATTLEFIELD, playerB, "Looming Altisaur");
        addCard(Zone.BATTLEFIELD, playerB, "Gate Colossus");
        addCard(Zone.BATTLEFIELD, playerB, "Looming Altisaur");
        addCard(Zone.BATTLEFIELD, playerB, "Charity Extractor");

        // Attach equipment as per .pzl
        runCode("Attach equipment", 1, PhaseStep.PRECOMBAT_MAIN, playerA, (info, player, game) -> {
            Permanent shanna = game.getBattlefield().getAllPermanents().stream()
                    .filter(p -> p.getName().equals("Shanna, Sisay's Legacy"))
                    .findFirst().orElse(null);
            Permanent sword = game.getBattlefield().getAllPermanents().stream()
                    .filter(p -> p.getName().equals("Sigiled Sword of Valeron"))
                    .findFirst().orElse(null);
            if (shanna != null && sword != null) {
                sword.attachTo(shanna.getId(), null, game);
            }

            Permanent bodyguard = game.getBattlefield().getAllPermanents().stream()
                    .filter(p -> p.getName().equals("Dauntless Bodyguard"))
                    .findFirst().orElse(null);
            Permanent blade = game.getBattlefield().getAllPermanents().stream()
                    .filter(p -> p.getName().equals("Forebear's Blade"))
                    .findFirst().orElse(null);
            if (bodyguard != null && blade != null) {
                blade.attachTo(bodyguard.getId(), null, game);
            }
        });

        execute();

        assertWonTheGame(playerA);
    }

    @Test
    public void test_MTGP_04_puzzle_llm_metrics() {
        beginPuzzle("test_MTGP_04_puzzle_llm_metrics", 1);

        // Setup MTGP_04 puzzle scenario (see https://mtgpuzzles.com/puzzle/4)
        // PlayerA (active): 5 life, hand: Dark Withering; Tahngarth's Rage; Alms of the
        // Vein
        // Battlefield: Frilled Deathspitter, Niblis of Dusk, Supreme Phantom,
        // Pyrohemia, Mountain x5, Watery Grave x2
        // PlayerB: 23 life, battlefield: Jin-Gitaxias, Core Augur

        // Set up PlayerA
        setLife(playerA, 5);
        addCard(Zone.HAND, playerA, "Dark Withering");
        addCard(Zone.HAND, playerA, "Tahngarth's Rage");
        addCard(Zone.HAND, playerA, "Alms of the Vein");
        addCard(Zone.BATTLEFIELD, playerA, "Frilled Deathspitter");
        addCard(Zone.BATTLEFIELD, playerA, "Niblis of Dusk");
        addCard(Zone.BATTLEFIELD, playerA, "Supreme Phantom");
        addCard(Zone.BATTLEFIELD, playerA, "Pyrohemia");
        addCard(Zone.BATTLEFIELD, playerA, "Mountain");
        addCard(Zone.BATTLEFIELD, playerA, "Mountain");
        addCard(Zone.BATTLEFIELD, playerA, "Mountain");
        addCard(Zone.BATTLEFIELD, playerA, "Mountain");
        addCard(Zone.BATTLEFIELD, playerA, "Mountain");
        addCard(Zone.BATTLEFIELD, playerA, "Watery Grave");
        addCard(Zone.BATTLEFIELD, playerA, "Watery Grave");

        // Set up PlayerB
        setLife(playerB, 23);
        addCard(Zone.BATTLEFIELD, playerB, "Jin-Gitaxias, Core Augur");

        setStrictChooseMode(false);

        // Run for one turn (as puzzle specifies "Win before opponent's next turn")
        setStopAt(1, PhaseStep.END_TURN);
        execute();

        // Wait for async ops
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("MTGP_04", 1);
    }

    @Test
    public void test_MTGP_06_puzzle_llm_metrics() {
        beginPuzzle("test_MTGP_06_puzzle_llm_metrics", 1);

        // Setup MTGP_06 puzzle scenario (see https://mtgpuzzles.com/puzzle/6)
        // PlayerA (active): 10 life, hand: Blazing Salvo
        // Battlefield: Oreskos Sun Guide (tapped), Nocturnal Feeder, Axis of Mortality,
        // Triskaidekaphobia, Savai Triome x3 (tapped)
        // PlayerB: 14 life, battlefield: Bogardan Firefiend

        // Set up PlayerA
        setLife(playerA, 10);
        addCard(Zone.HAND, playerA, "Blazing Salvo");
        addCard(Zone.BATTLEFIELD, playerA, "Oreskos Sun Guide", 1, true); // tapped
        addCard(Zone.BATTLEFIELD, playerA, "Nocturnal Feeder");
        addCard(Zone.BATTLEFIELD, playerA, "Axis of Mortality");
        addCard(Zone.BATTLEFIELD, playerA, "Triskaidekaphobia");
        addCard(Zone.BATTLEFIELD, playerA, "Savai Triome", 3, true); // three tapped copies

        // Set up PlayerB
        setLife(playerB, 14);
        addCard(Zone.BATTLEFIELD, playerB, "Bogardan Firefiend");

        setStrictChooseMode(false);

        // Run for one turn (puzzle specifies "Win this turn")
        setStopAt(1, PhaseStep.END_TURN);
        execute();

        // Wait for async ops
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("MTGP_06", 1);
    }

    @Test
    public void test_MTGP_08_puzzle_llm_metrics() {
        beginPuzzle("test_MTGP_08_puzzle_llm_metrics", 1);

        // Setup MTGP_08 puzzle scenario (see https://mtgpuzzles.com/puzzle/8)
        // PlayerA (active): 2 life, hand: Scar
        // Battlefield: Dread Shade, Carnifex Demon (with 2 -1/-1 counters), Dread
        // Shade, Swamp x3
        // PlayerB: 10 life, battlefield: Ashling the Pilgrim, Cascade Bluffs x3, Shivan
        // Reef x3

        // Set up PlayerA
        setLife(playerA, 2);
        addCard(Zone.HAND, playerA, "Scar");
        addCard(Zone.BATTLEFIELD, playerA, "Dread Shade");
        addCard(Zone.BATTLEFIELD, playerA, "Carnifex Demon");
        addCard(Zone.BATTLEFIELD, playerA, "Dread Shade");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp");

        // Set up PlayerB
        setLife(playerB, 10);
        addCard(Zone.BATTLEFIELD, playerB, "Ashling the Pilgrim");
        addCard(Zone.BATTLEFIELD, playerB, "Cascade Bluffs");
        addCard(Zone.BATTLEFIELD, playerB, "Cascade Bluffs");
        addCard(Zone.BATTLEFIELD, playerB, "Cascade Bluffs");
        addCard(Zone.BATTLEFIELD, playerB, "Shivan Reef");
        addCard(Zone.BATTLEFIELD, playerB, "Shivan Reef");
        addCard(Zone.BATTLEFIELD, playerB, "Shivan Reef");

        setStrictChooseMode(false);

        // Run for one turn (puzzle specifies "Win this turn")
        setStopAt(1, PhaseStep.END_TURN);
        execute();

        // Wait for async ops
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("MTGP_08", 1);
    }

}
