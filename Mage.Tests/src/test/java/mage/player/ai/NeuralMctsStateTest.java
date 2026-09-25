package mage.player.ai;

import mage.abilities.common.PassAbility;
import mage.abilities.ActivatedAbility;
import mage.choices.Choice;
import mage.util.RandomUtil;
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

    private void oracle(Runnable work) {
        String[] keys = {"neuralMcts.hiddenStateMode", "magellm.frozenBenchmark", "magellmfast.logTrajectory"};
        String[] old = Arrays.stream(keys).map(System::getProperty).toArray(String[]::new);
        System.setProperty(keys[0], "oracle"); System.setProperty(keys[1], "true"); System.setProperty(keys[2], "false");
        try { work.run(); }
        finally { for (int i = 0; i < keys.length; i++) {
            if (old[i] == null) System.clearProperty(keys[i]); else System.setProperty(keys[i], old[i]);
        } }
    }
    @Test public void oracleRequiresExplicitFrozenNonTrainingWorkflow() {
        String old = System.getProperty("neuralMcts.hiddenStateMode");
        System.setProperty("neuralMcts.hiddenStateMode", "oracle");
        try { assertThrows(IllegalStateException.class, NeuralMctsState::oracleEnabled); }
        finally { if (old == null) System.clearProperty("neuralMcts.hiddenStateMode"); else System.setProperty("neuralMcts.hiddenStateMode", old); }
    }
    @Test public void oraclePreservesHiddenAllocationsLibraryOrderStackAndLiveRng() {
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 1);
        addCard(Zone.HAND, playerA, "Lightning Bolt", 1);
        addCard(Zone.HAND, playerB, "Giant Growth", 1);
        addCard(Zone.LIBRARY, playerA, "Mountain", 20);
        addCard(Zone.LIBRARY, playerA, "Forest", 20);
        addCard(Zone.LIBRARY, playerB, "Island", 20);
        addCard(Zone.LIBRARY, playerB, "Forest", 20);
        runCode("oracle hidden state", 1, PhaseStep.PRECOMBAT_MAIN, playerA, (info, player, game) -> oracle(() -> {
            String rng = RandomUtil.liveStreamsReceipt(), before = NeuralMctsDiagnostics.stateReceipt(game);
            for (int i = 0; i < 8; i++) {
                NeuralMctsState copied = root(game, player.getId(), 314 + i);
                for (Player live : game.getPlayers().values()) {
                    assertEquals(live.getHand(), copied.game.getPlayer(live.getId()).getHand());
                    assertEquals(live.getLibrary().getCardList(), copied.game.getPlayer(live.getId()).getLibrary().getCardList());
                }
                JSONObject request = copied.request();
                assertEquals(0, request.getJSONObject("game_view").getJSONObject("opponentPlayer").getJSONArray("handCards").length());
                assertFalse(request.getBoolean("log_trajectory"));
                assertFalse(request.toString().contains("privileged_hidden_state"));
                assertTrue(copied.snapshot().getBoolean("privileged_oracle"));
                assertEquals(before, NeuralMctsDiagnostics.stateReceipt(game));
                assertEquals(rng, RandomUtil.liveStreamsReceipt());
            }
            NeuralMctsState first = root(game, player.getId(), 5);
            for (int i = 0; i < first.moves().size(); i++) {
                NeuralMctsState.Move move = first.moves().get(i);
                if (move.ability != null && !(move.ability instanceof PassAbility)) {
                    NeuralMctsState child = first.next(i);
                    NeuralMctsState copied = root(child.game, child.actor, 123);
                    assertEquals(child.game.getStack().size(), copied.game.getStack().size());
                    assertEquals(serializer.buildSearchObservation(child.game, child.game.getPlayer(child.actor)).toString(),
                            serializer.buildSearchObservation(copied.game, copied.game.getPlayer(child.actor)).toString());
                    break;
                }
            }
            assertEquals(before, NeuralMctsDiagnostics.stateReceipt(game));
            assertEquals(rng, RandomUtil.liveStreamsReceipt());
        }));
        setStopAt(1, PhaseStep.POSTCOMBAT_MAIN); execute();
    }

    private NeuralMctsState referenceTransition(NeuralMctsState state, int index) {
        NeuralMctsState.Move move = state.moves().get(index);
        Game reference;
        try (RandomUtil.RandomScope scope = RandomUtil.searchScope(state.childSeed(move))) {
            reference = state.game.createSimulationForAI();
            try (RandomUtil.RandomScope activation = RandomUtil.searchScope(move.activationSeed())) {
                assertTrue(reference.getPlayer(state.actor).activateAbility((ActivatedAbility) move.ability.copy(), reference));
            }
            reference.resume();
        }
        NeuralMctsState child = state.next(index);
        assertEquals(NeuralMctsDiagnostics.stateReceipt(reference), NeuralMctsDiagnostics.stateReceipt(child.game));
        assertEquals(reference.checkIfGameIsOver(), child.game.checkIfGameIsOver());
        return child;
    }
    /** Script the optional payment in this fixture; production auxiliaries are unchanged. */
    static class AlternativePaymentPlayer extends NeuralMctsState.SearchPlayer {
        AlternativePaymentPlayer(NeuralMctsState.SearchPlayer player) { super(player); }
        @Override public AlternativePaymentPlayer copy() { return new AlternativePaymentPlayer(this); }
        @Override public boolean choose(Outcome outcome, Choice choice, Game game) {
            if (choice.isKeyChoice()) {
                for (Map.Entry<String, String> option : choice.getKeyChoices().entrySet())
                    if (option.getValue().toLowerCase(Locale.ROOT).contains("sacrifice")) {
                        choice.setChoiceByKey(option.getKey()); return true;
                    }
            } else if (choice.getChoices() != null) {
                for (String option : choice.getChoices()) if (option.toLowerCase(Locale.ROOT).contains("sacrifice")) {
                    choice.setChoice(option); return true;
                }
            }
            return super.choose(outcome, choice, game);
        }
    }
    private void spellTransition(String spell, String land, int lands, String graveyardCard) {
        if (spell.equals("Lightning Bolt")) setLife(playerB, 3);
        addCard(Zone.BATTLEFIELD, playerA, land, lands);
        addCard(Zone.HAND, playerA, spell, 1);
        if (graveyardCard != null) addCard(Zone.GRAVEYARD, playerA, graveyardCard, 1);
        addCard(Zone.LIBRARY, playerA, "Forest", 30);
        addCard(Zone.LIBRARY, playerB, "Island", 30);
        runCode("reference " + spell, 1, PhaseStep.PRECOMBAT_MAIN, playerA, (info, player, game) -> oracle(() -> {
            NeuralMctsState state = root(game, player.getId(), 55);
            if (spell.equals("Fireblast")) state.game.getState().getPlayers().put(player.getId(),
                    new AlternativePaymentPlayer((NeuralMctsState.SearchPlayer) state.game.getPlayer(player.getId())));
            boolean found = false;
            for (int i = 0; i < state.moves().size(); i++) {
                NeuralMctsState.Move move = state.moves().get(i);
                if (move.ability != null && game.getCard(move.ability.getSourceId()) != null
                        && spell.equals(game.getCard(move.ability.getSourceId()).getName())) {
                    if (spell.equals("Lightning Bolt") && !game.getOpponents(player.getId()).iterator().next()
                            .equals(move.ability.getTargets().getFirstTarget())) continue;
                    NeuralMctsState child = referenceTransition(state, i);
                    for (int n = 0; n < 12 && !child.game.getStack().isEmpty(); n++) {
                        for (int p = 0; p < child.moves().size(); p++) if (child.moves().get(p).ability instanceof PassAbility) {
                            child = referenceTransition(child, p); break;
                        }
                    }
                    assertTrue(child.game.getStack().isEmpty());
                    if (spell.equals("Lightning Bolt")) assertEquals(Double.valueOf(1), child.terminalValue());
                    if (spell.equals("Fireblast")) assertEquals(0, child.game.getBattlefield().getAllActivePermanents(player.getId()).size());
                    if (graveyardCard != null) assertTrue(child.game.getBattlefield().getAllActivePermanents(player.getId()).stream()
                            .anyMatch(card -> graveyardCard.equals(card.getName())));
                    found = true; break;
                }
            }
            assertTrue("generated spell " + spell + " rejected=" + state.rejectedCandidates + " request=" + state.request(), found);
        }));
        setStopAt(1, PhaseStep.POSTCOMBAT_MAIN); execute();
    }
    @Test public void alternativeCostMatchesReferenceEngine() { spellTransition("Fireblast", "Mountain", 2, null); }
    @Test public void graveyardRecursionMatchesReferenceEngine() { spellTransition("Unearth", "Swamp", 1, "Grizzly Bears"); }
    @Test public void drawAndPendingChoiceMatchReferenceEngine() { spellTransition("Opt", "Island", 1, null); }
    @Test public void lethalSpellMatchesReferenceTerminalOutcome() { spellTransition("Lightning Bolt", "Mountain", 1, null); }

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
    @Test public void slighActionsAreInvariantAcrossSampledMadnessHands() {
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 3);
        addCard(Zone.BATTLEFIELD, playerA, "Wasteland", 1);
        addCard(Zone.BATTLEFIELD, playerA, "Mogg Fanatic", 1);
        addCard(Zone.BATTLEFIELD, playerA, "Grim Lavamancer", 1);
        for (String name : Arrays.asList("Lightning Bolt", "Chain Lightning", "Incinerate",
                "Fireblast", "Seal of Fire", "Price of Progress", "Ball Lightning"))
            addCard(Zone.HAND, playerA, name, 1);
        addCard(Zone.GRAVEYARD, playerA, "Mountain", 2);
        addCard(Zone.LIBRARY, playerA, "Mountain", 60);
        addCard(Zone.BATTLEFIELD, playerB, "Forest", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Wild Mongrel", 1);
        addCard(Zone.BATTLEFIELD, playerB, "Basking Rootwalla", 1);
        addCard(Zone.HAND, playerB, "Circular Logic", 2);
        addCard(Zone.LIBRARY, playerB, "Island", 20);
        addCard(Zone.LIBRARY, playerB, "Forest", 20);
        addCard(Zone.LIBRARY, playerB, "Basking Rootwalla", 20);
        runCode("root invariance", 1, PhaseStep.PRECOMBAT_MAIN, playerA, (info, player, game) -> {
            List<String> expected = new ArrayList<>();
            for (NeuralMctsState.Move move : root(game, player.getId(), 1000000005).moves()) expected.add(move.key);
            for (int i = 1; i < 16; i++) {
                List<String> actual = new ArrayList<>();
                for (NeuralMctsState.Move move : root(game, player.getId(), 1000000005 + i).moves()) actual.add(move.key);
                Set<String> missing = new TreeSet<>(expected), extra = new TreeSet<>(actual);
                missing.removeAll(actual); extra.removeAll(expected);
                assertTrue("world " + i + " missing=" + missing + " extra=" + extra,
                        missing.isEmpty() && extra.isEmpty());
            }
        });
        setStopAt(1, PhaseStep.POSTCOMBAT_MAIN);
        execute();
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
