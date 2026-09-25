package mage.player.ai;

import mage.abilities.Ability;
import mage.abilities.common.PassAbility;
import mage.abilities.costs.mana.ManaCostsImpl;
import mage.constants.PhaseStep;
import mage.constants.Zone;
import mage.game.Game;
import mage.game.permanent.Permanent;
import mage.util.RandomUtil;
import org.junit.Test;
import org.mage.test.serverside.base.CardTestPlayerBase;

import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

/** Activation probes and replay must make the same auxiliary payment choices. */
public class NeuralMctsActivationTest extends CardTestPlayerBase {
    private void oracle(Runnable work) {
        String[] keys = {"neuralMcts.hiddenStateMode", "magellm.frozenBenchmark", "magellmfast.logTrajectory"};
        String[] old = Arrays.stream(keys).map(System::getProperty).toArray(String[]::new);
        System.setProperty(keys[0], "oracle");
        System.setProperty(keys[1], "true");
        System.setProperty(keys[2], "false");
        try { work.run(); }
        finally {
            for (int i = 0; i < keys.length; i++) {
                if (old[i] == null) System.clearProperty(keys[i]);
                else System.setProperty(keys[i], old[i]);
            }
        }
    }

    private NeuralMctsState root(Game game, UUID player, long seed) {
        return NeuralMctsState.root(game, player, MCTSPlayer.NextAction.PRIORITY, seed,
                System.nanoTime() + 60_000_000_000L, "arm20_step17176",
                new DecisionHandler("http://127.0.0.1:9310"));
    }

    @Test
    public void equalScoreManaPaymentsChooseTheSameLandAcrossCopies() {
        addCard(Zone.BATTLEFIELD, playerA, "Volrath's Stronghold", 1);
        addCard(Zone.BATTLEFIELD, playerA, "Treetop Village", 1);
        runCode("copy payment", 1, PhaseStep.PRECOMBAT_MAIN, playerA, (info, player, game) -> oracle(() -> {
            NeuralMctsState state = root(game, player.getId(), 1000010028L);
            Ability ability = state.game.getBattlefield().getAllActivePermanents(player.getId()).stream()
                    .filter(p -> p.getName().equals("Volrath's Stronghold")).findFirst().get()
                    .getAbilities().getActivatedAbilities(Zone.BATTLEFIELD).stream()
                    .filter(a -> !a.isManaAbility()).findFirst().get();
            String before = NeuralMctsDiagnostics.stateReceipt(state.game);
            String rng = RandomUtil.liveStreamsReceipt();
            Set<UUID> expected = null;
            for (int i = 0; i < 32; i++) {
                try (RandomUtil.RandomScope scope = RandomUtil.searchScope(4711L)) {
                    Game copy = state.game.createSimulationForAI();
                    assertTrue(copy.getPlayer(player.getId()).playMana(ability.copy(),
                            new ManaCostsImpl<>("{1}"), "pay generic", copy));
                    Set<UUID> tapped = copy.getBattlefield().getAllActivePermanents(player.getId()).stream()
                            .filter(Permanent::isTapped).map(Permanent::getId).collect(Collectors.toSet());
                    assertEquals("one mana source must be used", 1, tapped.size());
                    if (expected == null) expected = tapped;
                    else assertEquals("same state and RNG must select the same mana source", expected, tapped);
                }
                assertEquals(before, NeuralMctsDiagnostics.stateReceipt(state.game));
                assertEquals(rng, RandomUtil.liveStreamsReceipt());
            }
        }));
        setStopAt(1, PhaseStep.POSTCOMBAT_MAIN);
        execute();
    }

    @Test
    public void opponentStrongholdAfterUntapReplaysEveryProbedAction() {
        setLife(playerB, 1);
        addCard(Zone.BATTLEFIELD, playerB, "Volrath's Stronghold", 1);
        addCard(Zone.BATTLEFIELD, playerB, "Llanowar Wastes", 4);
        addCard(Zone.BATTLEFIELD, playerB, "Treetop Village", 1);
        addCard(Zone.GRAVEYARD, playerB, "Birds of Paradise", 2);
        addCard(Zone.GRAVEYARD, playerB, "Yavimaya Elder", 1);
        addCard(Zone.HAND, playerB, "Vampiric Tutor", 1);
        addCard(Zone.HAND, playerB, "Duress", 2);
        addCard(Zone.HAND, playerB, "Swamp", 1);
        addCard(Zone.HAND, playerB, "Birds of Paradise", 1);
        addCard(Zone.LIBRARY, playerA, "Forest", 30);
        addCard(Zone.LIBRARY, playerB, "Island", 30);
        runCode("opponent activation", 1, PhaseStep.END_TURN, playerA, (info, player, game) -> oracle(() -> {
            game.getBattlefield().getAllActivePermanents(playerB.getId()).stream()
                    .filter(p -> !p.getName().equals("Llanowar Wastes")).forEach(p -> p.setTapped(true));
            String live = NeuralMctsDiagnostics.stateReceipt(game), rng = RandomUtil.liveStreamsReceipt();
            int strongholdCandidates = 0;
            for (int attempt = 0; attempt < 4; attempt++) {
                NeuralMctsState state = root(game, player.getId(), 1000010028L + attempt);
                for (int depth = 0; depth < 8; depth++) {
                    if (state.terminalValue() != null) break;
                    for (Ability candidate : ((NeuralMctsState.SearchPlayer) state.game.getPlayer(state.actor))
                            .getPlayableOptions(state.game)) {
                        if (candidate.getSourceId() != null && state.game.getObject(candidate.getSourceId()) != null
                                && state.game.getObject(candidate.getSourceId()).getName().equals("Volrath's Stronghold")) {
                            strongholdCandidates++;
                        }
                    }
                    state.moves();
                    String before = NeuralMctsDiagnostics.stateReceipt(state.game);
                    int pass = -1;
                    for (int i = 0; i < state.moves().size(); i++) {
                        if (state.moves().get(i).ability instanceof PassAbility) pass = i;
                        state.next(i);
                        assertEquals("replay must not change its parent", before,
                                NeuralMctsDiagnostics.stateReceipt(state.game));
                    }
                    if (pass < 0) break;
                    state = state.next(pass);
                }
            }
            assertTrue("fixture must reach opponent's untapped Stronghold candidates", strongholdCandidates > 0);
            assertEquals(live, NeuralMctsDiagnostics.stateReceipt(game));
            assertEquals(rng, RandomUtil.liveStreamsReceipt());
        }));
        setStopAt(2, PhaseStep.PRECOMBAT_MAIN);
        execute();
    }
}
