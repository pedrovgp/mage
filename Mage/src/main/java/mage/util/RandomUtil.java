package mage.util;

import java.awt.*;
import java.util.Collection;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.TreeMap;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Callable;

/**
 * Created by IGOUDT on 5-9-2016.
 */
public final class RandomUtil {

    private static final boolean TRACE_RNG_WRITES = Boolean.getBoolean("magellm.rngTrace");

    /** Optional provenance only. Transient metadata never contributes to receipts. */
    private static final class AuditedRandom extends Random {
        private static final long serialVersionUID = 1L;
        private transient volatile Map<String, Object> lastWrite;
        AuditedRandom() { super(); }
        AuditedRandom(long seed) { super(seed); }
        @Override protected int next(int bits) {
            int result = super.next(bits);
            if (TRACE_RNG_WRITES) recordWrite("next");
            return result;
        }
        @Override public synchronized void setSeed(long seed) {
            super.setSeed(seed);
            if (TRACE_RNG_WRITES) recordWrite("setSeed");
        }
        private void recordWrite(String operation) {
            Thread thread = Thread.currentThread();
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("thread", thread.getName()); entry.put("thread_id", thread.getId());
            entry.put("operation", operation); entry.put("nano_time", System.nanoTime());
            List<String> stack = new ArrayList<>();
            for (StackTraceElement frame : thread.getStackTrace()) {
                if (frame.getClassName().equals(Thread.class.getName())
                        || frame.getClassName().equals(Random.class.getName())
                        || frame.getClassName().startsWith(RandomUtil.class.getName())) continue;
                stack.add(frame.toString());
                if (stack.size() == 12) break;
            }
            entry.put("stack", stack);
            lastWrite = entry;
        }
    }

    // Global fallback RNG (used when no player context and not in simulation).
    private static final Random random = new AuditedRandom();

    // Per-player RNGs for real-game decisions (library shuffle, flip coin, AI choices, etc.).
    // Each player is seeded independently so PlayerA's random calls never affect PlayerB's.
    // Cleared by setSeed(); populated by registerPlayer() after players are created.
    private static final Map<UUID, Random> playerRandoms = new ConcurrentHashMap<>();

    // A simulation on one thread must not redirect another thread's live draws.
    // Executor work receives an explicit private fork, never a shared depth flag.
    private static final ThreadLocal<Integer> simulationDepth = ThreadLocal.withInitial(() -> 0);
    private static volatile Random simulationRandom = new AuditedRandom(0xFEEDFACEL);
    // Neural search is local to one thread. Never reseed/advance live-game RNGs.
    private static final ThreadLocal<Random> searchRandom = new ThreadLocal<>();
    private static final ThreadLocal<Random> searchAlphaRandom = new ThreadLocal<>();

    public static RandomScope searchScope(long seed) {
        return new RandomScope(seed);
    }

    public static final class RandomScope implements AutoCloseable {
        private final Random previous;
        private final Random previousAlpha;
        private final int previousDepth;
        private RandomScope(long seed) {
            this(new Random(seed), new Random(seed ^ 0xDEADBEEFL), false);
        }
        private RandomScope(Random general, Random alpha, boolean simulation) {
            previous = searchRandom.get();
            previousAlpha = searchAlphaRandom.get();
            previousDepth = simulationDepth.get();
            searchRandom.set(general);
            searchAlphaRandom.set(alpha);
            if (simulation) simulationDepth.set(previousDepth + 1);
        }
        @Override
        public void close() {
            if (previous == null) searchRandom.remove();
            else searchRandom.set(previous);
            if (previousAlpha == null) searchAlphaRandom.remove();
            else searchAlphaRandom.set(previousAlpha);
            if (previousDepth == 0) simulationDepth.remove();
            else simulationDepth.set(previousDepth);
        }
    }

    /**
     * Snapshot on the submitting thread; install only private RNGs on a worker.
     * A successful, consumed result advances its caller's simulation streams.
     * Cancelled/failed work never publishes RNG state, even if it ignores interrupts.
     */
    public static SimulationFork forkSimulation() { return new SimulationFork(); }

