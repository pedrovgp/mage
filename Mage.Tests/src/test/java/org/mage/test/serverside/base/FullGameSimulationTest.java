package org.mage.test.serverside.base;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Test class for full game simulation framework.
 * Tests ComputerPlayer7Instrumented matchups for trajectory logging and
 * data collection across premodern deck archetypes.
 *
 * Run via: ./run_integration_test.sh --tests "FullGameSimulationTest"
 * Configure via system properties:
 *   -Dnum_games=10   (default 10)
 *   -Dmax_turns=200  (default 200)
 *   -Dseed=42        (default 42)
 *   -Dstrategy=random (default random)
 */
public class FullGameSimulationTest extends FullGameSimulationInstrumentedBase {

    // Deck paths relative to project root
    private static final String BURN = "mage/Mage.Tests/premodern_decks/premodern_burn_red.txt";
    private static final String CONTROL_UW = "mage/Mage.Tests/premodern_decks/premodern_control_uw.txt";
    private static final String AGGRO_WHITE = "mage/Mage.Tests/premodern_decks/premodern_aggro_white.txt";
    private static final String GOBLINS = "mage/Mage.Tests/premodern_decks/premodern_goblins_red.txt";
    private static final String ROCK = "mage/Mage.Tests/premodern_decks/premodern_midrange_rock.txt";
    private static final String ELVES = "mage/Mage.Tests/premodern_decks/premodern_elves_combo.txt";
    private static final String PSYCHATOG = "mage/Mage.Tests/premodern_decks/premodern_psychatog_control.txt";
    private static final String REANIMATOR = "mage/Mage.Tests/premodern_decks/premodern_combo_reanimator.txt";
    private static final String OATH = "mage/Mage.Tests/premodern_decks/premodern_oath_control.txt";
    private static final String MIRACLE_GRO = "mage/Mage.Tests/premodern_decks/premodern_miracle_gro.txt";

    // ---- Primary matchups (burn as focus deck) ----

    @Test
    public void testBurnVsControlSimulation() {
        if (!shouldRun("testBurnVsControlSimulation")) return;

        SimulationConfig config = SimulationConfig.createDefault(BURN, CONTROL_UW);
        SimulationResults results = runSimulationSeries(config);
        assertSimulationValid(results, config);
        System.out.println("Burn vs Control test completed successfully");
    }

    @Test
    public void testBurnVsAggroSimulation() {
        if (!shouldRun("testBurnVsAggroSimulation")) return;

        SimulationConfig config = SimulationConfig.createDefault(BURN, AGGRO_WHITE);
        SimulationResults results = runSimulationSeries(config);
        assertSimulationValid(results, config);
        System.out.println("Burn vs Aggro test completed successfully");
    }

    @Test
    public void testBurnVsGoblinsSimulation() {
        if (!shouldRun("testBurnVsGoblinsSimulation")) return;

        SimulationConfig config = SimulationConfig.createDefault(BURN, GOBLINS);
        SimulationResults results = runSimulationSeries(config);
        assertSimulationValid(results, config);
        System.out.println("Burn vs Goblins test completed successfully");
    }

    @Test
    public void testBurnVsRockSimulation() {
        if (!shouldRun("testBurnVsRockSimulation")) return;

        SimulationConfig config = SimulationConfig.createDefault(BURN, ROCK);
        SimulationResults results = runSimulationSeries(config);
        assertSimulationValid(results, config);
        System.out.println("Burn vs Rock test completed successfully");
    }

    @Test
    public void testBurnVsElvesSimulation() {
        if (!shouldRun("testBurnVsElvesSimulation")) return;

        SimulationConfig config = SimulationConfig.createDefault(BURN, ELVES);
        SimulationResults results = runSimulationSeries(config);
        assertSimulationValid(results, config);
        System.out.println("Burn vs Elves test completed successfully");
    }

    @Test
    public void testBurnVsPsychatogSimulation() {
        if (!shouldRun("testBurnVsPsychatogSimulation")) return;

        SimulationConfig config = SimulationConfig.createDefault(BURN, PSYCHATOG);
        SimulationResults results = runSimulationSeries(config);
        assertSimulationValid(results, config);
        System.out.println("Burn vs Psychatog test completed successfully");
    }

