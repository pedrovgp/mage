package mage.player.ai;

import mage.abilities.Ability;
import mage.cards.Card;
import mage.cards.decks.Deck;
import mage.choices.Choice;
import mage.constants.Outcome;
import mage.game.Game;
import mage.game.GameState;
import mage.game.match.MatchPlayer;
import mage.game.permanent.Permanent;
import mage.players.Player;
import mage.abilities.costs.mana.ColoredManaCost;
import mage.abilities.costs.mana.GenericManaCost;
import mage.abilities.costs.mana.ManaCost;
import mage.cards.o.Ornithopter;
import mage.view.GameView;
import mage.view.PlayerView;
import mage.view.PermanentView;
import mage.view.CardView;
import mage.view.CardsView;
import mage.view.ManaPoolView;
import mage.view.CombatGroupView;

import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-game wall-clock timing statistics for all decision types.
 *
 * Single-instance per JVM via INSTANCE. A JVM shutdown hook prints the table
 * to stdout so it is captured in the worker log regardless of how the JVM exits.
 *
 * Thread-safety: uses AtomicLong so concurrent games in the same JVM (rare in
 * self-play, but possible) accumulate correctly.
 */
class DecisionStats {
    static final DecisionStats INSTANCE = new DecisionStats();

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(
            () -> INSTANCE.printSummary(), "decision-stats-printer"));
    }

    // Per-endpoint: count, serialisation (payload build), http, informPlayers, total
    private final AtomicLong actionCount    = new AtomicLong();
    private final AtomicLong actionSerial   = new AtomicLong();
    private final AtomicLong actionHttp     = new AtomicLong();
    private final AtomicLong actionInform   = new AtomicLong();

    private final AtomicLong choiceCount    = new AtomicLong();
    private final AtomicLong choiceSerial   = new AtomicLong();
    private final AtomicLong choiceHttp     = new AtomicLong();
    private final AtomicLong choiceInform   = new AtomicLong();

    private final AtomicLong attackerCount  = new AtomicLong();
    private final AtomicLong attackerSerial = new AtomicLong();
    private final AtomicLong attackerHttp   = new AtomicLong();
    private final AtomicLong attackerInform = new AtomicLong();

    private final AtomicLong targetCount    = new AtomicLong();
    private final AtomicLong targetSerial   = new AtomicLong();
    private final AtomicLong targetHttp     = new AtomicLong();
    private final AtomicLong targetInform   = new AtomicLong();

    // Local decisions (never go to RL server)
    private final AtomicLong localTargetCount = new AtomicLong();
    private final AtomicLong localTargetNs    = new AtomicLong();
    private final AtomicLong localBlockerCount = new AtomicLong();
    private final AtomicLong localBlockerNs    = new AtomicLong();

    // getPlayable() calls (called before every RL action decision)
    private final AtomicLong getPlayableCount = new AtomicLong();
    private final AtomicLong getPlayableNs    = new AtomicLong();

    void recordAction(long serialNs, long httpNs, long informNs) {
        actionCount.incrementAndGet();
        actionSerial.addAndGet(serialNs);
        actionHttp.addAndGet(httpNs);
        actionInform.addAndGet(informNs);
    }

    void recordChoice(long serialNs, long httpNs, long informNs) {
        choiceCount.incrementAndGet();
        choiceSerial.addAndGet(serialNs);
        choiceHttp.addAndGet(httpNs);
        choiceInform.addAndGet(informNs);
    }

    void recordAttackers(long serialNs, long httpNs, long informNs) {
        attackerCount.incrementAndGet();
        attackerSerial.addAndGet(serialNs);
        attackerHttp.addAndGet(httpNs);
        attackerInform.addAndGet(informNs);
    }

    void recordTarget(long serialNs, long httpNs, long informNs) {
        targetCount.incrementAndGet();
        targetSerial.addAndGet(serialNs);
        targetHttp.addAndGet(httpNs);
        targetInform.addAndGet(informNs);
    }

    void recordLocalTarget(long elapsedNs) {
        localTargetCount.incrementAndGet();
        localTargetNs.addAndGet(elapsedNs);
    }

    void recordLocalBlockers(long elapsedNs) {
        localBlockerCount.incrementAndGet();
        localBlockerNs.addAndGet(elapsedNs);
    }

    void recordGetPlayable(long elapsedNs) {
        getPlayableCount.incrementAndGet();
        getPlayableNs.addAndGet(elapsedNs);
    }

    private static String ms(long ns) {
        return String.format("%8.1f", ns / 1_000_000.0);
    }

    void printSummary() {
        long totalRlNs =
            actionSerial.get() + actionHttp.get() + actionInform.get() +
            choiceSerial.get() + choiceHttp.get() + choiceInform.get() +
            attackerSerial.get() + attackerHttp.get() + attackerInform.get() +
            targetSerial.get() + targetHttp.get() + targetInform.get();
        long totalLocalNs = localTargetNs.get() + localBlockerNs.get();
        long grandTotal = totalRlNs + totalLocalNs + getPlayableNs.get();

        // Build the report as a String so we can write it to both stderr and a stats file.
        // We write to System.err (not System.out) because Maven's -q flag suppresses
        // System.out from the Surefire-forked JVM but does NOT suppress System.err.
        StringBuilder sb = new StringBuilder();
        sb.append("\n=== DECISION STATS (ms) ===\n");
        sb.append(String.format("  %-28s  %6s  %10s  %10s  %10s  %10s%n",
            "endpoint", "count", "serial", "http", "inform", "total"));
        sb.append(String.format("  %-28s  %6s  %10s  %10s  %10s  %10s%n",
            "----------------------------", "------",
            "----------", "----------", "----------", "----------"));
        appendRlRow(sb, "/choose_from_all_actions",
            actionCount.get(), actionSerial.get(), actionHttp.get(), actionInform.get());
        appendRlRow(sb, "/choose_from_choices",
            choiceCount.get(), choiceSerial.get(), choiceHttp.get(), choiceInform.get());
        appendRlRow(sb, "/choose_attackers",
            attackerCount.get(), attackerSerial.get(), attackerHttp.get(), attackerInform.get());
        appendRlRow(sb, "/choose_targets",
            targetCount.get(), targetSerial.get(), targetHttp.get(), targetInform.get());
        appendLocalRow(sb, "LOCAL chooseTarget",   localTargetCount.get(),  localTargetNs.get());
        appendLocalRow(sb, "LOCAL selectBlockers", localBlockerCount.get(), localBlockerNs.get());
        appendLocalRow(sb, "getPlayable()",        getPlayableCount.get(),  getPlayableNs.get());
        sb.append(String.format("  %-28s  %6s  %10s  %10s  %10s  %10s%n",
            "----------------------------", "------",
            "----------", "----------", "----------", "----------"));
        sb.append(String.format("  %-28s  %30s  %10s%n",
            "TOTAL (RL decisions)",  "", ms(totalRlNs)));
        sb.append(String.format("  %-28s  %30s  %10s%n",
            "TOTAL (all incl local)", "", ms(grandTotal)));
        sb.append("===========================\n");

        String report = sb.toString();

        // 1. Write to stderr — bypasses Maven's -q stdout filter and lands in the
        //    Maven log file (Python captures both stdout+stderr to game_N_mvn.log).
        System.err.print(report);

        // 2. Write to a dedicated stats file so results are always findable even
        //    if the stderr stream is lost.  File name matches the JFR file.
        //    Location: <repo>/logs/sp_profiles/stats/game_{seed}.txt
        //    We derive the repo root by going two levels up from the mage/ dir
        //    (Maven cwd is <repo>/mage/).
        try {
            String dbDir = System.getProperty("mage.dbDir", "");
            // dbDir pattern: /tmp/mage_db_sp_g{seed}_{random}
            String seedTag;
            java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("mage_db_sp_g(\\d+)_").matcher(dbDir);
            if (m.find()) {
                seedTag = "game_" + m.group(1);
            } else {
                seedTag = "game_" + System.currentTimeMillis();
            }
            // Surefire forks the test JVM with cwd = <module-dir> (mage/Mage.Tests/),
            // so we need to go up TWO levels to reach the repo root.
            java.io.File repoRoot = new java.io.File(System.getProperty("user.dir", "."))
                .getParentFile()   // mage/Mage.Tests → mage/
                .getParentFile();  // mage/ → repo root
            java.io.File statsDir = new java.io.File(repoRoot, "logs/sp_profiles/stats");
            statsDir.mkdirs();
            java.io.File statsFile = new java.io.File(statsDir, seedTag + ".txt");
            try (java.io.PrintWriter pw = new java.io.PrintWriter(
                    new java.io.FileWriter(statsFile, java.nio.charset.StandardCharsets.UTF_8))) {
                pw.print(report);
            }
        } catch (Exception e) {
            System.err.println("[DecisionStats] Could not write stats file: " + e.getMessage());
        }
    }

    private static void appendRlRow(StringBuilder sb, String name,
            long count, long serial, long http, long inform) {
        long total = serial + http + inform;
        sb.append(String.format("  %-28s  %6d  %10s  %10s  %10s  %10s%n",
            name, count, ms(serial), ms(http), ms(inform), ms(total)));
    }

    private static void appendLocalRow(StringBuilder sb, String name, long count, long ns) {
        sb.append(String.format("  %-28s  %6d  %10s  %10s  %10s  %10s%n",
            name, count, ms(ns), "", "", ms(ns)));
    }
}

