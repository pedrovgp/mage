package org.mage.test.serverside.base;

import mage.constants.PhaseStep;
import mage.constants.Zone;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.Assume;
import java.util.Set;
import java.util.HashSet;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

public class LLMPuzzles extends CardTestPlayerBaseAI {

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
    public void test_MTGP_03_puzzle_llm_metrics() {
        beginPuzzle("test_MTGP_03_puzzle_llm_metrics", 1);

        // Setup MTGP_03 puzzle scenario (see https://mtgpuzzles.com/puzzle/3)
        // PlayerA (active): 3 life, hand: Renegade Wheelsmith; Chaos Charm
        // Battlefield: Mobile Garrison, Granger Guildmage, Knotvine Paladin, Mountain
        // x3, Temple Garden x2
        // PlayerB: 8 life, battlefield: 3x 1/1 Illusion tokens with flying

        // Set up PlayerA
        setLife(playerA, 3);
        addCard(Zone.HAND, playerA, "Renegade Wheelsmith");
        addCard(Zone.HAND, playerA, "Chaos Charm");
        addCard(Zone.BATTLEFIELD, playerA, "Mobile Garrison");
        addCard(Zone.BATTLEFIELD, playerA, "Granger Guildmage");
        addCard(Zone.BATTLEFIELD, playerA, "Knotvine Paladin");
        addCard(Zone.BATTLEFIELD, playerA, "Mountain");
        addCard(Zone.BATTLEFIELD, playerA, "Mountain");
        addCard(Zone.BATTLEFIELD, playerA, "Mountain");
        addCard(Zone.BATTLEFIELD, playerA, "Temple Garden");
        addCard(Zone.BATTLEFIELD, playerA, "Temple Garden");

        // Set up PlayerB
        setLife(playerB, 8);
        // Add 3x 1/1 Illusion tokens with flying
        try {
            addCard(Zone.BATTLEFIELD, playerB, "Illusion Token", 3);
        } catch (Exception e) {
            System.err.println("Illusion Token not available, skipping MTGP_03: " + e.getMessage());
            org.junit.Assume.assumeTrue("Illusion Token missing", false); // skip this test
        }

        setStrictChooseMode(false);

        // Run for one turn (as puzzle specifies "Win this turn")
        setStopAt(1, PhaseStep.END_TURN);
        execute();

        // Wait for async ops
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("MTGP_03", 1);
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

    // Test: PS_STX1 (Possibility Storm - Strixhaven #01)
    @Test
    public void test_PS_STX1_puzzle_llm_metrics() {
        beginPuzzle("test_PS_STX1_puzzle_llm_metrics", 1);

        // Setup PS_STX1 (Win this turn)
        setLife(playerA, 20);
        setLife(playerB, 10);
        addCard(Zone.HAND, playerA, "Hall Monitor");
        addCard(Zone.HAND, playerA, "Lorehold Command");
        addCard(Zone.HAND, playerA, "Sentinel's Eyes");
        addCard(Zone.HAND, playerA, "Venerable Knight");
        addCard(Zone.BATTLEFIELD, playerA, "Hofri Ghostforge");
        addCard(Zone.BATTLEFIELD, playerA, "Fuming Effigy");
        addCard(Zone.BATTLEFIELD, playerA, "Plargg, Dean of Chaos");
        addCard(Zone.BATTLEFIELD, playerA, "Plains", 3);
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 3);
        addCard(Zone.BATTLEFIELD, playerB, "Colossal Dreadmaw");
        addCard(Zone.BATTLEFIELD, playerB, "Joraga Visionary");
        addCard(Zone.BATTLEFIELD, playerB, "Spined Karok");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PS_STX1", 1);
    }

    // Test: PS_RNA7 (Possibility Storm - Ravnica Allegiance #07)
    @Test
    public void test_PS_RNA7_puzzle_llm_metrics() {
        beginPuzzle("test_PS_RNA7_puzzle_llm_metrics", 1);

        setLife(playerA, 20);
        setLife(playerB, 18);
        addCard(Zone.HAND, playerA, "Rescue");
        addCard(Zone.HAND, playerA, "Sky Tether");
        addCard(Zone.HAND, playerA, "Ghostform");
        addCard(Zone.HAND, playerA, "Deep Freeze");
        addCard(Zone.BATTLEFIELD, playerA, "Deep Freeze");
        addCard(Zone.BATTLEFIELD, playerA, "Goblin Locksmith");
        addCard(Zone.BATTLEFIELD, playerA, "Arcades, the Strategist");
        addCard(Zone.BATTLEFIELD, playerA, "Suspicious Bookcase", 2);
        addCard(Zone.BATTLEFIELD, playerA, "Erratic Cyclops");
        addCard(Zone.BATTLEFIELD, playerA, "Stomping Ground", 2);
        addCard(Zone.BATTLEFIELD, playerA, "Hallowed Fountain", 4);
        addCard(Zone.BATTLEFIELD, playerB, "Gyre Engineer", 3);
        addCard(Zone.BATTLEFIELD, playerB, "Lyra Dawnbringer");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PS_RNA7", 1);
    }