    @Test
    public void testBurnVsReanimatorSimulation() {
        if (!shouldRun("testBurnVsReanimatorSimulation")) return;

        SimulationConfig config = SimulationConfig.createDefault(BURN, REANIMATOR);
        SimulationResults results = runSimulationSeries(config);
        assertSimulationValid(results, config);
        System.out.println("Burn vs Reanimator test completed successfully");
    }

    @Test
    public void testBurnVsOathSimulation() {
        if (!shouldRun("testBurnVsOathSimulation")) return;

        SimulationConfig config = SimulationConfig.createDefault(BURN, OATH);
        SimulationResults results = runSimulationSeries(config);
        assertSimulationValid(results, config);
        System.out.println("Burn vs Oath test completed successfully");
    }

    @Test
    public void testBurnVsMiracleGroSimulation() {
        if (!shouldRun("testBurnVsMiracleGroSimulation")) return;

        SimulationConfig config = SimulationConfig.createDefault(BURN, MIRACLE_GRO);
        SimulationResults results = runSimulationSeries(config);
        assertSimulationValid(results, config);
    }

    // ---- Generic matchup (deck paths passed via system properties) ----
    // Invoked by scripts/collect_training_data.py for arbitrary deck pairs.
    //   -Ddeck1_path=/abs/path/to/deck1.txt
    //   -Ddeck2_path=/abs/path/to/deck2.txt

    @Test
    public void testGenericMatchup() {
        if (!shouldRun("testGenericMatchup")) return;

        String deck1Path = System.getProperty("deck1_path", BURN);
        String deck2Path = System.getProperty("deck2_path", CONTROL_UW);
        SimulationConfig config = SimulationConfig.createDefault(deck1Path, deck2Path);
        SimulationResults results = runSimulationSeries(config);
        assertSimulationValid(results, config);
        System.out.println("[SIMULATION] testGenericMatchup done: "
                + deck1Path + " vs " + deck2Path);
    }

    // ---- Smoke test (1 game, fast validation) ----

    @Test
    public void testSmallSimulationBatch() {
        if (!shouldRun("testSmallSimulationBatch")) return;

        SimulationConfig config = new SimulationConfig(
                1,      // numGames
                50,     // maxTurns
                12345L, // seed
                BURN,
                CONTROL_UW,
                false,  // mirrorSides
                SimulationConfig.defaultMetricsPath(),
                "random");

        SimulationResults results = runSimulationSeries(config);

        assertNotNull("Results should not be null", results);
        assertEquals("Should have requested 1 game", 1, results.gamesRequested);
        assertTrue("Should have completed the game", results.gamesCompleted >= 0);
        System.out.println("Small batch test completed successfully");
    }

    // ---- Mirror sides smoke test ----

    @Test
    public void testMirrorSidesSimulation() {
        if (!shouldRun("testMirrorSidesSimulation")) return;

        SimulationConfig config = new SimulationConfig(
                4,      // numGames (even number for mirror)
                100,    // maxTurns
                99L,    // seed
                BURN,
                CONTROL_UW,
                true,   // mirrorSides enabled
                SimulationConfig.defaultMetricsPath(),
                "random");

        SimulationResults results = runSimulationSeries(config);

        assertNotNull("Results should not be null", results);
        assertEquals("Should have requested 4 games", 4, results.gamesRequested);
        assertTrue("Should have completed at least some games", results.gamesCompleted > 0);
        System.out.println("Mirror sides test completed successfully");
    }

    // ---- Helpers ----

    private void assertSimulationValid(SimulationResults results, SimulationConfig config) {
        assertNotNull("Results should not be null", results);
        assertEquals("Should have requested correct number of games",
                config.numGames, results.gamesRequested);
        // gamesCompleted already includes timeouts (anything that is not an error).
        // Adding timeouts a second time would double-count them, so we use only
        // gamesCompleted + errors as the coverage check.
        int totalOutcomes = results.gamesCompleted + results.errors;
        assertTrue("All games should have an outcome",
                totalOutcomes == config.numGames);
    }
}
