package org.mage.test.serverside.base;

import mage.constants.MultiplayerAttackOption;
import mage.constants.RangeOfInfluence;
import mage.game.Game;
import mage.game.GameException;
import mage.game.GameOptions;
import mage.game.TwoPlayerDuel;
import mage.game.mulligan.MulliganType;
import org.json.JSONObject;
import org.mage.test.player.TestPlayer;

import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static org.junit.Assert.*;

/**
 * Base class for full game simulation tests between AI players.
 *
 * <p>Two modes controlled by {@code -Dstrategy=...}:
 * <ul>
 *   <li>{@code mageai} (default) — both players are CP7Instrumented (trajectory logging for BC training)
 *   <li>{@code rl} / {@code rl_eval} — PlayerA is CP8 (RL via HTTP to magellmfast), PlayerB is CP7 (MCTS)
 * </ul>
 *
 * <p>Enables batch execution of multiple games with configurable parameters.
 */
public abstract class FullGameSimulationInstrumentedBase extends CardTestPlayerBase {

    private static final Path PROJECT_ROOT = detectProjectRoot();

    // Read system properties for reproducible runs
    public static final String STRATEGY = System.getProperty("strategy", "random");
    public static final String SEED = System.getProperty("seed", "42");
    public static final int MAX_TURNS = Integer.parseInt(System.getProperty("max_turns", "200"));
    public static final int NUM_GAMES = Integer.parseInt(System.getProperty("num_games", "10"));

    // Base URL of the magellmfast inference server the AI players talk to.
    // MUST match -Dmagellmfast.url (set by run_fullgame_benchmark.py to the
    // per-run inference port, e.g. :9200) so the game_result POST lands on the
    // SAME server that logged the per-decision value predictions — otherwise
    // value_inference_log.jsonl and game_results.jsonl live on different
    // servers and the Value-Precision-by-turn join can never match
    // (see memory-bank/progress/bc_value_belief_heads.md D-9).
    public static final String MAGELLMFAST_URL =
            System.getProperty("magellmfast.url", "http://localhost:9000");

    // Tests selection: provide a comma-separated list via -Dtests="test_A,test_B".
    public static final Set<String> SELECTED_TESTS = getSelectedTests();

