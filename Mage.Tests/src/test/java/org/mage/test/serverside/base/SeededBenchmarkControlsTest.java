package org.mage.test.serverside.base;

import mage.constants.RangeOfInfluence;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mage.test.player.TestPlayer;
import org.mage.test.player.TestComputerPlayer7;
import org.mage.test.player.TestComputerPlayer7Instrumented;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.assertEquals;

/** Route the real test players: paired references must keep the opponent class. */
public class SeededBenchmarkControlsTest {
    private final Map<String, String> previous = new HashMap<>();
    private static final String[] PROPERTIES = {
            "strategy", "magellm.frozenBenchmark", "magellm.benchmarkPlainReference"};

    @Before public void saveProperties() {
        for (String name : PROPERTIES) {
            previous.put(name, System.getProperty(name));
            System.clearProperty(name);
        }
    }
    @After public void restoreProperties() {
        for (String name : PROPERTIES) {
            if (previous.get(name) == null) System.clearProperty(name);
            else System.setProperty(name, previous.get(name));
        }
    }
    private static class Harness extends FullGameSimulationInstrumentedBase {
        TestPlayer player(String name) {
            return createNewPlayer(name, RangeOfInfluence.ONE);
        }
    }
    private Class<?> delegate(TestPlayer player) throws Exception {
        Field field = TestPlayer.class.getDeclaredField("computerPlayer");
        field.setAccessible(true);
        return field.get(player).getClass();
    }
    @Test public void unchangedTeacherDefault() throws Exception {
        System.setProperty("strategy", "mageai");
        assertEquals(TestComputerPlayer7Instrumented.class, delegate(new Harness().player("PlayerA")));
    }
    @Test public void frozenReferenceMatchesNeuralOpponentClass() throws Exception {
        System.setProperty("magellm.frozenBenchmark", "true");
        System.setProperty("strategy", "rl");
        Class<?> neuralOpponent = delegate(new Harness().player("PlayerB"));
        System.setProperty("strategy", "mageai");
        System.setProperty("magellm.benchmarkPlainReference", "true");
        assertEquals(TestComputerPlayer7.class, neuralOpponent);
        assertEquals(neuralOpponent, delegate(new Harness().player("PlayerA")));
        assertEquals(neuralOpponent, delegate(new Harness().player("PlayerB")));
    }
    @Test(expected = IllegalStateException.class)
    public void rejectReferenceOutsideFrozenEvaluation() {
        System.setProperty("strategy", "mageai");
        System.setProperty("magellm.benchmarkPlainReference", "true");
        new Harness().player("PlayerA");
    }
    @Test(expected = IllegalStateException.class)
    public void rejectReferenceWithNeuralPilotConfiguration() {
        System.setProperty("strategy", "rl");
        System.setProperty("magellm.frozenBenchmark", "true");
        System.setProperty("magellm.benchmarkPlainReference", "true");
        new Harness().player("PlayerA");
    }
}
