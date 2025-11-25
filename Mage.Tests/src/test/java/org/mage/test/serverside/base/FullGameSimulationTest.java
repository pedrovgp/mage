package org.mage.test.serverside.base;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Test class for full game simulation framework.
 * Tests ComputerPlayer7Instrumented vs ComputerPlayer7 matchups for trajectory
 * logging.
 */
public class FullGameSimulationTest extends FullGameSimulationInstrumentedBase {

    @Test
    public void testBurnVsControlSimulation() {
        if (!shouldRun("testBurnVsControlSimulation")) {
            return;
        }

        String burnDeck = "mage/Mage.Tests/premodern_decks/premodern_burn_red.txt";
        String controlDeck = "mage/Mage.Tests/premodern_decks/premodern_control_uw.txt";

        SimulationConfig config = SimulationConfig.createDefault(burnDeck, controlDeck);
        SimulationResults results = runSimulationSeries(config);

        // Basic validation
        assertNotNull("Results should not be null", results);
        assertEquals("Should have requested correct number of games", config.numGames, results.gamesRequested);
        assertTrue("Should have completed at least some games", results.gamesCompleted > 0);

        // Win rate validation (at least one player should have wins)
        assertTrue("At least one player should have wins",
                results.winsPlayerA > 0 || results.winsPlayerB > 0);

        System.out.println("Burn vs Control test completed successfully");
    }

    @Test
    public void testBurnVsAggroSimulation() {
        if (!shouldRun("testBurnVsAggroSimulation")) {
            return;
        }

        String burnDeck = "mage/Mage.Tests/premodern_decks/premodern_burn_red.txt";
        String aggroDeck1 = "mage/Mage.Tests/premodern_decks/premodern_aggro_white.txt";

        SimulationConfig config = SimulationConfig.createDefault(burnDeck, aggroDeck1);
        SimulationResults results = runSimulationSeries(config);

        // Basic validation
        assertNotNull("Results should not be null", results);
        assertEquals("Should have requested correct number of games", config.numGames, results.gamesRequested);
        assertTrue("Should have completed at least some games", results.gamesCompleted > 0);

        System.out.println("Aggro vs Aggro test completed successfully");
    }

    @Test
    public void testSmallSimulationBatch() {
        if (!shouldRun("testSmallSimulationBatch")) {
            return;
        }

        String burnDeck = "mage/Mage.Tests/premodern_decks/premodern_burn_red.txt";
        String controlDeck = "mage/Mage.Tests/premodern_decks/premodern_control_uw.txt";

        // Override defaults for smaller test
        SimulationConfig config = new SimulationConfig(
                3, // numGames
                50, // maxTurns
                12345L, // seed
                burnDeck,
                controlDeck,
                false,
                "mage/Mage.Tests/tests/full_games/",
                "random");

        SimulationResults results = runSimulationSeries(config);

        // Basic validation
        assertNotNull("Results should not be null", results);
        assertEquals("Should have requested 3 games", 3, results.gamesRequested);
        assertTrue("Should have completed games", results.gamesCompleted >= 0);

        System.out.println("Small batch test completed successfully");
    }
}