/**
 * Unified decision handler that consolidates all LLM decision logic.
 * Handles payload building, communication with magellmfast, and result
 * processing.
 */
public class DecisionHandler {
    private static final Logger logger = Logger.getLogger(DecisionHandler.class);

    // Endpoint constants aligned with magellmfast routes (relative paths, base URL
    // added by LlmDecisionClient)
    private static final String ENDPOINT_CHOOSE_FROM_ALL_ACTIONS = "/choose_from_all_actions";
    private static final String ENDPOINT_CHOOSE_ATTACKERS = "/choose_attackers";
    private static final String ENDPOINT_CHOOSE_FROM_CHOICES = "/choose_from_choices";
    private static final String ENDPOINT_CHOOSE_TARGETS = "/choose_targets";
    private static final String ENDPOINT_CHOOSE_TARGET_AMOUNT = "/chooseTargetAmount";
    private static final String ENDPOINT_LOG_TRAJECTORY = "/v1/log_trajectory";
    private static final String ENDPOINT_SHADOW_AGREEMENT = "/v1/shadow_agreement";

    private final LlmDecisionClient client;
    private final ObjectMapper objectMapper;

    public DecisionHandler(String baseUrl) {
        this.client = new LlmDecisionClient(baseUrl);
        this.objectMapper = createConfiguredObjectMapper();
    }

    public DecisionHandler(LlmDecisionClient client) {
        this.client = client;
        this.objectMapper = createConfiguredObjectMapper();
    }