    public static final class SimulationFork {
        private final Thread owner = Thread.currentThread();
        private final Random parentGeneral = searchRandom.get();
        private final Random parentAlpha = searchAlphaRandom.get();
        private final Random sourceGeneral = parentGeneral == null ? simulationRandom : parentGeneral;
        private final Random sourceAlpha = parentAlpha == null ? alphaBetaRandom : parentAlpha;
        private Random general = copyRandom(sourceGeneral), alpha = copyRandom(sourceAlpha);
        private boolean used;
        private volatile boolean succeeded;
        public <T> T call(Callable<T> work) throws Exception {
            synchronized (this) {
                if (used) throw new IllegalStateException("simulation RNG fork already used");
                used = true;
            }
            T result;
            try (RandomScope ignored = new RandomScope(general, alpha, true)) {
                result = work.call();
                general = searchRandom.get(); alpha = searchAlphaRandom.get();
            }
            succeeded = true;
            return result;
        }
        public void commit() {
            if (Thread.currentThread() != owner || !succeeded)
                throw new IllegalStateException("only the submitting thread can commit completed simulation RNGs");
            synchronized (RandomUtil.class) {
                // Do not overwrite a newer seed/scope if another game replaced it.
                if (parentGeneral != null) {
                    if (searchRandom.get() == parentGeneral) searchRandom.set(general);
                } else if (simulationRandom == sourceGeneral) simulationRandom = general;
                if (parentAlpha != null) {
                    if (searchAlphaRandom.get() == parentAlpha) searchAlphaRandom.set(alpha);
                } else if (alphaBetaRandom == sourceAlpha) alphaBetaRandom = alpha;
            }
        }
    }

    private static Random copyRandom(Random source) {
        try {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            try (java.io.ObjectOutputStream out = new java.io.ObjectOutputStream(bytes)) { out.writeObject(source); }
            try (java.io.ObjectInputStream in = new java.io.ObjectInputStream(
                    new java.io.ByteArrayInputStream(bytes.toByteArray()))) { return (Random) in.readObject(); }
        } catch (Exception exc) { throw new IllegalStateException("cannot copy simulation RNG", exc); }
    }

    // Separate RNG for alpha-beta tie-breaking in ComputerPlayer6/7.
    // INTENTIONALLY seeded with a fixed constant (not the game seed) so that all
    // game seeds produce identical tie-breaking decisions.
    // resetAlphaBetaForThink() re-seeds this per think() call using game position
    // (turn + phase), making it independent of prior alpha-beta call counts.
    private static volatile Random alphaBetaRandom = new AuditedRandom();

    private RandomUtil() {
    }

    // -------------------------------------------------------------------------
    // Player registration
    // -------------------------------------------------------------------------

    /**
     * Register a player with their own isolated RNG seeded from (gameSeed ^ playerSlotConstant).
     * Call this for every player immediately after setSeed() and player creation.
     * Suggested constants: PlayerA -> 0xAAAAAAAAL, PlayerB -> 0xBBBBBBBBL.
     */
    public static void registerPlayer(UUID playerId, long seed) {
        playerRandoms.put(playerId, new AuditedRandom(seed));
    }

    public static void clearPlayers() {
        playerRandoms.clear();
    }

    // -------------------------------------------------------------------------
    // Simulation mode
    // -------------------------------------------------------------------------

    /**
     * Call before entering alpha-beta simulation (game.copy() + tree search).
     * While in simulation, all random calls route to simulationRandom rather than
     * per-player RNGs, preventing simulation from polluting real-game state.
     */
    public static void enterSimulation() {
        simulationDepth.set(simulationDepth.get() + 1);
    }

    /** Call in a finally block after alpha-beta simulation completes. */
    public static void exitSimulation() {
        int depth = simulationDepth.get();
        if (depth <= 0) throw new IllegalStateException("unbalanced simulation RNG scope");
        if (depth == 1) simulationDepth.remove();
        else simulationDepth.set(depth - 1);
    }

    public static boolean isInSimulation() {
        return simulationDepth.get() > 0;
    }

    // -------------------------------------------------------------------------
    // Internal RNG resolver
    // -------------------------------------------------------------------------

    private static Random resolveRng(UUID playerId) {
        if (searchRandom.get() != null) return searchRandom.get();
        if (simulationDepth.get() > 0) {
            return simulationRandom;
        }
        if (playerId != null) {
            Random r = playerRandoms.get(playerId);
            if (r != null) {
                return r;
            }
        }
        return random;
    }

    // -------------------------------------------------------------------------
    // Global random methods (no player context)
    // -------------------------------------------------------------------------

    public static Random getRandom() {
        return resolveRng(null);
    }

    public static int nextInt() {
        return resolveRng(null).nextInt();
    }

    public static int nextInt(int max) {
        return resolveRng(null).nextInt(max);
    }

    public static boolean nextBoolean() {
        return resolveRng(null).nextBoolean();
    }

    public static double nextDouble() {
        return resolveRng(null).nextDouble();
    }

    public static Color nextColor() {
        Random r = resolveRng(null);
        return new Color(r.nextInt(256), r.nextInt(256), r.nextInt(256));
    }

    public static void setSeed(long newSeed) {
        random.setSeed(newSeed);
        simulationRandom = new AuditedRandom(newSeed ^ 0xFEEDFACEL);
        playerRandoms.clear(); // re-populated by registerPlayer() after player creation
        // alphaBetaRandom is NOT seeded from the game seed; uses fixed constant via resetAlphaBetaForThink()
    }

    // -------------------------------------------------------------------------
    // Per-player random methods (with player context)
    // -------------------------------------------------------------------------

