package mage.player.ai;

import mage.abilities.common.PassAbility;
import mage.constants.*;
import mage.cards.Card;
import mage.players.Player;
import mage.game.Game;
import org.json.JSONObject;
import org.junit.Test;
import org.mage.test.serverside.base.CardTestPlayerBase;
import java.util.*;
import static org.junit.Assert.*;

/** Real rules-engine transitions. Optional integrationUrl exercises frozen RPC. */
public class NeuralMctsStateTest extends CardTestPlayerBase {
    private static final String CHECKPOINT = "arm20_step17176";
    private final DecisionHandler serializer = new DecisionHandler("http://127.0.0.1:9310");

    private NeuralMctsState root(Game game, UUID player, long seed) {
        return NeuralMctsState.root(game, player, MCTSPlayer.NextAction.PRIORITY, seed,
                System.nanoTime() + 60_000_000_000L, CHECKPOINT, serializer);
    }
    private NeuralMctsState pass(NeuralMctsState state) {
        for (int i = 0; i < state.moves().size(); i++)
            if (state.moves().get(i).ability instanceof PassAbility) return state.next(i);
        throw new AssertionError("missing pass");
    }
    private NeuralMctsState advanceTo(NeuralMctsState state, MCTSPlayer.NextAction kind) {
        for (int n = 0; n < 30 && state.kind != kind; n++) state = pass(state);
        assertEquals(kind, state.kind);
        return state;
    }
    private void evaluateIfRequested(NeuralMctsState state) {
        String url = System.getProperty("neuralMcts.integrationUrl");
        if (url == null) return;
        NeuralMctsInferenceClient client = new NeuralMctsInferenceClient(url, CHECKPOINT);
        JSONObject request = state.request();
        List<NeuralMctsSearch.Evaluation> batch = client.evaluate(Arrays.asList(request, request), state.deadline);
        NeuralMctsSearch.Evaluation scalar = client.evaluate(Collections.singletonList(request), state.deadline).get(0);
        assertEquals(scalar.value, batch.get(0).value, 2e-5);
        assertArrayEquals(scalar.priors, batch.get(0).priors, 2e-5);
    }
    @Test public void castAndResolveOnCopiesWithStableTargetsAndHiddenHand() {
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 1);
        addCard(Zone.HAND, playerA, "Lightning Bolt", 1);
        addCard(Zone.HAND, playerB, "Giant Growth", 1);
        addCard(Zone.LIBRARY, playerA, "Mountain", 60);
        addCard(Zone.LIBRARY, playerB, "Forest", 60);
        runCode("search bolt", 1, PhaseStep.PRECOMBAT_MAIN, playerA, (info, player, game) -> {
            NeuralMctsState first = root(game, player.getId(), 41);
            NeuralMctsState second = root(game, player.getId(), 42);
            assertEquals(first.actions(), second.actions());
            assertEquals(game.getPlayer(player.getId()).getHand(), first.game.getPlayer(player.getId()).getHand());
            assertEquals(0, first.request().getJSONObject("game_view").getJSONObject("opponentPlayer")
                    .getJSONArray("handCards").length());
            assertFalse(first.request().getBoolean("log_trajectory"));
            evaluateIfRequested(first);
            UUID opponent = game.getOpponents(player.getId()).iterator().next();
            Game altered = game.createSimulationForAI();
            Player other = altered.getPlayer(opponent);
            Card secret = altered.getCard(other.getHand().iterator().next());
            Card top = other.getLibrary().drawFromTop(altered);
            other.getHand().remove(secret);
            other.getLibrary().putOnBottom(secret, altered);
            top.setZone(Zone.HAND, altered); other.getHand().add(top);
            NeuralMctsState shuffled = root(altered, player.getId(), 41);
            assertEquals("sampling cannot depend on actual hidden allocation",
                    first.game.getPlayer(opponent).getHand(), shuffled.game.getPlayer(opponent).getHand());
            assertEquals(first.game.getPlayer(opponent).getLibrary().getCardList(),
                    shuffled.game.getPlayer(opponent).getLibrary().getCardList());
            altered.getState().getRevealed().createRevealed("known hand").add(top);
            other.setTopCardRevealed(true);
            UUID visibleTop = other.getLibrary().getFromTop(altered).getId();
            NeuralMctsState constrained = root(altered, player.getId(), 49);
            assertTrue(constrained.game.getPlayer(opponent).getHand().contains(top.getId()));
            assertEquals(visibleTop, constrained.game.getPlayer(opponent).getLibrary().getFromTop(constrained.game).getId());
            int liveLife = game.getPlayer(opponent).getLife();
            boolean cast = false;
            for (int i = 0; i < first.moves().size(); i++) {
                NeuralMctsState.Move move = first.moves().get(i);
                if (move.ability != null && game.getCard(move.ability.getSourceId()) != null
                        && "Lightning Bolt".equals(game.getCard(move.ability.getSourceId()).getName())
                        && opponent.equals(move.ability.getTargets().getFirstTarget())) {
                    Game unableToPay = first.game.createSimulationForAI();
                    unableToPay.getBattlefield().getAllActivePermanents(player.getId())
                            .forEach(permanent -> permanent.setTapped(true));
                    assertFalse("advisory options must pass actual payment/activation",
                            move.legalPriority(unableToPay, player.getId(), 41));
                    NeuralMctsState child = first.next(i);
                    assertEquals(1, child.game.getStack().size());
                    assertEquals(0, first.game.getStack().size());
                    for (int n = 0; n < 8 && !child.game.getStack().isEmpty(); n++) child = pass(child);
                    assertEquals(liveLife - 3, child.game.getPlayer(opponent).getLife());
                    assertEquals(liveLife, game.getPlayer(opponent).getLife());
                    evaluateIfRequested(child);
                    cast = true; break;
                }
            }
            assertTrue("targeted Bolt candidate was generated: " + first.request(), cast);
        });
        setStopAt(1, PhaseStep.POSTCOMBAT_MAIN);
        execute();
    }
    @Test public void attacksBlocksAndDamageAdvanceWithoutMutatingLiveGame() {
        addCard(Zone.BATTLEFIELD, playerA, "Grizzly Bears", 1);
        addCard(Zone.BATTLEFIELD, playerB, "Hill Giant", 1);
        addCard(Zone.LIBRARY, playerA, "Forest", 60);
        addCard(Zone.LIBRARY, playerB, "Mountain", 60);
        runCode("search combat", 1, PhaseStep.PRECOMBAT_MAIN, playerA, (info, player, game) -> {
            NeuralMctsState attack = advanceTo(root(game, player.getId(), 3), MCTSPlayer.NextAction.SELECT_ATTACKERS);
            assertEquals(player.getId(), attack.actor);
            assertEquals(2, attack.moves().size());
            evaluateIfRequested(attack);
            int attackIndex = attack.moves().get(0).attacks.isEmpty() ? 1 : 0;
            NeuralMctsState block = advanceTo(attack.next(attackIndex), MCTSPlayer.NextAction.SELECT_BLOCKERS);
            assertNotEquals(player.getId(), block.actor);
            assertEquals(2, block.moves().size());
            evaluateIfRequested(block);
            int blockIndex = block.moves().get(0).blocks.isEmpty() ? 1 : 0;
            NeuralMctsState damage = block.next(blockIndex);
            for (int n = 0; n < 20 && damage.game.getTurnStepType() != PhaseStep.POSTCOMBAT_MAIN; n++)
                damage = pass(damage);
            assertEquals(PhaseStep.POSTCOMBAT_MAIN, damage.game.getTurnStepType());
            assertEquals(1, damage.game.getPlayer(player.getId()).getGraveyard().size());
            assertEquals(0, game.getPlayer(player.getId()).getGraveyard().size());
            assertTrue(game.getCombat().getAttackers().isEmpty());
        });
        setStopAt(1, PhaseStep.POSTCOMBAT_MAIN);
        execute();
    }
}