    /**
     * Handle action selection decisions (choosing from list of abilities).
     * Times serialisation, HTTP, and informPlayers separately and records to DecisionStats.
     */
    public DecisionResult handleAction(Game game, Player currentPlayer, List<Ability> allActions, String strategy) {
        try {
            long t0 = System.nanoTime();
            JSONObject payload = buildChooseFromAllActionsPayload(game, currentPlayer, allActions, strategy);
            long t1 = System.nanoTime();
            DecisionPayload dp = new DecisionPayload(ENDPOINT_CHOOSE_FROM_ALL_ACTIONS, payload);
            DecisionResult result = client.requestDecision(dp);
            long t2 = System.nanoTime();
            informChosenAction(game, currentPlayer, allActions, result);
            long t3 = System.nanoTime();
            DecisionStats.INSTANCE.recordAction(t1 - t0, t2 - t1, t3 - t2);
            logDecision("ACTION", allActions.size(), result);
            return result;
        } catch (Exception e) {
            logger.error("Failed to handle action decision", e);
            return new DecisionResult(0, null, "fallback_to_first_action");
        }
    }

    /**
     * Handle choice selection decisions (choosing from array of choices).
     * Times serialisation, HTTP, and informPlayers separately and records to DecisionStats.
     */
    public DecisionResult handleChoice(Game game, Player currentPlayer, Outcome outcome,
            Choice choice, String[] allChoices, String strategy) {
        try {
            long t0 = System.nanoTime();
            JSONObject payload = buildChooseFromChoicesPayload(game, currentPlayer, outcome, choice, allChoices,
                    strategy);
            long t1 = System.nanoTime();
            DecisionPayload dp = new DecisionPayload(ENDPOINT_CHOOSE_FROM_CHOICES, payload);
            DecisionResult result = client.requestDecision(dp);
            long t2 = System.nanoTime();
            informChosenChoice(game, currentPlayer, allChoices, result);
            long t3 = System.nanoTime();
            DecisionStats.INSTANCE.recordChoice(t1 - t0, t2 - t1, t3 - t2);
            logDecision("CHOICE", allChoices.length, result);
            return result;
        } catch (Exception e) {
            logger.error("Failed to handle choice decision", e);
            return new DecisionResult(0, null, "fallback_to_first_choice");
        }
    }

    /**
     * Handle attacker selection decisions.
     * Times serialisation, HTTP, and informPlayers separately and records to DecisionStats.
     */
    public DecisionResult handleAttackers(Game game, Player currentPlayer,
            List<Permanent> possibleAttackers,
            List<Permanent> possibleBlockers, String strategy) {
        try {
            long t0 = System.nanoTime();
            JSONObject payload = buildChooseAttackersPayload(game, currentPlayer, possibleAttackers, possibleBlockers,
                    strategy);
            long t1 = System.nanoTime();
            DecisionPayload dp = new DecisionPayload(ENDPOINT_CHOOSE_ATTACKERS, payload);
            DecisionResult result = client.requestDecision(dp);
            long t2 = System.nanoTime();
            informChosenAttackers(game, currentPlayer, possibleAttackers, result);
            long t3 = System.nanoTime();
            DecisionStats.INSTANCE.recordAttackers(t1 - t0, t2 - t1, t3 - t2);
            logDecision("ATTACKERS", possibleAttackers.size(), result);
            return result;
        } catch (Exception e) {
            logger.error("Failed to handle attacker decision", e);
            return new DecisionResult(null, List.of(), "fallback_no_attackers");
        }
    }

    /**
     * Handle target selection decisions.
     * Times serialisation, HTTP, and informPlayers separately and records to DecisionStats.
     */
    public DecisionResult handleTargets(Game game, Player currentPlayer, Outcome outcome,
            String[] allChoices, String strategy) {
        try {
            long t0 = System.nanoTime();
            // Create a dummy choice object since the endpoint requires it
            Choice choice = new mage.choices.ChoiceImpl(true);
            choice.setMessage("Choose target");

            JSONObject payload = buildChooseFromChoicesPayload(game, currentPlayer, outcome, choice, allChoices,
                    strategy);
            long t1 = System.nanoTime();
            DecisionPayload dp = new DecisionPayload(ENDPOINT_CHOOSE_TARGETS, payload);
            DecisionResult result = client.requestDecision(dp);
            long t2 = System.nanoTime();
            informChosenReason(game, currentPlayer, "TARGET", result);
            long t3 = System.nanoTime();
            DecisionStats.INSTANCE.recordTarget(t1 - t0, t2 - t1, t3 - t2);
            logDecision("TARGET", allChoices.length, result);
            return result;
        } catch (Exception e) {
            logger.error("Failed to handle target decision", e);
            return new DecisionResult(0, null, "fallback_to_first_target");
        }
    }

    /**
     * Handle target amount selection decisions (chooseTargetAmount endpoint)
     */
    public DecisionResult handleChooseTargetAmount(Game game, Player currentPlayer,
            List<String> targetIds, int minAmount, int maxAmount, String strategy) {
        try {
            JSONObject payload = buildChooseTargetAmountPayload(game, currentPlayer, targetIds, minAmount, maxAmount,
                    strategy);
            DecisionPayload dp = new DecisionPayload(ENDPOINT_CHOOSE_TARGET_AMOUNT, payload);
            DecisionResult result = client.requestDecision(dp);

            logDecision("TARGET_AMOUNT", targetIds.size(), result);
            return result;
        } catch (Exception e) {
            logger.error("Failed to handle target amount decision", e);
            List<UUID> fallbackUuids = targetIds.isEmpty() ? List.of() : List.of(UUID.fromString(targetIds.get(0)));
            return new DecisionResult(null, fallbackUuids, "fallback_to_first_target");
        }
    }

