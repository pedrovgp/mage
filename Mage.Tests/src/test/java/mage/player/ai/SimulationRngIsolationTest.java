package mage.player.ai;

import mage.constants.RangeOfInfluence;
import mage.util.RandomUtil;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.*;

import static org.junit.Assert.*;

/** Exercises the real CP6/CP7 executor boundary, including late cancellation. */
public class SimulationRngIsolationTest {
    private static final UUID PLAYER = new UUID(0, 71);

    static class Worker extends ComputerPlayer6 {
        final CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1), done = new CountDownLatch(1);
        final boolean delayed;
        volatile int draw;
        volatile boolean tieBreak;
        Worker(boolean delayed) {
            super("rng worker", RangeOfInfluence.ALL, 1);
            this.delayed = delayed; maxThinkTimeSecs = 1;
        }
        @Override protected int addActions(SimulationNode2 node, int depth, int alpha, int beta) {
            started.countDown();
            try {
                if (delayed) {
                    boolean released = false;
                    while (!released) try { released = release.await(5, TimeUnit.SECONDS); }
                    catch (InterruptedException cancelled) { /* Model a rules-engine call still unwinding. */ }
                }
                draw = RandomUtil.nextInt();
                RandomUtil.getRandom().nextInt();
                RandomUtil.playerNextInt(PLAYER, 100);
                tieBreak = RandomUtil.nextAlphaBetaBoolean();
                return 7;
            } finally { done.countDown(); }
        }
        int run() { return addActionsTimed(); }
    }

    @Test public void scopedDiagnosticWorkerCannotTouchSharedStreams() throws Exception {
        RandomUtil.setSeed(19); RandomUtil.registerPlayer(PLAYER, 23); RandomUtil.resetAlphaBetaForThink(91);
        Map<String, String> before = RandomUtil.liveStreamReceipts();
        Worker worker = new Worker(false);
        try (RandomUtil.RandomScope ignored = RandomUtil.searchScope(31)) {
            assertEquals(7, worker.run());
            Random reference = new Random(31);
            reference.nextInt(); reference.nextInt(); reference.nextInt(100);
            assertEquals("completed work must advance the submitting scope", reference.nextInt(), RandomUtil.nextInt());
            assertEquals(new Random(31 ^ 0xDEADBEEFL).nextBoolean(), worker.tieBreak);
        }
        assertTrue(worker.done.await(2, TimeUnit.SECONDS));
        assertEquals("worker must start at the submitting scope's RNG state", new Random(31).nextInt(), worker.draw);
        assertEquals("executor must preserve isolated RNG context", Collections.emptyMap(), RandomUtil.liveStreamChanges(before));
    }

    @Test public void cancelledWorkerCannotAdvanceStreamsAfterCallerReturns() throws Exception {
        RandomUtil.setSeed(19); RandomUtil.registerPlayer(PLAYER, 23); RandomUtil.resetAlphaBetaForThink(91);
        Worker worker = new Worker(true);
        try {
            RandomUtil.enterSimulation();
            try { assertEquals(0, worker.run()); }
            finally { RandomUtil.exitSimulation(); }
            assertTrue(worker.started.await(2, TimeUnit.SECONDS));
            Map<String, String> before = RandomUtil.liveStreamReceipts();
            worker.release.countDown();
            assertTrue(worker.done.await(2, TimeUnit.SECONDS));
            assertEquals("cancelled work must retain private RNGs after timeout", Collections.emptyMap(), RandomUtil.liveStreamChanges(before));
        } finally { worker.release.countDown(); worker.done.await(2, TimeUnit.SECONDS); }
    }

    @Test public void simulationGetRandomMustNotAdvanceGlobalStream() {
        RandomUtil.setSeed(19);
        String before = RandomUtil.liveStreamReceipts().get("global");
        RandomUtil.enterSimulation();
        try { RandomUtil.getRandom().nextInt(); }
        finally { RandomUtil.exitSimulation(); }
        assertEquals(before, RandomUtil.liveStreamReceipts().get("global"));
    }

    @Test public void anotherThreadsSimulationCannotRedirectLivePlayerDraws() throws Exception {
        RandomUtil.setSeed(19); RandomUtil.registerPlayer(PLAYER, 23);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        RandomUtil.enterSimulation();
        try {
            int draw = executor.submit(() -> RandomUtil.playerNextInt(PLAYER, 100)).get(2, TimeUnit.SECONDS);
            assertEquals(new Random(23).nextInt(100), draw);
        } finally { RandomUtil.exitSimulation(); executor.shutdownNow(); }
    }

    @Test public void successfulWorkerPreservesSerialSimulationAndTieBreakSequences() throws Exception {
        RandomUtil.setSeed(19); RandomUtil.registerPlayer(PLAYER, 23); RandomUtil.resetAlphaBetaForThink(91);
        Map<String, String> before = RandomUtil.liveStreamReceipts();
        Random reference = new Random(19 ^ 0xFEEDFACEL), alpha = new Random(91 ^ 0xDEADBEEFL);
        RandomUtil.enterSimulation();
        try {
            for (int i = 0; i < 2; i++) {
                Worker worker = new Worker(false);
                assertEquals(7, worker.run());
                assertEquals(reference.nextInt(), worker.draw);
                reference.nextInt(); reference.nextInt(100);
                assertEquals(alpha.nextBoolean(), worker.tieBreak);
            }
        } finally { RandomUtil.exitSimulation(); }
        assertEquals(before.get("global"), RandomUtil.liveStreamReceipts().get("global"));
        assertEquals(before.get("player:" + PLAYER), RandomUtil.liveStreamReceipts().get("player:" + PLAYER));
        assertEquals(alpha.nextBoolean(), RandomUtil.nextAlphaBetaBoolean());
    }

    @Test public void nestedWorkerForksRetainProgressAndPooledThreadsRestoreContext() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        RandomUtil.setSeed(19); RandomUtil.registerPlayer(PLAYER, 23); RandomUtil.resetAlphaBetaForThink(91);
        String before = RandomUtil.liveStreamsReceipt();
        try (RandomUtil.RandomScope ignored = RandomUtil.searchScope(31)) {
            RandomUtil.SimulationFork outer = RandomUtil.forkSimulation();
            assertEquals(7, (int) executor.submit(() -> outer.call(() -> new Worker(false).run())).get(2, TimeUnit.SECONDS));
            outer.commit();
            Random expected = new Random(31); expected.nextInt(); expected.nextInt(); expected.nextInt(100);
            assertEquals(expected.nextInt(), RandomUtil.nextInt());
            assertFalse(executor.submit(RandomUtil::isInSimulation).get(2, TimeUnit.SECONDS));
            assertEquals(before, RandomUtil.liveStreamsReceipt());
            assertEquals("pooled thread must no longer carry the search RNG", new Random(19).nextInt(),
                    (int) executor.submit((Callable<Integer>) RandomUtil::nextInt).get(2, TimeUnit.SECONDS));
        } finally { executor.shutdownNow(); }
    }

    @Test public void failedWorkerRestoresContextAndCannotCommit() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        RandomUtil.setSeed(19); RandomUtil.resetAlphaBetaForThink(91);
        String before = RandomUtil.liveStreamsReceipt();
        RandomUtil.SimulationFork fork = RandomUtil.forkSimulation();
        try {
            Future<Integer> failed = executor.submit(() -> fork.call(() -> {
                RandomUtil.getRandom().nextInt(); RandomUtil.nextAlphaBetaBoolean();
                throw new IllegalStateException("injected worker failure");
            }));
            assertThrows(ExecutionException.class, () -> failed.get(2, TimeUnit.SECONDS));
            assertThrows(IllegalStateException.class, fork::commit);
            assertEquals(before, RandomUtil.liveStreamsReceipt());
            assertFalse(executor.submit(RandomUtil::isInSimulation).get(2, TimeUnit.SECONDS));
            assertEquals(new Random(19).nextInt(), (int) executor.submit((Callable<Integer>) RandomUtil::nextInt).get(2, TimeUnit.SECONDS));
        } finally { executor.shutdownNow(); }
    }

    @Test public void completedForkCannotOverwriteANewerGameSeed() throws Exception {
        RandomUtil.setSeed(19); RandomUtil.resetAlphaBetaForThink(91);
        RandomUtil.SimulationFork fork = RandomUtil.forkSimulation();
        fork.call(() -> { RandomUtil.nextInt(); RandomUtil.nextAlphaBetaBoolean(); return 1; });
        RandomUtil.setSeed(42); RandomUtil.resetAlphaBetaForThink(12);
        String newer = RandomUtil.liveStreamsReceipt();
        fork.commit();
        assertEquals(newer, RandomUtil.liveStreamsReceipt());
    }
}