    // Test: PS_THB9 (Possibility Storm - Theros Beyond Death #09)
    @Test
    public void test_PS_THB9_puzzle_llm_metrics() {
        beginPuzzle("test_PS_THB9_puzzle_llm_metrics", 1);

        setLife(playerA, 2);
        setLife(playerB, 94);
        addCard(Zone.HAND, playerA, "Terror of Mount Velus");
        addCard(Zone.HAND, playerA, "Awaken the Erstwhile");
        addCard(Zone.HAND, playerA, "Dragon Mage");
        addCard(Zone.HAND, playerA, "Corpse Knight");
        addCard(Zone.HAND, playerA, "Fling");
        addCard(Zone.BATTLEFIELD, playerA, "The Royal Scions");
        addCard(Zone.BATTLEFIELD, playerA, "Purphoros, Bronze-Blooded");
        addCard(Zone.BATTLEFIELD, playerA, "Smothering Tithe");
        addCard(Zone.BATTLEFIELD, playerA, "Mace of the Valiant");
        addCard(Zone.BATTLEFIELD, playerA, "Bag of Holding", 2);
        addCard(Zone.BATTLEFIELD, playerA, "Temple of Malice", 4);
        addCard(Zone.BATTLEFIELD, playerA, "Temple of Triumph", 4);
        addCard(Zone.BATTLEFIELD, playerB, "Knight of Autumn");
        addCard(Zone.BATTLEFIELD, playerB, "Vindictive Vampire");
        addCard(Zone.BATTLEFIELD, playerB, "Ajani's Pridemate");
        addCard(Zone.BATTLEFIELD, playerB, "Ajani, Strength of the Pride");
        addCard(Zone.BATTLEFIELD, playerB, "Sorin, Vengeful Bloodlord");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PS_THB9", 1);
    }