    /**
     * Handle trajectory logging for RL training data collection
     */
    public DecisionResult handleLogTrajectory(Game game, Player currentPlayer, String decisionType,
            Object availableActions, Map<String, Object> chosenAction, Map<String, Object> additionalContext,
            String strategy) {
        try {
            JSONObject payload = buildTrajectoryPayload(game, currentPlayer, decisionType, availableActions,
                    chosenAction, additionalContext);
            DecisionPayload dp = new DecisionPayload(ENDPOINT_LOG_TRAJECTORY, payload);
            DecisionResult result = client.requestDecision(dp);

            logDecision("TRAJECTORY_LOG", 1, result);
            return result;
        } catch (Exception e) {
            logger.error("Failed to handle trajectory logging", e);
            // Include 'fallback' in reason to satisfy tests that assert fallback wording
            return new DecisionResult(null, null, "fallback_trajectory_logging");
        }
    }

    /**
     * Best-effort POST of a CP7-shadow-vs-RL agreement record to
     * /v1/shadow_agreement (read-only research probe; see ComputerPlayer8).
     * Failures are swallowed: a logging outage must never affect the game.
     */
    public void postShadowAgreement(JSONObject payload) {
        try {
            DecisionPayload dp = new DecisionPayload(ENDPOINT_SHADOW_AGREEMENT, payload);
            client.requestDecision(dp);
        } catch (Exception e) {
            logger.debug("shadow_agreement post failed (ignored): " + e.getMessage());
        }
    }

    /**
     * Build trajectory logging payload for RL training data collection.
     * This is used by ComputerPlayer7Instrumented to log decision trajectories.
     */
    public JSONObject buildTrajectoryPayload(Game game, Player currentPlayer, String decisionType,
            Object availableActions, Map<String, Object> chosenAction, Map<String, Object> additionalContext) {
        // Start with DecisionBase fields using existing buildBasePayload method
        JSONObject payload = buildDecisionBasePayload(game, currentPlayer, "trajectory");

        // Add trajectory-specific fields (LogTrajectoryCreate schema)
        payload.put("decisionType", decisionType);
        payload.put("gameIsOver", game.checkIfGameIsOver());
        payload.put("game", convertObjectToJson(game));
        payload.put("currentPlayerId", currentPlayer.getId().toString());

        // Handle availableActions - convert to JSONArray or empty array if null
        if (availableActions != null) {
            Object actionsJson = convertObjectToJson(availableActions);
            if (actionsJson instanceof JSONArray) {
                payload.put("availableActions", actionsJson);
            } else if (actionsJson instanceof JSONObject &&
                    ((JSONObject) actionsJson).has("error")) {
                // Handle conversion error - use empty array
                payload.put("availableActions", new JSONArray());
            } else {
                // Convert single action to array
                JSONArray actionsArray = new JSONArray();
                actionsArray.put(actionsJson);
                payload.put("availableActions", actionsArray);
            }
        } else {
            // Use empty array instead of null for Pydantic validation
            payload.put("availableActions", new JSONArray());
        }

        // Handle chosenAction
        if (chosenAction != null && !chosenAction.isEmpty()) {
            payload.put("chosenAction", convertObjectToJson(chosenAction));
        } else {
            payload.put("chosenAction", JSONObject.NULL);
        }

        // Handle additionalContext — serialize each entry individually.
        // Jackson cannot serialize List<Map<String,Object>> stored as Object in a Map
        // (it falls back to bean introspection and emits {"empty":false}).
        // Instead, we manually build JSONArrays for List values via org.json so that
        // payload.toString() serializes them correctly without Jackson involvement.
        if (additionalContext != null && !additionalContext.isEmpty()) {
            JSONObject ctxJson = new JSONObject();
            for (java.util.Map.Entry<String, Object> entry : additionalContext.entrySet()) {
                Object val = entry.getValue();
                if (val instanceof java.util.List) {
                    JSONArray arr = new JSONArray();
                    for (Object item : (java.util.List<?>) val) {
                        if (item instanceof java.util.Map) {
                            @SuppressWarnings("unchecked")
                            java.util.Map<String, Object> m = (java.util.Map<String, Object>) item;
                            JSONObject itemObj = new JSONObject();
                            for (java.util.Map.Entry<String, Object> me : m.entrySet()) {
                                itemObj.put(me.getKey(), me.getValue() != null ? me.getValue() : JSONObject.NULL);
                            }
                            arr.put(itemObj);
                        } else {
                            arr.put(item != null ? item : JSONObject.NULL);
                        }
                    }
                    ctxJson.put(entry.getKey(), arr);
                } else {
                    ctxJson.put(entry.getKey(), convertObjectToJson(val));
                }
            }
            payload.put("additionalContext", ctxJson);
        } else {
            payload.put("additionalContext", JSONObject.NULL);
        }

        return payload;
    }

    // ================================================================================
    // SCHEMA-SPECIFIC PAYLOAD BUILDERS (aligned with schemas.py)
    // ================================================================================

    /**
     * Build payload for ChooseFromAllActionsCreate schema
     */
    private JSONObject buildChooseFromAllActionsPayload(Game game, Player currentPlayer, List<Ability> allActions,
            String strategy) {
        JSONObject payload = buildDecisionBasePayload(game, currentPlayer, strategy);
        payload.put("allActions", convertObjectToJson(allActions));
        return payload;
    }

    /**
     * Build payload for ChooseAttackersCreate schema
     */
    private JSONObject buildChooseAttackersPayload(Game game, Player currentPlayer,
            List<Permanent> possibleAttackers, List<Permanent> possibleBlockers, String strategy) {
        JSONObject payload = buildDecisionBasePayload(game, currentPlayer, strategy);
        payload.put("possibleAttackers", convertObjectToJson(possibleAttackers));
        payload.put("possibleBlockers", convertObjectToJson(possibleBlockers));
        return payload;
    }

