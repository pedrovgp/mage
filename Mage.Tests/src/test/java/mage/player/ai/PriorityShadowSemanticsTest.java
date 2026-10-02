package mage.player.ai;

import mage.abilities.Ability;
import mage.abilities.common.PassAbility;
import mage.constants.PhaseStep;
import mage.constants.RangeOfInfluence;
import mage.game.Game;
import mage.game.GameState;
import mage.game.combat.Combat;
import mage.util.RandomUtil;
import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Compare the probe gate to actual CP7 routing, rather than another case list. */
public class PriorityShadowSemanticsTest {
    private static class RoutingTeacher extends ComputerPlayer7 {
        int searches, acts, passes;
        RoutingTeacher() { super("teacher", RangeOfInfluence.ALL, 1); }
        @Override protected void calculateActions(Game game) { searches++; }
        @Override protected void act(Game game) { acts++; }
        @Override public void pass(Game game) { passes++; }
        @Override protected void printBattlefieldScore(Game game, String message) { }
    }

    private static class Probe extends ComputerPlayer8 {
        int searches;
        Ability proposal;
        Probe() { super("probe", RangeOfInfluence.ALL, 1); }
        @Override protected Ability freshCp7PriorityChoice(Game game) { searches++; return proposal; }
    }

    private Game game(PhaseStep step) {
        Game game = mock(Game.class);
        when(game.getState()).thenReturn(new GameState());
        when(game.getTurnStepType()).thenReturn(step);
        return game;
    }

    private Ability action() {
        Ability result = mock(Ability.class);
        when(result.getRule()).thenReturn("Deal two damage.");
        when(result.getSourceId()).thenReturn(UUID.fromString("00000000-0000-0000-0000-000000000123"));
        when(result.toString()).thenReturn("Cast test spell");
        return result;
    }

    @Test public void everyPhaseMatchesLiveTeacherSearchRoutingAndKeepsAllCandidates() {
        for (PhaseStep step : PhaseStep.values()) {
            RoutingTeacher teacher = new RoutingTeacher();
            boolean acts = teacher.priority(game(step));
            assertEquals(step.toString(), acts, ComputerPlayer7.searchesPriorityAtStep(step));
            assertEquals(acts ? 1 : 0, teacher.searches);
            assertEquals(acts ? 1 : 0, teacher.acts);
            Probe probe = new Probe();
            probe.proposal = action();
            List<Ability> candidates = new ArrayList<>(Arrays.asList(probe.proposal, new PassAbility()));
            List<Ability> before = new ArrayList<>(candidates);
            Game state = game(step);
            JSONObject comparison = probe.priorityShadowComparison(state, candidates, false);
            assertEquals(acts ? 1 : 0, probe.searches);
            assertEquals(!acts, comparison.getBoolean("cp7_is_pass"));
            assertEquals(acts ? 0 : 1, comparison.getInt("matched_index"));
            assertEquals(ComputerPlayer8.PRIORITY_SHADOW_V2, comparison.getString("teacher_semantics"));
            assertEquals("fresh", comparison.getString("teacher_history"));
            assertEquals(before, candidates);
            verify(state).getTurnStepType();
            verifyNoMoreInteractions(state); // No live priority/pass/act/events.
        }
    }

    @Test public void rawProposalIsOptionalAndCannotReplaceTheAutomaticPassLabel() {
        Probe probe = new Probe();
        probe.proposal = action();
        List<Ability> actions = Arrays.asList(new PassAbility(), probe.proposal);
        JSONObject gated = probe.priorityShadowComparison(game(PhaseStep.UPKEEP), actions, false);
        assertEquals(0, probe.searches);
        assertFalse(gated.has("raw_search"));
        JSONObject both = probe.priorityShadowComparison(game(PhaseStep.UPKEEP), actions, true);
        assertEquals(1, probe.searches);
        assertEquals(0, both.getInt("matched_index"));
        assertTrue(both.getBoolean("cp7_is_pass"));
        JSONObject raw = both.getJSONObject("raw_search");
        assertEquals(1, raw.getInt("matched_index"));
        assertFalse(raw.getBoolean("cp7_is_pass"));
        assertEquals(ComputerPlayer8.PRIORITY_SHADOW_RAW_V1, raw.getString("teacher_semantics"));
    }

    @Test public void noSearchResultPassesAndUnknownAbilityStaysUnmatched() {
        Probe probe = new Probe();
        List<Ability> actions = Arrays.asList(new PassAbility(), action());
        JSONObject pass = probe.priorityShadowComparison(game(PhaseStep.PRECOMBAT_MAIN), actions, false);
        assertTrue(pass.getBoolean("cp7_is_pass"));
        probe.proposal = action();
        when(probe.proposal.getSourceId()).thenReturn(UUID.randomUUID());
        JSONObject unmatched = probe.priorityShadowComparison(game(PhaseStep.PRECOMBAT_MAIN), actions, false);
        assertFalse(unmatched.getBoolean("cp7_is_pass"));
        assertTrue(unmatched.isNull("matched_index"));
    }

    @Test public void freshCopyCannotModifyLivePlanCacheOrCombat() {
        Probe live = new Probe();
        live.actions.add(action());
        live.actionCache.add("already used");
        live.combat = new Combat();
        live.root = mock(SimulationNode2.class);
        ComputerPlayer7 shadow = live.newShadowCp7();
        assertNotSame(live.actions, shadow.actions);
        assertNotSame(live.actionCache, shadow.actionCache);
        assertTrue(shadow.actions.isEmpty());
        assertTrue(shadow.actionCache.isEmpty());
        assertNull(shadow.root);
        assertNull(shadow.combat);
        shadow.actionCache.add("new shadow action");
        assertEquals(1, live.actionCache.size());
        assertEquals(1, live.actions.size());
        assertNotNull(live.root);
        assertNotNull(live.combat);
        assertTrue(shadow.maxThinkTimeSecs >= 3);
        assertTrue(shadow.maxNodes >= 5000);
    }

    @Test public void automaticPassProbeDoesNotConsumeGameRng() {
        Probe probe = new Probe();
        List<Ability> actions = Arrays.asList(new PassAbility(), action());
        RandomUtil.setSeed(12345L);
        int expected = RandomUtil.nextInt(1000000);
        RandomUtil.setSeed(12345L);
        probe.priorityShadowComparison(game(PhaseStep.UPKEEP), actions, false);
        assertEquals(expected, RandomUtil.nextInt(1000000));
        assertFalse(RandomUtil.isInSimulation());
        assertEquals(0, probe.searches);
    }
}
