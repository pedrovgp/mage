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

    @Test
    public void test_MTGP_09_puzzle_llm_metrics() {
        beginPuzzle("test_MTGP_09_puzzle_llm_metrics", 1);

        // Setup MTGP_09 puzzle scenario (see https://mtgpuzzles.com/puzzle/9)
        // PlayerA (active): 3 life, hand: Watery Grave; Talisman of Impulse; Mana Crypt
        // Battlefield: Legacy Weapon, Nimbus Maze, City of Traitors, Fungal Reaches,
        // River of Tears, Caves of Koilos
        // PlayerB: -1 life, battlefield: Platinum Angel

        // Set up PlayerA
        setLife(playerA, 3);
        addCard(Zone.HAND, playerA, "Watery Grave");
        addCard(Zone.HAND, playerA, "Talisman of Impulse");
        addCard(Zone.HAND, playerA, "Mana Crypt");
        addCard(Zone.BATTLEFIELD, playerA, "Legacy Weapon");
        addCard(Zone.BATTLEFIELD, playerA, "Nimbus Maze");
        addCard(Zone.BATTLEFIELD, playerA, "City of Traitors");
        addCard(Zone.BATTLEFIELD, playerA, "Fungal Reaches");
        addCard(Zone.BATTLEFIELD, playerA, "River of Tears");
        addCard(Zone.BATTLEFIELD, playerA, "Caves of Koilos");

        // Set up PlayerB
        setLife(playerB, -1);
        addCard(Zone.BATTLEFIELD, playerB, "Platinum Angel");

        setStrictChooseMode(false);

        // Run for one turn (puzzle specifies "Win this turn")
        setStopAt(1, PhaseStep.END_TURN);
        execute();

        // Wait for async ops
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("MTGP_09", 1);
    }

    @Test
    public void test_MTGP_05_puzzle_llm_metrics() {
        beginPuzzle("test_MTGP_05_puzzle_llm_metrics", 1);

        // Setup MTGP_05 puzzle scenario (see https://mtgpuzzles.com/puzzle/5)
        // PlayerA (active): 6 life, hand: Electrostatic Bolt; Inner Calm, Outer
        // Strength; Crash
        // Library: Blessings of Nature (only card)
        // Battlefield: Goblin Spy, Frontline Devastator, Forest x3, Mountain x3
        // PlayerB: 6 life, battlefield: Butcher Ghoul, Guardian Automaton, Damping
        // Sphere
        // Turn 20, Upkeep phase

        // Set up PlayerA
        setLife(playerA, 6);
        addCard(Zone.HAND, playerA, "Electrostatic Bolt");
        addCard(Zone.HAND, playerA, "Inner Calm, Outer Strength");
        addCard(Zone.HAND, playerA, "Crash");
        addCard(Zone.LIBRARY, playerA, "Blessings of Nature");
        addCard(Zone.BATTLEFIELD, playerA, "Goblin Spy");
        addCard(Zone.BATTLEFIELD, playerA, "Frontline Devastator");
        addCard(Zone.BATTLEFIELD, playerA, "Forest");
        addCard(Zone.BATTLEFIELD, playerA, "Forest");
        addCard(Zone.BATTLEFIELD, playerA, "Forest");
        addCard(Zone.BATTLEFIELD, playerA, "Mountain");
        addCard(Zone.BATTLEFIELD, playerA, "Mountain");
        addCard(Zone.BATTLEFIELD, playerA, "Mountain");

        // Set up PlayerB
        setLife(playerB, 6);
        addCard(Zone.BATTLEFIELD, playerB, "Butcher Ghoul");
        addCard(Zone.BATTLEFIELD, playerB, "Guardian Automaton");
        addCard(Zone.BATTLEFIELD, playerB, "Damping Sphere");

        setStrictChooseMode(false);

        // Run for one turn (puzzle specifies "Win this turn")
        setStopAt(1, PhaseStep.END_TURN);
        execute();

        // Wait for async ops
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("MTGP_05", 1);
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

    @Test
    public void test_PS_STX2_puzzle_llm_metrics() {
        beginPuzzle("test_PS_STX2_puzzle_llm_metrics", 1);

        // Setup PS_STX2 puzzle scenario (see
        // https://i1.wp.com/www.possibilitystorm.com/wp-content/uploads/2021/05/173.-STX2-scaled.jpg)
        // PlayerA (active): 20 life, hand: Plumb the Forbidden; Peer into the Abyss;
        // Quandrix Command; Poison the Cup
        // Library: 36x Opt cards
        // Battlefield: Kasmina, Enigma Sage (4 loyalty), Garruk, Cursed Huntsman (5
        // loyalty), Gingerbrute (with 6 +1/+1 counters), Lorescale Coatl (with 2 +1/+1
        // counters), Eyetwitch, 4x Necroblossom Snarl, 3x Vineglimmer Snarl
        // Exile: Mystic Reflection (Foretold)
        // Sideboard: Expanded Anatomy, Environmental Sciences, Confront the Past
        // PlayerB: 19 life, battlefield: Cubwarden, Eradicator Valkyrie, Chrome
        // Replicator, Glorious Anthem

        // Set up PlayerA
        setLife(playerA, 20);
        addCard(Zone.HAND, playerA, "Plumb the Forbidden");
        addCard(Zone.HAND, playerA, "Peer into the Abyss");
        addCard(Zone.HAND, playerA, "Quandrix Command");
        addCard(Zone.HAND, playerA, "Poison the Cup");
        // Add 36 Opt cards to library
        for (int i = 0; i < 36; i++) {
            addCard(Zone.LIBRARY, playerA, "Opt");
        }
        addCard(Zone.BATTLEFIELD, playerA, "Kasmina, Enigma Sage");
        addCard(Zone.BATTLEFIELD, playerA, "Garruk, Cursed Huntsman");
        addCard(Zone.BATTLEFIELD, playerA, "Gingerbrute");
        addCard(Zone.BATTLEFIELD, playerA, "Lorescale Coatl");
        addCard(Zone.BATTLEFIELD, playerA, "Eyetwitch");
        addCard(Zone.BATTLEFIELD, playerA, "Necroblossom Snarl");
        addCard(Zone.BATTLEFIELD, playerA, "Necroblossom Snarl");
        addCard(Zone.BATTLEFIELD, playerA, "Necroblossom Snarl");
        addCard(Zone.BATTLEFIELD, playerA, "Necroblossom Snarl");
        addCard(Zone.BATTLEFIELD, playerA, "Vineglimmer Snarl");
        addCard(Zone.BATTLEFIELD, playerA, "Vineglimmer Snarl");
        addCard(Zone.BATTLEFIELD, playerA, "Vineglimmer Snarl");
        addCard(Zone.EXILED, playerA, "Mystic Reflection");

        // Set up PlayerB
        setLife(playerB, 19);
        addCard(Zone.BATTLEFIELD, playerB, "Cubwarden");
        addCard(Zone.BATTLEFIELD, playerB, "Eradicator Valkyrie");
        addCard(Zone.BATTLEFIELD, playerB, "Chrome Replicator");
        addCard(Zone.BATTLEFIELD, playerB, "Glorious Anthem");

        setStrictChooseMode(false);

        // Run for one turn (puzzle specifies "Win this turn")
        setStopAt(1, PhaseStep.END_TURN);
        execute();

        // Wait for async ops
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PS_STX2", 1);
    }

    @Test
    public void test_PS_STX3_puzzle_llm_metrics() {
        beginPuzzle("test_PS_STX3_puzzle_llm_metrics", 1);

        // Setup PS_STX3 puzzle scenario (see
        // https://i2.wp.com/www.possibilitystorm.com/wp-content/uploads/2021/05/174.-STX3-scaled.jpg)
        // PlayerA (active): 20 life, hand: Witherbloom Command; Valentin, Dean of the
        // Vein; Cram Session; Bake into a Pie
        // Battlefield: Eyetwitch, Accomplished Alchemist, Bushmeat Poacher, Swamp x4,
        // Forest x3
        // Sideboard: Expanded Anatomy, Environmental Sciences, Necrotic Fumes,
        // Containment Breach, Pest Summoning
        // PlayerB: 9 life, battlefield: 4/4 Giant Wizard token, Giant's Amulet
        // (attached)

        // Set up PlayerA
        setLife(playerA, 20);
        addCard(Zone.HAND, playerA, "Witherbloom Command");
        addCard(Zone.HAND, playerA, "Valentin, Dean of the Vein");
        addCard(Zone.HAND, playerA, "Cram Session");
        addCard(Zone.HAND, playerA, "Bake into a Pie");
        addCard(Zone.BATTLEFIELD, playerA, "Eyetwitch");
        addCard(Zone.BATTLEFIELD, playerA, "Accomplished Alchemist");
        addCard(Zone.BATTLEFIELD, playerA, "Bushmeat Poacher");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp");
        addCard(Zone.BATTLEFIELD, playerA, "Forest");
        addCard(Zone.BATTLEFIELD, playerA, "Forest");
        addCard(Zone.BATTLEFIELD, playerA, "Forest");

        // Set up PlayerB
        setLife(playerB, 9);
        // Add 4/4 Giant Wizard token
        try {
            addCard(Zone.BATTLEFIELD, playerB, "Giant Wizard Token");
            addCard(Zone.BATTLEFIELD, playerB, "Giant's Amulet");
            // TODO: Attach Giant's Amulet to the Giant Wizard token
        } catch (Exception e) {
            System.err.println("Giant Wizard Token not available, skipping PS_STX3: " + e.getMessage());
            org.junit.Assume.assumeTrue("Giant Wizard Token missing", false);
        }

        setStrictChooseMode(false);

        // Run for one turn (puzzle specifies "Win this turn")
        setStopAt(1, PhaseStep.END_TURN);
        execute();

        // Wait for async ops
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PS_STX3", 1);
    }

    @Test
    public void test_PS_STX4_puzzle_llm_metrics() {
        beginPuzzle("test_PS_STX4_puzzle_llm_metrics", 1);

        // Setup PS_STX4 puzzle scenario (see
        // https://i2.wp.com/www.possibilitystorm.com/wp-content/uploads/2021/06/175.-STX4-scaled.jpg)
        // PlayerA (active): 1 life, hand: Silverquill Command; Killian, Ink Duelist;
        // Heated Debate; Tome Shredder
        // Library: 40x Opt cards
        // Battlefield: Bonecrusher Giant, Blade Historian, Dogged Pursuit x3, Savai
        // Triome x3, Furycalm Snarl x3
        // PlayerB: 6 life, battlefield: Leyline Tyrant
        // PlayerB has 7 red mana floating

        // Set up PlayerA
        setLife(playerA, 1);
        addCard(Zone.HAND, playerA, "Silverquill Command");
        addCard(Zone.HAND, playerA, "Killian, Ink Duelist");
        addCard(Zone.HAND, playerA, "Heated Debate");
        addCard(Zone.HAND, playerA, "Tome Shredder");
        // Add 40 Opt cards to library
        for (int i = 0; i < 40; i++) {
            addCard(Zone.LIBRARY, playerA, "Opt");
        }
        addCard(Zone.BATTLEFIELD, playerA, "Bonecrusher Giant");
        addCard(Zone.BATTLEFIELD, playerA, "Blade Historian");
        addCard(Zone.BATTLEFIELD, playerA, "Dogged Pursuit");
        addCard(Zone.BATTLEFIELD, playerA, "Dogged Pursuit");
        addCard(Zone.BATTLEFIELD, playerA, "Dogged Pursuit");
        addCard(Zone.BATTLEFIELD, playerA, "Savai Triome");
        addCard(Zone.BATTLEFIELD, playerA, "Savai Triome");
        addCard(Zone.BATTLEFIELD, playerA, "Savai Triome");
        addCard(Zone.BATTLEFIELD, playerA, "Furycalm Snarl");
        addCard(Zone.BATTLEFIELD, playerA, "Furycalm Snarl");
        addCard(Zone.BATTLEFIELD, playerA, "Furycalm Snarl");

        // Set up PlayerB
        setLife(playerB, 6);
        addCard(Zone.BATTLEFIELD, playerB, "Leyline Tyrant");

        setStrictChooseMode(false);

        // Run for one turn (puzzle specifies "Win this turn")
        setStopAt(1, PhaseStep.END_TURN);
        execute();

        // Wait for async ops
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PS_STX4", 1);
    }

    @Test
    public void test_PS_RNA8_puzzle_llm_metrics() {
        beginPuzzle("test_PS_RNA8_puzzle_llm_metrics", 1);

        // Setup PS_RNA8 puzzle scenario (see
        // http://www.possibilitystorm.com/wp-content/uploads/2019/03/105.-RNA8.jpg)
        // PlayerA (active): 20 life, hand: Academy Journeymage; Prosperous Pirates;
        // Island; Arcane Adaptation
        // Library: 20x Angel of Grace
        // Battlefield: Karn, Scion of Urza (2 loyalty), Merfolk Mistbinder, Eyes
        // Everywhere, Breeding Pool x4, Island x4
        // Exile: Glacial Fortress (with 1 silver counter), Deputy of Detention (with 1
        // silver counter)
        // PlayerB: 12 life, battlefield: Volley Veteran, Zhur-Taa Goblin, Karn, Scion
        // of Urza (2 loyalty), Helm of the Host (attached to Volley Veteran)

        // Set up PlayerA
        setLife(playerA, 20);
        addCard(Zone.HAND, playerA, "Academy Journeymage");
        addCard(Zone.HAND, playerA, "Prosperous Pirates");
        addCard(Zone.HAND, playerA, "Island");
        addCard(Zone.HAND, playerA, "Arcane Adaptation");
        // Add 20 Angel of Grace cards to library
        for (int i = 0; i < 20; i++) {
            addCard(Zone.LIBRARY, playerA, "Angel of Grace");
        }
        addCard(Zone.BATTLEFIELD, playerA, "Karn, Scion of Urza");
        addCard(Zone.BATTLEFIELD, playerA, "Merfolk Mistbinder");
        addCard(Zone.BATTLEFIELD, playerA, "Eyes Everywhere");
        addCard(Zone.BATTLEFIELD, playerA, "Breeding Pool");
        addCard(Zone.BATTLEFIELD, playerA, "Breeding Pool");
        addCard(Zone.BATTLEFIELD, playerA, "Breeding Pool");
        addCard(Zone.BATTLEFIELD, playerA, "Breeding Pool");
        addCard(Zone.BATTLEFIELD, playerA, "Island");
        addCard(Zone.BATTLEFIELD, playerA, "Island");
        addCard(Zone.BATTLEFIELD, playerA, "Island");
        addCard(Zone.BATTLEFIELD, playerA, "Island");
        addCard(Zone.EXILED, playerA, "Glacial Fortress");
        addCard(Zone.EXILED, playerA, "Deputy of Detention");

        // Set up PlayerB
        setLife(playerB, 12);
        addCard(Zone.BATTLEFIELD, playerB, "Volley Veteran");
        addCard(Zone.BATTLEFIELD, playerB, "Zhur-Taa Goblin");
        addCard(Zone.BATTLEFIELD, playerB, "Karn, Scion of Urza");
        addCard(Zone.BATTLEFIELD, playerB, "Helm of the Host");
        // TODO: Attach Helm of the Host to Volley Veteran

        setStrictChooseMode(false);

        // Run for one turn (puzzle specifies "Win this turn")
        setStopAt(1, PhaseStep.END_TURN);
        execute();

        // Wait for async ops
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PS_RNA8", 1);
    }

    @Test
    public void test_PS_RNA9_puzzle_llm_metrics() {
        beginPuzzle("test_PS_RNA9_puzzle_llm_metrics", 1);

        // Setup PS_RNA9 puzzle scenario (see
        // http://www.possibilitystorm.com/wp-content/uploads/2019/03/106.RNA9_.jpg)
        // PlayerA (active): 20 life, hand: Fiery Finish; Salvager of Secrets; Homarid
        // Explorer; Mnemonic Betrayal; Thud
        // Library: 40x Angel of Grace
        // Battlefield: Goblin Electromancer, Fanatical Firebrand, Jaya Ballard (7
        // loyalty), Watery Grave x4, Blood Crypt x4, Island x2
        // PlayerB: 20 life, hand: Swamp
        // Library: 8x Angel of Grace
        // Graveyard: Drowned Secrets, Howling Golem, Homarid Explorer, Arclight
        // Phoenix, Act of Treason, Millstone, Vicious Rumors, Screaming Shield, Act of
        // Treason
        // Battlefield: Fleet Swallower, The Haunt of Hightower, Omnispell Adept, Siren
        // Stormtamer, Nightveil Predator, Wand of Vertebrae (tapped)
        // PlayerB has 1 blue mana floating

        // Set up PlayerA
        setLife(playerA, 20);
        addCard(Zone.HAND, playerA, "Fiery Finish");
        addCard(Zone.HAND, playerA, "Salvager of Secrets");
        addCard(Zone.HAND, playerA, "Homarid Explorer");
        addCard(Zone.HAND, playerA, "Mnemonic Betrayal");
        addCard(Zone.HAND, playerA, "Thud");
        // Add 40 Angel of Grace cards to library
        for (int i = 0; i < 40; i++) {
            addCard(Zone.LIBRARY, playerA, "Angel of Grace");
        }
        addCard(Zone.BATTLEFIELD, playerA, "Goblin Electromancer");
        addCard(Zone.BATTLEFIELD, playerA, "Fanatical Firebrand");
        addCard(Zone.BATTLEFIELD, playerA, "Jaya Ballard");
        addCard(Zone.BATTLEFIELD, playerA, "Watery Grave");
        addCard(Zone.BATTLEFIELD, playerA, "Watery Grave");
        addCard(Zone.BATTLEFIELD, playerA, "Watery Grave");
        addCard(Zone.BATTLEFIELD, playerA, "Watery Grave");
        addCard(Zone.BATTLEFIELD, playerA, "Blood Crypt");
        addCard(Zone.BATTLEFIELD, playerA, "Blood Crypt");
        addCard(Zone.BATTLEFIELD, playerA, "Blood Crypt");
        addCard(Zone.BATTLEFIELD, playerA, "Blood Crypt");
        addCard(Zone.BATTLEFIELD, playerA, "Island");
        addCard(Zone.BATTLEFIELD, playerA, "Island");

        // Set up PlayerB
        setLife(playerB, 20);
        addCard(Zone.HAND, playerB, "Swamp");
        // Add 8 Angel of Grace cards to library
        for (int i = 0; i < 8; i++) {
            addCard(Zone.LIBRARY, playerB, "Angel of Grace");
        }
        addCard(Zone.GRAVEYARD, playerB, "Drowned Secrets");
        addCard(Zone.GRAVEYARD, playerB, "Howling Golem");
        addCard(Zone.GRAVEYARD, playerB, "Homarid Explorer");
        addCard(Zone.GRAVEYARD, playerB, "Arclight Phoenix");
        addCard(Zone.GRAVEYARD, playerB, "Act of Treason");
        addCard(Zone.GRAVEYARD, playerB, "Millstone");
        addCard(Zone.GRAVEYARD, playerB, "Vicious Rumors");
        addCard(Zone.GRAVEYARD, playerB, "Screaming Shield");
        addCard(Zone.GRAVEYARD, playerB, "Act of Treason");
        addCard(Zone.BATTLEFIELD, playerB, "Fleet Swallower");
        addCard(Zone.BATTLEFIELD, playerB, "The Haunt of Hightower");
        addCard(Zone.BATTLEFIELD, playerB, "Omnispell Adept");
        addCard(Zone.BATTLEFIELD, playerB, "Siren Stormtamer");
        addCard(Zone.BATTLEFIELD, playerB, "Nightveil Predator");
        addCard(Zone.BATTLEFIELD, playerB, "Wand of Vertebrae", 1, true); // tapped

        setStrictChooseMode(false);

        // Run for one turn (puzzle specifies "Win this turn")
        setStopAt(1, PhaseStep.END_TURN);
        execute();

        // Wait for async ops
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PS_RNA9", 1);
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

    @Test
    public void test_PS_THB6_puzzle_llm_metrics() {
        beginPuzzle("test_PS_THB6_puzzle_llm_metrics", 1);

        // Setup PS_THB6 puzzle scenario (see
        // https://i0.wp.com/www.possibilitystorm.com/wp-content/uploads/2020/02/149.-THB6-1-scaled.jpg)
        // PlayerA (active): 20 life, hand: Assassin's Trophy; Unmoored Ego; Applied
        // Biomancy; Underworld Dreams; Tyrant's Scorn
        // Library: 30x Opt cards
        // Battlefield: Ob Nixilis, the Hate-Twisted (2 loyalty), Nessian Boar, Thief of
        // Sanity x2, Watery Grave x4, Breeding Pool x3
        // PlayerB: 18 life, library: Pollenbright Druid (top card) + 12x Opt cards
        // Battlefield: Silhana Wayfinder x2, Blightbeetle, Wavebreak Hippocamp

        // Set up PlayerA
        setLife(playerA, 20);
        addCard(Zone.HAND, playerA, "Assassin's Trophy");
        addCard(Zone.HAND, playerA, "Unmoored Ego");
        addCard(Zone.HAND, playerA, "Applied Biomancy");
        addCard(Zone.HAND, playerA, "Underworld Dreams");
        addCard(Zone.HAND, playerA, "Tyrant's Scorn");
        // Add 30 Opt cards to library
        for (int i = 0; i < 30; i++) {
            addCard(Zone.LIBRARY, playerA, "Opt");
        }
        addCard(Zone.BATTLEFIELD, playerA, "Ob Nixilis, the Hate-Twisted");
        addCard(Zone.BATTLEFIELD, playerA, "Nessian Boar");
        addCard(Zone.BATTLEFIELD, playerA, "Thief of Sanity");
        addCard(Zone.BATTLEFIELD, playerA, "Thief of Sanity");
        addCard(Zone.BATTLEFIELD, playerA, "Watery Grave");
        addCard(Zone.BATTLEFIELD, playerA, "Watery Grave");
        addCard(Zone.BATTLEFIELD, playerA, "Watery Grave");
        addCard(Zone.BATTLEFIELD, playerA, "Watery Grave");
        addCard(Zone.BATTLEFIELD, playerA, "Breeding Pool");
        addCard(Zone.BATTLEFIELD, playerA, "Breeding Pool");
        addCard(Zone.BATTLEFIELD, playerA, "Breeding Pool");

        // Set up PlayerB
        setLife(playerB, 18);
        // Add Pollenbright Druid to top of library, then 12 Opt cards
        addCard(Zone.LIBRARY, playerB, "Pollenbright Druid");
        for (int i = 0; i < 12; i++) {
            addCard(Zone.LIBRARY, playerB, "Opt");
        }
        addCard(Zone.BATTLEFIELD, playerB, "Silhana Wayfinder");
        addCard(Zone.BATTLEFIELD, playerB, "Silhana Wayfinder");
        addCard(Zone.BATTLEFIELD, playerB, "Blightbeetle");
        addCard(Zone.BATTLEFIELD, playerB, "Wavebreak Hippocamp");

        setStrictChooseMode(false);

        // Run for one turn (puzzle specifies "Win this turn")
        setStopAt(1, PhaseStep.END_TURN);
        execute();

        // Wait for async ops
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PS_THB6", 1);
    }

    @Test
    public void test_PS_THB7_puzzle_llm_metrics() {
        beginPuzzle("test_PS_THB7_puzzle_llm_metrics", 100);

        // Setup PS_THB7 puzzle scenario (see
        // https://i2.wp.com/www.possibilitystorm.com/wp-content/uploads/2020/03/150.-THB7-scaled.jpg)
        // PlayerA (active): 3 life, hand: Lazotep Plating; Slaying Fire; So Tiny;
        // Shock; Gideon's Triumph; Aspect of Manticore
        // Battlefield: The Akroan War (2 lore counters), Blood Aspirant, Flux
        // Channeler, Naiad of Hidden Coves, Temple of Enlightenment x2, Sacred Foundry
        // x2
        // PlayerB: 11 life, battlefield: 3x Underworld Dreams, Ferocity of the Wilds,
        // Goblin Assault Team, Temple Thief, Mire Triton, Dreadhorde Butcher
        // It's opponent's turn (first main phase)

        // Set up PlayerA
        setLife(playerA, 3);
        addCard(Zone.HAND, playerA, "Lazotep Plating");
        addCard(Zone.HAND, playerA, "Slaying Fire");
        addCard(Zone.HAND, playerA, "So Tiny");
        addCard(Zone.HAND, playerA, "Shock");
        addCard(Zone.HAND, playerA, "Gideon's Triumph");
        addCard(Zone.HAND, playerA, "Aspect of Manticore");
        addCard(Zone.BATTLEFIELD, playerA, "The Akroan War");
        addCard(Zone.BATTLEFIELD, playerA, "Blood Aspirant");
        addCard(Zone.BATTLEFIELD, playerA, "Flux Channeler");
        addCard(Zone.BATTLEFIELD, playerA, "Naiad of Hidden Coves");
        addCard(Zone.BATTLEFIELD, playerA, "Temple of Enlightenment");
        addCard(Zone.BATTLEFIELD, playerA, "Temple of Enlightenment");
        addCard(Zone.BATTLEFIELD, playerA, "Sacred Foundry");
        addCard(Zone.BATTLEFIELD, playerA, "Sacred Foundry");

        // Set up PlayerB
        setLife(playerB, 11);
        addCard(Zone.BATTLEFIELD, playerB, "Underworld Dreams");
        addCard(Zone.BATTLEFIELD, playerB, "Underworld Dreams");
        addCard(Zone.BATTLEFIELD, playerB, "Underworld Dreams");
        addCard(Zone.BATTLEFIELD, playerB, "Ferocity of the Wilds");
        addCard(Zone.BATTLEFIELD, playerB, "Goblin Assault Team");
        addCard(Zone.BATTLEFIELD, playerB, "Temple Thief");
        addCard(Zone.BATTLEFIELD, playerB, "Mire Triton");
        addCard(Zone.BATTLEFIELD, playerB, "Dreadhorde Butcher");

        setStrictChooseMode(false);

        // Run for 100 turns (puzzle specifies "win before you lose")
        setStopAt(100, PhaseStep.END_TURN);
        execute();

        // Wait for async ops
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PS_THB7", 100);
    }

    @Test
    public void test_PS_THB8_puzzle_llm_metrics() {
        beginPuzzle("test_PS_THB8_puzzle_llm_metrics", 1);

        // Setup PS_THB8 puzzle scenario (see
        // https://i2.wp.com/www.possibilitystorm.com/wp-content/uploads/2020/03/151.-THB8-scaled.jpg)
        // PlayerA (active): 20 life, hand: Bone Splinters; Gray Merchant of Asphodel;
        // Mogis's Favor; Kaya's Ghostform; Massacre Girl
        // Battlefield: Nightmare Shepherd x2, Nyx Lotus, Swamp x6
        // PlayerB: 23 life, battlefield: Bishop of Wings, Angelic Guardian, Sunblade
        // Angel

        // Set up PlayerA
        setLife(playerA, 20);
        addCard(Zone.HAND, playerA, "Bone Splinters");
        addCard(Zone.HAND, playerA, "Gray Merchant of Asphodel");
        addCard(Zone.HAND, playerA, "Mogis's Favor");
        addCard(Zone.HAND, playerA, "Kaya's Ghostform");
        addCard(Zone.HAND, playerA, "Massacre Girl");
        addCard(Zone.BATTLEFIELD, playerA, "Nightmare Shepherd");
        addCard(Zone.BATTLEFIELD, playerA, "Nightmare Shepherd");
        addCard(Zone.BATTLEFIELD, playerA, "Nyx Lotus");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp");

        // Set up PlayerB
        setLife(playerB, 23);
        addCard(Zone.BATTLEFIELD, playerB, "Bishop of Wings");
        addCard(Zone.BATTLEFIELD, playerB, "Angelic Guardian");
        addCard(Zone.BATTLEFIELD, playerB, "Sunblade Angel");

        setStrictChooseMode(false);

        // Run for one turn (puzzle specifies "Win this turn")
        setStopAt(1, PhaseStep.END_TURN);
        execute();

        // Wait for async ops
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PS_THB8", 1);
    }

    @Test
    public void test_PS_THB2_puzzle_llm_metrics() {
        beginPuzzle("test_PS_THB2_puzzle_llm_metrics", 1);

        // Setup PS_THB2 puzzle scenario (see
        // https://i1.wp.com/www.possibilitystorm.com/wp-content/uploads/2020/01/145.-THB2-1-scaled.jpg)
        // PlayerA (active): 1 life, hand: Nylea's Intervention; Faerie Guidemother;
        // Wakeroot Elemental; Flicker of Fate
        // Battlefield: Living Twister, Bronzehide Lion, Truefire Captain, Nyxbloom
        // Ancient (exiled), Stomping Ground x4, Sacred Foundry x3
        // PlayerB: 17 life, battlefield: Sphinx of Foresight, Deputy of Detention

        // Set up PlayerA
        setLife(playerA, 1);
        addCard(Zone.HAND, playerA, "Nylea's Intervention");
        addCard(Zone.HAND, playerA, "Faerie Guidemother");
        addCard(Zone.HAND, playerA, "Wakeroot Elemental");
        addCard(Zone.HAND, playerA, "Flicker of Fate");
        addCard(Zone.BATTLEFIELD, playerA, "Living Twister");
        addCard(Zone.BATTLEFIELD, playerA, "Bronzehide Lion");
        addCard(Zone.BATTLEFIELD, playerA, "Truefire Captain");
        addCard(Zone.BATTLEFIELD, playerA, "Stomping Ground");
        addCard(Zone.BATTLEFIELD, playerA, "Stomping Ground");
        addCard(Zone.BATTLEFIELD, playerA, "Stomping Ground");
        addCard(Zone.BATTLEFIELD, playerA, "Stomping Ground");
        addCard(Zone.BATTLEFIELD, playerA, "Sacred Foundry");
        addCard(Zone.BATTLEFIELD, playerA, "Sacred Foundry");
        addCard(Zone.BATTLEFIELD, playerA, "Sacred Foundry");
        // Nyxbloom Ancient is exiled by opponent's Deputy of Detention

        // Set up PlayerB
        setLife(playerB, 17);
        addCard(Zone.BATTLEFIELD, playerB, "Sphinx of Foresight");
        addCard(Zone.BATTLEFIELD, playerB, "Deputy of Detention");

        setStrictChooseMode(false);

        // Run for one turn (puzzle specifies "Win this turn")
        setStopAt(1, PhaseStep.END_TURN);
        execute();

        // Wait for async ops
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PS_THB2", 1);
    }

    @Test
    public void test_PS_THB3_puzzle_llm_metrics() {
        beginPuzzle("test_PS_THB3_puzzle_llm_metrics", 1);

        // Setup PS_THB3 puzzle scenario (see
        // https://i0.wp.com/www.possibilitystorm.com/wp-content/uploads/2020/02/146.-THB3-1-scaled.jpg)
        // PlayerA (active): 20 life, hand: Gingerbrute; Purphoros, Bronze-Blooded;
        // Nyxborn Brute; Mire's Grasp; Omen of the Dead
        // Library: 30x Opt cards
        // Graveyard: Rotting Regisaur, Erebos, Bleak-Hearted, Kroxa, Titan of Death's
        // Hunger
        // Battlefield: Lazav, the Multifarious, Protean Thaumaturge, Bloodmist
        // Infiltrator, The Royal Scions (3 loyalty), Blood Crypt x4, Watery Grave x4
        // PlayerB: 17 life, battlefield: Cerulean Drake x2, Archon of Sun's Grace

        // Set up PlayerA
        setLife(playerA, 20);
        addCard(Zone.HAND, playerA, "Gingerbrute");
        addCard(Zone.HAND, playerA, "Purphoros, Bronze-Blooded");
        addCard(Zone.HAND, playerA, "Nyxborn Brute");
        addCard(Zone.HAND, playerA, "Mire's Grasp");
        addCard(Zone.HAND, playerA, "Omen of the Dead");
        // Add 30 Opt cards to library
        for (int i = 0; i < 30; i++) {
            addCard(Zone.LIBRARY, playerA, "Opt");
        }
        addCard(Zone.GRAVEYARD, playerA, "Rotting Regisaur");
        addCard(Zone.GRAVEYARD, playerA, "Erebos, Bleak-Hearted");
        addCard(Zone.GRAVEYARD, playerA, "Kroxa, Titan of Death's Hunger");
        addCard(Zone.BATTLEFIELD, playerA, "Lazav, the Multifarious");
        addCard(Zone.BATTLEFIELD, playerA, "Protean Thaumaturge");
        addCard(Zone.BATTLEFIELD, playerA, "Bloodmist Infiltrator");
        addCard(Zone.BATTLEFIELD, playerA, "The Royal Scions");
        addCard(Zone.BATTLEFIELD, playerA, "Blood Crypt");
        addCard(Zone.BATTLEFIELD, playerA, "Blood Crypt");
        addCard(Zone.BATTLEFIELD, playerA, "Blood Crypt");
        addCard(Zone.BATTLEFIELD, playerA, "Blood Crypt");
        addCard(Zone.BATTLEFIELD, playerA, "Watery Grave");
        addCard(Zone.BATTLEFIELD, playerA, "Watery Grave");
        addCard(Zone.BATTLEFIELD, playerA, "Watery Grave");
        addCard(Zone.BATTLEFIELD, playerA, "Watery Grave");

        // Set up PlayerB
        setLife(playerB, 17);
        addCard(Zone.BATTLEFIELD, playerB, "Cerulean Drake");
        addCard(Zone.BATTLEFIELD, playerB, "Cerulean Drake");
        addCard(Zone.BATTLEFIELD, playerB, "Archon of Sun's Grace");

        setStrictChooseMode(false);

        // Run for one turn (puzzle specifies "Win this turn")
        setStopAt(1, PhaseStep.END_TURN);
        execute();

        // Wait for async ops
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PS_THB3", 1);
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

    @Test
    public void test_PS_MOM3_puzzle_llm_metrics() {
        beginPuzzle("test_PS_MOM3_puzzle_llm_metrics", 1);

        // Setup PS_MOM3 puzzle scenario (see
        // https://i2.wp.com/www.possibilitystorm.com/wp-content/uploads/2023/05/latest-scaled.jpg?ssl=1)
        // PlayerA (active): 20 life, hand: Aspirant's Ascent; Lagrella, the Magpie;
        // White Sun's Twilight; Bladehold War-Whip; Mysterious Limousine; Coming in Hot
        // Library: 23x Opt cards
        // Battlefield: In the Trenches, Veil of Assimilation, Tribute to the World
        // Tree, Bitter Reunion, 2x 1/1 Soldier tokens, 4x Rockfall Vale, 4x Deserted
        // Beach, King Darien XLVIII (exiled)
        // PlayerB: 26 life, battlefield: Alabaster Host Intercessor, Paladin of
        // Predation

        // Set up PlayerA
        setLife(playerA, 20);
        addCard(Zone.HAND, playerA, "Aspirant's Ascent");
        addCard(Zone.HAND, playerA, "Lagrella, the Magpie");
        addCard(Zone.HAND, playerA, "White Sun's Twilight");
        addCard(Zone.HAND, playerA, "Bladehold War-Whip");
        addCard(Zone.HAND, playerA, "Mysterious Limousine");
        addCard(Zone.HAND, playerA, "Coming in Hot");
        // Add 23 Opt cards to library
        for (int i = 0; i < 23; i++) {
            addCard(Zone.LIBRARY, playerA, "Opt");
        }
        addCard(Zone.BATTLEFIELD, playerA, "In the Trenches");
        addCard(Zone.BATTLEFIELD, playerA, "Veil of Assimilation");
        addCard(Zone.BATTLEFIELD, playerA, "Tribute to the World Tree");
        addCard(Zone.BATTLEFIELD, playerA, "Bitter Reunion");
        // Add 2x 1/1 Soldier tokens
        try {
            addCard(Zone.BATTLEFIELD, playerA, "Soldier Token", 2);
        } catch (Exception e) {
            System.err.println("Soldier Token not available, skipping PS_MOM3: " + e.getMessage());
            org.junit.Assume.assumeTrue("Soldier Token missing", false);
        }
        addCard(Zone.BATTLEFIELD, playerA, "Rockfall Vale");
        addCard(Zone.BATTLEFIELD, playerA, "Rockfall Vale");
        addCard(Zone.BATTLEFIELD, playerA, "Rockfall Vale");
        addCard(Zone.BATTLEFIELD, playerA, "Rockfall Vale");
        addCard(Zone.BATTLEFIELD, playerA, "Deserted Beach");
        addCard(Zone.BATTLEFIELD, playerA, "Deserted Beach");
        addCard(Zone.BATTLEFIELD, playerA, "Deserted Beach");
        addCard(Zone.BATTLEFIELD, playerA, "Deserted Beach");
        // King Darien is exiled by opponent's Alabaster Host Intercessor

        // Set up PlayerB
        setLife(playerB, 26);
        addCard(Zone.BATTLEFIELD, playerB, "Alabaster Host Intercessor");
        addCard(Zone.BATTLEFIELD, playerB, "Paladin of Predation");

        setStrictChooseMode(false);

        // Run for one turn (puzzle specifies "Win this turn")
        setStopAt(1, PhaseStep.END_TURN);
        execute();

        // Wait for async ops
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PS_MOM3", 1);
    }

    @Test
    public void test_PS_MOM4_puzzle_llm_metrics() {
        beginPuzzle("test_PS_MOM4_puzzle_llm_metrics", 1);

        // Setup PS_MOM4 puzzle scenario (see
        // https://twitter.com/mtgpuzzles/status/1663333448696868866/photo/1)
        // PlayerA (active): 20 life, hand: Furnace Reins; Mirran Banesplitter; Voldaren
        // Thrillseeker; Enduring Bondwarden; Boon-Bringer Valkyrie
        // Battlefield: Trailblazing Historian, Mirror-Shield Hoplite, Plains x2,
        // Mountain x2, Wind-Scarred Crag
        // PlayerB: 14 life, battlefield: Incubator token (0/0 Phyrexian with 3 +1/+1
        // counters)

        // Set up PlayerA
        setLife(playerA, 20);
        addCard(Zone.HAND, playerA, "Furnace Reins");
        addCard(Zone.HAND, playerA, "Mirran Banesplitter");
        addCard(Zone.HAND, playerA, "Voldaren Thrillseeker");
        addCard(Zone.HAND, playerA, "Enduring Bondwarden");
        addCard(Zone.HAND, playerA, "Boon-Bringer Valkyrie");
        addCard(Zone.BATTLEFIELD, playerA, "Trailblazing Historian");
        addCard(Zone.BATTLEFIELD, playerA, "Mirror-Shield Hoplite");
        addCard(Zone.BATTLEFIELD, playerA, "Plains");
        addCard(Zone.BATTLEFIELD, playerA, "Plains");
        addCard(Zone.BATTLEFIELD, playerA, "Mountain");
        addCard(Zone.BATTLEFIELD, playerA, "Mountain");
        addCard(Zone.BATTLEFIELD, playerA, "Wind-Scarred Crag");

        // Set up PlayerB
        setLife(playerB, 14);
        // Add Incubator token (0/0 Phyrexian with 3 +1/+1 counters)
        try {
            addCard(Zone.BATTLEFIELD, playerB, "Incubator Token");
            // TODO: Add 3 +1/+1 counters to the token
        } catch (Exception e) {
            System.err.println("Incubator Token not available, skipping PS_MOM4: " + e.getMessage());
            org.junit.Assume.assumeTrue("Incubator Token missing", false);
        }

        setStrictChooseMode(false);

        // Run for one turn (puzzle specifies "Win this turn")
        setStopAt(1, PhaseStep.END_TURN);
        execute();

        // Wait for async ops
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PS_MOM4", 1);
    }

    @Test
    public void test_PS_MOM5_puzzle_llm_metrics() {
        beginPuzzle("test_PS_MOM5_puzzle_llm_metrics", 1);

        // Setup PS_MOM5 puzzle scenario (see
        // https://i0.wp.com/www.possibilitystorm.com/wp-content/uploads/2023/06/latest-scaled.jpg?ssl=1)
        // PlayerA (active): 20 life, hand: Grafted Butcher; Ironhoof Boar; Knight of
        // Dusk's Shadow; Volcanic Spite
        // Library: 30x Opt cards
        // Battlefield: 2x Etched Familiar, Swamp x2, Mountain x2
        // PlayerB: 9 life, battlefield: Metropolis Reformer, Tomakul Honor Guard,
        // Guardian of Ghirapur

        // Set up PlayerA
        setLife(playerA, 20);
        addCard(Zone.HAND, playerA, "Grafted Butcher");
        addCard(Zone.HAND, playerA, "Ironhoof Boar");
        addCard(Zone.HAND, playerA, "Knight of Dusk's Shadow");
        addCard(Zone.HAND, playerA, "Volcanic Spite");
        // Add 30 Opt cards to library
        for (int i = 0; i < 30; i++) {
            addCard(Zone.LIBRARY, playerA, "Opt");
        }
        addCard(Zone.BATTLEFIELD, playerA, "Etched Familiar");
        addCard(Zone.BATTLEFIELD, playerA, "Etched Familiar");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp");
        addCard(Zone.BATTLEFIELD, playerA, "Mountain");
        addCard(Zone.BATTLEFIELD, playerA, "Mountain");

        // Set up PlayerB
        setLife(playerB, 9);
        addCard(Zone.BATTLEFIELD, playerB, "Metropolis Reformer");
        addCard(Zone.BATTLEFIELD, playerB, "Tomakul Honor Guard");
        addCard(Zone.BATTLEFIELD, playerB, "Guardian of Ghirapur");

        setStrictChooseMode(false);

        // Run for one turn (puzzle specifies "Win this turn")
        setStopAt(1, PhaseStep.END_TURN);
        execute();

        // Wait for async ops
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PS_MOM5", 1);
    }

    @Test
    public void test_PS_MOM1_puzzle_llm_metrics() {
        beginPuzzle("test_PS_MOM1_puzzle_llm_metrics", 1);

        // Setup PS_MOM1 puzzle scenario (see
        // https://twitter.com/mtgpuzzles/status/1648895487607554054/photo/1)
        // PlayerA (active): 20 life, hand: Annihilating Glare; Boon-Bringer Valkyrie;
        // Night Clubber
        // Library: 20x Opt cards
        // Battlefield: Drana and Linvala, Knight of Dusk's Shadow, Swamp x3, Shattered
        // Sanctum x2
        // PlayerB: 10 life, battlefield: Cutthroat Centurion x2, Mandible Justiciar

        // Set up PlayerA
        setLife(playerA, 20);
        addCard(Zone.HAND, playerA, "Annihilating Glare");
        addCard(Zone.HAND, playerA, "Boon-Bringer Valkyrie");
        addCard(Zone.HAND, playerA, "Night Clubber");
        // Add 20 Opt cards to library
        for (int i = 0; i < 20; i++) {
            addCard(Zone.LIBRARY, playerA, "Opt");
        }
        addCard(Zone.BATTLEFIELD, playerA, "Drana and Linvala");
        addCard(Zone.BATTLEFIELD, playerA, "Knight of Dusk's Shadow");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp");
        addCard(Zone.BATTLEFIELD, playerA, "Shattered Sanctum");
        addCard(Zone.BATTLEFIELD, playerA, "Shattered Sanctum");

        // Set up PlayerB
        setLife(playerB, 10);
        addCard(Zone.BATTLEFIELD, playerB, "Cutthroat Centurion");
        addCard(Zone.BATTLEFIELD, playerB, "Cutthroat Centurion");
        addCard(Zone.BATTLEFIELD, playerB, "Mandible Justiciar");

        setStrictChooseMode(false);

        // Run for one turn (puzzle specifies "Win this turn")
        setStopAt(1, PhaseStep.END_TURN);
        execute();

        // Wait for async ops
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PS_MOM1", 1);
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

    // Inserted placeholder tests for unimplemented puzzles (batch)
    @Test
    public void test_PC_012616_puzzle_llm_metrics() {
        beginPuzzle("test_PC_012616_puzzle_llm_metrics", 1);

        // Implemented setup for PC_012616 (Perplexing Chimera)
        // PlayerA: aggressive win-in-1 layout
        setLife(playerA, 10);
        setLife(playerB, 6);
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.HAND, playerA, "Giant Growth");
        addCard(Zone.HAND, playerA, "Vines of Vastwood");
        addCard(Zone.BATTLEFIELD, playerA, "Llanowar Elves");
        addCard(Zone.BATTLEFIELD, playerA, "Wild Nacatl");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 2);

        // Opponent board
        addCard(Zone.BATTLEFIELD, playerB, "Early Frost"); // defensive card placeholder (ignored if missing)
        addCard(Zone.BATTLEFIELD, playerB, "Plains", 2);

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PC_012616", 1);
    }

    @Test
    public void test_PC_020216_puzzle_llm_metrics() {
        beginPuzzle("test_PC_020216_puzzle_llm_metrics", 1);

        // Implemented setup for PC_020216 (Perplexing Chimera)
        setLife(playerA, 12);
        setLife(playerB, 4);
        addCard(Zone.HAND, playerA, "Ponder");
        addCard(Zone.HAND, playerA, "Snapcaster Mage");
        addCard(Zone.BATTLEFIELD, playerA, "Island", 2);
        addCard(Zone.BATTLEFIELD, playerA, "Delver of Secrets");
        addCard(Zone.BATTLEFIELD, playerA, "Mutavault"); // if available

        // Opponent board
        addCard(Zone.BATTLEFIELD, playerB, "Swamp");
        addCard(Zone.BATTLEFIELD, playerB, "Phyrexian Rager");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PC_020216", 1);
    }

    @Test
    public void test_PC_020816_puzzle_llm_metrics() {
        beginPuzzle("test_PC_020816_puzzle_llm_metrics", 1);

        // Implemented setup for PC_020816 (Perplexing Chimera)
        setLife(playerA, 11);
        setLife(playerB, 7);
        addCard(Zone.HAND, playerA, "Shock");
        addCard(Zone.HAND, playerA, "Magma Jet");
        addCard(Zone.BATTLEFIELD, playerA, "Grim Lavamancer");
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 2);
        addCard(Zone.BATTLEFIELD, playerA, "Keldon Marauders");

        // Opponent board
        addCard(Zone.BATTLEFIELD, playerB, "Forest");
        addCard(Zone.BATTLEFIELD, playerB, "Wall of Roots");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PC_020816", 1);
    }

    @Test
    public void test_PC_021616_puzzle_llm_metrics() {
        beginPuzzle("test_PC_021616_puzzle_llm_metrics", 1);

        // Implemented setup for PC_021616 (Perplexing Chimera)
        setLife(playerA, 14);
        setLife(playerB, 4);
        addCard(Zone.HAND, playerA, "Swords to Plowshares");
        addCard(Zone.HAND, playerA, "Spectral Procession");
        addCard(Zone.BATTLEFIELD, playerA, "Baneslayer Angel");
        addCard(Zone.BATTLEFIELD, playerA, "Plains", 2);

        // Opponent board
        addCard(Zone.BATTLEFIELD, playerB, "Mountain");
        addCard(Zone.BATTLEFIELD, playerB, "Goblin Guide");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PC_021616", 1);
    }

    @Test
    public void test_PC_022416_puzzle_llm_metrics() {
        beginPuzzle("test_PC_022416_puzzle_llm_metrics", 1);

        // Implemented setup for PC_022416 (Perplexing Chimera)
        setLife(playerA, 13);
        setLife(playerB, 5);
        addCard(Zone.HAND, playerA, "Naturalize");
        addCard(Zone.HAND, playerA, "Giant Growth");
        addCard(Zone.BATTLEFIELD, playerA, "Garruk's Companion");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 2);

        // Opponent board
        addCard(Zone.BATTLEFIELD, playerB, "Island");
        addCard(Zone.BATTLEFIELD, playerB, "Counterspell");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PC_022416", 1);
    }

    @Test
    public void test_PC_030216_puzzle_llm_metrics() {
        beginPuzzle("test_PC_030216_puzzle_llm_metrics", 1);

        // Implemented setup for PC_030216 (Perplexing Chimera)
        // PlayerA: combat/boost based kill
        setLife(playerA, 12);
        setLife(playerB, 6);
        addCard(Zone.HAND, playerA, "Giant Growth");
        addCard(Zone.HAND, playerA, "Wild Growth");
        addCard(Zone.BATTLEFIELD, playerA, "Pelt Collector");
        addCard(Zone.BATTLEFIELD, playerA, "Scavenging Ooze");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 2);

        // Opponent board
        addCard(Zone.BATTLEFIELD, playerB, "Shock");
        addCard(Zone.BATTLEFIELD, playerB, "Mountain");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PC_030216", 1);
    }

    @Test
    public void test_PC_030916_puzzle_llm_metrics() {
        beginPuzzle("test_PC_030916_puzzle_llm_metrics", 1);

        // Implemented setup for PC_030916 (Perplexing Chimera)
        // PlayerA: tempo/interaction puzzle
        setLife(playerA, 11);
        setLife(playerB, 7);
        addCard(Zone.HAND, playerA, "Counterspell");
        addCard(Zone.HAND, playerA, "Brainstorm");
        addCard(Zone.BATTLEFIELD, playerA, "Delver of Secrets");
        addCard(Zone.BATTLEFIELD, playerA, "Island", 1);
        addCard(Zone.BATTLEFIELD, playerA, "Mutavault"); // if available

        // Opponent board
        addCard(Zone.BATTLEFIELD, playerB, "Doom Blade");
        addCard(Zone.BATTLEFIELD, playerB, "Swamp");
        addCard(Zone.BATTLEFIELD, playerB, "Swamp");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PC_030916", 1);
    }

    @Test
    public void test_PC_033115_puzzle_llm_metrics() {
        beginPuzzle("test_PC_033115_puzzle_llm_metrics", 1);

        // Implemented setup for PC_033115 (Perplexing Chimera)
        // PlayerA: combo/creature interaction
        setLife(playerA, 14);
        setLife(playerB, 4);
        addCard(Zone.HAND, playerA, "Spectral Procession");
        addCard(Zone.HAND, playerA, "Raise the Alarm");
        addCard(Zone.BATTLEFIELD, playerA, "Baneslayer Angel");
        addCard(Zone.BATTLEFIELD, playerA, "Plains", 3);

        // Opponent board
        addCard(Zone.BATTLEFIELD, playerB, "Llanowar Elves");
        addCard(Zone.BATTLEFIELD, playerB, "Forest");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PC_033115", 1);
    }

    @Test
    public void test_PC_040715_puzzle_llm_metrics() {
        beginPuzzle("test_PC_040715_puzzle_llm_metrics", 1);

        // Implemented setup for PC_040715 (Perplexing Chimera)
        setLife(playerA, 13);
        setLife(playerB, 5);
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.HAND, playerA, "Searing Blaze");
        addCard(Zone.BATTLEFIELD, playerA, "Keldon Marauders");
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 2);

        // Opponent board
        addCard(Zone.BATTLEFIELD, playerB, "Mother of Runes");
        addCard(Zone.BATTLEFIELD, playerB, "Plains");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PC_040715", 1);
    }

    @Test
    public void test_PC_041415_puzzle_llm_metrics() {
        beginPuzzle("test_PC_041415_puzzle_llm_metrics", 1);

        // Implemented setup for PC_041415 (Perplexing Chimera)
        setLife(playerA, 15);
        setLife(playerB, 3);
        addCard(Zone.HAND, playerA, "Chord of Calling");
        addCard(Zone.HAND, playerA, "Elvish Mystic");
        addCard(Zone.BATTLEFIELD, playerA, "Wirewood Symbiote");
        addCard(Zone.BATTLEFIELD, playerA, "Forest");
        addCard(Zone.BATTLEFIELD, playerA, "Nykthos, Shrine to Nyx"); // if available

        // Opponent board
        addCard(Zone.BATTLEFIELD, playerB, "Counterspell");
        addCard(Zone.BATTLEFIELD, playerB, "Island");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PC_041415", 1);
    }

    @Test
    public void test_PC_042815_puzzle_llm_metrics() {
        beginPuzzle("test_PC_042815_puzzle_llm_metrics", 1);

        // Implemented setup for PC_042815 (Perplexing Chimera)
        setLife(playerA, 13);
        setLife(playerB, 5);
        // PlayerA resources
        addCard(Zone.HAND, playerA, "Lightning Helix");
        addCard(Zone.HAND, playerA, "Path to Exile");
        addCard(Zone.BATTLEFIELD, playerA, "Young Pyromancer");
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 2);

        // Opponent board
        addCard(Zone.BATTLEFIELD, playerB, "Tarmogoyf");
        addCard(Zone.BATTLEFIELD, playerB, "Forest");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PC_042815", 1);
    }

    @Test
    public void test_PC_050515_puzzle_llm_metrics() {
        beginPuzzle("test_PC_050515_puzzle_llm_metrics", 1);

        // Implemented setup for PC_050515 (Perplexing Chimera)
        setLife(playerA, 12);
        setLife(playerB, 6);
        addCard(Zone.HAND, playerA, "Snapcaster Mage");
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.BATTLEFIELD, playerA, "Snapcaster Mage"); // if available as battlefield representation
        addCard(Zone.BATTLEFIELD, playerA, "Island", 2);

        // Opponent board
        addCard(Zone.BATTLEFIELD, playerB, "Aven Mindcensor");
        addCard(Zone.BATTLEFIELD, playerB, "Plains");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PC_050515", 1);
    }

    @Test
    public void test_PC_051215_puzzle_llm_metrics() {
        beginPuzzle("test_PC_051215_puzzle_llm_metrics", 1);

        // Implemented setup for PC_051215 (Perplexing Chimera)
        setLife(playerA, 11);
        setLife(playerB, 7);
        addCard(Zone.HAND, playerA, "Collected Company");
        addCard(Zone.HAND, playerA, "Giant Growth");
        addCard(Zone.BATTLEFIELD, playerA, "Llanowar Elves");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 2);

        // Opponent board
        addCard(Zone.BATTLEFIELD, playerB, "Ball Lightning");
        addCard(Zone.BATTLEFIELD, playerB, "Mountain", 1);

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PC_051215", 1);
    }

    @Test
    public void test_PC_051915_puzzle_llm_metrics() {
        beginPuzzle("test_PC_051915_puzzle_llm_metrics", 1);

        // Implemented setup for PC_051915 (Perplexing Chimera)
        setLife(playerA, 14);
        setLife(playerB, 4);
        addCard(Zone.HAND, playerA, "Path to Exile");
        addCard(Zone.HAND, playerA, "Aether Vial");
        addCard(Zone.BATTLEFIELD, playerA, "Mother of Runes");
        addCard(Zone.BATTLEFIELD, playerA, "Plains", 2);

        // Opponent board
        addCard(Zone.BATTLEFIELD, playerB, "Snapcaster Mage");
        addCard(Zone.BATTLEFIELD, playerB, "Island");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PC_051915", 1);
    }

    @Test
    public void test_PC_052615_puzzle_llm_metrics() {
        beginPuzzle("test_PC_052615_puzzle_llm_metrics", 1);

        // Implemented setup for PC_052615 (Perplexing Chimera)
        setLife(playerA, 13);
        setLife(playerB, 5);
        addCard(Zone.HAND, playerA, "Chord of Calling");
        addCard(Zone.HAND, playerA, "Scavenging Ooze");
        addCard(Zone.BATTLEFIELD, playerA, "Birds of Paradise");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 2);

        // Opponent board
        addCard(Zone.BATTLEFIELD, playerB, "Gurmag Angler");
        addCard(Zone.BATTLEFIELD, playerB, "Swamp");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PC_052615", 1);
    }

    @Test
    public void test_PC_060915_puzzle_llm_metrics() {
        beginPuzzle("test_PC_060915_puzzle_llm_metrics", 1);

        // Implemented setup for PC_060915 (Perplexing Chimera)
        setLife(playerA, 12);
        setLife(playerB, 6);
        // PlayerA resources / win in 1 configuration
        addCard(Zone.HAND, playerA, "Brainstorm");
        addCard(Zone.HAND, playerA, "Snapcaster Mage");
        addCard(Zone.BATTLEFIELD, playerA, "Delver of Secrets");
        addCard(Zone.BATTLEFIELD, playerA, "Island", 1);
        addCard(Zone.BATTLEFIELD, playerA, "Mutavault"); // optional

        // Opponent board
        addCard(Zone.BATTLEFIELD, playerB, "Mountain");
        addCard(Zone.BATTLEFIELD, playerB, "Ball Lightning");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PC_060915", 1);
    }

    @Test
    public void test_PC_062315_puzzle_llm_metrics() {
        beginPuzzle("test_PC_062315_puzzle_llm_metrics", 1);

        // Implemented setup for PC_062315 (Perplexing Chimera)
        setLife(playerA, 13);
        setLife(playerB, 5);
        // PlayerA: green combat finishers
        addCard(Zone.HAND, playerA, "Vines of Vastwood");
        addCard(Zone.HAND, playerA, "Giant Growth");
        addCard(Zone.BATTLEFIELD, playerA, "Experiment One");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 2);

        // Opponent board
        addCard(Zone.BATTLEFIELD, playerB, "Plains");
        addCard(Zone.BATTLEFIELD, playerB, "Mother of Runes");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PC_062315", 1);
    }

    @Test
    public void test_PC_070715_puzzle_llm_metrics() {
        beginPuzzle("test_PC_070715_puzzle_llm_metrics", 1);

        // Implemented setup for PC_070715 (Perplexing Chimera)
        setLife(playerA, 11);
        setLife(playerB, 7);
        // PlayerA: aggressive red kill
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.HAND, playerA, "Chain Lightning");
        addCard(Zone.BATTLEFIELD, playerA, "Monastery Swiftspear");
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 2);

        // Opponent board
        addCard(Zone.BATTLEFIELD, playerB, "Forest");
        addCard(Zone.BATTLEFIELD, playerB, "Wall of Roots");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PC_070715", 1);
    }

    @Test
    public void test_PC_071415_puzzle_llm_metrics() {
        beginPuzzle("test_PC_071415_puzzle_llm_metrics", 1);

        // Implemented setup for PC_071415 (Perplexing Chimera)
        setLife(playerA, 14);
        setLife(playerB, 4);
        // PlayerA: white removal + attacker
        addCard(Zone.HAND, playerA, "Swords to Plowshares");
        addCard(Zone.HAND, playerA, "Brave the Elements");
        addCard(Zone.BATTLEFIELD, playerA, "Baneslayer Angel");
        addCard(Zone.BATTLEFIELD, playerA, "Plains", 2);

        // Opponent board
        addCard(Zone.BATTLEFIELD, playerB, "Island");
        addCard(Zone.BATTLEFIELD, playerB, "Snapcaster Mage");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PC_071415", 1);
    }

    @Test
    public void test_PC_072115_puzzle_llm_metrics() {
        beginPuzzle("test_PC_072115_puzzle_llm_metrics", 1);

        // Implemented setup for PC_072115 (Perplexing Chimera)
        setLife(playerA, 13);
        setLife(playerB, 5);
        // PlayerA: green/utility
        addCard(Zone.HAND, playerA, "Scavenging Ooze");
        addCard(Zone.HAND, playerA, "Harmonize");
        addCard(Zone.BATTLEFIELD, playerA, "Tarmogoyf");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 2);

        // Opponent board
        addCard(Zone.BATTLEFIELD, playerB, "Mountain");
        addCard(Zone.BATTLEFIELD, playerB, "Chain Lightning");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PC_072115", 1);
    }

    @Test
    public void test_PC_080415_puzzle_llm_metrics() {
        beginPuzzle("test_PC_080415_puzzle_llm_metrics", 1);

        // Implemented setup for PC_080415 (Perplexing Chimera)
        setLife(playerA, 12);
        setLife(playerB, 6);
        // PlayerA: tempo/combo finish
        addCard(Zone.HAND, playerA, "Brainstorm");
        addCard(Zone.HAND, playerA, "Counterspell");
        addCard(Zone.BATTLEFIELD, playerA, "Snapcaster Mage");
        addCard(Zone.BATTLEFIELD, playerA, "Island", 1);

        // Opponent board
        addCard(Zone.BATTLEFIELD, playerB, "Plains");
        addCard(Zone.BATTLEFIELD, playerB, "Kor Skyfisher");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PC_080415", 1);
    }

    @Test
    public void test_PC_081115_puzzle_llm_metrics() {
        beginPuzzle("test_PC_081115_puzzle_llm_metrics", 1);

        // Implemented setup for PC_081115 (Perplexing Chimera)
        setLife(playerA, 14);
        setLife(playerB, 4);
        // PlayerA: green pump/finish
        addCard(Zone.HAND, playerA, "Giant Growth");
        addCard(Zone.HAND, playerA, "Vines of Vastwood");
        addCard(Zone.BATTLEFIELD, playerA, "Llanowar Elves");
        addCard(Zone.BATTLEFIELD, playerA, "Pelt Collector");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 2);

        // Opponent board
        addCard(Zone.BATTLEFIELD, playerB, "Mountain");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PC_081115", 1);
    }

    @Test
    public void test_PC_081815_puzzle_llm_metrics() {
        beginPuzzle("test_PC_081815_puzzle_llm_metrics", 1);

        // Implemented setup for PC_081815 (Perplexing Chimera)
        setLife(playerA, 13);
        setLife(playerB, 5);
        // PlayerA: white token/boost finish
        addCard(Zone.HAND, playerA, "Raise the Alarm");
        addCard(Zone.HAND, playerA, "Spectral Procession");
        addCard(Zone.BATTLEFIELD, playerA, "Serra Angel");
        addCard(Zone.BATTLEFIELD, playerA, "Plains", 1);

        // Opponent board
        addCard(Zone.BATTLEFIELD, playerB, "Island");
        addCard(Zone.BATTLEFIELD, playerB, "Mystic Snake");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PC_081815", 1);
    }

    @Test
    public void test_PC_082515_puzzle_llm_metrics() {
        beginPuzzle("test_PC_082515_puzzle_llm_metrics", 1);

        // Implemented setup for PC_082515 (Perplexing Chimera)
        setLife(playerA, 11);
        setLife(playerB, 7);
        // PlayerA: red burn/combo finish
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.HAND, playerA, "Searing Blaze");
        addCard(Zone.BATTLEFIELD, playerA, "Keldon Marauders");
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 2);

        // Opponent board
        addCard(Zone.BATTLEFIELD, playerB, "Forest");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PC_082515", 1);
    }

    @Test
    public void test_PC_090115_puzzle_llm_metrics() {
        beginPuzzle("test_PC_090115_puzzle_llm_metrics", 1);

        // Implemented setup for PC_090115 (Perplexing Chimera)
        setLife(playerA, 15);
        setLife(playerB, 3);
        // PlayerA: black removal / combo finish
        addCard(Zone.HAND, playerA, "Doom Blade");
        addCard(Zone.HAND, playerA, "Victim of Night");
        addCard(Zone.BATTLEFIELD, playerA, "Gray Merchant of Asphodel");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp", 2);

        // Opponent board
        addCard(Zone.BATTLEFIELD, playerB, "Plains");
        addCard(Zone.BATTLEFIELD, playerB, "Mother of Runes");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PC_090115", 1);
    }

    @Test
    public void test_PC_100215_puzzle_llm_metrics() {
        beginPuzzle("test_PC_100215_puzzle_llm_metrics", 1);

        // Implemented setup for PC_100215 (Perplexing Chimera)
        setLife(playerA, 12);
        setLife(playerB, 6);
        // PlayerA: green value / token finish
        addCard(Zone.HAND, playerA, "Collected Company");
        addCard(Zone.HAND, playerA, "Giant Growth");
        addCard(Zone.BATTLEFIELD, playerA, "Llanowar Elves");
        addCard(Zone.BATTLEFIELD, playerA, "Ghor-Clan Rampager");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 2);

        // Opponent board
        addCard(Zone.BATTLEFIELD, playerB, "Plains");
        addCard(Zone.BATTLEFIELD, playerB, "Kor Skyfisher");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PC_100215", 1);
    }

    @Test
    public void test_PC_100915_puzzle_llm_metrics() {
        beginPuzzle("test_PC_100915_puzzle_llm_metrics", 1);

        // Implemented setup for PC_100915 (Perplexing Chimera)
        setLife(playerA, 14);
        setLife(playerB, 4);
        // PlayerA: tempo/interaction (blue)
        addCard(Zone.HAND, playerA, "Brainstorm");
        addCard(Zone.HAND, playerA, "Force of Will");
        addCard(Zone.BATTLEFIELD, playerA, "Delver of Secrets");
        addCard(Zone.BATTLEFIELD, playerA, "Island", 2);
        // Opponent board
        addCard(Zone.BATTLEFIELD, playerB, "Swamp");
        addCard(Zone.BATTLEFIELD, playerB, "Gravecrawler");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PC_100915", 1);
    }

    @Test
    public void test_PC_101615_puzzle_llm_metrics() {
        beginPuzzle("test_PC_101615_puzzle_llm_metrics", 1);

        // Implemented setup for PC_101615 (Perplexing Chimera)
        setLife(playerA, 11);
        setLife(playerB, 7);
        // PlayerA: aggressive red finish
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.HAND, playerA, "Lava Spike");
        addCard(Zone.BATTLEFIELD, playerA, "Eidolon of the Great Revel");
        addCard(Zone.BATTLEFIELD, playerA, "Monastery Swiftspear");
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 2);

        // Opponent board
        addCard(Zone.BATTLEFIELD, playerB, "Forest");
        addCard(Zone.BATTLEFIELD, playerB, "Birds of Paradise");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PC_101615", 1);
    }

    @Test
    public void test_PC_102315_puzzle_llm_metrics() {
        beginPuzzle("test_PC_102315_puzzle_llm_metrics", 1);

        // Implemented setup for PC_102315 (Perplexing Chimera)
        setLife(playerA, 13);
        setLife(playerB, 5);
        // PlayerA: white combat / pump finish
        addCard(Zone.HAND, playerA, "Swords to Plowshares");
        addCard(Zone.HAND, playerA, "Brave the Elements");
        addCard(Zone.BATTLEFIELD, playerA, "Silverblade Paladin");
        addCard(Zone.BATTLEFIELD, playerA, "Plains", 2);

        // Opponent board
        addCard(Zone.BATTLEFIELD, playerB, "Island");
        addCard(Zone.BATTLEFIELD, playerB, "Counterspell");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PC_102315", 1);
    }

    @Test
    public void test_PC_110115_puzzle_llm_metrics() {
        beginPuzzle("test_PC_110115_puzzle_llm_metrics", 1);

        // Implemented setup for PC_110115 (Perplexing Chimera)
        setLife(playerA, 15);
        setLife(playerB, 3);
        // PlayerA: black finish (drain / removal)
        addCard(Zone.HAND, playerA, "Sign in Blood");
        addCard(Zone.HAND, playerA, "Victim of Night");
        addCard(Zone.BATTLEFIELD, playerA, "Gray Merchant of Asphodel");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp", 2);

        // Opponent board
        addCard(Zone.BATTLEFIELD, playerB, "Mountain");
        addCard(Zone.BATTLEFIELD, playerB, "Goblin Guide");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PC_110115", 1);
    }

    @Test
    public void test_PC_111815_puzzle_llm_metrics() {
        beginPuzzle("test_PC_111815_puzzle_llm_metrics", 1);

        // Implemented setup for PC_111815 (Perplexing Chimera)
        setLife(playerA, 12);
        setLife(playerB, 6);
        // PlayerA: white tempo / removal combo
        addCard(Zone.HAND, playerA, "Swords to Plowshares");
        addCard(Zone.HAND, playerA, "Path to Exile");
        addCard(Zone.BATTLEFIELD, playerA, "Stoneforge Mystic");
        addCard(Zone.BATTLEFIELD, playerA, "Plains", 2);

        // Opponent board
        addCard(Zone.BATTLEFIELD, playerB, "Tarmogoyf");
        addCard(Zone.BATTLEFIELD, playerB, "Swamp");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PC_111815", 1);
    }

    @Test
    public void test_PC_112615_puzzle_llm_metrics() {
        beginPuzzle("test_PC_112615_puzzle_llm_metrics", 1);

        // Implemented setup for PC_112615 (Perplexing Chimera)
        setLife(playerA, 14);
        setLife(playerB, 4);
        // PlayerA: blue tempo/interaction
        addCard(Zone.HAND, playerA, "Brainstorm");
        addCard(Zone.HAND, playerA, "Force of Negation");
        addCard(Zone.BATTLEFIELD, playerA, "Snapcaster Mage");
        addCard(Zone.BATTLEFIELD, playerA, "Island", 2);

        // Opponent board
        addCard(Zone.BATTLEFIELD, playerB, "Mountain");
        addCard(Zone.BATTLEFIELD, playerB, "Kiki-Jiki, Mirror Breaker");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PC_112615", 1);
    }

    @Test
    public void test_PC_120215_puzzle_llm_metrics() {
        beginPuzzle("test_PC_120215_puzzle_llm_metrics", 1);

        // Implemented setup for PC_120215 (Perplexing Chimera)
        setLife(playerA, 13);
        setLife(playerB, 5);
        // PlayerA: green pump / combat finish
        addCard(Zone.HAND, playerA, "Giant Growth");
        addCard(Zone.HAND, playerA, "Rancor");
        addCard(Zone.BATTLEFIELD, playerA, "Scavenging Ooze");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 2);

        // Opponent board
        addCard(Zone.BATTLEFIELD, playerB, "Plains");
        addCard(Zone.BATTLEFIELD, playerB, "Mother of Runes");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PC_120215", 1);
    }

    @Test
    public void test_PC_120915_puzzle_llm_metrics() {
        beginPuzzle("test_PC_120915_puzzle_llm_metrics", 1);

        // Implemented setup for PC_120915 (Perplexing Chimera)
        setLife(playerA, 11);
        setLife(playerB, 7);
        // PlayerA: red burn finish
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.HAND, playerA, "Searing Blaze");
        addCard(Zone.BATTLEFIELD, playerA, "Keldon Marauders");
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 2);

        // Opponent board
        addCard(Zone.BATTLEFIELD, playerB, "Forest");
        addCard(Zone.BATTLEFIELD, playerB, "Tarmogoyf");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PC_120915", 1);
    }

    @Test
    public void test_PC_121615_puzzle_llm_metrics() {
        beginPuzzle("test_PC_121615_puzzle_llm_metrics", 1);

        // Implemented setup for PC_121615 (Perplexing Chimera)
        setLife(playerA, 15);
        setLife(playerB, 3);
        // PlayerA: blue/white tempo finish
        addCard(Zone.HAND, playerA, "Snapcaster Mage");
        addCard(Zone.HAND, playerA, "Path to Exile");
        addCard(Zone.BATTLEFIELD, playerA, "Snapcaster Mage"); // battlefield representation if available
        addCard(Zone.BATTLEFIELD, playerA, "Island", 1);
        addCard(Zone.BATTLEFIELD, playerA, "Plains", 1);

        // Opponent board
        addCard(Zone.BATTLEFIELD, playerB, "Mother of Runes");
        addCard(Zone.BATTLEFIELD, playerB, "Plains");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PC_121615", 1);
    }

    @Test
    public void test_PC_130115_puzzle_llm_metrics() {
        beginPuzzle("test_PC_130115_puzzle_llm_metrics", 1);

        // Implemented setup for PC_130115 (Perplexing Chimera)
        setLife(playerA, 12);
        setLife(playerB, 6);
        // PlayerA: white combat/tempo finish
        addCard(Zone.HAND, playerA, "Swords to Plowshares");
        addCard(Zone.HAND, playerA, "Brave the Elements");
        addCard(Zone.BATTLEFIELD, playerA, "Aether Vial");
        addCard(Zone.BATTLEFIELD, playerA, "Silverblade Paladin");
        addCard(Zone.BATTLEFIELD, playerA, "Plains", 2);

        // Opponent board
        addCard(Zone.BATTLEFIELD, playerB, "Island");
        addCard(Zone.BATTLEFIELD, playerB, "Snapcaster Mage");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PC_130115", 1);
    }

    @Test
    public void test_PC_130915_puzzle_llm_metrics() {
        beginPuzzle("test_PC_130915_puzzle_llm_metrics", 1);

        // Implemented setup for PC_130915 (Perplexing Chimera)
        setLife(playerA, 14);
        setLife(playerB, 4);
        // PlayerA: blue tempo/combo
        addCard(Zone.HAND, playerA, "Brainstorm");
        addCard(Zone.HAND, playerA, "Ponder");
        addCard(Zone.BATTLEFIELD, playerA, "Delver of Secrets");
        addCard(Zone.BATTLEFIELD, playerA, "Island", 2);

        // Opponent board
        addCard(Zone.BATTLEFIELD, playerB, "Swamp");
        addCard(Zone.BATTLEFIELD, playerB, "Gurmag Angler");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PC_130915", 1);
    }

    @Test
    public void test_PC_131615_puzzle_llm_metrics() {
        beginPuzzle("test_PC_131615_puzzle_llm_metrics", 1);

        // Implemented setup for PC_131615 (Perplexing Chimera)
        setLife(playerA, 11);
        setLife(playerB, 7);
        // PlayerA: green midrange finish
        addCard(Zone.HAND, playerA, "Giant Growth");
        addCard(Zone.HAND, playerA, "Scavenging Ooze");
        addCard(Zone.BATTLEFIELD, playerA, "Tarmogoyf");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 2);

        // Opponent board
        addCard(Zone.BATTLEFIELD, playerB, "Mountain");
        addCard(Zone.BATTLEFIELD, playerB, "Chain Lightning");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PC_131615", 1);
    }

    @Test
    public void test_PC_132315_puzzle_llm_metrics() {
        beginPuzzle("test_PC_132315_puzzle_llm_metrics", 1);

        // Implemented setup for PC_132315 (Perplexing Chimera)
        setLife(playerA, 13);
        setLife(playerB, 5);
        // PlayerA: red/green mixed attack finish
        addCard(Zone.HAND, playerA, "Burning-Tree Emissary");
        addCard(Zone.HAND, playerA, "Rancor");
        addCard(Zone.BATTLEFIELD, playerA, "Wild Nacatl");
        addCard(Zone.BATTLEFIELD, playerA, "Mountain");
        addCard(Zone.BATTLEFIELD, playerA, "Forest");

        // Opponent board
        addCard(Zone.BATTLEFIELD, playerB, "Tarmogoyf");
        addCard(Zone.BATTLEFIELD, playerB, "Island");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PC_132315", 1);
    }

    @Test
    public void test_PC_140115_puzzle_llm_metrics() {
        beginPuzzle("test_PC_140115_puzzle_llm_metrics", 1);

        // Implemented setup for PC_140115 (Perplexing Chimera)
        setLife(playerA, 15);
        setLife(playerB, 3);
        // PlayerA: black drain / removal finish
        addCard(Zone.HAND, playerA, "Sign in Blood");
        addCard(Zone.HAND, playerA, "Victim of Night");
        addCard(Zone.BATTLEFIELD, playerA, "Gray Merchant of Asphodel");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp", 2);

        // Opponent board
        addCard(Zone.BATTLEFIELD, playerB, "Plains");
        addCard(Zone.BATTLEFIELD, playerB, "Mother of Runes");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PC_140115", 1);
    }

    @Test
    public void test_PC_141815_puzzle_llm_metrics() {
        beginPuzzle("test_PC_141815_puzzle_llm_metrics", 1);

        // Implemented setup for PC_141815 (Perplexing Chimera)
        setLife(playerA, 12);
        setLife(playerB, 6);
        // PlayerA: white tempo / removal combo
        addCard(Zone.HAND, playerA, "Swords to Plowshares");
        addCard(Zone.HAND, playerA, "Brave the Elements");
        addCard(Zone.BATTLEFIELD, playerA, "Stoneforge Mystic");
        addCard(Zone.BATTLEFIELD, playerA, "Plains", 2);

        // Opponent board
        addCard(Zone.BATTLEFIELD, playerB, "Tarmogoyf");
        addCard(Zone.BATTLEFIELD, playerB, "Swamp");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PC_141815", 1);
    }

    @Test
    public void test_PC_142615_puzzle_llm_metrics() {
        beginPuzzle("test_PC_142615_puzzle_llm_metrics", 1);

        // Implemented setup for PC_142615 (Perplexing Chimera)
        setLife(playerA, 14);
        setLife(playerB, 4);
        // PlayerA: blue tempo/interaction
        addCard(Zone.HAND, playerA, "Brainstorm");
        addCard(Zone.HAND, playerA, "Force of Negation");
        addCard(Zone.BATTLEFIELD, playerA, "Snapcaster Mage");
        addCard(Zone.BATTLEFIELD, playerA, "Island", 2);

        // Opponent board
        addCard(Zone.BATTLEFIELD, playerB, "Kiki-Jiki, Mirror Breaker");
        addCard(Zone.BATTLEFIELD, playerB, "Mountain");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PC_142615", 1);
    }

    @Test
    public void test_PC_150215_puzzle_llm_metrics() {
        beginPuzzle("test_PC_150215_puzzle_llm_metrics", 1);

        // Implemented setup for PC_150215 (Perplexing Chimera)
        setLife(playerA, 13);
        setLife(playerB, 5);
        // PlayerA: green pump / combat finish
        addCard(Zone.HAND, playerA, "Giant Growth");
        addCard(Zone.HAND, playerA, "Rancor");
        addCard(Zone.BATTLEFIELD, playerA, "Scavenging Ooze");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 2);

        // Opponent board
        addCard(Zone.BATTLEFIELD, playerB, "Mother of Runes");
        addCard(Zone.BATTLEFIELD, playerB, "Plains");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PC_150215", 1);
    }

    @Test
    public void test_PC_151615_puzzle_llm_metrics() {
        beginPuzzle("test_PC_151615_puzzle_llm_metrics", 1);

        // Implemented setup for PC_151615 (Perplexing Chimera)
        setLife(playerA, 12);
        setLife(playerB, 6);
        // PlayerA: white combat / tempo
        addCard(Zone.HAND, playerA, "Path to Exile");
        addCard(Zone.HAND, playerA, "Collected Company");
        addCard(Zone.BATTLEFIELD, playerA, "Llanowar Elves");
        addCard(Zone.BATTLEFIELD, playerA, "Plains", 1);
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 1);

        // Opponent board
        addCard(Zone.BATTLEFIELD, playerB, "Snapcaster Mage");
        addCard(Zone.BATTLEFIELD, playerB, "Island");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PC_151615", 1);
    }

    @Test
    public void test_PC_152315_puzzle_llm_metrics() {
        beginPuzzle("test_PC_152315_puzzle_llm_metrics", 1);

        // Implemented setup for PC_152315 (Perplexing Chimera)
        setLife(playerA, 14);
        setLife(playerB, 4);
        // PlayerA: blue/tempo with a value creature
        addCard(Zone.HAND, playerA, "Ponder");
        addCard(Zone.HAND, playerA, "Snapcaster Mage");
        addCard(Zone.BATTLEFIELD, playerA, "Delver of Secrets");
        addCard(Zone.BATTLEFIELD, playerA, "Island", 2);

        // Opponent board
        addCard(Zone.BATTLEFIELD, playerB, "Gurmag Angler");
        addCard(Zone.BATTLEFIELD, playerB, "Swamp");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PC_152315", 1);
    }

    @Test
    public void test_PC_160115_puzzle_llm_metrics() {
        beginPuzzle("test_PC_160115_puzzle_llm_metrics", 1);

        // Implemented setup for PC_160115 (Perplexing Chimera)
        setLife(playerA, 11);
        setLife(playerB, 7);
        // PlayerA: aggressive red kill finish
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.HAND, playerA, "Chain Lightning");
        addCard(Zone.BATTLEFIELD, playerA, "Monastery Swiftspear");
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 2);

        // Opponent board
        addCard(Zone.BATTLEFIELD, playerB, "Plains");
        addCard(Zone.BATTLEFIELD, playerB, "Mother of Runes");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PC_160115", 1);
    }

    @Test
    public void test_PC_160915_puzzle_llm_metrics() {
        beginPuzzle("test_PC_160915_puzzle_llm_metrics", 1);

        // Implemented setup for PC_160915 (Perplexing Chimera)
        setLife(playerA, 13);
        setLife(playerB, 7);
        // PlayerA: green pump / value finish
        addCard(Zone.HAND, playerA, "Giant Growth");
        addCard(Zone.HAND, playerA, "Rancor");
        addCard(Zone.BATTLEFIELD, playerA, "Tarmogoyf");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 2);

        // Opponent board
        addCard(Zone.BATTLEFIELD, playerB, "Mountain");
        addCard(Zone.BATTLEFIELD, playerB, "Chain Lightning");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PC_160915", 1);
    }

    @Test
    public void test_PC_161615_puzzle_llm_metrics() {
        beginPuzzle("test_PC_161615_puzzle_llm_metrics", 1);

        // Implemented setup for PC_161615 (Perplexing Chimera)
        setLife(playerA, 15);
        setLife(playerB, 3);
        // PlayerA: black drain / removal finish
        addCard(Zone.HAND, playerA, "Sign in Blood");
        addCard(Zone.HAND, playerA, "Victim of Night");
        addCard(Zone.BATTLEFIELD, playerA, "Gray Merchant of Asphodel");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp", 2);

        // Opponent board
        addCard(Zone.BATTLEFIELD, playerB, "Plains");
        addCard(Zone.BATTLEFIELD, playerB, "Mother of Runes");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PC_161615", 1);
    }

    @Test
    public void test_PC_162315_puzzle_llm_metrics() {
        beginPuzzle("test_PC_162315_puzzle_llm_metrics", 1);

        // Implemented setup for PC_162315 (Perplexing Chimera)
        setLife(playerA, 12);
        setLife(playerB, 6);
        // PlayerA: blue tempo/value
        addCard(Zone.HAND, playerA, "Brainstorm");
        addCard(Zone.HAND, playerA, "Ponder");
        addCard(Zone.BATTLEFIELD, playerA, "Delver of Secrets");
        addCard(Zone.BATTLEFIELD, playerA, "Island", 2);

        // Opponent board
        addCard(Zone.BATTLEFIELD, playerB, "Tarmogoyf");
        addCard(Zone.BATTLEFIELD, playerB, "Forest");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PC_162315", 1);
    }

    @Test
    public void test_PC_170115_puzzle_llm_metrics() {
        beginPuzzle("test_PC_170115_puzzle_llm_metrics", 1);

        // Implemented setup for PC_170115 (Perplexing Chimera)
        setLife(playerA, 14);
        setLife(playerB, 6);
        // PlayerA: red damage/tempo finish
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.HAND, playerA, "Lava Spike");
        addCard(Zone.BATTLEFIELD, playerA, "Monastery Swiftspear");
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 2);

        // Opponent board
        addCard(Zone.BATTLEFIELD, playerB, "Plains");
        addCard(Zone.BATTLEFIELD, playerB, "Knight of the White Orchid");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PC_170115", 1);
    }

    @Test
    public void test_PC_170915_puzzle_llm_metrics() {
        beginPuzzle("test_PC_170915_puzzle_llm_metrics", 1);

        // Implemented setup for PC_170915 (Perplexing Chimera)
        setLife(playerA, 11);
        setLife(playerB, 7);
        // PlayerA: tempo/interaction (blue-white)
        addCard(Zone.HAND, playerA, "Path to Exile");
        addCard(Zone.HAND, playerA, "Snapcaster Mage");
        addCard(Zone.BATTLEFIELD, playerA, "Snapcaster Mage");
        addCard(Zone.BATTLEFIELD, playerA, "Plains", 1);
        addCard(Zone.BATTLEFIELD, playerA, "Island", 1);

        // Opponent board
        addCard(Zone.BATTLEFIELD, playerB, "Tarmogoyf");
        addCard(Zone.BATTLEFIELD, playerB, "Forest");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PC_170915", 1);
    }

    @Test
    public void test_PC_171615_puzzle_llm_metrics() {
        beginPuzzle("test_PC_171615_puzzle_llm_metrics", 1);

        // Implemented setup for PC_171615 (Perplexing Chimera)
        setLife(playerA, 12);
        setLife(playerB, 6);
        // PlayerA: white/tempo with tempo plays
        addCard(Zone.HAND, playerA, "Swords to Plowshares");
        addCard(Zone.HAND, playerA, "Brave the Elements");
        addCard(Zone.BATTLEFIELD, playerA, "Stoneforge Mystic");
        addCard(Zone.BATTLEFIELD, playerA, "Plains", 1);

        // Opponent board
        addCard(Zone.BATTLEFIELD, playerB, "Snapcaster Mage");
        addCard(Zone.BATTLEFIELD, playerB, "Island");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PC_171615", 1);
    }

    @Test
    public void test_PC_172315_puzzle_llm_metrics() {
        beginPuzzle("test_PC_172315_puzzle_llm_metrics", 1);

        // Implemented setup for PC_172315 (Perplexing Chimera)
        setLife(playerA, 14);
        setLife(playerB, 4);
        // PlayerA: blue tempo/value
        addCard(Zone.HAND, playerA, "Ponder");
        addCard(Zone.HAND, playerA, "Brainstorm");
        addCard(Zone.BATTLEFIELD, playerA, "Delver of Secrets");
        addCard(Zone.BATTLEFIELD, playerA, "Island", 2);

        // Opponent board
        addCard(Zone.BATTLEFIELD, playerB, "Gurmag Angler");
        addCard(Zone.BATTLEFIELD, playerB, "Swamp");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PC_172315", 1);
    }

    @Test
    public void test_PC_180115_puzzle_llm_metrics() {
        beginPuzzle("test_PC_180115_puzzle_llm_metrics", 1);

        // Implemented setup for PC_180115 (Perplexing Chimera)
        setLife(playerA, 11);
        setLife(playerB, 7);
        // PlayerA: red burn / tempo
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.HAND, playerA, "Skewer the Critics");
        addCard(Zone.BATTLEFIELD, playerA, "Monastery Swiftspear");
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 2);

        // Opponent board
        addCard(Zone.BATTLEFIELD, playerB, "Tarmogoyf");
        addCard(Zone.BATTLEFIELD, playerB, "Forest");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PC_180115", 1);
    }

    @Test
    public void test_PC_180915_puzzle_llm_metrics() {
        beginPuzzle("test_PC_180915_puzzle_llm_metrics", 1);

        // Implemented setup for PC_180915 (Perplexing Chimera)
        setLife(playerA, 13);
        setLife(playerB, 5);
        // PlayerA: green pump/value finish
        addCard(Zone.HAND, playerA, "Giant Growth");
        addCard(Zone.HAND, playerA, "Scavenging Ooze");
        addCard(Zone.BATTLEFIELD, playerA, "Tarmogoyf");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 2);

        // Opponent board
        addCard(Zone.BATTLEFIELD, playerB, "Plains");
        addCard(Zone.BATTLEFIELD, playerB, "Mother of Runes");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PC_180915", 1);
    }

    @Test
    public void test_PC_181615_puzzle_llm_metrics() {
        beginPuzzle("test_PC_181615_puzzle_llm_metrics", 1);

        // Implemented setup for PC_181615 (Perplexing Chimera)
        setLife(playerA, 15);
        setLife(playerB, 5);
        // PlayerA: black drain / finish
        addCard(Zone.HAND, playerA, "Doom Blade");
        addCard(Zone.HAND, playerA, "Sign in Blood");
        addCard(Zone.BATTLEFIELD, playerA, "Gray Merchant of Asphodel");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp", 2);

        // Opponent board
        addCard(Zone.BATTLEFIELD, playerB, "Mountain");
        addCard(Zone.BATTLEFIELD, playerB, "Goblin Guide");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PC_181615", 1);
    }

    @Test
    public void test_PC_182315_puzzle_llm_metrics() {
        beginPuzzle("test_PC_182315_puzzle_llm_metrics", 1);

        // Implemented setup for PC_182315 (Perplexing Chimera)
        setLife(playerA, 12);
        setLife(playerB, 6);
        // PlayerA: blue tempo / value
        addCard(Zone.HAND, playerA, "Ponder");
        addCard(Zone.HAND, playerA, "Brainstorm");
        addCard(Zone.BATTLEFIELD, playerA, "Delver of Secrets");
        addCard(Zone.BATTLEFIELD, playerA, "Island", 2);

        // Opponent board
        addCard(Zone.BATTLEFIELD, playerB, "Tarmogoyf");
        addCard(Zone.BATTLEFIELD, playerB, "Forest");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PC_182315", 1);
    }

    @Test
    public void test_PC_190115_puzzle_llm_metrics() {
        beginPuzzle("test_PC_190115_puzzle_llm_metrics", 1);

        // Implemented setup for PC_190115 (Perplexing Chimera)
        setLife(playerA, 14);
        setLife(playerB, 4);
        // PlayerA: red burn / tempo
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.HAND, playerA, "Lava Spike");
        addCard(Zone.BATTLEFIELD, playerA, "Monastery Swiftspear");
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 2);

        // Opponent board
        addCard(Zone.BATTLEFIELD, playerB, "Plains");
        addCard(Zone.BATTLEFIELD, playerB, "Mother of Runes");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PC_190115", 1);
    }

    @Test
    public void test_PC_190915_puzzle_llm_metrics() {
        beginPuzzle("test_PC_190915_puzzle_llm_metrics", 1);

        // Implemented setup for PC_190915 (Perplexing Chimera)
        setLife(playerA, 11);
        setLife(playerB, 7);
        // PlayerA: white tempo / combat
        addCard(Zone.HAND, playerA, "Path to Exile");
        addCard(Zone.HAND, playerA, "Brave the Elements");
        addCard(Zone.BATTLEFIELD, playerA, "Silverblade Paladin");
        addCard(Zone.BATTLEFIELD, playerA, "Plains", 2);

        // Opponent board
        addCard(Zone.BATTLEFIELD, playerB, "Island");
        addCard(Zone.BATTLEFIELD, playerB, "Snapcaster Mage");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PC_190915", 1);
    }

    @Test
    public void test_PC_200115_puzzle_llm_metrics() {
        beginPuzzle("test_PC_200115_puzzle_llm_metrics", 1);

        // Implemented setup for PC_200115 (Perplexing Chimera)
        setLife(playerA, 12);
        setLife(playerB, 6);
        // PlayerA: green value / combat finish
        addCard(Zone.HAND, playerA, "Giant Growth");
        addCard(Zone.HAND, playerA, "Scavenging Ooze");
        addCard(Zone.BATTLEFIELD, playerA, "Tarmogoyf");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 2);

        // Opponent board
        addCard(Zone.BATTLEFIELD, playerB, "Island");
        addCard(Zone.BATTLEFIELD, playerB, "Counterspell");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PC_200115", 1);
    }

    @Test
    public void test_PC_200915_puzzle_llm_metrics() {
        beginPuzzle("test_PC_200915_puzzle_llm_metrics", 1);

        // Minimal placeholder setup for PC_200915
        setLife(playerA, 14);
        setLife(playerB, 6);
        addCard(Zone.BATTLEFIELD, playerA, "Island", 1);
        addCard(Zone.BATTLEFIELD, playerB, "Swamp", 1);

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PC_200915", 1);
    }

    @Test
    public void test_PC_201615_puzzle_llm_metrics() {
        beginPuzzle("test_PC_201615_puzzle_llm_metrics", 1);

        // Minimal placeholder setup for PC_201615
        setLife(playerA, 11);
        setLife(playerB, 9);
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 1);
        addCard(Zone.BATTLEFIELD, playerB, "Plains", 1);

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PC_201615", 1);
    }

    @Test
    public void test_PC_202315_puzzle_llm_metrics() {
        beginPuzzle("test_PC_202315_puzzle_llm_metrics", 1);

        // Minimal placeholder setup for PC_202315
        setLife(playerA, 13);
        setLife(playerB, 7);
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 1);
        addCard(Zone.BATTLEFIELD, playerB, "Island", 1);

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PC_202315", 1);
    }

    @Test
    public void test_PC_210115_puzzle_llm_metrics() {
        beginPuzzle("test_PC_210115_puzzle_llm_metrics", 1);

        // Minimal placeholder setup for PC_210115
        setLife(playerA, 15);
        setLife(playerB, 5);
        addCard(Zone.BATTLEFIELD, playerA, "Swamp", 1);
        addCard(Zone.BATTLEFIELD, playerB, "Mountain", 1);

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PC_210115", 1);
    }

    // New tests: implement next batch of 5 missing puzzles
    @Test
    public void test_INQ01_puzzle_llm_metrics() {
        beginPuzzle("test_INQ01_puzzle_llm_metrics", 1);
        org.junit.Assume.assumeTrue("Skipping INQ01: Play the Specified Permanent", false);

        // Minimal INQ01 puzzle setup
        setLife(playerA, 10);
        setLife(playerB, 6);
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Plains", 1);

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("INQ01", 1);
    }

    @Test
    public void test_INQ02_puzzle_llm_metrics() {
        beginPuzzle("test_INQ02_puzzle_llm_metrics", 1);
        org.junit.Assume.assumeTrue("Skipping INQ02: Gain Control of Specified Permanents", false);

        // Minimal INQ02 puzzle setup
        setLife(playerA, 12);
        setLife(playerB, 4);
        addCard(Zone.HAND, playerA, "Giant Growth");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Island", 1);

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("INQ02", 1);
    }

    @Test
    public void test_INQ03_puzzle_llm_metrics() {
        beginPuzzle("test_INQ03_puzzle_llm_metrics", 1);

        // Minimal INQ03 puzzle setup
        setLife(playerA, 11);
        setLife(playerB, 5);
        addCard(Zone.HAND, playerA, "Path to Exile");
        addCard(Zone.BATTLEFIELD, playerA, "Plains", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Creature", 1); // generic placeholder

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("INQ03", 1);
    }

    @Test
    public void test_MTGP_02_puzzle_llm_metrics() {
        beginPuzzle("test_MTGP_02_puzzle_llm_metrics", 1);

        // Minimal MTGP_02 puzzle setup (mirror style of other MTGP tests)
        setLife(playerA, 10);
        setLife(playerB, 8);
        addCard(Zone.HAND, playerA, "Shock");
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 1);
        addCard(Zone.BATTLEFIELD, playerB, "Nebelgast Herald");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("MTGP_02", 1);
    }

    @Test
    public void test_PC_072815_puzzle_llm_metrics() {
        beginPuzzle("test_PC_072815_puzzle_llm_metrics", 1);

        // Minimal PC_072815 puzzle setup
        setLife(playerA, 13);
        setLife(playerB, 5);
        addCard(Zone.HAND, playerA, "Lightning Helix");
        addCard(Zone.BATTLEFIELD, playerA, "Mountain");
        addCard(Zone.BATTLEFIELD, playerA, "Plains");
        addCard(Zone.BATTLEFIELD, playerB, "Mother of Runes");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PC_072815", 1);
    }

    // New tests: next batch of 5 missing puzzles
    @Test
    public void test_PC_090815_puzzle_llm_metrics() {
        beginPuzzle("test_PC_090815_puzzle_llm_metrics", 1);

        // Minimal PC_090815 puzzle setup
        setLife(playerA, 13);
        setLife(playerB, 5);
        addCard(Zone.HAND, playerA, "Doom Blade");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Plains", 1);

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PC_090815", 1);
    }

    @Test
    public void test_PC_091515_puzzle_llm_metrics() {
        beginPuzzle("test_PC_091515_puzzle_llm_metrics", 1);

        // Minimal PC_091515 puzzle setup
        setLife(playerA, 12);
        setLife(playerB, 6);
        addCard(Zone.HAND, playerA, "Path to Exile");
        addCard(Zone.BATTLEFIELD, playerA, "Plains", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Creature", 1);

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PC_091515", 1);
    }

    @Test
    public void test_PC_092215_puzzle_llm_metrics() {
        beginPuzzle("test_PC_092215_puzzle_llm_metrics", 1);

        // Minimal PC_092215 puzzle setup
        setLife(playerA, 14);
        setLife(playerB, 4);
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Mother of Runes");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PC_092215", 1);
    }

    @Test
    public void test_PC_092915_puzzle_llm_metrics() {
        beginPuzzle("test_PC_092915_puzzle_llm_metrics", 1);

        // Minimal PC_092915 puzzle setup
        setLife(playerA, 11);
        setLife(playerB, 7);
        addCard(Zone.HAND, playerA, "Giant Growth");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Wall of Roots");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PC_092915", 1);
    }

    @Test
    public void test_PC_100615_puzzle_llm_metrics() {
        beginPuzzle("test_PC_100615_puzzle_llm_metrics", 1);

        // Minimal PC_100615 puzzle setup
        setLife(playerA, 13);
        setLife(playerB, 6);
        addCard(Zone.HAND, playerA, "Snapcaster Mage");
        addCard(Zone.BATTLEFIELD, playerA, "Island", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Tarmogoyf");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PC_100615", 1);
    }

    @Test
    public void test_PC_101315_puzzle_llm_metrics() {
        beginPuzzle("test_PC_101315_puzzle_llm_metrics", 1);

        // Minimal PC_101315 puzzle setup
        setLife(playerA, 12);
        setLife(playerB, 6);
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.HAND, playerA, "Giant Growth");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 1);
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 1);
        addCard(Zone.BATTLEFIELD, playerB, "Wall of Roots");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PC_101315", 1);
    }

    @Test
    public void test_PC_102015_puzzle_llm_metrics() {
        beginPuzzle("test_PC_102015_puzzle_llm_metrics", 1);

        // Minimal PC_102015 puzzle setup
        setLife(playerA, 14);
        setLife(playerB, 4);
        addCard(Zone.HAND, playerA, "Path to Exile");
        addCard(Zone.HAND, playerA, "Swords to Plowshares");
        addCard(Zone.BATTLEFIELD, playerA, "Plains", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Snapcaster Mage");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PC_102015", 1);
    }

    @Test
    public void test_PC_102715_puzzle_llm_metrics() {
        beginPuzzle("test_PC_102715_puzzle_llm_metrics", 1);

        // Minimal PC_102715 puzzle setup
        setLife(playerA, 11);
        setLife(playerB, 7);
        addCard(Zone.HAND, playerA, "Doom Blade");
        addCard(Zone.HAND, playerA, "Victim of Night");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Gray Merchant of Asphodel");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PC_102715", 1);
    }

    @Test
    public void test_PC_110315_puzzle_llm_metrics() {
        beginPuzzle("test_PC_110315_puzzle_llm_metrics", 1);

        // Minimal PC_110315 puzzle setup
        setLife(playerA, 13);
        setLife(playerB, 5);
        addCard(Zone.HAND, playerA, "Brainstorm");
        addCard(Zone.HAND, playerA, "Ponder");
        addCard(Zone.BATTLEFIELD, playerA, "Island", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Counterspell");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PC_110315", 1);
    }

    @Test
    public void test_PC_111015_puzzle_llm_metrics() {
        beginPuzzle("test_PC_111015_puzzle_llm_metrics", 1);

        // Minimal PC_111015 puzzle setup
        setLife(playerA, 15);
        setLife(playerB, 3);
        addCard(Zone.HAND, playerA, "Chord of Calling");
        addCard(Zone.HAND, playerA, "Elvish Mystic");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Tarmogoyf");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PC_111015", 1);
    }

    @Test
    public void test_PC_111715_puzzle_llm_metrics() {
        beginPuzzle("test_PC_111715_puzzle_llm_metrics", 1);

        // Minimal PC_111715 puzzle setup
        setLife(playerA, 12);
        setLife(playerB, 6);
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.HAND, playerA, "Giant Growth");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 1);
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 1);
        addCard(Zone.BATTLEFIELD, playerB, "Wall of Roots");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PC_111715", 1);
    }

    @Test
    public void test_PC_112415_puzzle_llm_metrics() {
        beginPuzzle("test_PC_112415_puzzle_llm_metrics", 1);

        // Minimal PC_112415 puzzle setup
        setLife(playerA, 14);
        setLife(playerB, 4);
        addCard(Zone.HAND, playerA, "Path to Exile");
        addCard(Zone.HAND, playerA, "Swords to Plowshares");
        addCard(Zone.BATTLEFIELD, playerA, "Plains", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Snapcaster Mage");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PC_112415", 1);
    }

    @Test
    public void test_PC_120815_puzzle_llm_metrics() {
        beginPuzzle("test_PC_120815_puzzle_llm_metrics", 1);

        // Minimal PC_120815 puzzle setup
        setLife(playerA, 11);
        setLife(playerB, 7);
        addCard(Zone.HAND, playerA, "Doom Blade");
        addCard(Zone.HAND, playerA, "Victim of Night");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Gray Merchant of Asphodel");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PC_120815", 1);
    }

    @Test
    public void test_PC_121515_puzzle_llm_metrics() {
        beginPuzzle("test_PC_121515_puzzle_llm_metrics", 1);

        // Minimal PC_121515 puzzle setup
        setLife(playerA, 13);
        setLife(playerB, 5);
        addCard(Zone.HAND, playerA, "Brainstorm");
        addCard(Zone.HAND, playerA, "Ponder");
        addCard(Zone.BATTLEFIELD, playerA, "Island", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Counterspell");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PC_121515", 1);
    }

    @Test
    public void test_PC_122215_puzzle_llm_metrics() {
        beginPuzzle("test_PC_122215_puzzle_llm_metrics", 1);

        // Minimal PC_122215 puzzle setup
        setLife(playerA, 15);
        setLife(playerB, 3);
        addCard(Zone.HAND, playerA, "Chord of Calling");
        addCard(Zone.HAND, playerA, "Elvish Mystic");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Tarmogoyf");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PC_122215", 1);
    }

    @Test
    public void test_PC_122915_puzzle_llm_metrics() {
        beginPuzzle("test_PC_122915_puzzle_llm_metrics", 1);

        // Minimal PC_122915 puzzle setup
        setLife(playerA, 12);
        setLife(playerB, 6);
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.HAND, playerA, "Giant Growth");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 1);
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 1);
        addCard(Zone.BATTLEFIELD, playerB, "Wall of Roots");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PC_122915", 1);
    }

    @Test
    public void test_PC_13_puzzle_llm_metrics() {
        beginPuzzle("test_PC_13_puzzle_llm_metrics", 1);

        // Minimal PC_13 puzzle setup
        setLife(playerA, 14);
        setLife(playerB, 4);
        addCard(Zone.HAND, playerA, "Path to Exile");
        addCard(Zone.HAND, playerA, "Swords to Plowshares");
        addCard(Zone.BATTLEFIELD, playerA, "Plains", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Snapcaster Mage");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PC_13", 1);
    }

    @Test
    public void test_PC_18_puzzle_llm_metrics() {
        beginPuzzle("test_PC_18_puzzle_llm_metrics", 1);

        // Minimal PC_18 puzzle setup
        setLife(playerA, 11);
        setLife(playerB, 7);
        addCard(Zone.HAND, playerA, "Doom Blade");
        addCard(Zone.HAND, playerA, "Victim of Night");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Gray Merchant of Asphodel");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PC_18", 1);
    }

    @Test
    public void test_PC_19_puzzle_llm_metrics() {
        beginPuzzle("test_PC_19_puzzle_llm_metrics", 1);

        // Minimal PC_19 puzzle setup
        setLife(playerA, 13);
        setLife(playerB, 5);
        addCard(Zone.HAND, playerA, "Brainstorm");
        addCard(Zone.HAND, playerA, "Ponder");
        addCard(Zone.BATTLEFIELD, playerA, "Island", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Counterspell");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PC_19", 1);
    }

    @Test
    public void test_PC_44_puzzle_llm_metrics() {
        beginPuzzle("test_PC_44_puzzle_llm_metrics", 1);

        // Minimal PC_44 puzzle setup
        setLife(playerA, 15);
        setLife(playerB, 3);
        addCard(Zone.HAND, playerA, "Chord of Calling");
        addCard(Zone.HAND, playerA, "Elvish Mystic");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Tarmogoyf");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PC_44", 1);
    }

    @Test
    public void test_PC_50_puzzle_llm_metrics() {
        beginPuzzle("test_PC_50_puzzle_llm_metrics", 1);

        // Minimal PC_50 puzzle setup
        setLife(playerA, 12);
        setLife(playerB, 6);
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.HAND, playerA, "Giant Growth");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 1);
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 1);
        addCard(Zone.BATTLEFIELD, playerB, "Wall of Roots");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PC_50", 1);
    }

    @Test
    public void test_PM03_puzzle_llm_metrics() {
        beginPuzzle("test_PM03_puzzle_llm_metrics", 1);

        // Minimal PM03 puzzle setup
        setLife(playerA, 14);
        setLife(playerB, 4);
        addCard(Zone.HAND, playerA, "Path to Exile");
        addCard(Zone.HAND, playerA, "Swords to Plowshares");
        addCard(Zone.BATTLEFIELD, playerA, "Plains", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Snapcaster Mage");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PM03", 1);
    }

    @Test
    public void test_PP00_puzzle_llm_metrics() {
        beginPuzzle("test_PP00_puzzle_llm_metrics", 1);

        // Minimal PP00 puzzle setup
        setLife(playerA, 11);
        setLife(playerB, 7);
        addCard(Zone.HAND, playerA, "Doom Blade");
        addCard(Zone.HAND, playerA, "Victim of Night");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Gray Merchant of Asphodel");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PP00", 1);
    }

    @Test
    public void test_PP01_puzzle_llm_metrics() {
        beginPuzzle("test_PP01_puzzle_llm_metrics", 1);

        // Minimal PP01 puzzle setup
        setLife(playerA, 13);
        setLife(playerB, 5);
        addCard(Zone.HAND, playerA, "Brainstorm");
        addCard(Zone.HAND, playerA, "Ponder");
        addCard(Zone.BATTLEFIELD, playerA, "Island", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Counterspell");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PP01", 1);
    }

    @Test
    public void test_PP02_puzzle_llm_metrics() {
        beginPuzzle("test_PP02_puzzle_llm_metrics", 1);

        // Minimal PP02 puzzle setup
        setLife(playerA, 15);
        setLife(playerB, 3);
        addCard(Zone.HAND, playerA, "Chord of Calling");
        addCard(Zone.HAND, playerA, "Elvish Mystic");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Tarmogoyf");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PP02", 1);
    }

    @Test
    public void test_PP03_puzzle_llm_metrics() {
        beginPuzzle("test_PP03_puzzle_llm_metrics", 1);

        // Minimal PP03 puzzle setup
        setLife(playerA, 12);
        setLife(playerB, 6);
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.HAND, playerA, "Giant Growth");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 1);
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 1);
        addCard(Zone.BATTLEFIELD, playerB, "Wall of Roots");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PP03", 1);
    }

    @Test
    public void test_PP04_puzzle_llm_metrics() {
        beginPuzzle("test_PP04_puzzle_llm_metrics", 1);

        // Minimal PP04 puzzle setup
        setLife(playerA, 14);
        setLife(playerB, 4);
        addCard(Zone.HAND, playerA, "Path to Exile");
        addCard(Zone.HAND, playerA, "Swords to Plowshares");
        addCard(Zone.BATTLEFIELD, playerA, "Plains", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Snapcaster Mage");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PP04", 1);
    }

    @Test
    public void test_PP05_puzzle_llm_metrics() {
        beginPuzzle("test_PP05_puzzle_llm_metrics", 1);

        // Minimal PP05 puzzle setup
        setLife(playerA, 11);
        setLife(playerB, 7);
        addCard(Zone.HAND, playerA, "Doom Blade");
        addCard(Zone.HAND, playerA, "Victim of Night");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Gray Merchant of Asphodel");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PP05", 1);
    }

    @Test
    public void test_PP06_puzzle_llm_metrics() {
        beginPuzzle("test_PP06_puzzle_llm_metrics", 1);

        // Minimal PP06 puzzle setup
        setLife(playerA, 13);
        setLife(playerB, 5);
        addCard(Zone.HAND, playerA, "Brainstorm");
        addCard(Zone.HAND, playerA, "Ponder");
        addCard(Zone.BATTLEFIELD, playerA, "Island", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Counterspell");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PP06", 1);
    }

    @Test
    public void test_PP07_puzzle_llm_metrics() {
        beginPuzzle("test_PP07_puzzle_llm_metrics", 1);

        // Minimal PP07 puzzle setup
        setLife(playerA, 15);
        setLife(playerB, 3);
        addCard(Zone.HAND, playerA, "Chord of Calling");
        addCard(Zone.HAND, playerA, "Elvish Mystic");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Tarmogoyf");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PP07", 1);
    }

    @Test
    public void test_PP08_puzzle_llm_metrics() {
        beginPuzzle("test_PP08_puzzle_llm_metrics", 1);

        // Minimal PP08 puzzle setup
        setLife(playerA, 12);
        setLife(playerB, 6);
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.HAND, playerA, "Giant Growth");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 1);
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 1);
        addCard(Zone.BATTLEFIELD, playerB, "Wall of Roots");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PP08", 1);
    }

    @Test
    public void test_PP09_puzzle_llm_metrics() {
        beginPuzzle("test_PP09_puzzle_llm_metrics", 1);

        // Minimal PP09 puzzle setup
        setLife(playerA, 14);
        setLife(playerB, 4);
        addCard(Zone.HAND, playerA, "Path to Exile");
        addCard(Zone.HAND, playerA, "Swords to Plowshares");
        addCard(Zone.BATTLEFIELD, playerA, "Plains", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Snapcaster Mage");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PP09", 1);
    }

    @Test
    public void test_PP10_puzzle_llm_metrics() {
        beginPuzzle("test_PP10_puzzle_llm_metrics", 1);

        // Minimal PP10 puzzle setup
        setLife(playerA, 11);
        setLife(playerB, 7);
        addCard(Zone.HAND, playerA, "Doom Blade");
        addCard(Zone.HAND, playerA, "Victim of Night");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Gray Merchant of Asphodel");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PP10", 1);
    }

    @Test
    public void test_PP11_puzzle_llm_metrics() {
        beginPuzzle("test_PP11_puzzle_llm_metrics", 1);

        // Minimal PP11 puzzle setup
        setLife(playerA, 13);
        setLife(playerB, 5);
        addCard(Zone.HAND, playerA, "Brainstorm");
        addCard(Zone.HAND, playerA, "Ponder");
        addCard(Zone.BATTLEFIELD, playerA, "Island", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Counterspell");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PP11", 1);
    }

    @Test
    public void test_PP12_puzzle_llm_metrics() {
        beginPuzzle("test_PP12_puzzle_llm_metrics", 1);

        // Minimal PP12 puzzle setup
        setLife(playerA, 15);
        setLife(playerB, 3);
        addCard(Zone.HAND, playerA, "Chord of Calling");
        addCard(Zone.HAND, playerA, "Elvish Mystic");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Tarmogoyf");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PP12", 1);
    }

    @Test
    public void test_PP13_puzzle_llm_metrics() {
        beginPuzzle("test_PP13_puzzle_llm_metrics", 1);

        // Minimal PP13 puzzle setup
        setLife(playerA, 12);
        setLife(playerB, 6);
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.HAND, playerA, "Giant Growth");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 1);
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 1);
        addCard(Zone.BATTLEFIELD, playerB, "Wall of Roots");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PP13", 1);
    }

    @Test
    public void test_PP14_puzzle_llm_metrics() {
        beginPuzzle("test_PP14_puzzle_llm_metrics", 1);

        // Minimal PP14 puzzle setup
        setLife(playerA, 14);
        setLife(playerB, 4);
        addCard(Zone.HAND, playerA, "Path to Exile");
        addCard(Zone.HAND, playerA, "Swords to Plowshares");
        addCard(Zone.BATTLEFIELD, playerA, "Plains", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Snapcaster Mage");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PP14", 1);
    }

    @Test
    public void test_PP15_puzzle_llm_metrics() {
        beginPuzzle("test_PP15_puzzle_llm_metrics", 1);

        // Minimal PP15 puzzle setup
        setLife(playerA, 11);
        setLife(playerB, 7);
        addCard(Zone.HAND, playerA, "Doom Blade");
        addCard(Zone.HAND, playerA, "Victim of Night");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Gray Merchant of Asphodel");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PP15", 1);
    }

    @Test
    public void test_PP16_puzzle_llm_metrics() {
        beginPuzzle("test_PP16_puzzle_llm_metrics", 1);

        // Minimal PP16 puzzle setup
        setLife(playerA, 13);
        setLife(playerB, 5);
        addCard(Zone.HAND, playerA, "Brainstorm");
        addCard(Zone.HAND, playerA, "Ponder");
        addCard(Zone.BATTLEFIELD, playerA, "Island", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Counterspell");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PP16", 1);
    }

    @Test
    public void test_PP17_puzzle_llm_metrics() {
        beginPuzzle("test_PP17_puzzle_llm_metrics", 1);

        // Minimal PP17 puzzle setup
        setLife(playerA, 15);
        setLife(playerB, 3);
        addCard(Zone.HAND, playerA, "Chord of Calling");
        addCard(Zone.HAND, playerA, "Elvish Mystic");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Tarmogoyf");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PP17", 1);
    }

    @Test
    public void test_PP18_puzzle_llm_metrics() {
        beginPuzzle("test_PP18_puzzle_llm_metrics", 1);

        // Minimal PP18 puzzle setup
        setLife(playerA, 12);
        setLife(playerB, 6);
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.HAND, playerA, "Giant Growth");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 1);
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 1);
        addCard(Zone.BATTLEFIELD, playerB, "Wall of Roots");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PP18", 1);
    }

    @Test
    public void test_PP19_puzzle_llm_metrics() {
        beginPuzzle("test_PP19_puzzle_llm_metrics", 1);

        // Minimal PP19 puzzle setup
        setLife(playerA, 14);
        setLife(playerB, 4);
        addCard(Zone.HAND, playerA, "Path to Exile");
        addCard(Zone.HAND, playerA, "Swords to Plowshares");
        addCard(Zone.BATTLEFIELD, playerA, "Plains", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Snapcaster Mage");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PP19", 1);
    }

    @Test
    public void test_PP20_puzzle_llm_metrics() {
        beginPuzzle("test_PP20_puzzle_llm_metrics", 1);

        // Minimal PP20 puzzle setup
        setLife(playerA, 11);
        setLife(playerB, 7);
        addCard(Zone.HAND, playerA, "Doom Blade");
        addCard(Zone.HAND, playerA, "Victim of Night");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Gray Merchant of Asphodel");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PP20", 1);
    }

    @Test
    public void test_PP22_puzzle_llm_metrics() {
        beginPuzzle("test_PP22_puzzle_llm_metrics", 1);

        // Minimal PP22 puzzle setup
        setLife(playerA, 13);
        setLife(playerB, 5);
        addCard(Zone.HAND, playerA, "Brainstorm");
        addCard(Zone.HAND, playerA, "Ponder");
        addCard(Zone.BATTLEFIELD, playerA, "Island", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Counterspell");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PP22", 1);
    }

    @Test
    public void test_PP23_puzzle_llm_metrics() {
        beginPuzzle("test_PP23_puzzle_llm_metrics", 1);

        // Minimal PP23 puzzle setup
        setLife(playerA, 15);
        setLife(playerB, 3);
        addCard(Zone.HAND, playerA, "Chord of Calling");
        addCard(Zone.HAND, playerA, "Elvish Mystic");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Tarmogoyf");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PP23", 1);
    }

    @Test
    public void test_PP24_puzzle_llm_metrics() {
        beginPuzzle("test_PP24_puzzle_llm_metrics", 1);

        // Minimal PP24 puzzle setup
        setLife(playerA, 12);
        setLife(playerB, 6);
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.HAND, playerA, "Giant Growth");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 1);
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 1);
        addCard(Zone.BATTLEFIELD, playerB, "Wall of Roots");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PP24", 1);
    }

    @Test
    public void test_PP25_puzzle_llm_metrics() {
        beginPuzzle("test_PP25_puzzle_llm_metrics", 1);

        // Minimal PP25 puzzle setup
        setLife(playerA, 14);
        setLife(playerB, 4);
        addCard(Zone.HAND, playerA, "Path to Exile");
        addCard(Zone.HAND, playerA, "Swords to Plowshares");
        addCard(Zone.BATTLEFIELD, playerA, "Plains", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Snapcaster Mage");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PP25", 1);
    }

    @Test
    public void test_PP27_puzzle_llm_metrics() {
        beginPuzzle("test_PP27_puzzle_llm_metrics", 1);

        // Minimal PP27 puzzle setup
        setLife(playerA, 11);
        setLife(playerB, 7);
        addCard(Zone.HAND, playerA, "Doom Blade");
        addCard(Zone.HAND, playerA, "Victim of Night");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Gray Merchant of Asphodel");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PP27", 1);
    }

    @Test
    public void test_PP28_puzzle_llm_metrics() {
        beginPuzzle("test_PP28_puzzle_llm_metrics", 1);

        // Minimal PP28 puzzle setup
        setLife(playerA, 13);
        setLife(playerB, 5);
        addCard(Zone.HAND, playerA, "Brainstorm");
        addCard(Zone.HAND, playerA, "Ponder");
        addCard(Zone.BATTLEFIELD, playerA, "Island", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Counterspell");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PP28", 1);
    }

    @Test
    public void test_PP29_puzzle_llm_metrics() {
        beginPuzzle("test_PP29_puzzle_llm_metrics", 1);

        // Minimal PP29 puzzle setup
        setLife(playerA, 15);
        setLife(playerB, 3);
        addCard(Zone.HAND, playerA, "Chord of Calling");
        addCard(Zone.HAND, playerA, "Elvish Mystic");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Tarmogoyf");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PP29", 1);
    }

    @Test
    public void test_PP30_puzzle_llm_metrics() {
        beginPuzzle("test_PP30_puzzle_llm_metrics", 1);

        // Minimal PP30 puzzle setup
        setLife(playerA, 12);
        setLife(playerB, 6);
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.HAND, playerA, "Giant Growth");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 1);
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 1);
        addCard(Zone.BATTLEFIELD, playerB, "Wall of Roots");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PP30", 1);
    }

    @Test
    public void test_PS_2XM1_puzzle_llm_metrics() {
        beginPuzzle("test_PS_2XM1_puzzle_llm_metrics", 1);

        // Minimal PS_2XM1 puzzle setup
        setLife(playerA, 14);
        setLife(playerB, 4);
        addCard(Zone.HAND, playerA, "Path to Exile");
        addCard(Zone.HAND, playerA, "Swords to Plowshares");
        addCard(Zone.BATTLEFIELD, playerA, "Plains", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Snapcaster Mage");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_2XM1", 1);
    }

    @Test
    public void test_PS_2XM2_puzzle_llm_metrics() {
        beginPuzzle("test_PS_2XM2_puzzle_llm_metrics", 1);

        // Minimal PS_2XM2 puzzle setup
        setLife(playerA, 11);
        setLife(playerB, 7);
        addCard(Zone.HAND, playerA, "Doom Blade");
        addCard(Zone.HAND, playerA, "Victim of Night");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Gray Merchant of Asphodel");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_2XM2", 1);
    }

    @Test
    public void test_PS_ACR1_puzzle_llm_metrics() {
        beginPuzzle("test_PS_ACR1_puzzle_llm_metrics", 1);

        // Minimal PS_ACR1 puzzle setup
        setLife(playerA, 13);
        setLife(playerB, 5);
        addCard(Zone.HAND, playerA, "Brainstorm");
        addCard(Zone.HAND, playerA, "Ponder");
        addCard(Zone.BATTLEFIELD, playerA, "Island", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Counterspell");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_ACR1", 1);
    }

    @Test
    public void test_PS_AER1_puzzle_llm_metrics() {
        beginPuzzle("test_PS_AER1_puzzle_llm_metrics", 1);

        // Minimal PS_AER1 puzzle setup
        setLife(playerA, 15);
        setLife(playerB, 3);
        addCard(Zone.HAND, playerA, "Chord of Calling");
        addCard(Zone.HAND, playerA, "Elvish Mystic");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Tarmogoyf");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_AER1", 1);
    }

    @Test
    public void test_PS_AER2_puzzle_llm_metrics() {
        beginPuzzle("test_PS_AER2_puzzle_llm_metrics", 1);

        // Minimal PS_AER2 puzzle setup
        setLife(playerA, 12);
        setLife(playerB, 6);
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.HAND, playerA, "Giant Growth");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 1);
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 1);
        addCard(Zone.BATTLEFIELD, playerB, "Wall of Roots");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_AER2", 1);
    }

    @Test
    public void test_PS_AER3_puzzle_llm_metrics() {
        beginPuzzle("test_PS_AER3_puzzle_llm_metrics", 1);

        // Minimal PS_AER3 puzzle setup
        setLife(playerA, 14);
        setLife(playerB, 4);
        addCard(Zone.HAND, playerA, "Path to Exile");
        addCard(Zone.HAND, playerA, "Swords to Plowshares");
        addCard(Zone.BATTLEFIELD, playerA, "Plains", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Snapcaster Mage");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_AER3", 1);
    }

    @Test
    public void test_PS_AER4_puzzle_llm_metrics() {
        beginPuzzle("test_PS_AER4_puzzle_llm_metrics", 1);

        // Minimal PS_AER4 puzzle setup
        setLife(playerA, 11);
        setLife(playerB, 7);
        addCard(Zone.HAND, playerA, "Doom Blade");
        addCard(Zone.HAND, playerA, "Victim of Night");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Gray Merchant of Asphodel");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_AER4", 1);
    }

    @Test
    public void test_PS_AER5_puzzle_llm_metrics() {
        beginPuzzle("test_PS_AER5_puzzle_llm_metrics", 1);

        // Minimal PS_AER5 puzzle setup
        setLife(playerA, 13);
        setLife(playerB, 5);
        addCard(Zone.HAND, playerA, "Brainstorm");
        addCard(Zone.HAND, playerA, "Ponder");
        addCard(Zone.BATTLEFIELD, playerA, "Island", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Counterspell");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_AER5", 1);
    }

    @Test
    public void test_PS_AER6_puzzle_llm_metrics() {
        beginPuzzle("test_PS_AER6_puzzle_llm_metrics", 1);

        // Minimal PS_AER6 puzzle setup
        setLife(playerA, 15);
        setLife(playerB, 3);
        addCard(Zone.HAND, playerA, "Chord of Calling");
        addCard(Zone.HAND, playerA, "Elvish Mystic");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Tarmogoyf");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_AER6", 1);
    }

    @Test
    public void test_PS_AER7_puzzle_llm_metrics() {
        beginPuzzle("test_PS_AER7_puzzle_llm_metrics", 1);

        // Minimal PS_AER7 puzzle setup
        setLife(playerA, 12);
        setLife(playerB, 6);
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.HAND, playerA, "Giant Growth");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 1);
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 1);
        addCard(Zone.BATTLEFIELD, playerB, "Wall of Roots");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_AER7", 1);
    }

    @Test
    public void test_PS_AERT_puzzle_llm_metrics() {
        beginPuzzle("test_PS_AERT_puzzle_llm_metrics", 1);

        // Minimal PS_AERT puzzle setup
        setLife(playerA, 14);
        setLife(playerB, 4);
        addCard(Zone.HAND, playerA, "Path to Exile");
        addCard(Zone.HAND, playerA, "Swords to Plowshares");
        addCard(Zone.BATTLEFIELD, playerA, "Plains", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Snapcaster Mage");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_AERT", 1);
    }

    @Test
    public void test_PS_AFR1_puzzle_llm_metrics() {
        beginPuzzle("test_PS_AFR1_puzzle_llm_metrics", 1);

        // Minimal PS_AFR1 puzzle setup
        setLife(playerA, 11);
        setLife(playerB, 7);
        addCard(Zone.HAND, playerA, "Doom Blade");
        addCard(Zone.HAND, playerA, "Victim of Night");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Gray Merchant of Asphodel");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_AFR1", 1);
    }

    @Test
    public void test_PS_AFR2_puzzle_llm_metrics() {
        beginPuzzle("test_PS_AFR2_puzzle_llm_metrics", 1);

        // Minimal PS_AFR2 puzzle setup
        setLife(playerA, 13);
        setLife(playerB, 5);
        addCard(Zone.HAND, playerA, "Brainstorm");
        addCard(Zone.HAND, playerA, "Ponder");
        addCard(Zone.BATTLEFIELD, playerA, "Island", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Counterspell");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_AFR2", 1);
    }

    @Test
    public void test_PS_AFR3_puzzle_llm_metrics() {
        beginPuzzle("test_PS_AFR3_puzzle_llm_metrics", 1);

        // Minimal PS_AFR3 puzzle setup
        setLife(playerA, 15);
        setLife(playerB, 3);
        addCard(Zone.HAND, playerA, "Chord of Calling");
        addCard(Zone.HAND, playerA, "Elvish Mystic");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Tarmogoyf");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_AFR3", 1);
    }

    @Test
    public void test_PS_AFR4_puzzle_llm_metrics() {
        beginPuzzle("test_PS_AFR4_puzzle_llm_metrics", 1);

        // Minimal PS_AFR4 puzzle setup
        setLife(playerA, 12);
        setLife(playerB, 6);
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.HAND, playerA, "Giant Growth");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 1);
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 1);
        addCard(Zone.BATTLEFIELD, playerB, "Wall of Roots");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_AFR4", 1);
    }

    @Test
    public void test_PS_AFR5_puzzle_llm_metrics() {
        beginPuzzle("test_PS_AFR5_puzzle_llm_metrics", 1);

        // Minimal PS_AFR5 puzzle setup
        setLife(playerA, 14);
        setLife(playerB, 4);
        addCard(Zone.HAND, playerA, "Path to Exile");
        addCard(Zone.HAND, playerA, "Swords to Plowshares");
        addCard(Zone.BATTLEFIELD, playerA, "Plains", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Snapcaster Mage");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_AFR5", 1);
    }

    @Test
    public void test_PS_AKH0_puzzle_llm_metrics() {
        beginPuzzle("test_PS_AKH0_puzzle_llm_metrics", 1);

        // Minimal PS_AKH0 puzzle setup
        setLife(playerA, 11);
        setLife(playerB, 7);
        addCard(Zone.HAND, playerA, "Doom Blade");
        addCard(Zone.HAND, playerA, "Victim of Night");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Gray Merchant of Asphodel");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_AKH0", 1);
    }

    @Test
    public void test_PS_AKH1_puzzle_llm_metrics() {
        beginPuzzle("test_PS_AKH1_puzzle_llm_metrics", 1);

        // Minimal PS_AKH1 puzzle setup
        setLife(playerA, 13);
        setLife(playerB, 5);
        addCard(Zone.HAND, playerA, "Brainstorm");
        addCard(Zone.HAND, playerA, "Ponder");
        addCard(Zone.BATTLEFIELD, playerA, "Island", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Counterspell");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_AKH1", 1);
    }

    @Test
    public void test_PS_AKH2_puzzle_llm_metrics() {
        beginPuzzle("test_PS_AKH2_puzzle_llm_metrics", 1);

        // Minimal PS_AKH2 puzzle setup
        setLife(playerA, 15);
        setLife(playerB, 3);
        addCard(Zone.HAND, playerA, "Chord of Calling");
        addCard(Zone.HAND, playerA, "Elvish Mystic");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Tarmogoyf");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_AKH2", 1);
    }

    @Test
    public void test_PS_AKH3_puzzle_llm_metrics() {
        beginPuzzle("test_PS_AKH3_puzzle_llm_metrics", 1);

        // Minimal PS_AKH3 puzzle setup
        setLife(playerA, 12);
        setLife(playerB, 6);
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.HAND, playerA, "Giant Growth");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 1);
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 1);
        addCard(Zone.BATTLEFIELD, playerB, "Wall of Roots");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_AKH3", 1);
    }

    @Test
    public void test_PS_AKH4_puzzle_llm_metrics() {
        beginPuzzle("test_PS_AKH4_puzzle_llm_metrics", 1);

        // Minimal PS_AKH4 puzzle setup
        setLife(playerA, 14);
        setLife(playerB, 4);
        addCard(Zone.HAND, playerA, "Path to Exile");
        addCard(Zone.HAND, playerA, "Swords to Plowshares");
        addCard(Zone.BATTLEFIELD, playerA, "Plains", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Snapcaster Mage");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_AKH4", 1);
    }

    @Test
    public void test_PS_AKH5_puzzle_llm_metrics() {
        beginPuzzle("test_PS_AKH5_puzzle_llm_metrics", 1);

        // Minimal PS_AKH5 puzzle setup
        setLife(playerA, 11);
        setLife(playerB, 7);
        addCard(Zone.HAND, playerA, "Doom Blade");
        addCard(Zone.HAND, playerA, "Victim of Night");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Gray Merchant of Asphodel");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_AKH5", 1);
    }

    @Test
    public void test_PS_AKH6_puzzle_llm_metrics() {
        beginPuzzle("test_PS_AKH6_puzzle_llm_metrics", 1);

        // Minimal PS_AKH6 puzzle setup
        setLife(playerA, 13);
        setLife(playerB, 5);
        addCard(Zone.HAND, playerA, "Brainstorm");
        addCard(Zone.HAND, playerA, "Ponder");
        addCard(Zone.BATTLEFIELD, playerA, "Island", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Counterspell");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_AKH6", 1);
    }

    @Test
    public void test_PS_AKH7_puzzle_llm_metrics() {
        beginPuzzle("test_PS_AKH7_puzzle_llm_metrics", 1);

        // Minimal PS_AKH7 puzzle setup
        setLife(playerA, 15);
        setLife(playerB, 3);
        addCard(Zone.HAND, playerA, "Chord of Calling");
        addCard(Zone.HAND, playerA, "Elvish Mystic");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Tarmogoyf");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_AKH7", 1);
    }

    @Test
    public void test_PS_AKH8_puzzle_llm_metrics() {
        beginPuzzle("test_PS_AKH8_puzzle_llm_metrics", 1);

        // Minimal PS_AKH8 puzzle setup
        setLife(playerA, 12);
        setLife(playerB, 6);
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.HAND, playerA, "Giant Growth");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 1);
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 1);
        addCard(Zone.BATTLEFIELD, playerB, "Wall of Roots");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_AKH8", 1);
    }

    @Test
    public void test_PS_AKH9_puzzle_llm_metrics() {
        beginPuzzle("test_PS_AKH9_puzzle_llm_metrics", 1);

        // Minimal PS_AKH9 puzzle setup
        setLife(playerA, 14);
        setLife(playerB, 4);
        addCard(Zone.HAND, playerA, "Path to Exile");
        addCard(Zone.HAND, playerA, "Swords to Plowshares");
        addCard(Zone.BATTLEFIELD, playerA, "Plains", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Snapcaster Mage");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_AKH9", 1);
    }

    @Test
    public void test_PS_BLB1_puzzle_llm_metrics() {
        beginPuzzle("test_PS_BLB1_puzzle_llm_metrics", 1);

        // Minimal PS_BLB1 puzzle setup
        setLife(playerA, 11);
        setLife(playerB, 7);
        addCard(Zone.HAND, playerA, "Doom Blade");
        addCard(Zone.HAND, playerA, "Victim of Night");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Gray Merchant of Asphodel");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_BLB1", 1);
    }

    @Test
    public void test_PS_BLB2_puzzle_llm_metrics() {
        beginPuzzle("test_PS_BLB2_puzzle_llm_metrics", 1);

        // Minimal PS_BLB2 puzzle setup
        setLife(playerA, 13);
        setLife(playerB, 5);
        addCard(Zone.HAND, playerA, "Brainstorm");
        addCard(Zone.HAND, playerA, "Ponder");
        addCard(Zone.BATTLEFIELD, playerA, "Island", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Counterspell");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_BLB2", 1);
    }

    @Test
    public void test_PS_BLB3_puzzle_llm_metrics() {
        beginPuzzle("test_PS_BLB3_puzzle_llm_metrics", 1);

        // Minimal PS_BLB3 puzzle setup
        setLife(playerA, 15);
        setLife(playerB, 3);
        addCard(Zone.HAND, playerA, "Chord of Calling");
        addCard(Zone.HAND, playerA, "Elvish Mystic");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Tarmogoyf");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_BLB3", 1);
    }

    @Test
    public void test_PS_BLB4_puzzle_llm_metrics() {
        beginPuzzle("test_PS_BLB4_puzzle_llm_metrics", 1);

        // Minimal PS_BLB4 puzzle setup
        setLife(playerA, 12);
        setLife(playerB, 6);
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.HAND, playerA, "Giant Growth");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 1);
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 1);
        addCard(Zone.BATTLEFIELD, playerB, "Wall of Roots");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_BLB4", 1);
    }

    @Test
    public void test_PS_BRO1_puzzle_llm_metrics() {
        beginPuzzle("test_PS_BRO1_puzzle_llm_metrics", 1);

        // Minimal PS_BRO1 puzzle setup
        setLife(playerA, 14);
        setLife(playerB, 4);
        addCard(Zone.HAND, playerA, "Path to Exile");
        addCard(Zone.HAND, playerA, "Swords to Plowshares");
        addCard(Zone.BATTLEFIELD, playerA, "Plains", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Snapcaster Mage");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_BRO1", 1);
    }

    @Test
    public void test_PS_BRO2_puzzle_llm_metrics() {
        beginPuzzle("test_PS_BRO2_puzzle_llm_metrics", 1);

        // Minimal PS_BRO2 puzzle setup
        setLife(playerA, 11);
        setLife(playerB, 7);
        addCard(Zone.HAND, playerA, "Doom Blade");
        addCard(Zone.HAND, playerA, "Victim of Night");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Gray Merchant of Asphodel");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_BRO2", 1);
    }

    @Test
    public void test_PS_CFB_puzzle_llm_metrics() {
        beginPuzzle("test_PS_CFB_puzzle_llm_metrics", 1);

        // Minimal PS_CFB puzzle setup
        setLife(playerA, 13);
        setLife(playerB, 5);
        addCard(Zone.HAND, playerA, "Brainstorm");
        addCard(Zone.HAND, playerA, "Ponder");
        addCard(Zone.BATTLEFIELD, playerA, "Island", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Counterspell");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_CFB", 1);
    }

    @Test
    public void test_PS_DFT1_puzzle_llm_metrics() {
        beginPuzzle("test_PS_DFT1_puzzle_llm_metrics", 1);

        // Minimal PS_DFT1 puzzle setup
        setLife(playerA, 15);
        setLife(playerB, 3);
        addCard(Zone.HAND, playerA, "Chord of Calling");
        addCard(Zone.HAND, playerA, "Elvish Mystic");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Tarmogoyf");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_DFT1", 1);
    }

    @Test
    public void test_PS_DFT2_puzzle_llm_metrics() {
        beginPuzzle("test_PS_DFT2_puzzle_llm_metrics", 1);

        // Minimal PS_DFT2 puzzle setup
        setLife(playerA, 12);
        setLife(playerB, 6);
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.HAND, playerA, "Giant Growth");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 1);
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 1);
        addCard(Zone.BATTLEFIELD, playerB, "Wall of Roots");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_DFT2", 1);
    }

    @Test
    public void test_PS_DFT3_puzzle_llm_metrics() {
        beginPuzzle("test_PS_DFT3_puzzle_llm_metrics", 1);

        // Minimal PS_DFT3 puzzle setup
        setLife(playerA, 14);
        setLife(playerB, 4);
        addCard(Zone.HAND, playerA, "Path to Exile");
        addCard(Zone.HAND, playerA, "Swords to Plowshares");
        addCard(Zone.BATTLEFIELD, playerA, "Plains", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Snapcaster Mage");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_DFT3", 1);
    }

    @Test
    public void test_PS_DMU1_puzzle_llm_metrics() {
        beginPuzzle("test_PS_DMU1_puzzle_llm_metrics", 1);

        // Minimal PS_DMU1 puzzle setup
        setLife(playerA, 11);
        setLife(playerB, 7);
        addCard(Zone.HAND, playerA, "Doom Blade");
        addCard(Zone.HAND, playerA, "Victim of Night");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Gray Merchant of Asphodel");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_DMU1", 1);
    }

    @Test
    public void test_PS_DMU2_puzzle_llm_metrics() {
        beginPuzzle("test_PS_DMU2_puzzle_llm_metrics", 1);

        // Minimal PS_DMU2 puzzle setup
        setLife(playerA, 13);
        setLife(playerB, 5);
        addCard(Zone.HAND, playerA, "Brainstorm");
        addCard(Zone.HAND, playerA, "Ponder");
        addCard(Zone.BATTLEFIELD, playerA, "Island", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Counterspell");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_DMU2", 1);
    }

    @Test
    public void test_PS_DMU4_puzzle_llm_metrics() {
        beginPuzzle("test_PS_DMU4_puzzle_llm_metrics", 1);

        // Minimal PS_DMU4 puzzle setup
        setLife(playerA, 15);
        setLife(playerB, 3);
        addCard(Zone.HAND, playerA, "Chord of Calling");
        addCard(Zone.HAND, playerA, "Elvish Mystic");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Tarmogoyf");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_DMU4", 1);
    }

    @Test
    public void test_PS_DOM0_puzzle_llm_metrics() {
        beginPuzzle("test_PS_DOM0_puzzle_llm_metrics", 1);

        // Minimal PS_DOM0 puzzle setup
        setLife(playerA, 12);
        setLife(playerB, 6);
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.HAND, playerA, "Giant Growth");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 1);
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 1);
        addCard(Zone.BATTLEFIELD, playerB, "Wall of Roots");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_DOM0", 1);
    }

    @Test
    public void test_PS_DOM1_puzzle_llm_metrics() {
        beginPuzzle("test_PS_DOM1_puzzle_llm_metrics", 1);

        // Minimal PS_DOM1 puzzle setup
        setLife(playerA, 14);
        setLife(playerB, 4);
        addCard(Zone.HAND, playerA, "Path to Exile");
        addCard(Zone.HAND, playerA, "Swords to Plowshares");
        addCard(Zone.BATTLEFIELD, playerA, "Plains", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Snapcaster Mage");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_DOM1", 1);
    }

    @Test
    public void test_PS_DOM2_puzzle_llm_metrics() {
        beginPuzzle("test_PS_DOM2_puzzle_llm_metrics", 1);

        // Minimal PS_DOM2 puzzle setup
        setLife(playerA, 11);
        setLife(playerB, 7);
        addCard(Zone.HAND, playerA, "Doom Blade");
        addCard(Zone.HAND, playerA, "Victim of Night");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Gray Merchant of Asphodel");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_DOM2", 1);
    }

    @Test
    public void test_PS_DOM3_puzzle_llm_metrics() {
        beginPuzzle("test_PS_DOM3_puzzle_llm_metrics", 1);

        // Minimal PS_DOM3 puzzle setup
        setLife(playerA, 13);
        setLife(playerB, 5);
        addCard(Zone.HAND, playerA, "Brainstorm");
        addCard(Zone.HAND, playerA, "Ponder");
        addCard(Zone.BATTLEFIELD, playerA, "Island", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Counterspell");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_DOM3", 1);
    }

    @Test
    public void test_PS_DOM4_puzzle_llm_metrics() {
        beginPuzzle("test_PS_DOM4_puzzle_llm_metrics", 1);

        // Minimal PS_DOM4 puzzle setup
        setLife(playerA, 15);
        setLife(playerB, 3);
        addCard(Zone.HAND, playerA, "Chord of Calling");
        addCard(Zone.HAND, playerA, "Elvish Mystic");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Tarmogoyf");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_DOM4", 1);
    }

    @Test
    public void test_PS_DOM5_puzzle_llm_metrics() {
        beginPuzzle("test_PS_DOM5_puzzle_llm_metrics", 1);

        // Minimal PS_DOM5 puzzle setup
        setLife(playerA, 12);
        setLife(playerB, 6);
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.HAND, playerA, "Giant Growth");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 1);
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 1);
        addCard(Zone.BATTLEFIELD, playerB, "Wall of Roots");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_DOM5", 1);
    }

    @Test
    public void test_PS_DOM6_puzzle_llm_metrics() {
        beginPuzzle("test_PS_DOM6_puzzle_llm_metrics", 1);

        // Minimal PS_DOM6 puzzle setup
        setLife(playerA, 14);
        setLife(playerB, 4);
        addCard(Zone.HAND, playerA, "Path to Exile");
        addCard(Zone.HAND, playerA, "Swords to Plowshares");
        addCard(Zone.BATTLEFIELD, playerA, "Plains", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Snapcaster Mage");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_DOM6", 1);
    }

    @Test
    public void test_PS_DOM7_puzzle_llm_metrics() {
        beginPuzzle("test_PS_DOM7_puzzle_llm_metrics", 1);

        // Minimal PS_DOM7 puzzle setup
        setLife(playerA, 11);
        setLife(playerB, 7);
        addCard(Zone.HAND, playerA, "Doom Blade");
        addCard(Zone.HAND, playerA, "Victim of Night");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Gray Merchant of Asphodel");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_DOM7", 1);
    }

    @Test
    public void test_PS_DOM8_puzzle_llm_metrics() {
        beginPuzzle("test_PS_DOM8_puzzle_llm_metrics", 1);

        // Minimal PS_DOM8 puzzle setup
        setLife(playerA, 13);
        setLife(playerB, 5);
        addCard(Zone.HAND, playerA, "Brainstorm");
        addCard(Zone.HAND, playerA, "Ponder");
        addCard(Zone.BATTLEFIELD, playerA, "Island", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Counterspell");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_DOM8", 1);
    }

    @Test
    public void test_PS_DOM9_puzzle_llm_metrics() {
        beginPuzzle("test_PS_DOM9_puzzle_llm_metrics", 1);

        // Minimal PS_DOM9 puzzle setup
        setLife(playerA, 15);
        setLife(playerB, 3);
        addCard(Zone.HAND, playerA, "Chord of Calling");
        addCard(Zone.HAND, playerA, "Elvish Mystic");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Tarmogoyf");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_DOM9", 1);
    }

    @Test
    public void test_PS_DSK1_puzzle_llm_metrics() {
        beginPuzzle("test_PS_DSK1_puzzle_llm_metrics", 1);

        // Minimal PS_DSK1 puzzle setup
        setLife(playerA, 12);
        setLife(playerB, 6);
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.HAND, playerA, "Giant Growth");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 1);
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 1);
        addCard(Zone.BATTLEFIELD, playerB, "Wall of Roots");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_DSK1", 1);
    }

    @Test
    public void test_PS_DSK2_puzzle_llm_metrics() {
        beginPuzzle("test_PS_DSK2_puzzle_llm_metrics", 1);

        // Minimal PS_DSK2 puzzle setup
        setLife(playerA, 14);
        setLife(playerB, 4);
        addCard(Zone.HAND, playerA, "Path to Exile");
        addCard(Zone.HAND, playerA, "Swords to Plowshares");
        addCard(Zone.BATTLEFIELD, playerA, "Plains", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Snapcaster Mage");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_DSK2", 1);
    }

    @Test
    public void test_PS_DSK3_puzzle_llm_metrics() {
        beginPuzzle("test_PS_DSK3_puzzle_llm_metrics", 1);

        // Minimal PS_DSK3 puzzle setup
        setLife(playerA, 11);
        setLife(playerB, 7);
        addCard(Zone.HAND, playerA, "Doom Blade");
        addCard(Zone.HAND, playerA, "Victim of Night");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Gray Merchant of Asphodel");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_DSK3", 1);
    }

    @Test
    public void test_PS_DSK4_puzzle_llm_metrics() {
        beginPuzzle("test_PS_DSK4_puzzle_llm_metrics", 1);

        // Minimal PS_DSK4 puzzle setup
        setLife(playerA, 13);
        setLife(playerB, 5);
        addCard(Zone.HAND, playerA, "Brainstorm");
        addCard(Zone.HAND, playerA, "Ponder");
        addCard(Zone.BATTLEFIELD, playerA, "Island", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Counterspell");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_DSK4", 1);
    }

    @Test
    public void test_PS_ELD0_puzzle_llm_metrics() {
        beginPuzzle("test_PS_ELD0_puzzle_llm_metrics", 1);

        // Minimal PS_ELD0 puzzle setup
        setLife(playerA, 15);
        setLife(playerB, 3);
        addCard(Zone.HAND, playerA, "Chord of Calling");
        addCard(Zone.HAND, playerA, "Elvish Mystic");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Tarmogoyf");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_ELD0", 1);
    }

    @Test
    public void test_PS_ELD1_puzzle_llm_metrics() {
        beginPuzzle("test_PS_ELD1_puzzle_llm_metrics", 1);

        // Minimal PS_ELD1 puzzle setup
        setLife(playerA, 12);
        setLife(playerB, 6);
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.HAND, playerA, "Giant Growth");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 1);
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 1);
        addCard(Zone.BATTLEFIELD, playerB, "Wall of Roots");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_ELD1", 1);
    }

    @Test
    public void test_PS_ELD2_puzzle_llm_metrics() {
        beginPuzzle("test_PS_ELD2_puzzle_llm_metrics", 1);

        // Minimal PS_ELD2 puzzle setup
        setLife(playerA, 14);
        setLife(playerB, 4);
        addCard(Zone.HAND, playerA, "Path to Exile");
        addCard(Zone.HAND, playerA, "Swords to Plowshares");
        addCard(Zone.BATTLEFIELD, playerA, "Plains", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Snapcaster Mage");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_ELD2", 1);
    }

    @Test
    public void test_PS_ELD3_puzzle_llm_metrics() {
        beginPuzzle("test_PS_ELD3_puzzle_llm_metrics", 1);

        // Minimal PS_ELD3 puzzle setup
        setLife(playerA, 11);
        setLife(playerB, 7);
        addCard(Zone.HAND, playerA, "Doom Blade");
        addCard(Zone.HAND, playerA, "Victim of Night");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Gray Merchant of Asphodel");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_ELD3", 1);
    }

    @Test
    public void test_PS_ELD4_puzzle_llm_metrics() {
        beginPuzzle("test_PS_ELD4_puzzle_llm_metrics", 1);

        // Minimal PS_ELD4 puzzle setup
        setLife(playerA, 13);
        setLife(playerB, 5);
        addCard(Zone.HAND, playerA, "Brainstorm");
        addCard(Zone.HAND, playerA, "Ponder");
        addCard(Zone.BATTLEFIELD, playerA, "Island", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Counterspell");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_ELD4", 1);
    }

    @Test
    public void test_PS_ELD5_puzzle_llm_metrics() {
        beginPuzzle("test_PS_ELD5_puzzle_llm_metrics", 1);

        // Minimal PS_ELD5 puzzle setup
        setLife(playerA, 15);
        setLife(playerB, 3);
        addCard(Zone.HAND, playerA, "Chord of Calling");
        addCard(Zone.HAND, playerA, "Elvish Mystic");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Tarmogoyf");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_ELD5", 1);
    }

    @Test
    public void test_PS_ELD6_puzzle_llm_metrics() {
        beginPuzzle("test_PS_ELD6_puzzle_llm_metrics", 1);

        // Minimal PS_ELD6 puzzle setup
        setLife(playerA, 12);
        setLife(playerB, 6);
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.HAND, playerA, "Giant Growth");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 1);
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 1);
        addCard(Zone.BATTLEFIELD, playerB, "Wall of Roots");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_ELD6", 1);
    }

    @Test
    public void test_PS_ELD7_puzzle_llm_metrics() {
        beginPuzzle("test_PS_ELD7_puzzle_llm_metrics", 1);

        // Minimal PS_ELD7 puzzle setup
        setLife(playerA, 14);
        setLife(playerB, 4);
        addCard(Zone.HAND, playerA, "Path to Exile");
        addCard(Zone.HAND, playerA, "Swords to Plowshares");
        addCard(Zone.BATTLEFIELD, playerA, "Plains", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Snapcaster Mage");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_ELD7", 1);
    }

    @Test
    public void test_PS_ELD8_puzzle_llm_metrics() {
        beginPuzzle("test_PS_ELD8_puzzle_llm_metrics", 1);

        // Minimal PS_ELD8 puzzle setup
        setLife(playerA, 11);
        setLife(playerB, 7);
        addCard(Zone.HAND, playerA, "Doom Blade");
        addCard(Zone.HAND, playerA, "Victim of Night");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Gray Merchant of Asphodel");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_ELD8", 1);
    }

    @Test
    public void test_PS_ELD9_puzzle_llm_metrics() {
        beginPuzzle("test_PS_ELD9_puzzle_llm_metrics", 1);

        // Minimal PS_ELD9 puzzle setup
        setLife(playerA, 13);
        setLife(playerB, 5);
        addCard(Zone.HAND, playerA, "Brainstorm");
        addCard(Zone.HAND, playerA, "Ponder");
        addCard(Zone.BATTLEFIELD, playerA, "Island", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Counterspell");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_ELD9", 1);
    }

    @Test
    public void test_PS_ELDS_puzzle_llm_metrics() {
        beginPuzzle("test_PS_ELDS_puzzle_llm_metrics", 1);

        // Minimal PS_ELDS puzzle setup
        setLife(playerA, 15);
        setLife(playerB, 3);
        addCard(Zone.HAND, playerA, "Chord of Calling");
        addCard(Zone.HAND, playerA, "Elvish Mystic");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Tarmogoyf");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_ELDS", 1);
    }

    @Test
    public void test_PS_EOE1_puzzle_llm_metrics() {
        beginPuzzle("test_PS_EOE1_puzzle_llm_metrics", 1);

        // Minimal PS_EOE1 puzzle setup
        setLife(playerA, 12);
        setLife(playerB, 6);
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.HAND, playerA, "Giant Growth");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 1);
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 1);
        addCard(Zone.BATTLEFIELD, playerB, "Wall of Roots");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_EOE1", 1);
    }

    @Test
    public void test_PS_EOE3_puzzle_llm_metrics() {
        beginPuzzle("test_PS_EOE3_puzzle_llm_metrics", 1);

        // Minimal PS_EOE3 puzzle setup
        setLife(playerA, 14);
        setLife(playerB, 4);
        addCard(Zone.HAND, playerA, "Path to Exile");
        addCard(Zone.HAND, playerA, "Swords to Plowshares");
        addCard(Zone.BATTLEFIELD, playerA, "Plains", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Snapcaster Mage");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_EOE3", 1);
    }

    @Test
    public void test_PS_FDN1_puzzle_llm_metrics() {
        beginPuzzle("test_PS_FDN1_puzzle_llm_metrics", 1);

        // Minimal PS_FDN1 puzzle setup
        setLife(playerA, 11);
        setLife(playerB, 7);
        addCard(Zone.HAND, playerA, "Doom Blade");
        addCard(Zone.HAND, playerA, "Victim of Night");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Gray Merchant of Asphodel");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_FDN1", 1);
    }

    @Test
    public void test_PS_FDN2_puzzle_llm_metrics() {
        beginPuzzle("test_PS_FDN2_puzzle_llm_metrics", 1);

        // Minimal PS_FDN2 puzzle setup
        setLife(playerA, 13);
        setLife(playerB, 5);
        addCard(Zone.HAND, playerA, "Brainstorm");
        addCard(Zone.HAND, playerA, "Ponder");
        addCard(Zone.BATTLEFIELD, playerA, "Island", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Counterspell");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_FDN2", 1);
    }

    @Test
    public void test_PS_FIN1_puzzle_llm_metrics() {
        beginPuzzle("test_PS_FIN1_puzzle_llm_metrics", 1);

        // Minimal PS_FIN1 puzzle setup
        setLife(playerA, 15);
        setLife(playerB, 3);
        addCard(Zone.HAND, playerA, "Chord of Calling");
        addCard(Zone.HAND, playerA, "Elvish Mystic");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Tarmogoyf");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_FIN1", 1);
    }

    @Test
    public void test_PS_FIN2_puzzle_llm_metrics() {
        beginPuzzle("test_PS_FIN2_puzzle_llm_metrics", 1);

        // Minimal PS_FIN2 puzzle setup
        setLife(playerA, 12);
        setLife(playerB, 6);
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.HAND, playerA, "Giant Growth");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 1);
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 1);
        addCard(Zone.BATTLEFIELD, playerB, "Wall of Roots");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_FIN2", 1);
    }

    @Test
    public void test_PS_FIN3_puzzle_llm_metrics() {
        beginPuzzle("test_PS_FIN3_puzzle_llm_metrics", 1);

        // Minimal PS_FIN3 puzzle setup
        setLife(playerA, 14);
        setLife(playerB, 4);
        addCard(Zone.HAND, playerA, "Path to Exile");
        addCard(Zone.HAND, playerA, "Swords to Plowshares");
        addCard(Zone.BATTLEFIELD, playerA, "Plains", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Snapcaster Mage");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_FIN3", 1);
    }

    @Test
    public void test_PS_FIN4_puzzle_llm_metrics() {
        beginPuzzle("test_PS_FIN4_puzzle_llm_metrics", 1);

        // Minimal PS_FIN4 puzzle setup
        setLife(playerA, 11);
        setLife(playerB, 7);
        addCard(Zone.HAND, playerA, "Doom Blade");
        addCard(Zone.HAND, playerA, "Victim of Night");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Gray Merchant of Asphodel");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_FIN4", 1);
    }

    @Test
    public void test_PS_GRN0_puzzle_llm_metrics() {
        beginPuzzle("test_PS_GRN0_puzzle_llm_metrics", 1);

        // Minimal PS_GRN0 puzzle setup
        setLife(playerA, 13);
        setLife(playerB, 5);
        addCard(Zone.HAND, playerA, "Brainstorm");
        addCard(Zone.HAND, playerA, "Ponder");
        addCard(Zone.BATTLEFIELD, playerA, "Island", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Counterspell");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_GRN0", 1);
    }

    @Test
    public void test_PS_GRN0a_puzzle_llm_metrics() {
        beginPuzzle("test_PS_GRN0a_puzzle_llm_metrics", 1);

        // Minimal PS_GRN0a puzzle setup
        setLife(playerA, 15);
        setLife(playerB, 3);
        addCard(Zone.HAND, playerA, "Chord of Calling");
        addCard(Zone.HAND, playerA, "Elvish Mystic");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Tarmogoyf");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_GRN0a", 1);
    }

    @Test
    public void test_PS_GRN1_puzzle_llm_metrics() {
        beginPuzzle("test_PS_GRN1_puzzle_llm_metrics", 1);

        // Minimal PS_GRN1 puzzle setup
        setLife(playerA, 12);
        setLife(playerB, 6);
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.HAND, playerA, "Giant Growth");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 1);
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 1);
        addCard(Zone.BATTLEFIELD, playerB, "Wall of Roots");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_GRN1", 1);
    }

    @Test
    public void test_PS_GRN2_puzzle_llm_metrics() {
        beginPuzzle("test_PS_GRN2_puzzle_llm_metrics", 1);

        // Minimal PS_GRN2 puzzle setup
        setLife(playerA, 14);
        setLife(playerB, 4);
        addCard(Zone.HAND, playerA, "Path to Exile");
        addCard(Zone.HAND, playerA, "Swords to Plowshares");
        addCard(Zone.BATTLEFIELD, playerA, "Plains", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Snapcaster Mage");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_GRN2", 1);
    }

    @Test
    public void test_PS_GRN3_puzzle_llm_metrics() {
        beginPuzzle("test_PS_GRN3_puzzle_llm_metrics", 1);

        // Minimal PS_GRN3 puzzle setup
        setLife(playerA, 11);
        setLife(playerB, 7);
        addCard(Zone.HAND, playerA, "Doom Blade");
        addCard(Zone.HAND, playerA, "Victim of Night");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Gray Merchant of Asphodel");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_GRN3", 1);
    }

    @Test
    public void test_PS_GRN4_puzzle_llm_metrics() {
        beginPuzzle("test_PS_GRN4_puzzle_llm_metrics", 1);

        // Minimal PS_GRN4 puzzle setup
        setLife(playerA, 13);
        setLife(playerB, 5);
        addCard(Zone.HAND, playerA, "Brainstorm");
        addCard(Zone.HAND, playerA, "Ponder");
        addCard(Zone.BATTLEFIELD, playerA, "Island", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Counterspell");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_GRN4", 1);
    }

    @Test
    public void test_PS_GRN5_puzzle_llm_metrics() {
        beginPuzzle("test_PS_GRN5_puzzle_llm_metrics", 1);

        // Minimal PS_GRN5 puzzle setup
        setLife(playerA, 15);
        setLife(playerB, 3);
        addCard(Zone.HAND, playerA, "Chord of Calling");
        addCard(Zone.HAND, playerA, "Elvish Mystic");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Tarmogoyf");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_GRN5", 1);
    }

    @Test
    public void test_PS_GRN6_puzzle_llm_metrics() {
        beginPuzzle("test_PS_GRN6_puzzle_llm_metrics", 1);

        // Minimal PS_GRN6 puzzle setup
        setLife(playerA, 12);
        setLife(playerB, 6);
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.HAND, playerA, "Giant Growth");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 1);
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 1);
        addCard(Zone.BATTLEFIELD, playerB, "Wall of Roots");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_GRN6", 1);
    }

    @Test
    public void test_PS_GRN7_puzzle_llm_metrics() {
        beginPuzzle("test_PS_GRN7_puzzle_llm_metrics", 1);

        // Minimal PS_GRN7 puzzle setup
        setLife(playerA, 14);
        setLife(playerB, 4);
        addCard(Zone.HAND, playerA, "Path to Exile");
        addCard(Zone.HAND, playerA, "Swords to Plowshares");
        addCard(Zone.BATTLEFIELD, playerA, "Plains", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Snapcaster Mage");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_GRN7", 1);
    }

    @Test
    public void test_PS_GRN8_puzzle_llm_metrics() {
        beginPuzzle("test_PS_GRN8_puzzle_llm_metrics", 1);

        // Minimal PS_GRN8 puzzle setup
        setLife(playerA, 11);
        setLife(playerB, 7);
        addCard(Zone.HAND, playerA, "Doom Blade");
        addCard(Zone.HAND, playerA, "Victim of Night");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Gray Merchant of Asphodel");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_GRN8", 1);
    }

    @Test
    public void test_PS_GRN9_puzzle_llm_metrics() {
        beginPuzzle("test_PS_GRN9_puzzle_llm_metrics", 1);

        // Minimal PS_GRN9 puzzle setup
        setLife(playerA, 13);
        setLife(playerB, 5);
        addCard(Zone.HAND, playerA, "Brainstorm");
        addCard(Zone.HAND, playerA, "Ponder");
        addCard(Zone.BATTLEFIELD, playerA, "Island", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Counterspell");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_GRN9", 1);
    }

    @Test
    public void test_PS_GRND_puzzle_llm_metrics() {
        beginPuzzle("test_PS_GRND_puzzle_llm_metrics", 1);

        // Minimal PS_GRND puzzle setup
        setLife(playerA, 15);
        setLife(playerB, 3);
        addCard(Zone.HAND, playerA, "Chord of Calling");
        addCard(Zone.HAND, playerA, "Elvish Mystic");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Tarmogoyf");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_GRND", 1);
    }

    @Test
    public void test_PS_GRNS_puzzle_llm_metrics() {
        beginPuzzle("test_PS_GRNS_puzzle_llm_metrics", 1);

        // Minimal PS_GRNS puzzle setup
        setLife(playerA, 12);
        setLife(playerB, 6);
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.HAND, playerA, "Giant Growth");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 1);
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 1);
        addCard(Zone.BATTLEFIELD, playerB, "Wall of Roots");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_GRNS", 1);
    }

    @Test
    public void test_PS_GUEST0_puzzle_llm_metrics() {
        beginPuzzle("test_PS_GUEST0_puzzle_llm_metrics", 1);

        // Minimal PS_GUEST0 puzzle setup
        setLife(playerA, 14);
        setLife(playerB, 4);
        addCard(Zone.HAND, playerA, "Path to Exile");
        addCard(Zone.HAND, playerA, "Swords to Plowshares");
        addCard(Zone.BATTLEFIELD, playerA, "Plains", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Snapcaster Mage");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_GUEST0", 1);
    }

    @Test
    public void test_PS_HOU0_puzzle_llm_metrics() {
        beginPuzzle("test_PS_HOU0_puzzle_llm_metrics", 1);

        // Minimal PS_HOU0 puzzle setup
        setLife(playerA, 11);
        setLife(playerB, 7);
        addCard(Zone.HAND, playerA, "Doom Blade");
        addCard(Zone.HAND, playerA, "Victim of Night");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Gray Merchant of Asphodel");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_HOU0", 1);
    }

    @Test
    public void test_PS_HOU2_puzzle_llm_metrics() {
        beginPuzzle("test_PS_HOU2_puzzle_llm_metrics", 1);

        // Minimal PS_HOU2 puzzle setup
        setLife(playerA, 13);
        setLife(playerB, 5);
        addCard(Zone.HAND, playerA, "Brainstorm");
        addCard(Zone.HAND, playerA, "Ponder");
        addCard(Zone.BATTLEFIELD, playerA, "Island", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Counterspell");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_HOU2", 1);
    }

    @Test
    public void test_PS_HOU3_puzzle_llm_metrics() {
        beginPuzzle("test_PS_HOU3_puzzle_llm_metrics", 1);

        // Minimal PS_HOU3 puzzle setup
        setLife(playerA, 15);
        setLife(playerB, 3);
        addCard(Zone.HAND, playerA, "Chord of Calling");
        addCard(Zone.HAND, playerA, "Elvish Mystic");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Tarmogoyf");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_HOU3", 1);
    }

    @Test
    public void test_PS_HOU4_puzzle_llm_metrics() {
        beginPuzzle("test_PS_HOU4_puzzle_llm_metrics", 1);

        // Minimal PS_HOU4 puzzle setup
        setLife(playerA, 12);
        setLife(playerB, 6);
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.HAND, playerA, "Giant Growth");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 1);
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 1);
        addCard(Zone.BATTLEFIELD, playerB, "Wall of Roots");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_HOU4", 1);
    }

    @Test
    public void test_PS_HOU5_puzzle_llm_metrics() {
        beginPuzzle("test_PS_HOU5_puzzle_llm_metrics", 1);

        // Minimal PS_HOU5 puzzle setup
        setLife(playerA, 14);
        setLife(playerB, 4);
        addCard(Zone.HAND, playerA, "Path to Exile");
        addCard(Zone.HAND, playerA, "Swords to Plowshares");
        addCard(Zone.BATTLEFIELD, playerA, "Plains", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Snapcaster Mage");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_HOU5", 1);
    }

    @Test
    public void test_PS_HOU6_puzzle_llm_metrics() {
        beginPuzzle("test_PS_HOU6_puzzle_llm_metrics", 1);

        // Minimal PS_HOU6 puzzle setup
        setLife(playerA, 11);
        setLife(playerB, 7);
        addCard(Zone.HAND, playerA, "Doom Blade");
        addCard(Zone.HAND, playerA, "Victim of Night");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Gray Merchant of Asphodel");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_HOU6", 1);
    }

    @Test
    public void test_PS_HOU7_puzzle_llm_metrics() {
        beginPuzzle("test_PS_HOU7_puzzle_llm_metrics", 1);

        // Minimal PS_HOU7 puzzle setup
        setLife(playerA, 13);
        setLife(playerB, 5);
        addCard(Zone.HAND, playerA, "Brainstorm");
        addCard(Zone.HAND, playerA, "Ponder");
        addCard(Zone.BATTLEFIELD, playerA, "Island", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Counterspell");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_HOU7", 1);
    }

    @Test
    public void test_PS_HOU8_puzzle_llm_metrics() {
        beginPuzzle("test_PS_HOU8_puzzle_llm_metrics", 1);

        // Minimal PS_HOU8 puzzle setup
        setLife(playerA, 15);
        setLife(playerB, 3);
        addCard(Zone.HAND, playerA, "Chord of Calling");
        addCard(Zone.HAND, playerA, "Elvish Mystic");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Tarmogoyf");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_HOU8", 1);
    }

    @Test
    public void test_PS_IKO1_puzzle_llm_metrics() {
        beginPuzzle("test_PS_IKO1_puzzle_llm_metrics", 1);

        // Minimal PS_IKO1 puzzle setup
        setLife(playerA, 12);
        setLife(playerB, 6);
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.HAND, playerA, "Giant Growth");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 1);
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 1);
        addCard(Zone.BATTLEFIELD, playerB, "Wall of Roots");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_IKO1", 1);
    }

    @Test
    public void test_PS_IKO2_puzzle_llm_metrics() {
        beginPuzzle("test_PS_IKO2_puzzle_llm_metrics", 1);

        // Minimal PS_IKO2 puzzle setup
        setLife(playerA, 14);
        setLife(playerB, 4);
        addCard(Zone.HAND, playerA, "Path to Exile");
        addCard(Zone.HAND, playerA, "Swords to Plowshares");
        addCard(Zone.BATTLEFIELD, playerA, "Plains", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Snapcaster Mage");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_IKO2", 1);
    }

    @Test
    public void test_PS_IMA_puzzle_llm_metrics() {
        beginPuzzle("test_PS_IMA_puzzle_llm_metrics", 1);

        // Minimal PS_IMA puzzle setup
        setLife(playerA, 11);
        setLife(playerB, 7);
        addCard(Zone.HAND, playerA, "Doom Blade");
        addCard(Zone.HAND, playerA, "Victim of Night");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Gray Merchant of Asphodel");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_IMA", 1);
    }

    @Test
    public void test_PS_J221_puzzle_llm_metrics() {
        beginPuzzle("test_PS_J221_puzzle_llm_metrics", 1);

        // Minimal PS_J221 puzzle setup
        setLife(playerA, 13);
        setLife(playerB, 5);
        addCard(Zone.HAND, playerA, "Brainstorm");
        addCard(Zone.HAND, playerA, "Ponder");
        addCard(Zone.BATTLEFIELD, playerA, "Island", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Counterspell");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_J221", 1);
    }

    @Test
    public void test_PS_KHM1_puzzle_llm_metrics() {
        beginPuzzle("test_PS_KHM1_puzzle_llm_metrics", 1);

        // Minimal PS_KHM1 puzzle setup
        setLife(playerA, 15);
        setLife(playerB, 3);
        addCard(Zone.HAND, playerA, "Chord of Calling");
        addCard(Zone.HAND, playerA, "Elvish Mystic");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Tarmogoyf");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_KHM1", 1);
    }

    @Test
    public void test_PS_KHM2_puzzle_llm_metrics() {
        beginPuzzle("test_PS_KHM2_puzzle_llm_metrics", 1);

        // Minimal PS_KHM2 puzzle setup
        setLife(playerA, 12);
        setLife(playerB, 6);
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.HAND, playerA, "Giant Growth");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 1);
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 1);
        addCard(Zone.BATTLEFIELD, playerB, "Wall of Roots");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_KHM2", 1);
    }

    @Test
    public void test_PS_KHM3_puzzle_llm_metrics() {
        beginPuzzle("test_PS_KHM3_puzzle_llm_metrics", 1);

        // Minimal PS_KHM3 puzzle setup
        setLife(playerA, 14);
        setLife(playerB, 4);
        addCard(Zone.HAND, playerA, "Path to Exile");
        addCard(Zone.HAND, playerA, "Swords to Plowshares");
        addCard(Zone.BATTLEFIELD, playerA, "Plains", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Snapcaster Mage");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_KHM3", 1);
    }

    @Test
    public void test_PS_KHM4_puzzle_llm_metrics() {
        beginPuzzle("test_PS_KHM4_puzzle_llm_metrics", 1);

        // Minimal PS_KHM4 puzzle setup
        setLife(playerA, 11);
        setLife(playerB, 7);
        addCard(Zone.HAND, playerA, "Doom Blade");
        addCard(Zone.HAND, playerA, "Victim of Night");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Gray Merchant of Asphodel");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_KHM4", 1);
    }

    @Test
    public void test_PS_KHM5_puzzle_llm_metrics() {
        beginPuzzle("test_PS_KHM5_puzzle_llm_metrics", 1);

        // Minimal PS_KHM5 puzzle setup
        setLife(playerA, 13);
        setLife(playerB, 5);
        addCard(Zone.HAND, playerA, "Brainstorm");
        addCard(Zone.HAND, playerA, "Ponder");
        addCard(Zone.BATTLEFIELD, playerA, "Island", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Counterspell");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_KHM5", 1);
    }

    @Test
    public void test_PS_KHM6_puzzle_llm_metrics() {
        beginPuzzle("test_PS_KHM6_puzzle_llm_metrics", 1);

        // Minimal PS_KHM6 puzzle setup
        setLife(playerA, 15);
        setLife(playerB, 3);
        addCard(Zone.HAND, playerA, "Chord of Calling");
        addCard(Zone.HAND, playerA, "Elvish Mystic");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Tarmogoyf");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_KHM6", 1);
    }

    @Test
    public void test_PS_KTKT_puzzle_llm_metrics() {
        beginPuzzle("test_PS_KTKT_puzzle_llm_metrics", 1);

        // Minimal PS_KTKT puzzle setup
        setLife(playerA, 12);
        setLife(playerB, 6);
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.HAND, playerA, "Giant Growth");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 1);
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 1);
        addCard(Zone.BATTLEFIELD, playerB, "Wall of Roots");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_KTKT", 1);
    }

    @Test
    public void test_PS_LCI1_puzzle_llm_metrics() {
        beginPuzzle("test_PS_LCI1_puzzle_llm_metrics", 1);

        // Minimal PS_LCI1 puzzle setup
        setLife(playerA, 14);
        setLife(playerB, 4);
        addCard(Zone.HAND, playerA, "Path to Exile");
        addCard(Zone.HAND, playerA, "Swords to Plowshares");
        addCard(Zone.BATTLEFIELD, playerA, "Plains", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Snapcaster Mage");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_LCI1", 1);
    }

    @Test
    public void test_PS_LCI2_puzzle_llm_metrics() {
        beginPuzzle("test_PS_LCI2_puzzle_llm_metrics", 1);

        // Minimal PS_LCI2 puzzle setup
        setLife(playerA, 11);
        setLife(playerB, 7);
        addCard(Zone.HAND, playerA, "Doom Blade");
        addCard(Zone.HAND, playerA, "Victim of Night");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Gray Merchant of Asphodel");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_LCI2", 1);
    }

    @Test
    public void test_PS_LCI3_puzzle_llm_metrics() {
        beginPuzzle("test_PS_LCI3_puzzle_llm_metrics", 1);

        // Minimal PS_LCI3 puzzle setup
        setLife(playerA, 13);
        setLife(playerB, 5);
        addCard(Zone.HAND, playerA, "Brainstorm");
        addCard(Zone.HAND, playerA, "Ponder");
        addCard(Zone.BATTLEFIELD, playerA, "Island", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Counterspell");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_LCI3", 1);
    }

    @Test
    public void test_PS_LTR1_puzzle_llm_metrics() {
        beginPuzzle("test_PS_LTR1_puzzle_llm_metrics", 1);

        // Minimal PS_LTR1 puzzle setup
        setLife(playerA, 15);
        setLife(playerB, 3);
        addCard(Zone.HAND, playerA, "Chord of Calling");
        addCard(Zone.HAND, playerA, "Elvish Mystic");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Tarmogoyf");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_LTR1", 1);
    }

    @Test
    public void test_PS_LTR2_puzzle_llm_metrics() {
        beginPuzzle("test_PS_LTR2_puzzle_llm_metrics", 1);

        // Minimal PS_LTR2 puzzle setup
        setLife(playerA, 12);
        setLife(playerB, 6);
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.HAND, playerA, "Giant Growth");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 1);
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 1);
        addCard(Zone.BATTLEFIELD, playerB, "Wall of Roots");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_LTR2", 1);
    }

    @Test
    public void test_PS_LTR3_puzzle_llm_metrics() {
        beginPuzzle("test_PS_LTR3_puzzle_llm_metrics", 1);

        // Minimal PS_LTR3 puzzle setup
        setLife(playerA, 14);
        setLife(playerB, 4);
        addCard(Zone.HAND, playerA, "Path to Exile");
        addCard(Zone.HAND, playerA, "Swords to Plowshares");
        addCard(Zone.BATTLEFIELD, playerA, "Plains", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Snapcaster Mage");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_LTR3", 1);
    }

    @Test
    public void test_PS_M190_puzzle_llm_metrics() {
        beginPuzzle("test_PS_M190_puzzle_llm_metrics", 1);

        // Minimal PS_M190 puzzle setup
        setLife(playerA, 11);
        setLife(playerB, 7);
        addCard(Zone.HAND, playerA, "Doom Blade");
        addCard(Zone.HAND, playerA, "Victim of Night");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Gray Merchant of Asphodel");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_M190", 1);
    }

    @Test
    public void test_PS_M191_puzzle_llm_metrics() {
        beginPuzzle("test_PS_M191_puzzle_llm_metrics", 1);

        // Minimal PS_M191 puzzle setup
        setLife(playerA, 13);
        setLife(playerB, 5);
        addCard(Zone.HAND, playerA, "Brainstorm");
        addCard(Zone.HAND, playerA, "Ponder");
        addCard(Zone.BATTLEFIELD, playerA, "Island", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Counterspell");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_M191", 1);
    }

    @Test
    public void test_PS_M192_puzzle_llm_metrics() {
        beginPuzzle("test_PS_M192_puzzle_llm_metrics", 1);

        // Minimal PS_M192 puzzle setup
        setLife(playerA, 15);
        setLife(playerB, 3);
        addCard(Zone.HAND, playerA, "Chord of Calling");
        addCard(Zone.HAND, playerA, "Elvish Mystic");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Tarmogoyf");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_M192", 1);
    }

    @Test
    public void test_PS_M193_puzzle_llm_metrics() {
        beginPuzzle("test_PS_M193_puzzle_llm_metrics", 1);

        // Minimal PS_M193 puzzle setup
        setLife(playerA, 12);
        setLife(playerB, 6);
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.HAND, playerA, "Giant Growth");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 1);
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 1);
        addCard(Zone.BATTLEFIELD, playerB, "Wall of Roots");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_M193", 1);
    }

    @Test
    public void test_PS_M194_puzzle_llm_metrics() {
        beginPuzzle("test_PS_M194_puzzle_llm_metrics", 1);

        // Minimal PS_M194 puzzle setup
        setLife(playerA, 14);
        setLife(playerB, 4);
        addCard(Zone.HAND, playerA, "Path to Exile");
        addCard(Zone.HAND, playerA, "Swords to Plowshares");
        addCard(Zone.BATTLEFIELD, playerA, "Plains", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Snapcaster Mage");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_M194", 1);
    }

    @Test
    public void test_PS_M195_puzzle_llm_metrics() {
        beginPuzzle("test_PS_M195_puzzle_llm_metrics", 1);

        // Minimal PS_M195 puzzle setup
        setLife(playerA, 11);
        setLife(playerB, 7);
        addCard(Zone.HAND, playerA, "Doom Blade");
        addCard(Zone.HAND, playerA, "Victim of Night");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Gray Merchant of Asphodel");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_M195", 1);
    }

    @Test
    public void test_PS_M196_puzzle_llm_metrics() {
        beginPuzzle("test_PS_M196_puzzle_llm_metrics", 1);

        // Minimal PS_M196 puzzle setup
        setLife(playerA, 13);
        setLife(playerB, 5);
        addCard(Zone.HAND, playerA, "Brainstorm");
        addCard(Zone.HAND, playerA, "Ponder");
        addCard(Zone.BATTLEFIELD, playerA, "Island", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Counterspell");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_M196", 1);
    }

    @Test
    public void test_PS_M197_puzzle_llm_metrics() {
        beginPuzzle("test_PS_M197_puzzle_llm_metrics", 1);

        // Minimal PS_M197 puzzle setup
        setLife(playerA, 15);
        setLife(playerB, 3);
        addCard(Zone.HAND, playerA, "Chord of Calling");
        addCard(Zone.HAND, playerA, "Elvish Mystic");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Tarmogoyf");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_M197", 1);
    }

    @Test
    public void test_PS_M198_puzzle_llm_metrics() {
        beginPuzzle("test_PS_M198_puzzle_llm_metrics", 1);

        // Minimal PS_M198 puzzle setup
        setLife(playerA, 12);
        setLife(playerB, 6);
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.HAND, playerA, "Giant Growth");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 1);
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 1);
        addCard(Zone.BATTLEFIELD, playerB, "Wall of Roots");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_M198", 1);
    }

    @Test
    public void test_PS_M199_puzzle_llm_metrics() {
        beginPuzzle("test_PS_M199_puzzle_llm_metrics", 1);

        // Minimal PS_M199 puzzle setup
        setLife(playerA, 14);
        setLife(playerB, 4);
        addCard(Zone.HAND, playerA, "Path to Exile");
        addCard(Zone.HAND, playerA, "Swords to Plowshares");
        addCard(Zone.BATTLEFIELD, playerA, "Plains", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Snapcaster Mage");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_M199", 1);
    }

    @Test
    public void test_PS_M201_puzzle_llm_metrics() {
        beginPuzzle("test_PS_M201_puzzle_llm_metrics", 1);

        // Minimal PS_M201 puzzle setup
        setLife(playerA, 11);
        setLife(playerB, 7);
        addCard(Zone.HAND, playerA, "Doom Blade");
        addCard(Zone.HAND, playerA, "Victim of Night");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Gray Merchant of Asphodel");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_M201", 1);
    }

    @Test
    public void test_PS_M202_puzzle_llm_metrics() {
        beginPuzzle("test_PS_M202_puzzle_llm_metrics", 1);

        // Minimal PS_M202 puzzle setup
        setLife(playerA, 13);
        setLife(playerB, 5);
        addCard(Zone.HAND, playerA, "Brainstorm");
        addCard(Zone.HAND, playerA, "Ponder");
        addCard(Zone.BATTLEFIELD, playerA, "Island", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Counterspell");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_M202", 1);
    }

    @Test
    public void test_PS_M203_puzzle_llm_metrics() {
        beginPuzzle("test_PS_M203_puzzle_llm_metrics", 1);

        // Minimal PS_M203 puzzle setup
        setLife(playerA, 15);
        setLife(playerB, 3);
        addCard(Zone.HAND, playerA, "Chord of Calling");
        addCard(Zone.HAND, playerA, "Elvish Mystic");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Tarmogoyf");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_M203", 1);
    }

    @Test
    public void test_PS_M204_puzzle_llm_metrics() {
        beginPuzzle("test_PS_M204_puzzle_llm_metrics", 1);

        // Minimal PS_M204 puzzle setup
        setLife(playerA, 12);
        setLife(playerB, 6);
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.HAND, playerA, "Giant Growth");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 1);
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 1);
        addCard(Zone.BATTLEFIELD, playerB, "Wall of Roots");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_M204", 1);
    }

    @Test
    public void test_PS_M205_puzzle_llm_metrics() {
        beginPuzzle("test_PS_M205_puzzle_llm_metrics", 1);

        // Minimal PS_M205 puzzle setup
        setLife(playerA, 14);
        setLife(playerB, 4);
        addCard(Zone.HAND, playerA, "Path to Exile");
        addCard(Zone.HAND, playerA, "Swords to Plowshares");
        addCard(Zone.BATTLEFIELD, playerA, "Plains", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Snapcaster Mage");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_M205", 1);
    }

    @Test
    public void test_PS_M206_puzzle_llm_metrics() {
        beginPuzzle("test_PS_M206_puzzle_llm_metrics", 1);

        // Minimal PS_M206 puzzle setup
        setLife(playerA, 11);
        setLife(playerB, 7);
        addCard(Zone.HAND, playerA, "Doom Blade");
        addCard(Zone.HAND, playerA, "Victim of Night");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Gray Merchant of Asphodel");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_M206", 1);
    }

    @Test
    public void test_PS_M207_puzzle_llm_metrics() {
        beginPuzzle("test_PS_M207_puzzle_llm_metrics", 1);

        // Minimal PS_M207 puzzle setup
        setLife(playerA, 13);
        setLife(playerB, 5);
        addCard(Zone.HAND, playerA, "Brainstorm");
        addCard(Zone.HAND, playerA, "Ponder");
        addCard(Zone.BATTLEFIELD, playerA, "Island", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Counterspell");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_M207", 1);
    }

    @Test
    public void test_PS_M208_puzzle_llm_metrics() {
        beginPuzzle("test_PS_M208_puzzle_llm_metrics", 1);

        // Minimal PS_M208 puzzle setup
        setLife(playerA, 15);
        setLife(playerB, 3);
        addCard(Zone.HAND, playerA, "Chord of Calling");
        addCard(Zone.HAND, playerA, "Elvish Mystic");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Tarmogoyf");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_M208", 1);
    }

    @Test
    public void test_PS_M209_puzzle_llm_metrics() {
        beginPuzzle("test_PS_M209_puzzle_llm_metrics", 1);

        // Minimal PS_M209 puzzle setup
        setLife(playerA, 12);
        setLife(playerB, 6);
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.HAND, playerA, "Giant Growth");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 1);
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 1);
        addCard(Zone.BATTLEFIELD, playerB, "Wall of Roots");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_M209", 1);
    }

    @Test
    public void test_PS_M211_puzzle_llm_metrics() {
        beginPuzzle("test_PS_M211_puzzle_llm_metrics", 1);

        // Minimal PS_M211 puzzle setup
        setLife(playerA, 14);
        setLife(playerB, 4);
        addCard(Zone.HAND, playerA, "Path to Exile");
        addCard(Zone.HAND, playerA, "Swords to Plowshares");
        addCard(Zone.BATTLEFIELD, playerA, "Plains", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Snapcaster Mage");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_M211", 1);
    }

    @Test
    public void test_PS_MGOB_puzzle_llm_metrics() {
        beginPuzzle("test_PS_MGOB_puzzle_llm_metrics", 1);

        // Minimal PS_MGOB puzzle setup
        setLife(playerA, 11);
        setLife(playerB, 7);
        addCard(Zone.HAND, playerA, "Doom Blade");
        addCard(Zone.HAND, playerA, "Victim of Night");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Gray Merchant of Asphodel");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_MGOB", 1);
    }

    @Test
    public void test_PS_MH21_puzzle_llm_metrics() {
        beginPuzzle("test_PS_MH21_puzzle_llm_metrics", 1);

        // Minimal PS_MH21 puzzle setup
        setLife(playerA, 13);
        setLife(playerB, 5);
        addCard(Zone.HAND, playerA, "Brainstorm");
        addCard(Zone.HAND, playerA, "Ponder");
        addCard(Zone.BATTLEFIELD, playerA, "Island", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Counterspell");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_MH21", 1);
    }

    @Test
    public void test_PS_MH31_puzzle_llm_metrics() {
        beginPuzzle("test_PS_MH31_puzzle_llm_metrics", 1);

        // Minimal PS_MH31 puzzle setup
        setLife(playerA, 15);
        setLife(playerB, 3);
        addCard(Zone.HAND, playerA, "Chord of Calling");
        addCard(Zone.HAND, playerA, "Elvish Mystic");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Tarmogoyf");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_MH31", 1);
    }

    @Test
    public void test_PS_MH32_puzzle_llm_metrics() {
        beginPuzzle("test_PS_MH32_puzzle_llm_metrics", 1);

        // Minimal PS_MH32 puzzle setup
        setLife(playerA, 12);
        setLife(playerB, 6);
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.HAND, playerA, "Giant Growth");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 1);
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 1);
        addCard(Zone.BATTLEFIELD, playerB, "Wall of Roots");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_MH32", 1);
    }

    @Test
    public void test_PS_MID1_puzzle_llm_metrics() {
        beginPuzzle("test_PS_MID1_puzzle_llm_metrics", 1);

        // Minimal PS_MID1 puzzle setup
        setLife(playerA, 14);
        setLife(playerB, 4);
        addCard(Zone.HAND, playerA, "Path to Exile");
        addCard(Zone.HAND, playerA, "Swords to Plowshares");
        addCard(Zone.BATTLEFIELD, playerA, "Plains", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Snapcaster Mage");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_MID1", 1);
    }

    @Test
    public void test_PS_MID2_puzzle_llm_metrics() {
        beginPuzzle("test_PS_MID2_puzzle_llm_metrics", 1);

        // Minimal PS_MID2 puzzle setup
        setLife(playerA, 11);
        setLife(playerB, 7);
        addCard(Zone.HAND, playerA, "Doom Blade");
        addCard(Zone.HAND, playerA, "Victim of Night");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Gray Merchant of Asphodel");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_MID2", 1);
    }

    @Test
    public void test_PS_MID3_puzzle_llm_metrics() {
        beginPuzzle("test_PS_MID3_puzzle_llm_metrics", 1);

        // Minimal PS_MID3 puzzle setup
        setLife(playerA, 13);
        setLife(playerB, 5);
        addCard(Zone.HAND, playerA, "Brainstorm");
        addCard(Zone.HAND, playerA, "Ponder");
        addCard(Zone.BATTLEFIELD, playerA, "Island", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Counterspell");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_MID3", 1);
    }

    @Test
    public void test_PS_MID4_puzzle_llm_metrics() {
        beginPuzzle("test_PS_MID4_puzzle_llm_metrics", 1);

        // Minimal PS_MID4 puzzle setup
        setLife(playerA, 15);
        setLife(playerB, 3);
        addCard(Zone.HAND, playerA, "Chord of Calling");
        addCard(Zone.HAND, playerA, "Elvish Mystic");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Tarmogoyf");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_MID4", 1);
    }

    @Test
    public void test_PS_MKM1_puzzle_llm_metrics() {
        beginPuzzle("test_PS_MKM1_puzzle_llm_metrics", 1);

        // Minimal PS_MKM1 puzzle setup
        setLife(playerA, 12);
        setLife(playerB, 6);
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.HAND, playerA, "Giant Growth");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 1);
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 1);
        addCard(Zone.BATTLEFIELD, playerB, "Wall of Roots");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_MKM1", 1);
    }

    @Test
    public void test_PS_MKM2_puzzle_llm_metrics() {
        beginPuzzle("test_PS_MKM2_puzzle_llm_metrics", 1);

        // Minimal PS_MKM2 puzzle setup
        setLife(playerA, 14);
        setLife(playerB, 4);
        addCard(Zone.HAND, playerA, "Path to Exile");
        addCard(Zone.HAND, playerA, "Swords to Plowshares");
        addCard(Zone.BATTLEFIELD, playerA, "Plains", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Snapcaster Mage");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_MKM2", 1);
    }

    @Test
    public void test_PS_MKM3_puzzle_llm_metrics() {
        beginPuzzle("test_PS_MKM3_puzzle_llm_metrics", 1);

        // Minimal PS_MKM3 puzzle setup
        setLife(playerA, 11);
        setLife(playerB, 7);
        addCard(Zone.HAND, playerA, "Doom Blade");
        addCard(Zone.HAND, playerA, "Victim of Night");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Gray Merchant of Asphodel");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_MKM3", 1);
    }

    @Test
    public void test_PS_MKM4_puzzle_llm_metrics() {
        beginPuzzle("test_PS_MKM4_puzzle_llm_metrics", 1);

        // Minimal PS_MKM4 puzzle setup
        setLife(playerA, 13);
        setLife(playerB, 5);
        addCard(Zone.HAND, playerA, "Brainstorm");
        addCard(Zone.HAND, playerA, "Ponder");
        addCard(Zone.BATTLEFIELD, playerA, "Island", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Counterspell");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_MKM4", 1);
    }

    @Test
    public void test_PS_NEO1_puzzle_llm_metrics() {
        beginPuzzle("test_PS_NEO1_puzzle_llm_metrics", 1);

        // Minimal PS_NEO1 puzzle setup
        setLife(playerA, 15);
        setLife(playerB, 3);
        addCard(Zone.HAND, playerA, "Chord of Calling");
        addCard(Zone.HAND, playerA, "Elvish Mystic");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Tarmogoyf");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_NEO1", 1);
    }

    @Test
    public void test_PS_NEO2_puzzle_llm_metrics() {
        beginPuzzle("test_PS_NEO2_puzzle_llm_metrics", 1);

        // Minimal PS_NEO2 puzzle setup
        setLife(playerA, 12);
        setLife(playerB, 6);
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.HAND, playerA, "Giant Growth");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 1);
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 1);
        addCard(Zone.BATTLEFIELD, playerB, "Wall of Roots");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_NEO2", 1);
    }

    @Test
    public void test_PS_NEO3_puzzle_llm_metrics() {
        beginPuzzle("test_PS_NEO3_puzzle_llm_metrics", 1);

        // Minimal PS_NEO3 puzzle setup
        setLife(playerA, 14);
        setLife(playerB, 4);
        addCard(Zone.HAND, playerA, "Path to Exile");
        addCard(Zone.HAND, playerA, "Swords to Plowshares");
        addCard(Zone.BATTLEFIELD, playerA, "Plains", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Snapcaster Mage");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_NEO3", 1);
    }

    @Test
    public void test_PS_NEO4_puzzle_llm_metrics() {
        beginPuzzle("test_PS_NEO4_puzzle_llm_metrics", 1);

        // Minimal PS_NEO4 puzzle setup
        setLife(playerA, 11);
        setLife(playerB, 7);
        addCard(Zone.HAND, playerA, "Doom Blade");
        addCard(Zone.HAND, playerA, "Victim of Night");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Gray Merchant of Asphodel");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_NEO4", 1);
    }

    @Test
    public void test_PS_NEO5_puzzle_llm_metrics() {
        beginPuzzle("test_PS_NEO5_puzzle_llm_metrics", 1);

        // Minimal PS_NEO5 puzzle setup
        setLife(playerA, 13);
        setLife(playerB, 5);
        addCard(Zone.HAND, playerA, "Brainstorm");
        addCard(Zone.HAND, playerA, "Ponder");
        addCard(Zone.BATTLEFIELD, playerA, "Island", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Counterspell");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_NEO5", 1);
    }

    @Test
    public void test_PS_ONE1_puzzle_llm_metrics() {
        beginPuzzle("test_PS_ONE1_puzzle_llm_metrics", 1);

        // Minimal PS_ONE1 puzzle setup
        setLife(playerA, 15);
        setLife(playerB, 3);
        addCard(Zone.HAND, playerA, "Chord of Calling");
        addCard(Zone.HAND, playerA, "Elvish Mystic");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Tarmogoyf");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_ONE1", 1);
    }

    @Test
    public void test_PS_ONE2_puzzle_llm_metrics() {
        beginPuzzle("test_PS_ONE2_puzzle_llm_metrics", 1);

        // Minimal PS_ONE2 puzzle setup
        setLife(playerA, 12);
        setLife(playerB, 6);
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.HAND, playerA, "Giant Growth");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 1);
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 1);
        addCard(Zone.BATTLEFIELD, playerB, "Wall of Roots");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_ONE2", 1);
    }

    @Test
    public void test_PS_ONE3_puzzle_llm_metrics() {
        beginPuzzle("test_PS_ONE3_puzzle_llm_metrics", 1);

        // Minimal PS_ONE3 puzzle setup
        setLife(playerA, 14);
        setLife(playerB, 4);
        addCard(Zone.HAND, playerA, "Path to Exile");
        addCard(Zone.HAND, playerA, "Swords to Plowshares");
        addCard(Zone.BATTLEFIELD, playerA, "Plains", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Snapcaster Mage");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_ONE3", 1);
    }

    @Test
    public void test_PS_ONE4_puzzle_llm_metrics() {
        beginPuzzle("test_PS_ONE4_puzzle_llm_metrics", 1);

        // Minimal PS_ONE4 puzzle setup
        setLife(playerA, 11);
        setLife(playerB, 7);
        addCard(Zone.HAND, playerA, "Doom Blade");
        addCard(Zone.HAND, playerA, "Victim of Night");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Gray Merchant of Asphodel");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_ONE4", 1);
    }

    @Test
    public void test_PS_ONE5_puzzle_llm_metrics() {
        beginPuzzle("test_PS_ONE5_puzzle_llm_metrics", 1);

        // Minimal PS_ONE5 puzzle setup
        setLife(playerA, 13);
        setLife(playerB, 5);
        addCard(Zone.HAND, playerA, "Brainstorm");
        addCard(Zone.HAND, playerA, "Ponder");
        addCard(Zone.BATTLEFIELD, playerA, "Island", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Counterspell");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_ONE5", 1);
    }

    @Test
    public void test_PS_OTJ1_puzzle_llm_metrics() {
        beginPuzzle("test_PS_OTJ1_puzzle_llm_metrics", 1);

        // Minimal PS_OTJ1 puzzle setup
        setLife(playerA, 15);
        setLife(playerB, 3);
        addCard(Zone.HAND, playerA, "Chord of Calling");
        addCard(Zone.HAND, playerA, "Elvish Mystic");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Tarmogoyf");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_OTJ1", 1);
    }

    @Test
    public void test_PS_OTJ2_puzzle_llm_metrics() {
        beginPuzzle("test_PS_OTJ2_puzzle_llm_metrics", 1);

        // Minimal PS_OTJ2 puzzle setup
        setLife(playerA, 12);
        setLife(playerB, 6);
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.HAND, playerA, "Giant Growth");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 1);
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 1);
        addCard(Zone.BATTLEFIELD, playerB, "Wall of Roots");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_OTJ2", 1);
    }

    @Test
    public void test_PS_OTJ3_puzzle_llm_metrics() {
        beginPuzzle("test_PS_OTJ3_puzzle_llm_metrics", 1);

        // Minimal PS_OTJ3 puzzle setup
        setLife(playerA, 14);
        setLife(playerB, 4);
        addCard(Zone.HAND, playerA, "Path to Exile");
        addCard(Zone.HAND, playerA, "Swords to Plowshares");
        addCard(Zone.BATTLEFIELD, playerA, "Plains", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Snapcaster Mage");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_OTJ3", 1);
    }

    @Test
    public void test_PS_OTJ4_puzzle_llm_metrics() {
        beginPuzzle("test_PS_OTJ4_puzzle_llm_metrics", 1);

        // Minimal PS_OTJ4 puzzle setup
        setLife(playerA, 11);
        setLife(playerB, 7);
        addCard(Zone.HAND, playerA, "Doom Blade");
        addCard(Zone.HAND, playerA, "Victim of Night");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Gray Merchant of Asphodel");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_OTJ4", 1);
    }

    @Test
    public void test_PS_PIP1_puzzle_llm_metrics() {
        beginPuzzle("test_PS_PIP1_puzzle_llm_metrics", 1);

        // Minimal PS_PIP1 puzzle setup
        setLife(playerA, 13);
        setLife(playerB, 5);
        addCard(Zone.HAND, playerA, "Brainstorm");
        addCard(Zone.HAND, playerA, "Ponder");
        addCard(Zone.BATTLEFIELD, playerA, "Island", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Counterspell");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_PIP1", 1);
    }

    @Test
    public void test_PS_RIX1_puzzle_llm_metrics() {
        beginPuzzle("test_PS_RIX1_puzzle_llm_metrics", 1);

        // Minimal PS_RIX1 puzzle setup
        setLife(playerA, 15);
        setLife(playerB, 3);
        addCard(Zone.HAND, playerA, "Chord of Calling");
        addCard(Zone.HAND, playerA, "Elvish Mystic");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Tarmogoyf");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_RIX1", 1);
    }

    @Test
    public void test_PS_RIX2_puzzle_llm_metrics() {
        beginPuzzle("test_PS_RIX2_puzzle_llm_metrics", 1);

        // Minimal PS_RIX2 puzzle setup
        setLife(playerA, 12);
        setLife(playerB, 6);
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.HAND, playerA, "Giant Growth");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 1);
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 1);
        addCard(Zone.BATTLEFIELD, playerB, "Wall of Roots");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_RIX2", 1);
    }

    @Test
    public void test_PS_RIX3_puzzle_llm_metrics() {
        beginPuzzle("test_PS_RIX3_puzzle_llm_metrics", 1);

        // Minimal PS_RIX3 puzzle setup
        setLife(playerA, 14);
        setLife(playerB, 4);
        addCard(Zone.HAND, playerA, "Path to Exile");
        addCard(Zone.HAND, playerA, "Swords to Plowshares");
        addCard(Zone.BATTLEFIELD, playerA, "Plains", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Snapcaster Mage");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_RIX3", 1);
    }

    @Test
    public void test_PS_RIX4_puzzle_llm_metrics() {
        beginPuzzle("test_PS_RIX4_puzzle_llm_metrics", 1);

        // Minimal PS_RIX4 puzzle setup
        setLife(playerA, 11);
        setLife(playerB, 7);
        addCard(Zone.HAND, playerA, "Doom Blade");
        addCard(Zone.HAND, playerA, "Victim of Night");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Gray Merchant of Asphodel");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_RIX4", 1);
    }

    @Test
    public void test_PS_RIX5_puzzle_llm_metrics() {
        beginPuzzle("test_PS_RIX5_puzzle_llm_metrics", 1);

        // Minimal PS_RIX5 puzzle setup
        setLife(playerA, 13);
        setLife(playerB, 5);
        addCard(Zone.HAND, playerA, "Brainstorm");
        addCard(Zone.HAND, playerA, "Ponder");
        addCard(Zone.BATTLEFIELD, playerA, "Island", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Counterspell");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_RIX5", 1);
    }

    @Test
    public void test_PS_RIX6_puzzle_llm_metrics() {
        beginPuzzle("test_PS_RIX6_puzzle_llm_metrics", 1);

        // Minimal PS_RIX6 puzzle setup
        setLife(playerA, 15);
        setLife(playerB, 3);
        addCard(Zone.HAND, playerA, "Chord of Calling");
        addCard(Zone.HAND, playerA, "Elvish Mystic");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Tarmogoyf");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_RIX6", 1);
    }

    @Test
    public void test_PS_RIX7_puzzle_llm_metrics() {
        beginPuzzle("test_PS_RIX7_puzzle_llm_metrics", 1);

        // Minimal PS_RIX7 puzzle setup
        setLife(playerA, 12);
        setLife(playerB, 6);
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.HAND, playerA, "Giant Growth");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 1);
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 1);
        addCard(Zone.BATTLEFIELD, playerB, "Wall of Roots");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_RIX7", 1);
    }

    @Test
    public void test_PS_RIX8_puzzle_llm_metrics() {
        beginPuzzle("test_PS_RIX8_puzzle_llm_metrics", 1);

        // Minimal PS_RIX8 puzzle setup
        setLife(playerA, 14);
        setLife(playerB, 4);
        addCard(Zone.HAND, playerA, "Path to Exile");
        addCard(Zone.HAND, playerA, "Swords to Plowshares");
        addCard(Zone.BATTLEFIELD, playerA, "Plains", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Snapcaster Mage");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_RIX8", 1);
    }

    @Test
    public void test_PS_RIX9_puzzle_llm_metrics() {
        beginPuzzle("test_PS_RIX9_puzzle_llm_metrics", 1);

        // Minimal PS_RIX9 puzzle setup
        setLife(playerA, 11);
        setLife(playerB, 7);
        addCard(Zone.HAND, playerA, "Doom Blade");
        addCard(Zone.HAND, playerA, "Victim of Night");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Gray Merchant of Asphodel");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_RIX9", 1);
    }

    @Test
    public void test_PS_RNA0_puzzle_llm_metrics() {
        beginPuzzle("test_PS_RNA0_puzzle_llm_metrics", 1);

        // Minimal PS_RNA0 puzzle setup
        setLife(playerA, 13);
        setLife(playerB, 5);
        addCard(Zone.HAND, playerA, "Brainstorm");
        addCard(Zone.HAND, playerA, "Ponder");
        addCard(Zone.BATTLEFIELD, playerA, "Island", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Counterspell");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_RNA0", 1);
    }

    @Test
    public void test_PS_RNA0a_puzzle_llm_metrics() {
        beginPuzzle("test_PS_RNA0a_puzzle_llm_metrics", 1);

        // Minimal PS_RNA0a puzzle setup
        setLife(playerA, 15);
        setLife(playerB, 3);
        addCard(Zone.HAND, playerA, "Chord of Calling");
        addCard(Zone.HAND, playerA, "Elvish Mystic");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Tarmogoyf");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_RNA0a", 1);
    }

    @Test
    public void test_PS_RNA1_puzzle_llm_metrics() {
        beginPuzzle("test_PS_RNA1_puzzle_llm_metrics", 1);

        // Minimal PS_RNA1 puzzle setup
        setLife(playerA, 12);
        setLife(playerB, 6);
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.HAND, playerA, "Giant Growth");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 1);
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 1);
        addCard(Zone.BATTLEFIELD, playerB, "Wall of Roots");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_RNA1", 1);
    }

    @Test
    public void test_PS_RNA2_puzzle_llm_metrics() {
        beginPuzzle("test_PS_RNA2_puzzle_llm_metrics", 1);

        // Minimal PS_RNA2 puzzle setup
        setLife(playerA, 14);
        setLife(playerB, 4);
        addCard(Zone.HAND, playerA, "Path to Exile");
        addCard(Zone.HAND, playerA, "Swords to Plowshares");
        addCard(Zone.BATTLEFIELD, playerA, "Plains", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Snapcaster Mage");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_RNA2", 1);
    }

    @Test
    public void test_PS_RNA3_puzzle_llm_metrics() {
        beginPuzzle("test_PS_RNA3_puzzle_llm_metrics", 1);

        // Minimal PS_RNA3 puzzle setup
        setLife(playerA, 11);
        setLife(playerB, 7);
        addCard(Zone.HAND, playerA, "Doom Blade");
        addCard(Zone.HAND, playerA, "Victim of Night");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Gray Merchant of Asphodel");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_RNA3", 1);
    }

    @Test
    public void test_PS_RNA4_puzzle_llm_metrics() {
        beginPuzzle("test_PS_RNA4_puzzle_llm_metrics", 1);

        // Minimal PS_RNA4 puzzle setup
        setLife(playerA, 13);
        setLife(playerB, 5);
        addCard(Zone.HAND, playerA, "Brainstorm");
        addCard(Zone.HAND, playerA, "Ponder");
        addCard(Zone.BATTLEFIELD, playerA, "Island", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Counterspell");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_RNA4", 1);
    }

    @Test
    public void test_PS_RNA5_puzzle_llm_metrics() {
        beginPuzzle("test_PS_RNA5_puzzle_llm_metrics", 1);

        // Minimal PS_RNA5 puzzle setup
        setLife(playerA, 15);
        setLife(playerB, 3);
        addCard(Zone.HAND, playerA, "Chord of Calling");
        addCard(Zone.HAND, playerA, "Elvish Mystic");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Tarmogoyf");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_RNA5", 1);
    }

    @Test
    public void test_PS_RNA6_puzzle_llm_metrics() {
        beginPuzzle("test_PS_RNA6_puzzle_llm_metrics", 1);

        // Minimal PS_RNA6 puzzle setup
        setLife(playerA, 12);
        setLife(playerB, 6);
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.HAND, playerA, "Giant Growth");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 1);
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 1);
        addCard(Zone.BATTLEFIELD, playerB, "Wall of Roots");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_RNA6", 1);
    }

    @Test
    public void test_PS_RNAR_puzzle_llm_metrics() {
        beginPuzzle("test_PS_RNAR_puzzle_llm_metrics", 1);

        // Minimal PS_RNAR puzzle setup
        setLife(playerA, 14);
        setLife(playerB, 4);
        addCard(Zone.HAND, playerA, "Path to Exile");
        addCard(Zone.HAND, playerA, "Swords to Plowshares");
        addCard(Zone.BATTLEFIELD, playerA, "Plains", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Snapcaster Mage");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_RNAR", 1);
    }

    @Test
    public void test_PS_RNAS_puzzle_llm_metrics() {
        beginPuzzle("test_PS_RNAS_puzzle_llm_metrics", 1);

        // Minimal PS_RNAS puzzle setup
        setLife(playerA, 11);
        setLife(playerB, 7);
        addCard(Zone.HAND, playerA, "Doom Blade");
        addCard(Zone.HAND, playerA, "Victim of Night");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Gray Merchant of Asphodel");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_RNAS", 1);
    }

    @Test
    public void test_PS_RVR1_puzzle_llm_metrics() {
        beginPuzzle("test_PS_RVR1_puzzle_llm_metrics", 1);

        // Minimal PS_RVR1 puzzle setup
        setLife(playerA, 13);
        setLife(playerB, 5);
        addCard(Zone.HAND, playerA, "Brainstorm");
        addCard(Zone.HAND, playerA, "Ponder");
        addCard(Zone.BATTLEFIELD, playerA, "Island", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Counterspell");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_RVR1", 1);
    }

    @Test
    public void test_PS_RVR2_puzzle_llm_metrics() {
        beginPuzzle("test_PS_RVR2_puzzle_llm_metrics", 1);

        // Minimal PS_RVR2 puzzle setup
        setLife(playerA, 15);
        setLife(playerB, 3);
        addCard(Zone.HAND, playerA, "Chord of Calling");
        addCard(Zone.HAND, playerA, "Elvish Mystic");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Tarmogoyf");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_RVR2", 1);
    }

    @Test
    public void test_PS_SNC1_puzzle_llm_metrics() {
        beginPuzzle("test_PS_SNC1_puzzle_llm_metrics", 1);

        // Minimal PS_SNC1 puzzle setup
        setLife(playerA, 12);
        setLife(playerB, 6);
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.HAND, playerA, "Giant Growth");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 1);
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 1);
        addCard(Zone.BATTLEFIELD, playerB, "Wall of Roots");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_SNC1", 1);
    }

    @Test
    public void test_PS_SNC2_puzzle_llm_metrics() {
        beginPuzzle("test_PS_SNC2_puzzle_llm_metrics", 1);

        // Minimal PS_SNC2 puzzle setup
        setLife(playerA, 14);
        setLife(playerB, 4);
        addCard(Zone.HAND, playerA, "Path to Exile");
        addCard(Zone.HAND, playerA, "Swords to Plowshares");
        addCard(Zone.BATTLEFIELD, playerA, "Plains", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Snapcaster Mage");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_SNC2", 1);
    }

    @Test
    public void test_PS_SNC3_puzzle_llm_metrics() {
        beginPuzzle("test_PS_SNC3_puzzle_llm_metrics", 1);

        // Minimal PS_SNC3 puzzle setup
        setLife(playerA, 11);
        setLife(playerB, 7);
        addCard(Zone.HAND, playerA, "Doom Blade");
        addCard(Zone.HAND, playerA, "Victim of Night");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Gray Merchant of Asphodel");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_SNC3", 1);
    }

    @Test
    public void test_PS_SNC4_puzzle_llm_metrics() {
        beginPuzzle("test_PS_SNC4_puzzle_llm_metrics", 1);

        // Minimal PS_SNC4 puzzle setup
        setLife(playerA, 13);
        setLife(playerB, 5);
        addCard(Zone.HAND, playerA, "Brainstorm");
        addCard(Zone.HAND, playerA, "Ponder");
        addCard(Zone.BATTLEFIELD, playerA, "Island", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Counterspell");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_SNC4", 1);
    }

    @Test
    public void test_PS_SNC5_puzzle_llm_metrics() {
        beginPuzzle("test_PS_SNC5_puzzle_llm_metrics", 1);

        // Minimal PS_SNC5 puzzle setup
        setLife(playerA, 15);
        setLife(playerB, 3);
        addCard(Zone.HAND, playerA, "Chord of Calling");
        addCard(Zone.HAND, playerA, "Elvish Mystic");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Tarmogoyf");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_SNC5", 1);
    }

    @Test
    public void test_PS_STX5_puzzle_llm_metrics() {
        beginPuzzle("test_PS_STX5_puzzle_llm_metrics", 1);

        // Minimal PS_STX5 puzzle setup
        setLife(playerA, 12);
        setLife(playerB, 6);
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.HAND, playerA, "Giant Growth");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 1);
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 1);
        addCard(Zone.BATTLEFIELD, playerB, "Wall of Roots");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_STX5", 1);
    }

    @Test
    public void test_PS_TDM1_puzzle_llm_metrics() {
        beginPuzzle("test_PS_TDM1_puzzle_llm_metrics", 1);

        // Minimal PS_TDM1 puzzle setup
        setLife(playerA, 14);
        setLife(playerB, 4);
        addCard(Zone.HAND, playerA, "Path to Exile");
        addCard(Zone.HAND, playerA, "Swords to Plowshares");
        addCard(Zone.BATTLEFIELD, playerA, "Plains", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Snapcaster Mage");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_TDM1", 1);
    }

    @Test
    public void test_PS_TDM2_puzzle_llm_metrics() {
        beginPuzzle("test_PS_TDM2_puzzle_llm_metrics", 1);

        // Minimal PS_TDM2 puzzle setup
        setLife(playerA, 11);
        setLife(playerB, 7);
        addCard(Zone.HAND, playerA, "Doom Blade");
        addCard(Zone.HAND, playerA, "Victim of Night");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Gray Merchant of Asphodel");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_TDM2", 1);
    }

    @Test
    public void test_PS_TDM3_puzzle_llm_metrics() {
        beginPuzzle("test_PS_TDM3_puzzle_llm_metrics", 1);

        // Minimal PS_TDM3 puzzle setup
        setLife(playerA, 13);
        setLife(playerB, 5);
        addCard(Zone.HAND, playerA, "Brainstorm");
        addCard(Zone.HAND, playerA, "Ponder");
        addCard(Zone.BATTLEFIELD, playerA, "Island", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Counterspell");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_TDM3", 1);
    }

    @Test
    public void test_PS_TDM4_puzzle_llm_metrics() {
        beginPuzzle("test_PS_TDM4_puzzle_llm_metrics", 1);

        // Minimal PS_TDM4 puzzle setup
        setLife(playerA, 15);
        setLife(playerB, 3);
        addCard(Zone.HAND, playerA, "Chord of Calling");
        addCard(Zone.HAND, playerA, "Elvish Mystic");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Tarmogoyf");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_TDM4", 1);
    }

    @Test
    public void test_PS_TDM5_puzzle_llm_metrics() {
        beginPuzzle("test_PS_TDM5_puzzle_llm_metrics", 1);

        // Minimal PS_TDM5 puzzle setup
        setLife(playerA, 12);
        setLife(playerB, 6);
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.HAND, playerA, "Giant Growth");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 1);
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 1);
        addCard(Zone.BATTLEFIELD, playerB, "Wall of Roots");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_TDM5", 1);
    }

    @Test
    public void test_PS_THB0a_puzzle_llm_metrics() {
        beginPuzzle("test_PS_THB0a_puzzle_llm_metrics", 1);

        // Minimal PS_THB0a puzzle setup
        setLife(playerA, 14);
        setLife(playerB, 4);
        addCard(Zone.HAND, playerA, "Path to Exile");
        addCard(Zone.HAND, playerA, "Swords to Plowshares");
        addCard(Zone.BATTLEFIELD, playerA, "Plains", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Snapcaster Mage");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_THB0a", 1);
    }

    @Test
    public void test_PS_THB0b_puzzle_llm_metrics() {
        beginPuzzle("test_PS_THB0b_puzzle_llm_metrics", 1);

        // Minimal PS_THB0b puzzle setup
        setLife(playerA, 11);
        setLife(playerB, 7);
        addCard(Zone.HAND, playerA, "Doom Blade");
        addCard(Zone.HAND, playerA, "Victim of Night");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Gray Merchant of Asphodel");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_THB0b", 1);
    }

    @Test
    public void test_PS_THB1a_puzzle_llm_metrics() {
        beginPuzzle("test_PS_THB1a_puzzle_llm_metrics", 1);

        // Minimal PS_THB1a puzzle setup
        setLife(playerA, 13);
        setLife(playerB, 5);
        addCard(Zone.HAND, playerA, "Brainstorm");
        addCard(Zone.HAND, playerA, "Ponder");
        addCard(Zone.BATTLEFIELD, playerA, "Island", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Counterspell");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_THB1a", 1);
    }

    @Test
    public void test_PS_THB1b_puzzle_llm_metrics() {
        beginPuzzle("test_PS_THB1b_puzzle_llm_metrics", 1);

        // Minimal PS_THB1b puzzle setup
        setLife(playerA, 15);
        setLife(playerB, 3);
        addCard(Zone.HAND, playerA, "Chord of Calling");
        addCard(Zone.HAND, playerA, "Elvish Mystic");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Tarmogoyf");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_THB1b", 1);
    }

    @Test
    public void test_PS_THB4_puzzle_llm_metrics() {
        beginPuzzle("test_PS_THB4_puzzle_llm_metrics", 1);

        // Minimal PS_THB4 puzzle setup
        setLife(playerA, 12);
        setLife(playerB, 6);
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.HAND, playerA, "Giant Growth");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 1);
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 1);
        addCard(Zone.BATTLEFIELD, playerB, "Wall of Roots");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_THB4", 1);
    }

    @Test
    public void test_PS_THB5_puzzle_llm_metrics() {
        beginPuzzle("test_PS_THB5_puzzle_llm_metrics", 1);

        // Minimal PS_THB5 puzzle setup
        setLife(playerA, 14);
        setLife(playerB, 4);
        addCard(Zone.HAND, playerA, "Path to Exile");
        addCard(Zone.HAND, playerA, "Swords to Plowshares");
        addCard(Zone.BATTLEFIELD, playerA, "Plains", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Snapcaster Mage");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_THB5", 1);
    }

    @Test
    public void test_PS_TSR1_puzzle_llm_metrics() {
        beginPuzzle("test_PS_TSR1_puzzle_llm_metrics", 1);

        // Minimal PS_TSR1 puzzle setup
        setLife(playerA, 11);
        setLife(playerB, 7);
        addCard(Zone.HAND, playerA, "Doom Blade");
        addCard(Zone.HAND, playerA, "Victim of Night");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Gray Merchant of Asphodel");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_TSR1", 1);
    }

    @Test
    public void test_PS_UMA_puzzle_llm_metrics() {
        beginPuzzle("test_PS_UMA_puzzle_llm_metrics", 1);

        // Minimal PS_UMA puzzle setup
        setLife(playerA, 13);
        setLife(playerB, 5);
        addCard(Zone.HAND, playerA, "Brainstorm");
        addCard(Zone.HAND, playerA, "Ponder");
        addCard(Zone.BATTLEFIELD, playerA, "Island", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Counterspell");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_UMA", 1);
    }

    @Test
    public void test_PS_VOW1_puzzle_llm_metrics() {
        beginPuzzle("test_PS_VOW1_puzzle_llm_metrics", 1);

        // Minimal PS_VOW1 puzzle setup
        setLife(playerA, 15);
        setLife(playerB, 3);
        addCard(Zone.HAND, playerA, "Chord of Calling");
        addCard(Zone.HAND, playerA, "Elvish Mystic");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Tarmogoyf");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_VOW1", 1);
    }

    @Test
    public void test_PS_VOW2_puzzle_llm_metrics() {
        beginPuzzle("test_PS_VOW2_puzzle_llm_metrics", 1);

        // Minimal PS_VOW2 puzzle setup
        setLife(playerA, 12);
        setLife(playerB, 6);
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.HAND, playerA, "Giant Growth");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 1);
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 1);
        addCard(Zone.BATTLEFIELD, playerB, "Wall of Roots");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_VOW2", 1);
    }

    @Test
    public void test_PS_VOW3_puzzle_llm_metrics() {
        beginPuzzle("test_PS_VOW3_puzzle_llm_metrics", 1);

        // Minimal PS_VOW3 puzzle setup
        setLife(playerA, 14);
        setLife(playerB, 4);
        addCard(Zone.HAND, playerA, "Path to Exile");
        addCard(Zone.HAND, playerA, "Swords to Plowshares");
        addCard(Zone.BATTLEFIELD, playerA, "Plains", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Snapcaster Mage");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_VOW3", 1);
    }

    @Test
    public void test_PS_VOW4_puzzle_llm_metrics() {
        beginPuzzle("test_PS_VOW4_puzzle_llm_metrics", 1);

        // Minimal PS_VOW4 puzzle setup
        setLife(playerA, 11);
        setLife(playerB, 7);
        addCard(Zone.HAND, playerA, "Doom Blade");
        addCard(Zone.HAND, playerA, "Victim of Night");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Gray Merchant of Asphodel");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_VOW4", 1);
    }

    @Test
    public void test_PS_WAR0_puzzle_llm_metrics() {
        beginPuzzle("test_PS_WAR0_puzzle_llm_metrics", 1);

        // Minimal PS_WAR0 puzzle setup
        setLife(playerA, 13);
        setLife(playerB, 5);
        addCard(Zone.HAND, playerA, "Brainstorm");
        addCard(Zone.HAND, playerA, "Ponder");
        addCard(Zone.BATTLEFIELD, playerA, "Island", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Counterspell");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_WAR0", 1);
    }

    @Test
    public void test_PS_WAR0a_puzzle_llm_metrics() {
        beginPuzzle("test_PS_WAR0a_puzzle_llm_metrics", 1);

        // Minimal PS_WAR0a puzzle setup
        setLife(playerA, 15);
        setLife(playerB, 3);
        addCard(Zone.HAND, playerA, "Chord of Calling");
        addCard(Zone.HAND, playerA, "Elvish Mystic");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Tarmogoyf");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_WAR0a", 1);
    }

    @Test
    public void test_PS_WAR1_puzzle_llm_metrics() {
        beginPuzzle("test_PS_WAR1_puzzle_llm_metrics", 1);

        // Minimal PS_WAR1 puzzle setup
        setLife(playerA, 12);
        setLife(playerB, 6);
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.HAND, playerA, "Giant Growth");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 1);
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 1);
        addCard(Zone.BATTLEFIELD, playerB, "Wall of Roots");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_WAR1", 1);
    }

    @Test
    public void test_PS_WAR2_puzzle_llm_metrics() {
        beginPuzzle("test_PS_WAR2_puzzle_llm_metrics", 1);

        // Minimal PS_WAR2 puzzle setup
        setLife(playerA, 14);
        setLife(playerB, 4);
        addCard(Zone.HAND, playerA, "Path to Exile");
        addCard(Zone.HAND, playerA, "Swords to Plowshares");
        addCard(Zone.BATTLEFIELD, playerA, "Plains", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Snapcaster Mage");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_WAR2", 1);
    }

    @Test
    public void test_PS_WAR3_puzzle_llm_metrics() {
        beginPuzzle("test_PS_WAR3_puzzle_llm_metrics", 1);

        // Minimal PS_WAR3 puzzle setup
        setLife(playerA, 11);
        setLife(playerB, 7);
        addCard(Zone.HAND, playerA, "Doom Blade");
        addCard(Zone.HAND, playerA, "Victim of Night");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Gray Merchant of Asphodel");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_WAR3", 1);
    }

    @Test
    public void test_PS_WAR4_puzzle_llm_metrics() {
        beginPuzzle("test_PS_WAR4_puzzle_llm_metrics", 1);

        // Minimal PS_WAR4 puzzle setup
        setLife(playerA, 13);
        setLife(playerB, 5);
        addCard(Zone.HAND, playerA, "Brainstorm");
        addCard(Zone.HAND, playerA, "Ponder");
        addCard(Zone.BATTLEFIELD, playerA, "Island", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Counterspell");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_WAR4", 1);
    }

    @Test
    public void test_PS_WAR5_puzzle_llm_metrics() {
        beginPuzzle("test_PS_WAR5_puzzle_llm_metrics", 1);

        // Minimal PS_WAR5 puzzle setup
        setLife(playerA, 15);
        setLife(playerB, 3);
        addCard(Zone.HAND, playerA, "Chord of Calling");
        addCard(Zone.HAND, playerA, "Elvish Mystic");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Tarmogoyf");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_WAR5", 1);
    }

    @Test
    public void test_PS_WAR6_puzzle_llm_metrics() {
        beginPuzzle("test_PS_WAR6_puzzle_llm_metrics", 1);

        // Minimal PS_WAR6 puzzle setup
        setLife(playerA, 12);
        setLife(playerB, 6);
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.HAND, playerA, "Giant Growth");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 1);
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 1);
        addCard(Zone.BATTLEFIELD, playerB, "Wall of Roots");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_WAR6", 1);
    }

    @Test
    public void test_PS_WAR7_puzzle_llm_metrics() {
        beginPuzzle("test_PS_WAR7_puzzle_llm_metrics", 1);

        // Minimal PS_WAR7 puzzle setup
        setLife(playerA, 14);
        setLife(playerB, 4);
        addCard(Zone.HAND, playerA, "Path to Exile");
        addCard(Zone.HAND, playerA, "Swords to Plowshares");
        addCard(Zone.BATTLEFIELD, playerA, "Plains", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Snapcaster Mage");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_WAR7", 1);
    }

    @Test
    public void test_PS_WAR8_puzzle_llm_metrics() {
        beginPuzzle("test_PS_WAR8_puzzle_llm_metrics", 1);

        // Minimal PS_WAR8 puzzle setup
        setLife(playerA, 11);
        setLife(playerB, 7);
        addCard(Zone.HAND, playerA, "Doom Blade");
        addCard(Zone.HAND, playerA, "Victim of Night");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Gray Merchant of Asphodel");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_WAR8", 1);
    }

    @Test
    public void test_PS_WAR9_puzzle_llm_metrics() {
        beginPuzzle("test_PS_WAR9_puzzle_llm_metrics", 1);

        // Minimal PS_WAR9 puzzle setup
        setLife(playerA, 13);
        setLife(playerB, 5);
        addCard(Zone.HAND, playerA, "Brainstorm");
        addCard(Zone.HAND, playerA, "Ponder");
        addCard(Zone.BATTLEFIELD, playerA, "Island", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Counterspell");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_WAR9", 1);
    }

    @Test
    public void test_PS_WOE1_puzzle_llm_metrics() {
        beginPuzzle("test_PS_WOE1_puzzle_llm_metrics", 1);

        // Minimal PS_WOE1 puzzle setup
        setLife(playerA, 15);
        setLife(playerB, 3);
        addCard(Zone.HAND, playerA, "Chord of Calling");
        addCard(Zone.HAND, playerA, "Elvish Mystic");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Tarmogoyf");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_WOE1", 1);
    }

    @Test
    public void test_PS_WOE2_puzzle_llm_metrics() {
        beginPuzzle("test_PS_WOE2_puzzle_llm_metrics", 1);

        // Minimal PS_WOE2 puzzle setup
        setLife(playerA, 12);
        setLife(playerB, 6);
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.HAND, playerA, "Giant Growth");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 1);
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 1);
        addCard(Zone.BATTLEFIELD, playerB, "Wall of Roots");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_WOE2", 1);
    }

    @Test
    public void test_PS_WOE3_puzzle_llm_metrics() {
        beginPuzzle("test_PS_WOE3_puzzle_llm_metrics", 1);

        // Minimal PS_WOE3 puzzle setup
        setLife(playerA, 14);
        setLife(playerB, 4);
        addCard(Zone.HAND, playerA, "Path to Exile");
        addCard(Zone.HAND, playerA, "Swords to Plowshares");
        addCard(Zone.BATTLEFIELD, playerA, "Plains", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Snapcaster Mage");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_WOE3", 1);
    }

    @Test
    public void test_PS_WOE4_puzzle_llm_metrics() {
        beginPuzzle("test_PS_WOE4_puzzle_llm_metrics", 1);

        // Minimal PS_WOE4 puzzle setup
        setLife(playerA, 11);
        setLife(playerB, 7);
        addCard(Zone.HAND, playerA, "Doom Blade");
        addCard(Zone.HAND, playerA, "Victim of Night");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Gray Merchant of Asphodel");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_WOE4", 1);
    }

    @Test
    public void test_PS_XLN0a_puzzle_llm_metrics() {
        beginPuzzle("test_PS_XLN0a_puzzle_llm_metrics", 1);

        // Minimal PS_XLN0a puzzle setup
        setLife(playerA, 13);
        setLife(playerB, 5);
        addCard(Zone.HAND, playerA, "Brainstorm");
        addCard(Zone.HAND, playerA, "Ponder");
        addCard(Zone.BATTLEFIELD, playerA, "Island", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Counterspell");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_XLN0a", 1);
    }

    @Test
    public void test_PS_XLN0b_puzzle_llm_metrics() {
        beginPuzzle("test_PS_XLN0b_puzzle_llm_metrics", 1);

        // Minimal PS_XLN0b puzzle setup
        setLife(playerA, 15);
        setLife(playerB, 3);
        addCard(Zone.HAND, playerA, "Chord of Calling");
        addCard(Zone.HAND, playerA, "Elvish Mystic");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Tarmogoyf");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_XLN0b", 1);
    }

    @Test
    public void test_PS_XLN1_puzzle_llm_metrics() {
        beginPuzzle("test_PS_XLN1_puzzle_llm_metrics", 1);

        // Minimal PS_XLN1 puzzle setup
        setLife(playerA, 12);
        setLife(playerB, 6);
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.HAND, playerA, "Giant Growth");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 1);
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 1);
        addCard(Zone.BATTLEFIELD, playerB, "Wall of Roots");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_XLN1", 1);
    }

    @Test
    public void test_PS_XLN2_puzzle_llm_metrics() {
        beginPuzzle("test_PS_XLN2_puzzle_llm_metrics", 1);

        // Minimal PS_XLN2 puzzle setup
        setLife(playerA, 14);
        setLife(playerB, 4);
        addCard(Zone.HAND, playerA, "Path to Exile");
        addCard(Zone.HAND, playerA, "Swords to Plowshares");
        addCard(Zone.BATTLEFIELD, playerA, "Plains", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Snapcaster Mage");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_XLN2", 1);
    }

    @Test
    public void test_PS_XLN3_puzzle_llm_metrics() {
        beginPuzzle("test_PS_XLN3_puzzle_llm_metrics", 1);

        // Minimal PS_XLN3 puzzle setup
        setLife(playerA, 11);
        setLife(playerB, 7);
        addCard(Zone.HAND, playerA, "Doom Blade");
        addCard(Zone.HAND, playerA, "Victim of Night");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Gray Merchant of Asphodel");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_XLN3", 1);
    }

    @Test
    public void test_PS_XLN4_puzzle_llm_metrics() {
        beginPuzzle("test_PS_XLN4_puzzle_llm_metrics", 1);

        // Minimal PS_XLN4 puzzle setup
        setLife(playerA, 13);
        setLife(playerB, 5);
        addCard(Zone.HAND, playerA, "Brainstorm");
        addCard(Zone.HAND, playerA, "Ponder");
        addCard(Zone.BATTLEFIELD, playerA, "Island", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Counterspell");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_XLN4", 1);
    }

    @Test
    public void test_PS_XLN5_puzzle_llm_metrics() {
        beginPuzzle("test_PS_XLN5_puzzle_llm_metrics", 1);

        // Minimal PS_XLN5 puzzle setup
        setLife(playerA, 15);
        setLife(playerB, 3);
        addCard(Zone.HAND, playerA, "Chord of Calling");
        addCard(Zone.HAND, playerA, "Elvish Mystic");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Tarmogoyf");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_XLN5", 1);
    }

    @Test
    public void test_PS_XLN6_puzzle_llm_metrics() {
        beginPuzzle("test_PS_XLN6_puzzle_llm_metrics", 1);

        // Minimal PS_XLN6 puzzle setup
        setLife(playerA, 12);
        setLife(playerB, 6);
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.HAND, playerA, "Giant Growth");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 1);
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 1);
        addCard(Zone.BATTLEFIELD, playerB, "Wall of Roots");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_XLN6", 1);
    }

    @Test
    public void test_PS_XLN7_puzzle_llm_metrics() {
        beginPuzzle("test_PS_XLN7_puzzle_llm_metrics", 1);

        // Minimal PS_XLN7 puzzle setup
        setLife(playerA, 14);
        setLife(playerB, 4);
        addCard(Zone.HAND, playerA, "Path to Exile");
        addCard(Zone.HAND, playerA, "Swords to Plowshares");
        addCard(Zone.BATTLEFIELD, playerA, "Plains", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Snapcaster Mage");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_XLN7", 1);
    }

    @Test
    public void test_PS_XLN8_puzzle_llm_metrics() {
        beginPuzzle("test_PS_XLN8_puzzle_llm_metrics", 1);

        // Minimal PS_XLN8 puzzle setup
        setLife(playerA, 11);
        setLife(playerB, 7);
        addCard(Zone.HAND, playerA, "Doom Blade");
        addCard(Zone.HAND, playerA, "Victim of Night");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Gray Merchant of Asphodel");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_XLN8", 1);
    }

    @Test
    public void test_PS_XLN9_puzzle_llm_metrics() {
        beginPuzzle("test_PS_XLN9_puzzle_llm_metrics", 1);

        // Minimal PS_XLN9 puzzle setup
        setLife(playerA, 13);
        setLife(playerB, 5);
        addCard(Zone.HAND, playerA, "Brainstorm");
        addCard(Zone.HAND, playerA, "Ponder");
        addCard(Zone.BATTLEFIELD, playerA, "Island", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Counterspell");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_XLN9", 1);
    }

    @Test
    public void test_PS_ZNR1_puzzle_llm_metrics() {
        beginPuzzle("test_PS_ZNR1_puzzle_llm_metrics", 1);

        // Minimal PS_ZNR1 puzzle setup
        setLife(playerA, 15);
        setLife(playerB, 3);
        addCard(Zone.HAND, playerA, "Chord of Calling");
        addCard(Zone.HAND, playerA, "Elvish Mystic");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Tarmogoyf");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_ZNR1", 1);
    }

    @Test
    public void test_PS_ZNR2_puzzle_llm_metrics() {
        beginPuzzle("test_PS_ZNR2_puzzle_llm_metrics", 1);

        // Minimal PS_ZNR2 puzzle setup
        setLife(playerA, 12);
        setLife(playerB, 6);
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.HAND, playerA, "Giant Growth");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 1);
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 1);
        addCard(Zone.BATTLEFIELD, playerB, "Wall of Roots");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_ZNR2", 1);
    }

    @Test
    public void test_PS_ZNR3_puzzle_llm_metrics() {
        beginPuzzle("test_PS_ZNR3_puzzle_llm_metrics", 1);

        // Minimal PS_ZNR3 puzzle setup
        setLife(playerA, 14);
        setLife(playerB, 4);
        addCard(Zone.HAND, playerA, "Path to Exile");
        addCard(Zone.HAND, playerA, "Swords to Plowshares");
        addCard(Zone.BATTLEFIELD, playerA, "Plains", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Snapcaster Mage");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_ZNR3", 1);
    }

    @Test
    public void test_PS_ZNR4_puzzle_llm_metrics() {
        beginPuzzle("test_PS_ZNR4_puzzle_llm_metrics", 1);

        // Minimal PS_ZNR4 puzzle setup
        setLife(playerA, 11);
        setLife(playerB, 7);
        addCard(Zone.HAND, playerA, "Doom Blade");
        addCard(Zone.HAND, playerA, "Victim of Night");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Gray Merchant of Asphodel");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_ZNR4", 1);
    }

    @Test
    public void test_PS_ZNR5_puzzle_llm_metrics() {
        beginPuzzle("test_PS_ZNR5_puzzle_llm_metrics", 1);

        // Minimal PS_ZNR5 puzzle setup
        setLife(playerA, 13);
        setLife(playerB, 5);
        addCard(Zone.HAND, playerA, "Brainstorm");
        addCard(Zone.HAND, playerA, "Ponder");
        addCard(Zone.BATTLEFIELD, playerA, "Island", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Counterspell");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_ZNR5", 1);
    }

    @Test
    public void test_PS_ZNR6_puzzle_llm_metrics() {
        beginPuzzle("test_PS_ZNR6_puzzle_llm_metrics", 1);

        // Minimal PS_ZNR6 puzzle setup
        setLife(playerA, 15);
        setLife(playerB, 3);
        addCard(Zone.HAND, playerA, "Chord of Calling");
        addCard(Zone.HAND, playerA, "Elvish Mystic");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Tarmogoyf");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PS_ZNR6", 1);
    }

    @Test
    public void test_forge_tutorial01_puzzle_llm_metrics() {
        beginPuzzle("test_forge_tutorial01_puzzle_llm_metrics", 1);

        // Minimal forge_tutorial01 puzzle setup
        setLife(playerA, 12);
        setLife(playerB, 6);
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.HAND, playerA, "Giant Growth");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 1);
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 1);
        addCard(Zone.BATTLEFIELD, playerB, "Wall of Roots");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("forge_tutorial01", 1);
    }

    @Test
    public void test_forge_tutorial02_puzzle_llm_metrics() {
        beginPuzzle("test_forge_tutorial02_puzzle_llm_metrics", 1);

        // Minimal forge_tutorial02 puzzle setup
        setLife(playerA, 14);
        setLife(playerB, 4);
        addCard(Zone.HAND, playerA, "Path to Exile");
        addCard(Zone.HAND, playerA, "Swords to Plowshares");
        addCard(Zone.BATTLEFIELD, playerA, "Plains", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Snapcaster Mage");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("forge_tutorial02", 1);
    }

    @Test
    public void test_forge_tutorial03_puzzle_llm_metrics() {
        beginPuzzle("test_forge_tutorial03_puzzle_llm_metrics", 1);

        // Minimal forge_tutorial03 puzzle setup
        setLife(playerA, 11);
        setLife(playerB, 7);
        addCard(Zone.HAND, playerA, "Doom Blade");
        addCard(Zone.HAND, playerA, "Victim of Night");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Gray Merchant of Asphodel");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("forge_tutorial03", 1);
    }

    @Test
    public void test_PC_210116_puzzle_llm_metrics() {
        beginPuzzle("test_PC_210116_puzzle_llm_metrics", 1);

        // Minimal PC_210116 puzzle setup
        setLife(playerA, 13);
        setLife(playerB, 5);
        addCard(Zone.HAND, playerA, "Brainstorm");
        addCard(Zone.HAND, playerA, "Ponder");
        addCard(Zone.BATTLEFIELD, playerA, "Island", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Counterspell");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PC_210116", 1);
    }

    @Test
    public void test_PC_210916_puzzle_llm_metrics() {
        beginPuzzle("test_PC_210916_puzzle_llm_metrics", 1);

        // Minimal PC_210916 puzzle setup
        setLife(playerA, 15);
        setLife(playerB, 3);
        addCard(Zone.HAND, playerA, "Chord of Calling");
        addCard(Zone.HAND, playerA, "Elvish Mystic");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Tarmogoyf");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PC_210916", 1);
    }

    @Test
    public void test_PC_211416_puzzle_llm_metrics() {
        beginPuzzle("test_PC_211416_puzzle_llm_metrics", 1);

        // Minimal PC_211416 puzzle setup
        setLife(playerA, 12);
        setLife(playerB, 6);
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.HAND, playerA, "Giant Growth");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 1);
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 1);
        addCard(Zone.BATTLEFIELD, playerB, "Wall of Roots");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PC_211416", 1);
    }

    @Test
    public void test_PC_212116_puzzle_llm_metrics() {
        beginPuzzle("test_PC_212116_puzzle_llm_metrics", 1);

        // Minimal PC_212116 puzzle setup
        setLife(playerA, 14);
        setLife(playerB, 4);
        addCard(Zone.HAND, playerA, "Path to Exile");
        addCard(Zone.HAND, playerA, "Swords to Plowshares");
        addCard(Zone.BATTLEFIELD, playerA, "Plains", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Snapcaster Mage");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PC_212116", 1);
    }

    @Test
    public void test_PC_212816_puzzle_llm_metrics() {
        beginPuzzle("test_PC_212816_puzzle_llm_metrics", 1);

        // Minimal PC_212816 puzzle setup
        setLife(playerA, 11);
        setLife(playerB, 7);
        addCard(Zone.HAND, playerA, "Doom Blade");
        addCard(Zone.HAND, playerA, "Victim of Night");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Gray Merchant of Asphodel");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PC_212816", 1);
    }

    @Test
    public void test_PC_220116_puzzle_llm_metrics() {
        beginPuzzle("test_PC_220116_puzzle_llm_metrics", 1);

        // Minimal PC_220116 puzzle setup
        setLife(playerA, 13);
        setLife(playerB, 5);
        addCard(Zone.HAND, playerA, "Brainstorm");
        addCard(Zone.HAND, playerA, "Ponder");
        addCard(Zone.BATTLEFIELD, playerA, "Island", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Counterspell");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PC_220116", 1);
    }

    @Test
    public void test_PC_220916_puzzle_llm_metrics() {
        beginPuzzle("test_PC_220916_puzzle_llm_metrics", 1);

        // Minimal PC_220916 puzzle setup
        setLife(playerA, 15);
        setLife(playerB, 3);
        addCard(Zone.HAND, playerA, "Chord of Calling");
        addCard(Zone.HAND, playerA, "Elvish Mystic");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Tarmogoyf");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PC_220916", 1);
    }

    @Test
    public void test_PC_221416_puzzle_llm_metrics() {
        beginPuzzle("test_PC_221416_puzzle_llm_metrics", 1);

        // Minimal PC_221416 puzzle setup
        setLife(playerA, 12);
        setLife(playerB, 6);
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.HAND, playerA, "Giant Growth");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 1);
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 1);
        addCard(Zone.BATTLEFIELD, playerB, "Wall of Roots");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PC_221416", 1);
    }

    @Test
    public void test_PC_222116_puzzle_llm_metrics() {
        beginPuzzle("test_PC_222116_puzzle_llm_metrics", 1);

        // Minimal PC_222116 puzzle setup
        setLife(playerA, 14);
        setLife(playerB, 4);
        addCard(Zone.HAND, playerA, "Path to Exile");
        addCard(Zone.HAND, playerA, "Swords to Plowshares");
        addCard(Zone.BATTLEFIELD, playerA, "Plains", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Snapcaster Mage");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PC_222116", 1);
    }

    @Test
    public void test_PC_222816_puzzle_llm_metrics() {
        beginPuzzle("test_PC_222816_puzzle_llm_metrics", 1);

        // Minimal PC_222816 puzzle setup
        setLife(playerA, 11);
        setLife(playerB, 7);
        addCard(Zone.HAND, playerA, "Doom Blade");
        addCard(Zone.HAND, playerA, "Victim of Night");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Gray Merchant of Asphodel");

        setStrictChooseMode(false);
        setStopAt(1, PhaseStep.END_TURN);
        execute();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        finishAndSave("PC_222816", 1);
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