    /**
     * Build payload for ChooseFromChoicesCreate schema
     */
    private JSONObject buildChooseFromChoicesPayload(Game game, Player currentPlayer, Outcome outcome,
            Choice choice, String[] allChoices, String strategy) {
        JSONObject payload = buildDecisionBasePayload(game, currentPlayer, strategy);
        payload.put("outcome", convertObjectToJson(outcome));
        payload.put("choice", convertObjectToJson(choice));
        payload.put("allChoices", convertObjectToJson(allChoices));
        return payload;
    }

    /**
     * Build payload for ChooseTargetAmountCreate schema
     */
    private JSONObject buildChooseTargetAmountPayload(Game game, Player currentPlayer,
            List<String> targetIds, int minAmount, int maxAmount, String strategy) {
        JSONObject payload = buildBaseRequestPayload(game, currentPlayer, strategy);
        payload.put("targetIds", convertObjectToJson(targetIds));
        payload.put("minAmount", minAmount);
        payload.put("maxAmount", maxAmount);
        return payload;
    }

    // ================================================================================
    // HELPER METHODS FOR PAYLOAD BUILDING
    // ================================================================================

    /**
     * Build DecisionBase payload with common fields for decision schemas
     */
    private JSONObject buildDecisionBasePayload(Game game, Player currentPlayer, String strategy) {
        JSONObject payload = buildBaseRequestPayload(game, currentPlayer, strategy);

        // Add DecisionBase-specific fields (true state)
        payload.put("gameCards", convertObjectToJson(game.getCards()));
        payload.put("gameState", convertObjectToJson(game.getState()));
        payload.put("currentPlayer", convertObjectToJson(currentPlayer));
        payload.put("opponentPlayer", convertObjectToJson(findOpponent(game, currentPlayer)));

        // Add information state (GameView) — uses XMage's built-in information hiding
        payload.put("gameView", buildGameViewJson(game, currentPlayer));

        return payload;
    }

    /**
     * Build information-state JSON from XMage's GameView.
     *
     * Constructs a real GameView (which applies XMage's information boundary:
     * opponent hand hidden, face-down permanents masked, etc.) then extracts
     * only the strategically relevant fields into a clean JSONObject.
     *
     * Falls back to empty JSONObject if GameView construction fails.
     */
    private JSONObject buildGameViewJson(Game game, Player currentPlayer) {
        try {
            GameView gameView = new GameView(game.getState(), game, currentPlayer.getId(), null);
            return serializeGameView(gameView);
        } catch (Exception e) {
            logger.error("[DN1a] Failed to build GameView for player " + currentPlayer.getId() + ": " + e.getMessage(), e);
            return new JSONObject();
        }
    }

    /**
     * Serialize a GameView into a clean JSONObject with only strategically
     * relevant fields. Does NOT reimplement information hiding — that is handled
     * by XMage's GameView/PlayerView constructors.
     */
    private JSONObject serializeGameView(GameView gameView) {
        JSONObject result = new JSONObject();

        // Phase / turn metadata
        result.put("phase", gameView.getPhase() != null ? gameView.getPhase().toString() : "");
        result.put("step", gameView.getStep() != null ? gameView.getStep().toString() : "");
        result.put("turn", gameView.getTurn());
        result.put("activePlayerId", gameView.getActivePlayerId() != null
                ? gameView.getActivePlayerId().toString() : "");

        // My player (full hand visible)
        PlayerView myPlayerView = gameView.getMyPlayer();
        if (myPlayerView != null) {
            result.put("myPlayer", serializePlayerView(myPlayerView, gameView.getMyHand()));
        } else {
            result.put("myPlayer", new JSONObject());
        }

        // Opponent player (hand cards hidden — handCount only)
        PlayerView opponentView = null;
        for (PlayerView pv : gameView.getPlayers()) {
            if (myPlayerView == null || !pv.getPlayerId().equals(myPlayerView.getPlayerId())) {
                opponentView = pv;
                break;
            }
        }
        if (opponentView != null) {
            result.put("opponentPlayer", serializePlayerView(opponentView, null));
        } else {
            result.put("opponentPlayer", new JSONObject());
        }

        // Stack (public)
        result.put("stack", serializeCardsView(gameView.getStack()));

        // Combat groups
        JSONArray combatArray = new JSONArray();
        for (CombatGroupView cg : gameView.getCombat()) {
            JSONObject cgObj = new JSONObject();
            cgObj.put("attackers", serializeCardsView(cg.getAttackers()));
            cgObj.put("blockers", serializeCardsView(cg.getBlockers()));
            combatArray.put(cgObj);
        }
        result.put("combat", combatArray);

        return result;
    }

    /**
     * Serialize a PlayerView into a JSONObject.
     *
     * @param pv        The PlayerView to serialize (may be own player or opponent)
     * @param handCards The actual hand cards (non-null only for own player, from
     *                  GameView.getMyHand()). Pass null for opponent to enforce
     *                  information boundary.
     */
    private JSONObject serializePlayerView(PlayerView pv, CardsView handCards) {
        JSONObject obj = new JSONObject();
        obj.put("id", pv.getPlayerId().toString());
        obj.put("name", pv.getName());
        obj.put("life", pv.getLife());
        obj.put("handCount", pv.getHandCount());
        obj.put("libraryCount", pv.getLibraryCount());

        // Hand cards: full details for own player, empty for opponent
        if (handCards != null) {
            obj.put("handCards", serializeCardsView(handCards));
        } else {
            obj.put("handCards", new JSONArray());
        }

        // Top card (if revealed)
        if (pv.getTopCard() != null) {
            obj.put("topCard", serializeCardView(pv.getTopCard()));
        } else {
            obj.put("topCard", JSONObject.NULL);
        }

        // Battlefield permanents
        JSONArray battlefield = new JSONArray();
        for (PermanentView permanentView : pv.getBattlefield().values()) {
            battlefield.put(serializePermanentView(permanentView));
        }
        obj.put("battlefield", battlefield);

        // Public zones
        obj.put("graveyard", serializeCardsView(pv.getGraveyard()));
        obj.put("exile", serializeCardsView(pv.getExile()));

        // Mana pool
        obj.put("manaPool", serializeManaPool(pv.getManaPool()));

        return obj;
    }