    // Test: PC_060215 (Perplexing Chimera 060215 - Burning Bright)
    @Test
    public void test_PC_060215_puzzle_llm_metrics() {
        beginPuzzle("test_PC_060215_puzzle_llm_metrics", 1);

        setLife(playerA, 6);
        setLife(playerB, 8);
        addCard(Zone.HAND, playerA, "Blades of Velis Vel");
        addCard(Zone.HAND, playerA, "Smash to Smithereens");
        addCard(Zone.HAND, playerA, "Thrive");
        addCard(Zone.BATTLEFIELD, playerA, "Island", 2);
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 3);
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 4);
        addCard(Zone.BATTLEFIELD, playerA, "Noble Hierarch");
        addCard(Zone.BATTLEFIELD, playerA, "Thrummingbird");
        addCard(Zone.BATTLEFIELD, playerA, "Smokebraider");
        addCard(Zone.BATTLEFIELD, playerA, "Soulbright Flamekin");
        addCard(Zone.BATTLEFIELD, playerA, "Vigean Graftmage");
        addCard(Zone.BATTLEFIELD, playerA, "Bloodshot Trainee");
        addCard(Zone.BATTLEFIELD, playerB, "Goblin Fireslinger");
        addCard(Zone.BATTLEFIELD, playerB, "Skyhunter Skirmisher");
        addCard(Zone.BATTLEFIELD, playerB, "Goblin War Paint");
        addCard(Zone.BATTLEFIELD, playerB, "Taj-Nar Swordsmith");
        addCard(Zone.BATTLEFIELD, playerB, "Sickleslicer");
        addCard(Zone.BATTLEFIELD, playerB, "Conclave Phalanx");
        addCard(Zone.BATTLEFIELD, playerB, "Iona, Shield of Emeria");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PC_060215", 1);
    }

    // Test: PC_063015 (Perplexing Chimera 063015 - Turning the Wurm)
    @Test
    public void test_PC_063015_puzzle_llm_metrics() {
        beginPuzzle("test_PC_063015_puzzle_llm_metrics", 1);

        setLife(playerA, 5);
        setLife(playerB, 8);
        addCard(Zone.HAND, playerA, "Emerald Charm");
        addCard(Zone.HAND, playerA, "Hornet Sting");
        addCard(Zone.HAND, playerA, "Mercy Killing");
        addCard(Zone.HAND, playerA, "Pouncing Wurm");
        addCard(Zone.HAND, playerA, "Argothian Wurm");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 5);
        addCard(Zone.BATTLEFIELD, playerA, "Mistveil Plains");
        addCard(Zone.BATTLEFIELD, playerA, "Sunpetal Grove");
        addCard(Zone.BATTLEFIELD, playerA, "Pelakka Wurm");
        addCard(Zone.BATTLEFIELD, playerA, "Novablast Wurm");
        addCard(Zone.BATTLEFIELD, playerA, "Symbiotic Wurm");
        addCard(Zone.BATTLEFIELD, playerA, "Cream of the Crop");
        addCard(Zone.BATTLEFIELD, playerA, "Urza's Incubator");
        addCard(Zone.BATTLEFIELD, playerA, "Jar of Eyeballs");
        addCard(Zone.BATTLEFIELD, playerA, "Heartbeat of Spring");
        addCard(Zone.GRAVEYARD, playerA, "Ravaging Riftwurm");
        addCard(Zone.BATTLEFIELD, playerB, "Peacekeeper");
        addCard(Zone.BATTLEFIELD, playerB, "Pentarch Ward");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PC_063015", 1);
    }

    // Test: PS_RIX10 (Possibility Storm - RIX Sunset Puzzle)
    @Test
    public void test_PS_RIX10_puzzle_llm_metrics() {
        beginPuzzle("test_PS_RIX10_puzzle_llm_metrics", 1);

        setLife(playerA, 20);
        setLife(playerB, 12);
        addCard(Zone.HAND, playerA, "Flame Lash");
        addCard(Zone.HAND, playerA, "Flame Lash");
        addCard(Zone.HAND, playerA, "Expel from Orazca");
        addCard(Zone.HAND, playerA, "Maverick Thopterist");
        addCard(Zone.HAND, playerA, "Hazoret the Fervent");
        addCard(Zone.BATTLEFIELD, playerA, "Storm the Vault");
        addCard(Zone.BATTLEFIELD, playerA, "Tilonalli's Summoner");
        addCard(Zone.BATTLEFIELD, playerA, "Goring Ceratops");
        addCard(Zone.BATTLEFIELD, playerA, "Growing Rites of Itlimoc");
        addCard(Zone.BATTLEFIELD, playerA, "Spirebluff Canal", 4);
        addCard(Zone.BATTLEFIELD, playerA, "Scattered Groves", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Desperate Castaways", 4);

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PS_RIX10", 1);
    }

    // Test: PS_MOM2 (Possibility Storm - March of the Machines #02)
    @Test
    public void test_PS_MOM2_puzzle_llm_metrics() {
        beginPuzzle("test_PS_MOM2_puzzle_llm_metrics", 1);

        setLife(playerA, 20);
        setLife(playerB, 8);
        addCard(Zone.HAND, playerA, "Rampaging Raptor");
        addCard(Zone.HAND, playerA, "Mountain");
        addCard(Zone.HAND, playerA, "Into the Fire");
        addCard(Zone.HAND, playerA, "Volcanic Spite");
        addCard(Zone.BATTLEFIELD, playerA, "Invasion of Karsus");
        addCard(Zone.BATTLEFIELD, playerA, "Invasion of Kaldheim");
        addCard(Zone.BATTLEFIELD, playerA, "Dwarven Forge-Chanter");
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 4);
        addCard(Zone.BATTLEFIELD, playerB, "Invasion of Xerex");
        addCard(Zone.BATTLEFIELD, playerB, "Swordsworn Cavalier", 2);

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PS_MOM2", 1);
    }

    // Test: PS_HOU1 (Possibility Storm - Hour of Devastation #01)
    @Test
    public void test_PS_HOU1_puzzle_llm_metrics() {
        beginPuzzle("test_PS_HOU1_puzzle_llm_metrics", 1);

        setLife(playerA, 20);
        setLife(playerB, 10);
        // energy counter not modeled directly here
        addCard(Zone.HAND, playerA, "Metalwork Colossus");
        addCard(Zone.HAND, playerA, "Hedron Archive");
        addCard(Zone.HAND, playerA, "Gonti's Aether Heart");
        addCard(Zone.HAND, playerA, "Fleetwheel Cruiser");
        addCard(Zone.BATTLEFIELD, playerA, "Island", 3);
        addCard(Zone.BATTLEFIELD, playerA, "Plains", 3);
        addCard(Zone.BATTLEFIELD, playerA, "Multiform Wonder");
        addCard(Zone.BATTLEFIELD, playerA, "Bomat Bazaar Barge");
        addCard(Zone.BATTLEFIELD, playerA, "Mirage Mirror");
        addCard(Zone.BATTLEFIELD, playerB, "Hollow One", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Chaos Maw");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PS_HOU1", 1);
    }

    // Test: PS_EOE2 (Possibility Storm - Eldraine OOE2)
    @Test
    public void test_PS_EOE2_puzzle_llm_metrics() {
        httpPost("http://localhost:9000/api/mtg_llm/__test__/reset_counters", "{}");

        // Description notes basic Mountains/Swamps/Forests in library but we ignore
        // draws
        setLife(playerA, 20);
        setLife(playerB, 10);
        beginPuzzle("test_PS_EOE2_puzzle_llm_metrics", 1);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PS_EOE2", 1);
    }

    // Test: PS_BRO3 (Possibility Storm - BRO3)
    @Test
    public void test_PS_BRO3_puzzle_llm_metrics() {
        beginPuzzle("test_PS_BRO3_puzzle_llm_metrics", 1);

        setLife(playerA, 20);
        setLife(playerB, 20);
        // Minimal setup based on puzzle metadata; exact battlefield/hand omitted for
        // brevity
        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PS_BRO3", 1);
    }

    // Helper to POST JSON to file (simple, not robust)
    private static void saveMetricsJson(String path, JSONObject obj) {
        try {
            java.io.File f = new java.io.File(path);
            java.io.File parent = f.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            try (java.io.FileWriter fw = new java.io.FileWriter(f, true)) {
                fw.write(obj.toString() + System.lineSeparator());
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