    public static int playerNextInt(UUID playerId, int max) {
        return resolveRng(playerId).nextInt(max);
    }

    public static boolean playerNextBoolean(UUID playerId) {
        return resolveRng(playerId).nextBoolean();
    }

    public static <T> T playerRandomFromCollection(UUID playerId, Collection<T> collection) {
        if (collection.size() < 2) {
            return collection.stream().findFirst().orElse(null);
        }
        int rand = playerNextInt(playerId, collection.size());
        int count = 0;
        for (T current : collection) {
            if (count == rand) {
                return current;
            }
            count++;
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Legacy helpers (no player context — fallback to resolveRng(null))
    // -------------------------------------------------------------------------

    public static <T> T randomFromCollection(Collection<T> collection) {
        return playerRandomFromCollection(null, collection);
    }

    // -------------------------------------------------------------------------
    // Alpha-beta tie-breaking (completely separate RNG, not affected by simulation mode)
    // -------------------------------------------------------------------------

    /**
     * Resets alphaBetaRandom before each think() call using only game-observable
     * state (turn number + phase step ordinal), NOT the game seed or player UUID.
     *
     * Why this matters:
     *   - In mageai_log: PlayerA (CP7) and PlayerB (CP7) both call think().
     *   - In rl_trained: PlayerA (CP8/RL) skips think(); PlayerB (CP7) still calls it.
     *   - Without a reset, PlayerA's prior think() calls advance alphaBetaRandom before
     *     PlayerB's turn, causing different tie-breaking in rl_trained vs mageai_log.
     *   - By resetting to the same position (turn ^ phase), both runs guarantee identical
     *     tie-breaking for PlayerB at any given game state.
     *
     * Cross-seed consistency:
     *   - positionSeed depends only on turn + phase, not on the game seed.
     *   - All game seeds (42-46) reach the same turn/phase → same reset → same trajectory.
     */
    public static void resetAlphaBetaForThink(long positionSeed) {
        if (searchAlphaRandom.get() == null) alphaBetaRandom = new AuditedRandom(0xDEADBEEFL ^ positionSeed);
        else searchAlphaRandom.get().setSeed(0xDEADBEEFL ^ positionSeed);
    }

    /**
     * Returns the next boolean from alphaBetaRandom for tie-breaking.
     * Call resetAlphaBetaForThink() before each think() to ensure determinism.
     */
    public static boolean nextAlphaBetaBoolean() {
        return (searchAlphaRandom.get() == null ? alphaBetaRandom : searchAlphaRandom.get()).nextBoolean();
    }

    /** Non-consuming receipt for frozen-benchmark isolation checks. Never an inference input. */
    public static String liveStreamsReceipt() {
        try {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            try (java.io.ObjectOutputStream out = new java.io.ObjectOutputStream(bytes)) {
                out.writeObject(random); out.writeObject(simulationRandom); out.writeObject(alphaBetaRandom);
                out.writeObject(new java.util.TreeMap<>(playerRandoms));
            }
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray());
            return java.util.Base64.getEncoder().encodeToString(digest);
        } catch (Exception exc) { throw new IllegalStateException("cannot audit RNG streams", exc); }
    }

    private static Map<String, Random> liveStreams() {
        Map<String, Random> streams = new TreeMap<>();
        streams.put("global", random); streams.put("simulation", simulationRandom);
        streams.put("alpha_beta", alphaBetaRandom);
        playerRandoms.forEach((player, rng) -> streams.put("player:" + player, rng));
        return streams;
    }

    public static Map<String, String> liveStreamReceipts() {
        Map<String, String> receipts = new TreeMap<>();
        liveStreams().forEach((name, rng) -> {
            try {
                java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                try (java.io.ObjectOutputStream out = new java.io.ObjectOutputStream(bytes)) { out.writeObject(rng); }
                receipts.put(name, java.util.Base64.getEncoder().encodeToString(
                        java.security.MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray())));
            } catch (Exception exc) { throw new IllegalStateException("cannot audit RNG stream " + name, exc); }
        });
        return receipts;
    }

    public static Map<String, Object> liveStreamChanges(Map<String, String> before) {
        Map<String, String> after = liveStreamReceipts();
        Map<String, Random> streams = liveStreams();
        Map<String, Object> changes = new TreeMap<>();
        Set<String> names = new java.util.TreeSet<>(before.keySet()); names.addAll(after.keySet());
        for (String name : names) if (!java.util.Objects.equals(before.get(name), after.get(name))) {
            Map<String, Object> change = new LinkedHashMap<>();
            change.put("before", before.get(name)); change.put("after", after.get(name));
            Random rng = streams.get(name);
            if (rng instanceof AuditedRandom) change.put("last_write", ((AuditedRandom) rng).lastWrite);
            changes.put(name, change);
        }
        return changes;
    }
}