    /**
     * Serialize a CardsView (map of UUID → CardView) into a JSONArray.
     */
    private JSONArray serializeCardsView(CardsView cv) {
        JSONArray arr = new JSONArray();
        if (cv == null) {
            return arr;
        }
        for (CardView cardView : cv.values()) {
            arr.put(serializeCardView(cardView));
        }
        return arr;
    }

    /**
     * Serialize a CardView into a JSONObject with strategically relevant fields.
     * Face-down card names are already masked by XMage's view layer.
     */
    private JSONObject serializeCardView(CardView cv) {
        JSONObject obj = new JSONObject();
        obj.put("id", cv.getId() != null ? cv.getId().toString() : "");
        obj.put("name", cv.getName() != null ? cv.getName() : "");
        obj.put("power", cv.getPower() != null ? cv.getPower() : "");
        obj.put("toughness", cv.getToughness() != null ? cv.getToughness() : "");
        obj.put("manaCost", cv.getManaCostStr() != null ? cv.getManaCostStr() : "");
        obj.put("typeText", cv.getTypeText() != null ? cv.getTypeText() : "");
        // Rules list
        JSONArray rules = new JSONArray();
        if (cv.getRules() != null) {
            for (String rule : cv.getRules()) {
                rules.put(rule);
            }
        }
        obj.put("rules", rules);
        return obj;
    }

    /**
     * Serialize a PermanentView (extends CardView) into a JSONObject.
     * Includes battlefield-specific fields: tapped, face-down, counters, etc.
     */
    private JSONObject serializePermanentView(PermanentView pv) {
        JSONObject obj = serializeCardView(pv);
        obj.put("tapped", pv.isTapped());
        obj.put("faceDown", pv.isMorphed() || pv.isManifested() || pv.isCloaked() || pv.isDisguised());
        obj.put("controlled", pv.isControlled());
        obj.put("damage", pv.getDamage());
        obj.put("summoningSickness", pv.hasSummoningSickness());
        obj.put("attachedTo", pv.getAttachedTo() != null ? pv.getAttachedTo().toString() : JSONObject.NULL);
        return obj;
    }

    /**
     * Serialize a ManaPoolView into a JSONObject.
     */
    private JSONObject serializeManaPool(ManaPoolView mp) {
        JSONObject obj = new JSONObject();
        if (mp == null) {
            return obj;
        }
        obj.put("red", mp.getRed());
        obj.put("green", mp.getGreen());
        obj.put("blue", mp.getBlue());
        obj.put("white", mp.getWhite());
        obj.put("black", mp.getBlack());
        obj.put("colorless", mp.getColorless());
        return obj;
    }

    /**
     * Build BaseRequest payload with common fields for all request schemas
     */
    private JSONObject buildBaseRequestPayload(Game game, Player currentPlayer, String strategy) {
        JSONObject payload = new JSONObject();

        // Add BaseRequest fields
        payload.put("strategy", strategy);
        payload.put("gameId", getGameId(game));
        payload.put("matchId", getMatchId(game));

        // logTrajectory: controlled by a JVM system property.
        // Defaults to true — trajectory logging is on by default.
        // Benchmark clients are launched with -Dmagellmfast.logTrajectory=false
        // to opt out; self-play workers rely on the default.
        boolean logTraj = Boolean.parseBoolean(
            System.getProperty("magellmfast.logTrajectory", "true"));
        payload.put("logTrajectory", logTraj);

        // strategyId: sub-strategy selector within the rl branch.
        // Set via -Dstrategy.id=<id> (e.g. cosine, zone, noop).
        // Absent → Python server defaults to "cosine" (back-compat).
        String strategyId = System.getProperty("strategy.id");
        if (strategyId != null && !strategyId.isEmpty()) {
            payload.put("strategyId", strategyId);
        }

        return payload;
    }

    /**
     * Helper method to find opponent player
     */
    private Player findOpponent(Game game, Player currentPlayer) {
        for (UUID opponentId : game.getState().getPlayers().keySet()) {
            if (!opponentId.equals(currentPlayer.getId())) {
                return game.getPlayer(opponentId);
            }
        }
        return null;
    }

    /**
     * Generate game ID from game instance
     */
    private String getGameId(Game game) {
        return game.getId().toString();
    }

    /**
     * Generate match ID from game instance
     */
    private String getMatchId(Game game) {
        return "match_" + game.getGameType().toString();
    }

    /**
     * Log decision results for debugging
     */
    private void logDecision(String decisionType, int optionsCount, DecisionResult result) {
        if (logger.isInfoEnabled()) {
            String chosen = result.getChosenIndex() != null ? "index " + result.getChosenIndex()
                    : "uuids " + (result.getChosenUuids() != null ? result.getChosenUuids().size() : 0);
            logger.info(String.format("%s decision: %s from %d options - %s",
                    decisionType, chosen, optionsCount, result.getReason()));
        }
    }

