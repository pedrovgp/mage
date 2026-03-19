package mage.util;

import java.awt.*;
import java.util.Collection;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by IGOUDT on 5-9-2016.
 */
public final class RandomUtil {

    // Global fallback RNG (used when no player context and not in simulation).
    private static final Random random = new Random();

    // Per-player RNGs for real-game decisions (library shuffle, flip coin, AI choices, etc.).
    // Each player is seeded independently so PlayerA's random calls never affect PlayerB's.
    // Cleared by setSeed(); populated by registerPlayer() after players are created.
    private static final Map<UUID, Random> playerRandoms = new ConcurrentHashMap<>();

    // Simulation depth counter. When > 0, all random calls are routed to simulationRandom
    // so alpha-beta simulations do NOT advance real-game per-player RNGs.
    // Use enterSimulation()/exitSimulation() around game.copy()-based search.
    private static final AtomicInteger simulationDepth = new AtomicInteger(0);
    private static final Random simulationRandom = new Random(0xFEEDFACEL);

    // Separate RNG for alpha-beta tie-breaking in ComputerPlayer6/7.
    // INTENTIONALLY seeded with a fixed constant (not the game seed) so that all
    // game seeds produce identical tie-breaking decisions.
    // resetAlphaBetaForThink() re-seeds this per think() call using game position
    // (turn + phase), making it independent of prior alpha-beta call counts.
    private static final Random alphaBetaRandom = new Random();

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
        playerRandoms.put(playerId, new Random(seed));
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
        simulationDepth.incrementAndGet();
    }

    /** Call in a finally block after alpha-beta simulation completes. */
    public static void exitSimulation() {
        simulationDepth.decrementAndGet();
    }

    public static boolean isInSimulation() {
        return simulationDepth.get() > 0;
    }

    // -------------------------------------------------------------------------
    // Internal RNG resolver
    // -------------------------------------------------------------------------

    private static Random resolveRng(UUID playerId) {
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
        return random;
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
        simulationRandom.setSeed(newSeed ^ 0xFEEDFACEL);
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
        alphaBetaRandom.setSeed(0xDEADBEEFL ^ positionSeed);
    }

    /**
     * Returns the next boolean from alphaBetaRandom for tie-breaking.
     * Call resetAlphaBetaForThink() before each think() to ensure determinism.
     */
    public static boolean nextAlphaBetaBoolean() {
        return alphaBetaRandom.nextBoolean();
    }
}