    public static Set<String> getSelectedTests() {
        String raw = System.getProperty("tests", "").trim();
        if (raw.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> s = new HashSet<>();
        for (String p : raw.split(",")) {
            s.add(p.trim());
        }
        return s;
    }

    public static boolean shouldRun(String testName) {
        // If no explicit selection provided, run everything.
        return SELECTED_TESTS.isEmpty() || SELECTED_TESTS.contains(testName);
    }

    // ── DAgger mixture mode (-Dstrategy=dagger) ─────────────────────────
    // ONE seeded draw at game setup decides PlayerA's pilot for the ENTIRE
    // game: student (CP8, RL via HTTP, shadow CP7 labels every decision) or
    // teacher (CP7Instrumented — an ordinary expert game).  PlayerB is always
    // CP7Instrumented (learnings §9: consistent reference opponent).
    //
    // The draw is dedicated and deterministic — a SplitMix64 hash of
    // gameSeed ^ fixed salt — NOT RandomUtil per-player streams (consuming
    // engine RNG would perturb game evolution; learnings §10) and NOT
    // wall-clock randomness.  A game is therefore exactly reproducible from
    // (seed, decks, fraction).  SplitMix64 rather than
    // new java.util.Random(seed).nextDouble(): java.util.Random's seed
    // scrambler is weak, so SEQUENTIAL game seeds yield strongly correlated
    // first draws (measured ~26% realized share at fraction 0.30 — see
    // DaggerDrawTest).
    private static final long DAGGER_DRAW_SALT = 0xDA66E201L;

    /** Per-game pilot draw: true → student (CP8) pilots PlayerA. */
    public static boolean daggerStudentDraw(long gameSeed, double fraction) {
        long z = gameSeed ^ DAGGER_DRAW_SALT;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z = z ^ (z >>> 31);
        double u = (z >>> 11) * 0x1.0p-53;  // uniform in [0, 1)
        return u < fraction;
    }

    /** Mixture parameter P(student game), set per spawn by the worker. */
    public static double daggerFraction() {
        try {
            return Double.parseDouble(System.getProperty("magellm.daggerFraction", "0.0"));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    /**
     * Configuration class for simulation parameters
     */
    public static class SimulationConfig {
        public final int numGames;
        public final int maxTurns;
        public final long seed;
        public final String deck1Path;
        public final String deck2Path;
        public final boolean mirrorSides;
        public final String metricsOutputPath;
        public final String strategy;

        public SimulationConfig(int numGames, int maxTurns, long seed, String deck1Path, String deck2Path,
                boolean mirrorSides, String metricsOutputPath, String strategy) {
            this.numGames = numGames;
            this.maxTurns = maxTurns;
            this.seed = seed;
            this.deck1Path = deck1Path;
            this.deck2Path = deck2Path;
            this.mirrorSides = mirrorSides;
            this.metricsOutputPath = metricsOutputPath;
            this.strategy = strategy;
        }

        public static String defaultMetricsPath() {
            // Allow override via -Dmetrics_output_path for per-run isolation
            String override = System.getProperty("metrics_output_path");
            if (override != null && !override.isEmpty()) {
                return override;
            }
            return PROJECT_ROOT.resolve("logs").resolve("full_games").toString();
        }

        public static SimulationConfig createDefault(String deck1Path, String deck2Path) {
            return new SimulationConfig(
                    NUM_GAMES,
                    MAX_TURNS,
                    SEED.isEmpty() ? System.currentTimeMillis() : Long.parseLong(SEED),
                    deck1Path,
                    deck2Path,
                    false,
                    defaultMetricsPath(),
                    STRATEGY);
        }
    }

    /**
     * Result class for individual game outcomes
     */
    public static class GameResult {
        public final int gameIndex;
        public final String winner; // "PlayerA", "PlayerB", "Draw", "Timeout"
        public final int turnsPlayed;
        public final String errorMessage;
        public final long durationMs;

        public GameResult(int gameIndex, String winner, int turnsPlayed, String errorMessage, long durationMs) {
            this.gameIndex = gameIndex;
            this.winner = winner;
            this.turnsPlayed = turnsPlayed;
            this.errorMessage = errorMessage;
            this.durationMs = durationMs;
        }

        public boolean isError() {
            return errorMessage != null && !errorMessage.isEmpty();
        }

        public boolean isTimeout() {
            return "Timeout".equals(winner);
        }

        public boolean isDraw() {
            return "Draw".equals(winner);
        }
    }

    /**
     * Aggregate results for a simulation series
     */
    public static class SimulationResults {
        public final String matchup;
        public final int gamesRequested;
        public final int gamesCompleted;
        public final int winsPlayerA;
        public final int winsPlayerB;
        public final int draws;
        public final int timeouts;
        public final int errors;
        public final double avgTurns;
        public final double medianTurns;
        public final long totalDurationMs;
        public final long seed;
        public final List<GameResult> gameResults;

        public SimulationResults(String matchup, int gamesRequested, List<GameResult> gameResults, long seed) {
            this.matchup = matchup;
            this.gamesRequested = gamesRequested;
            this.gameResults = new ArrayList<>(gameResults);
            this.seed = seed;

            // Calculate aggregates
            this.gamesCompleted = (int) gameResults.stream().filter(r -> !r.isError()).count();
            this.winsPlayerA = (int) gameResults.stream().filter(r -> "PlayerA".equals(r.winner)).count();
            this.winsPlayerB = (int) gameResults.stream().filter(r -> "PlayerB".equals(r.winner)).count();
            this.draws = (int) gameResults.stream().filter(r -> r.isDraw()).count();
            this.timeouts = (int) gameResults.stream().filter(r -> r.isTimeout()).count();
            this.errors = (int) gameResults.stream().filter(r -> r.isError()).count();

            this.avgTurns = gameResults.stream()
                    .filter(r -> !r.isError())
                    .mapToInt(r -> r.turnsPlayed)
                    .average()
                    .orElse(0.0);

            List<Integer> turnCounts = gameResults.stream()
                    .filter(r -> !r.isError())
                    .map(r -> r.turnsPlayed)
                    .sorted()
                    .toList();

            this.medianTurns = turnCounts.isEmpty() ? 0.0
                    : turnCounts.size() % 2 == 0
                            ? (turnCounts.get(turnCounts.size() / 2 - 1) + turnCounts.get(turnCounts.size() / 2)) / 2.0
                            : turnCounts.get(turnCounts.size() / 2);

            this.totalDurationMs = gameResults.stream().mapToLong(r -> r.durationMs).sum();
        }

        public JSONObject toJson() {
            JSONObject obj = new JSONObject();
            obj.put("matchup", matchup);
            obj.put("games_requested", gamesRequested);
            obj.put("games_completed", gamesCompleted);
            obj.put("wins_playerA", winsPlayerA);
            obj.put("wins_playerB", winsPlayerB);
            obj.put("draws", draws);
            obj.put("timeouts", timeouts);
            obj.put("errors", errors);
            obj.put("avg_turns", Math.round(avgTurns * 100.0) / 100.0);
            obj.put("median_turns", Math.round(medianTurns * 100.0) / 100.0);
            obj.put("total_duration_ms", totalDurationMs);
            obj.put("seed", seed);
            return obj;
        }
    }

    /**
     * Run a series of simulation games with the given configuration.
     * This is the main entry point for simulation testing.
     */
    protected SimulationResults runSimulationSeries(SimulationConfig config) {
        System.out.println("[SIMULATION] Starting series: " + config.deck1Path + " vs " + config.deck2Path);
        System.out.println("[SIMULATION] Games: " + config.numGames + ", Max turns: " + config.maxTurns +
                ", Seed: " + config.seed + ", Strategy: " + config.strategy);

        List<GameResult> results = new ArrayList<>();
        Random random = new Random(config.seed);

        Path deck1File = resolveDeckPath(config.deck1Path);
        Path deck2File = resolveDeckPath(config.deck2Path);
        String deck1Resolved = deck1File.toString();
        String deck2Resolved = deck2File.toString();

        // Set system properties for the simulation
        System.setProperty("strategy", config.strategy);

        for (int gameIndex = 0; gameIndex < config.numGames; gameIndex++) {
            long gameStartTime = System.currentTimeMillis();

            // Mirror sides: swap decks every other game when enabled
            boolean swapped = config.mirrorSides && (gameIndex % 2 == 1);
            String gameDeck1 = swapped ? deck2Resolved : deck1Resolved;
            String gameDeck2 = swapped ? deck1Resolved : deck2Resolved;

            try {
                // Reset trajectory counters for this game (non-fatal if server not reachable)
                try {
                    httpPost(MAGELLMFAST_URL + "/__test__/reset_counters", "{}");
                } catch (Exception resetEx) {
                    System.err.println("[SIMULATION] Warning: could not reset counters: " + resetEx.getMessage());
                }

                // Create new game with seeded random
                long gameSeed = random.nextLong();

                // DAgger mixture: resolve the per-game pilot BEFORE player
                // creation; createNewPlayer and the players' provenance
                // stamps read magellm.daggerPilot.
                if ("dagger".equals(config.strategy)) {
                    double fraction = daggerFraction();
                    boolean student = daggerStudentDraw(gameSeed, fraction);
                    System.setProperty("magellm.daggerPilot", student ? "student" : "teacher");
                    System.out.println("[DAGGER] pilot=" + (student ? "student" : "teacher")
                            + " fraction=" + fraction + " gameSeed=" + gameSeed);
                }

                Game game = createGameWithDecks(gameDeck1, gameDeck2, gameSeed);

                GameOptions gameOptions = new GameOptions();
                gameOptions.testMode = false;
                gameOptions.skipInitShuffling = false;
                gameOptions.rollbackTurnsAllowed = false;
                game.setGameOptions(gameOptions);

                // Create players and add them to the game
                TestPlayer playerA = createPlayer(game, "PlayerA", gameDeck1);
                TestPlayer playerB = createPlayer(game, "PlayerB", gameDeck2);

                // Start the game – GameImpl drives phases internally (mirrors LoadTest
                // behaviour)
                game.start(playerA.getId());

                int turnsPlayed = game.getTurnNum();
                long gameDuration = System.currentTimeMillis() - gameStartTime;

                boolean timedOut = turnsPlayed > config.maxTurns;
                String winner;
                if (timedOut) {
                    winner = "Timeout";
                } else {
                    String rawWinner = game.getWinner();
                    if (rawWinner != null) {
                        if (rawWinner.contains(playerA.getName())) {
                            // When sides are swapped, PlayerA is playing deck2 so
                            // a "PlayerA seat" win is actually a "Deck2" win.
                            winner = swapped ? "PlayerB" : "PlayerA";
                        } else if (rawWinner.contains(playerB.getName())) {
                            winner = swapped ? "PlayerA" : "PlayerB";
                        } else {
                            winner = "Draw";
                        }
                    } else {
                        winner = "Draw";
                    }
                }

                results.add(new GameResult(gameIndex, winner, turnsPlayed, null, gameDuration));

                // Post game result to magellmfast for DN2 value function training
                // AND the monitor "Game Rates" panel.  Fire for EVERY terminal
                // outcome — decisive win, draw, AND timeout — not just decisive
                // games, so REMOTE BC worker pods report draws/timeouts identically
                // to LOCAL workers (parity).  The decisive-win payload keeps its
                // exact pre-existing shape (winner_player_id/loser_player_id/lives)
                // so DN2 value labelling is unaffected; the new "outcome" field lets
                // the server/monitor categorise without guessing.
                try {
                    mage.players.Player pA = game.getPlayer(playerA.getId());
                    mage.players.Player pB = game.getPlayer(playerB.getId());
                    String gameId  = game.getId() != null ? game.getId().toString() : "unknown";
                    JSONObject payload = new JSONObject();
                    payload.put("game_id",            gameId);
                    payload.put("match_id",           config.deck1Path + "_vs_" + config.deck2Path);
                    payload.put("turns",              turnsPlayed);
                    payload.put("strategy_winner",    config.strategy);
                    payload.put("strategy_loser",     config.strategy);
                    if (("PlayerA".equals(winner) || "PlayerB".equals(winner)) && pA != null && pB != null) {
                        // Decisive game — preserve the legacy decisive-win payload.
                        String winnerPlayerId = "PlayerA".equals(winner) ? playerA.getId().toString() : playerB.getId().toString();
                        String loserPlayerId  = "PlayerA".equals(winner) ? playerB.getId().toString() : playerA.getId().toString();
                        int winnerLife = "PlayerA".equals(winner) ? pA.getLife() : pB.getLife();
                        int loserLife  = "PlayerA".equals(winner) ? pB.getLife() : pA.getLife();
                        payload.put("outcome",            "win");
                        payload.put("winner_player_id",   winnerPlayerId);
                        payload.put("loser_player_id",    loserPlayerId);
                        payload.put("winner_final_life",  winnerLife);
                        payload.put("loser_final_life",   loserLife);
                    } else if ("Timeout".equals(winner)) {
                        payload.put("outcome", "timeout");
                    } else {
                        payload.put("outcome", "draw");
                    }
                    httpPost(MAGELLMFAST_URL + "/v1/game_result", payload.toString());
                } catch (Exception resultEx) {
                    System.err.println("[SIMULATION] Warning: could not post game_result: " + resultEx.getMessage());
                }

                String sideNote = swapped ? " [sides swapped]" : "";
                System.out.println(String.format("[SIMULATION] Game %d/%d: %s (%d turns, %dms)%s",
                        gameIndex + 1, config.numGames, winner, turnsPlayed, gameDuration, sideNote));

            } catch (Throwable e) {
                long gameDuration = System.currentTimeMillis() - gameStartTime;
                String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
                boolean isStall = msg.contains("Too much priority calls");
                String winner = isStall ? "Timeout" : "Error";
                results.add(new GameResult(gameIndex, winner, 0, msg, gameDuration));
                // Parity: also stream the non-decisive terminal outcome so remote
                // BC workers report timeouts/errors (an aborted game has no winner
                // or reliable life totals, so only the classification is posted).
                try {
                    JSONObject payload = new JSONObject();
                    payload.put("game_id",         "unknown");
                    payload.put("match_id",        config.deck1Path + "_vs_" + config.deck2Path);
                    payload.put("turns",           0);
                    payload.put("strategy_winner", config.strategy);
                    payload.put("strategy_loser",  config.strategy);
                    payload.put("outcome",         isStall ? "timeout" : "error");
                    httpPost(MAGELLMFAST_URL + "/v1/game_result", payload.toString());
                } catch (Exception postEx) {
                    System.err.println("[SIMULATION] Warning: could not post error game_result: " + postEx.getMessage());
                }
                System.err.println(String.format("[SIMULATION] Game %d/%d %s: %s",
                        gameIndex + 1, config.numGames, winner.toLowerCase(), msg));
            }
        }

        // Create results summary
        String matchup = deck1File.getFileName() + " vs " + deck2File.getFileName();
        SimulationResults simulationResults = new SimulationResults(matchup, config.numGames, results, config.seed);

        // Save metrics to file
        saveSimulationResults(simulationResults, config.metricsOutputPath);

        // Print summary
        System.out.println("[SIMULATION] Series complete:");
        System.out.println("  Matchup: " + simulationResults.matchup);
        System.out.println("  Completed: " + simulationResults.gamesCompleted + "/" + simulationResults.gamesRequested);
        System.out.println("  PlayerA wins: " + simulationResults.winsPlayerA);
        System.out.println("  PlayerB wins: " + simulationResults.winsPlayerB);
        System.out.println("  Draws: " + simulationResults.draws);
        System.out.println("  Timeouts: " + simulationResults.timeouts);
        System.out.println("  Errors: " + simulationResults.errors);
        System.out.println("  Avg turns: " + String.format("%.1f", simulationResults.avgTurns));
        System.out.println("  Total duration: " + simulationResults.totalDurationMs + "ms");

        return simulationResults;
    }

    /**
     * Create a game with the specified decks and seed
     */
    protected Game createGameWithDecks(String deck1Path, String deck2Path, long seed)
            throws GameException, FileNotFoundException {
        Game game = new TwoPlayerDuel(MultiplayerAttackOption.LEFT, RangeOfInfluence.ONE,
                MulliganType.GAME_DEFAULT.getMulligan(0), 60, 20, 7);

        // Note: Random seed setting not available in this version, using default
        return game;
    }

    @Override
    protected TestPlayer createNewPlayer(String playerName, RangeOfInfluence rangeOfInfluence) {
        TestPlayer player;
        String currentStrategy = System.getProperty("strategy", STRATEGY);
        if ("rl".equals(currentStrategy) || "rl_eval".equals(currentStrategy)) {
            // Competitive evaluation mode: CP8 (RL via HTTP) vs CP7 (MCTS).
            // PlayerA is the RL agent; PlayerB is the rule-based opponent.
            if ("PlayerA".equals(playerName)) {
                player = new TestPlayer(
                        new org.mage.test.player.TestComputerPlayer8(playerName, rangeOfInfluence, 8));
            } else {
                player = new TestPlayer(
                        new org.mage.test.player.TestComputerPlayer7(playerName, rangeOfInfluence, 8));
            }
        } else if ("self_play".equals(currentStrategy)) {
            // Self-play mode: both seats use CP8 (RL policy via HTTP to magellmfast).
            // Trajectories from both players are logged by the server for online training.
            player = new TestPlayer(
                    new org.mage.test.player.TestComputerPlayer8(playerName, rangeOfInfluence, 8));
        } else if ("dagger".equals(currentStrategy)) {
            // DAgger mixture mode: the per-game seeded draw (set in
            // runSimulationSeries via magellm.daggerPilot) decides PlayerA's
            // pilot for the whole game.  Student game: CP8 (RL) with shadow
            // CP7 labels; teacher game: CP7Instrumented — an ordinary expert
            // game.  PlayerB is always CP7Instrumented (learnings §9).
            boolean studentGame = "student".equals(
                    System.getProperty("magellm.daggerPilot", "teacher"));
            if (studentGame && "PlayerA".equals(playerName)) {
                player = new TestPlayer(
                        new org.mage.test.player.TestComputerPlayer8(playerName, rangeOfInfluence, 8));
            } else {
                player = new TestPlayer(
                        new org.mage.test.player.TestComputerPlayer7Instrumented(playerName, rangeOfInfluence, 8));
            }
        } else {
            // Data collection mode (strategy=mageai or default): both players instrumented
            // so trajectories are logged for BC training.
            player = new TestPlayer(
                    new org.mage.test.player.TestComputerPlayer7Instrumented(playerName, rangeOfInfluence, 8));
        }
        player.setAIPlayer(true);
        player.setTestMode(true);
        return player;
    }

    /**
     * Save simulation results to a JSON file
     */
    protected void saveSimulationResults(SimulationResults results, String outputPath) {
        try {
            // Ensure output directory exists
            Path outputDir = Paths.get(outputPath);
            Files.createDirectories(outputDir);

            // Create filename with timestamp
            String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date());
            String filename = String.format("simulation_%s_%s.json", timestamp,
                    results.matchup.replaceAll("[^a-zA-Z0-9_-]", "_"));

            Path outputFile = outputDir.resolve(filename);

            // Write JSON
            try (FileWriter writer = new FileWriter(outputFile.toFile())) {
                writer.write(results.toJson().toString(2));
            }

            System.out.println("[SIMULATION] Results saved to: " + outputFile.toAbsolutePath());

        } catch (IOException e) {
            System.err.println("[SIMULATION] Failed to save results: " + e.getMessage());
        }
    }

    private static Path detectProjectRoot() {
        // Walk up looking for the magellm project root.
        // We identify it by pyproject.toml (unique to the magellm repo root,
        // not present in the mage submodule).  Fall back to .git directory
        // (not file — submodules use a .git file) if pyproject.toml isn't found.
        Path current = Paths.get("").toAbsolutePath();
        while (current != null) {
            if (Files.exists(current.resolve("pyproject.toml"))) {
                return current;
            }
            current = current.getParent();
        }
        // Fallback: walk up looking for a .git *directory* (not file)
        current = Paths.get("").toAbsolutePath();
        while (current != null) {
            Path gitPath = current.resolve(".git");
            if (Files.isDirectory(gitPath)) {
                return current;
            }
            current = current.getParent();
        }
        return Paths.get("").toAbsolutePath();
    }

    protected Path resolveDeckPath(String deckPath) {
        Path raw = Paths.get(deckPath);
        if (Files.exists(raw)) {
            return raw.toAbsolutePath();
        }

        Path repoCandidate = PROJECT_ROOT.resolve(raw).normalize();
        if (Files.exists(repoCandidate)) {
            return repoCandidate.toAbsolutePath();
        }

        if (raw.getNameCount() > 1) {
            Path projectName = PROJECT_ROOT.getFileName();
            Path firstSegment = raw.getName(0);
            if (projectName != null && projectName.toString().equals(firstSegment.toString())) {
                Path trimmed = raw.subpath(1, raw.getNameCount());
                Path trimmedCandidate = PROJECT_ROOT.resolve(trimmed).normalize();
                if (Files.exists(trimmedCandidate)) {
                    return trimmedCandidate.toAbsolutePath();
                }
            }
        }

        throw new IllegalArgumentException("Deck file not found: " + deckPath);
    }

    // HTTP helpers (copied from LLMPuzzlesBase)
    protected static void httpPost(String urlString, String body) {
        try {
            java.net.URL url = java.net.URI.create(urlString).toURL();
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(3000);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setDoOutput(true);
            try (java.io.OutputStream os = conn.getOutputStream()) {
                byte[] input = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            int code = conn.getResponseCode();
            if (code != 200) {
                throw new RuntimeException("POST failed: " + urlString + ", code=" + code);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected static JSONObject httpGetJson(String urlString) {
        try {
            java.net.URL url = java.net.URI.create(urlString).toURL();
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(3000);
            int code = conn.getResponseCode();
            if (code != 200) {
                throw new RuntimeException("GET failed: " + urlString + ", code=" + code);
            }
            try (java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(conn.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
                return new JSONObject(sb.toString());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