    /**
     * Convert objects to JSON using configured ObjectMapper
     */
    public Object convertObjectToJson(Object obj) {
        try {
            // Handle null objects explicitly - return empty array for availableActions
            // compatibility
            if (obj == null) {
                return new JSONArray(); // Return empty array instead of empty object for Pydantic list validation
            }

            String jsonString = objectMapper.writeValueAsString(obj);
            if (jsonString.equals("null")) {
                return new JSONArray(); // Return empty array for null serialization
            } else if (jsonString.startsWith("{")) {
                return new JSONObject(jsonString);
            } else if (jsonString.startsWith("[")) {
                return new JSONArray(jsonString);
            } else {
                // Handle primitive values and other simple types
                return jsonString.replaceAll("\"", ""); // Remove quotes from simple string values
            }
        } catch (JsonProcessingException e) {
            String className = obj != null ? obj.getClass().getName() : "null";
            logger.error("Error converting object to JSON: " + className, e);
            // For trajectory logging, return empty array instead of error object to
            // maintain Pydantic compatibility
            logger.warn("Returning empty array for failed JSON conversion to maintain Pydantic compatibility");
            return new JSONArray();
        }
    }

    /**
     * Helper method to put JSON fields with proper type handling
     */
    private void putJsonField(JSONObject payload, String fieldName, Object jsonValue) {
        if (jsonValue instanceof JSONObject) {
            payload.put(fieldName, (JSONObject) jsonValue);
        } else if (jsonValue instanceof JSONArray) {
            payload.put(fieldName, (JSONArray) jsonValue);
        } else {
            payload.put(fieldName, jsonValue.toString());
        }
    }

    /**
     * Create and configure ObjectMapper with all necessary mixins and serializers
     */
    private ObjectMapper createConfiguredObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

        // Add all the mixins
        mapper.addMixIn(Ornithopter.class, OrnithopterMixIn.class);
        mapper.addMixIn(SimulatedPlayer2.class, SimulatedPlayer2MixIn.class);
        mapper.addMixIn(MatchPlayer.class, MatchPlayerMixIn.class);
        mapper.addMixIn(Player.class, HumanPlayerMixIn.class);
        mapper.addMixIn(Deck.class, DeckMixIn.class);
        mapper.addMixIn(Card.class, CardMixIn.class);
        mapper.addMixIn(ManaCost.class, ManaCostMixIn.class);
        mapper.addMixIn(ColoredManaCost.class, ColoredManaCostMixIn.class);
        mapper.addMixIn(GameState.class, GameStateMixIn.class);
        mapper.addMixIn(GenericManaCost.class, GenericManaCostMixIn.class);

        // Register custom serializers
        SimpleModule module = new SimpleModule();
        module.addSerializer(Card.class, new CardSerializer());
        module.addSerializer(Ability.class, new AbilitySerializer());
        module.addSerializer(Outcome.class, new OutcomeSerializer());
        mapper.registerModule(module);

