package mage.player.ai;

import mage.abilities.Ability;
import mage.abilities.common.PassAbility;
import mage.constants.PhaseStep;
import mage.constants.RangeOfInfluence;
import mage.constants.Zone;
import mage.game.Game;
import mage.util.RandomUtil;
import org.json.JSONObject;
import org.junit.Test;
import org.mage.test.serverside.base.CardTestPlayerBase;
import org.mage.test.player.TestComputerPlayer8;
import org.mage.test.player.TestPlayer;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/** Exercise the real alpha-beta path on a copied board, without an HTTP policy. */
public class PriorityShadowSearchIsolationTest extends CardTestPlayerBase {
    private TestComputerPlayer8 sourcePlayer;

    @Override protected TestPlayer createPlayer(String name, RangeOfInfluence range) {
        TestComputerPlayer8 player = new TestComputerPlayer8(name, range, 1);
        if ("PlayerA".equals(name)) sourcePlayer = player;
        return new TestPlayer(player); // Scripted fixture, not an HTTP-driven game.
    }

    private static class LiveProbe extends ComputerPlayer8 {
        LiveProbe(ComputerPlayer8 source) {
            super(source);
            passed = false;
        }
        JSONObject compare(Game game, List<Ability> candidates) {
            return priorityShadowComparison(game, candidates, true);
        }
    }

    @Test public void realSearchLeavesLiveBoardAndGameRngUnchanged() {
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 3);
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.BATTLEFIELD, playerB, "Jackal Pup");
        setStopAt(1, PhaseStep.PRECOMBAT_MAIN);
        execute();
        assertEquals(PhaseStep.PRECOMBAT_MAIN, currentGame.getTurnStepType());
        // The scripted fixture stops after everyone has passed. Reopen priority
        // before copying the player so the shadow explores the offered spell.
        currentGame.getState().resume();
        currentGame.getPlayers().resetPassed();
        currentGame.getPlayerList().setCurrent(playerA.getId());
        currentGame.resumeTimer(playerA.getId());
        currentGame.getState().setPriorityPlayerId(playerA.getId());
        LiveProbe probe = new LiveProbe(sourcePlayer);
        List<Ability> candidates = new ArrayList<>();
        candidates.add(new PassAbility());
        candidates.addAll(probe.getPlayable(currentGame, false));
        assertTrue("The fixture must offer a real spell", candidates.size() > 1);
        String before = currentGame.getState().getValue(true);
        int handBefore = playerA.getHand().size();
        int lifeBefore = playerB.getLife();
        RandomUtil.setSeed(87123L);
        int next = RandomUtil.nextInt(1000000);
        RandomUtil.setSeed(87123L);
        JSONObject result = probe.compare(currentGame, candidates);
        assertFalse("The real search must propose an action in this fixture", result.getBoolean("cp7_is_pass"));
        assertFalse(result.isNull("matched_index"));
        assertEquals(next, RandomUtil.nextInt(1000000));
        assertFalse(RandomUtil.isInSimulation());
        assertEquals(before, currentGame.getState().getValue(true));
        assertEquals(handBefore, playerA.getHand().size());
        assertEquals(lifeBefore, playerB.getLife());
        assertEquals(ComputerPlayer8.PRIORITY_SHADOW_V2, result.getString("teacher_semantics"));
        assertEquals(result.get("matched_index"), result.getJSONObject("raw_search").get("matched_index"));
    }
}