        return mapper;
    }

    // MixIn classes (copied from ComputerPlayer8 for consistency)
    public abstract class GenericManaCostMixIn {
        @JsonIgnore
        public GenericManaCost getUnpaid;
        @JsonIgnore
        private GenericManaCost unpaid;
        @JsonIgnore
        private List<mage.Mana> options;
        @JsonIgnore
        private boolean paid;
    }

    public abstract class OrnithopterMixIn {
        @JsonIgnore
        private Ability secondFaceSpellAbility;
    }

    public class RealPlayerUuidSerializer extends JsonSerializer<Player> {
        @Override
        public void serialize(Player player, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeString(player.getId().toString());
        }
    }

    public class OutcomeSerializer extends JsonSerializer<Outcome> {
        @Override
        public void serialize(Outcome outcome, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeStartObject();
            gen.writeStringField("name", outcome.name());
            gen.writeBooleanField("good", outcome.isGood());
            gen.writeBooleanField("canTargetAll", outcome.isCanTargetAll());
            gen.writeEndObject();
        }
    }

    @JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
    public abstract class SimulatedPlayer2MixIn {
        @JsonIgnore
        private MatchPlayer matchPlayer;
        @JsonIgnore
        private Player realPlayer;
    }

    @JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "name")
    public abstract class MatchPlayerMixIn {
        @JsonIgnore
        private Deck deck;
        @JsonIgnore
        private Player player;
    }

    @JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
    public abstract class HumanPlayerMixIn {
        @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = RealPlayerUuidSerializer.class)
        private Player realPlayer;
    }

    public abstract class DeckMixIn {
        @JsonIgnore
        private java.util.Set<Card> cards;
    }

    public abstract class CardMixIn {
        @JsonIgnore
        private ManaCost manaCost;
        @JsonIgnore
        private List<ManaCost> manaCosts;
        @JsonIgnore
        private Ability secondFaceSpellAbility;
    }

    public abstract class ManaCostMixIn {
        @JsonIgnore
        private List<ColoredManaCost> manaCosts;
        @JsonIgnore
        private boolean unpaid;
    }

    public abstract class ColoredManaCostMixIn {
        @JsonIgnore
        private boolean unpaid;
    }

    public abstract class GameStateMixIn {
        @JsonIgnore
        private java.util.Map<UUID, mage.abilities.TriggeredAbilities> triggers;
        @JsonIgnore
        private mage.players.Players players;
        @JsonIgnore
        private mage.players.PlayerList playersList;
    }

    public class AbilitySerializer extends StdSerializer<Ability> {
        public AbilitySerializer() {
            this(null);
        }

        public AbilitySerializer(Class<Ability> t) {
            super(t);
        }

        @Override
        public void serialize(Ability ability, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeStartObject();
            gen.writeStringField("className", ability.getClass().getName());
            gen.writeStringField("description", ability.toString());
            gen.writeStringField("abilityType",
                    ability.getAbilityType() != null ? ability.getAbilityType().toString() : "");
            gen.writeStringField("controllerId",
                    ability.getControllerId() != null ? ability.getControllerId().toString() : "");
            gen.writeStringField("controllerOrOwnerId",
                    ability.getControllerOrOwnerId() != null ? ability.getControllerOrOwnerId().toString() : "");
            gen.writeStringField("rule", ability.getRule() != null ? ability.getRule().toString() : "");
            gen.writeStringField("sourceId", ability.getSourceId() != null ? ability.getSourceId().toString() : "");
            gen.writeStringField("costAdjuster",
                    ability.getCostAdjuster() != null ? ability.getCostAdjuster().toString() : "");
            gen.writeStringField("costs", ability.getCosts() != null ? ability.getCosts().toString() : "");
            gen.writeStringField("customOutcome",
                    ability.getCustomOutcome() != null ? ability.getCustomOutcome().toString() : "");
            gen.writeStringField("effects", ability.getEffects() != null ? ability.getEffects().toString() : "");
            gen.writeStringField("targets", ability.getTargets() != null ? ability.getTargets().toString() : "");
            gen.writeStringField("zone", ability.getZone() != null ? ability.getZone().toString() : "");
            gen.writeStringField("manaCost",
                    ability.getManaCosts() != null && !ability.getManaCosts().isEmpty()
                            ? ability.getManaCosts().getText() : "");
            gen.writeEndObject();
        }
    }

    public class CardSerializer extends StdSerializer<Card> {
        public CardSerializer() {
            this(null);
        }

        public CardSerializer(Class<Card> t) {
            super(t);
        }

        @Override
        public void serialize(Card card, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeStartObject();
            gen.writeStringField("name", card.getName());
            gen.writeStringField("id", card.getId().toString());
            gen.writeStringField("abilities", card.getAbilities() != null ? card.getAbilities().toString() : "");
            gen.writeStringField("cardType", card.getCardType() != null ? card.getCardType().toString() : "");
            gen.writeStringField("color", card.getColor() != null ? card.getColor().toString() : "");
            gen.writeStringField("ownerId", card.getOwnerId() != null ? card.getOwnerId().toString() : "");
            gen.writeStringField("power", card.getPower() != null ? card.getPower().toString() : "");
            gen.writeStringField("toughness", card.getToughness() != null ? card.getToughness().toString() : "");
            gen.writeStringField("rules", card.getRules() != null ? card.getRules().toString() : "");
            gen.writeStringField("spellAbility",
                    card.getSpellAbility() != null ? card.getSpellAbility().toString() : "");
            gen.writeStringField("subtype", card.getSubtype() != null ? card.getSubtype().toString() : "");
            gen.writeStringField("supertype", card.getSuperType() != null ? card.getSuperType().toString() : "");
            gen.writeStringField("manaCost", card.getManaCost() != null ? card.getManaCost().toString() : "");
            gen.writeEndObject();
        }
    }

    /*
     * Informational helpers moved here so all player-facing messages come from the
     * same place.
     */
    public void informChosenAction(Game game, Player player, List<Ability> allActions, DecisionResult result) {
        try {
            game.informPlayers(player.getLogName() + " POSSIBLE ACTIONS:");
            for (int i = 0; i < allActions.size(); i++) {
                game.informPlayers(i + ": " + allActions.get(i).toString());
            }
            if (result.getChosenIndex() != null) {
                int idx = result.getChosenIndex();
                if (idx >= 0 && idx < allActions.size()) {
                    game.informPlayers("LLM CHOSEN ACTION:");
                    game.informPlayers(idx + ": " + allActions.get(idx).toString());
                }
            } else if (result.getChosenUuids() != null) {
                game.informPlayers("LLM CHOSEN ACTION UUIDS: " + result.getChosenUuids().toString());
            }
            game.informPlayers(" REASON:");
            game.informPlayers(result.getReason());
        } catch (Exception e) {
            logger.error("Error informing chosen action", e);
        }
    }

    public void informChosenChoice(Game game, Player player, String[] allChoices, DecisionResult result) {
        try {
            game.informPlayers(player.getLogName() + " POSSIBLE CHOICES:");
            for (int i = 0; i < allChoices.length; i++) {
                game.informPlayers(i + ": " + allChoices[i]);
            }
            if (result.getChosenIndex() != null) {
                int idx = result.getChosenIndex();
                if (idx >= 0 && idx < allChoices.length) {
                    game.informPlayers("LLM CHOSEN ACTION:");
                    game.informPlayers(idx + ": " + allChoices[idx]);
                }
            } else if (result.getChosenUuids() != null) {
                game.informPlayers("LLM CHOSEN ACTION UUIDS: " + result.getChosenUuids().toString());
            }
            game.informPlayers(" REASON:");
            game.informPlayers(result.getReason());
        } catch (Exception e) {
            logger.error("Error informing chosen choice", e);
        }
    }

    public void informChosenAttackers(Game game, Player player, List<Permanent> possibleAttackers,
            DecisionResult result) {
        try {
            game.informPlayers(player.getLogName() + " POSSIBLE ATTACKERS:");
            for (int i = 0; i < possibleAttackers.size(); i++) {
                Permanent p = possibleAttackers.get(i);
                game.informPlayers(i + ": " + (p != null ? p.toString() : "unknown"));
            }
            if (result.getChosenUuids() != null) {
                game.informPlayers("LLM CHOSEN ATTACKERS (uuids): " + result.getChosenUuids().toString());
            } else if (result.getChosenIndex() != null) {
                game.informPlayers("LLM CHOSEN ATTACKERS index: " + result.getChosenIndex());
            }
            game.informPlayers(" REASON:");
            game.informPlayers(result.getReason());
        } catch (Exception e) {
            logger.error("Error informing chosen attackers", e);
        }
    }

    public void informChosenReason(Game game, Player player, String reasonFor, DecisionResult result) {
        try {
            game.informPlayers(player.getLogName() + " REASON FOR " + reasonFor);
            game.informPlayers(result.getReason());
        } catch (Exception e) {
            logger.error("Error informing reason", e);
        }
    }
}
