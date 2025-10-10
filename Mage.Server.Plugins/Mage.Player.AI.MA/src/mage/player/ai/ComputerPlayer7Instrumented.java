package mage.player.ai;

import mage.abilities.Ability;
import mage.cards.Cards;
import mage.choices.Choice;
import mage.constants.Outcome;
import mage.constants.RangeOfInfluence;
import mage.game.Game;
import mage.target.TargetCard;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * ComputerPlayer7 instrumented for trajectory logging to support RL training.
 * Extends ComputerPlayer7 and logs all major decision points to the magellmfast
 * server.
 *
 * @author AI RL Training Team
 */
public class ComputerPlayer7Instrumented extends ComputerPlayer7 {

    private static final Logger logger = Logger.getLogger(ComputerPlayer7Instrumented.class);

    // HTTP client configuration
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private static final String MAGELLMFAST_BASE_URL = System.getProperty("magellmfast.url", "http://localhost:8000");
    private static final String LOG_TRAJECTORY_ENDPOINT = MAGELLMFAST_BASE_URL + "/v1/log_trajectory";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // Performance tracking
    private long totalLoggingTime = 0;
    private int loggingCallCount = 0;

    public ComputerPlayer7Instrumented(String name, RangeOfInfluence range, int skill) {
        super(name, range, skill);
    }

    public ComputerPlayer7Instrumented(final ComputerPlayer7Instrumented player) {
        super(player);
        this.totalLoggingTime = player.totalLoggingTime;
        this.loggingCallCount = player.loggingCallCount;
    }

    @Override
    public ComputerPlayer7Instrumented copy() {
        return new ComputerPlayer7Instrumented(this);
    }

    @Override
    public boolean priority(Game game) {
        long startTime = System.currentTimeMillis();

        // Log pre-decision state for priority decisions
        try {
            logTrajectoryData(game, "priority", null, null, null);
        } catch (Exception e) {
            logger.warn("Failed to log pre-priority trajectory: " + e.getMessage());
        }

        // Execute original priority logic
        boolean result = super.priority(game);

        // Log post-decision state with chosen actions
        try {
            Map<String, Object> chosenAction = new HashMap<>();
            chosenAction.put("result", result);
            chosenAction.put("step_type", game.getTurnStepType().toString());
            chosenAction.put("actions_taken", actions != null ? actions.size() : 0);

            logTrajectoryData(game, "priority_result", null, chosenAction, null);
        } catch (Exception e) {
            logger.warn("Failed to log post-priority trajectory: " + e.getMessage());
        }

        updatePerformanceMetrics(startTime);
        return result;
    }

    @Override
    public boolean choose(Outcome outcome, Choice choice, Game game) {
        long startTime = System.currentTimeMillis();

        // Log pre-choice state
        try {
            Map<String, Object> availableChoices = new HashMap<>();
            availableChoices.put("outcome", outcome.toString());
            availableChoices.put("choice_type", choice.getClass().getSimpleName());
            availableChoices.put("message", choice.getMessage());
            availableChoices.put("choices", choice.getChoices());

            logTrajectoryData(game, "choice", availableChoices, null, null);
        } catch (Exception e) {
            logger.warn("Failed to log pre-choice trajectory: " + e.getMessage());
        }

        // Execute original choice logic
        boolean result = super.choose(outcome, choice, game);

        // Log chosen option
        try {
            Map<String, Object> chosenAction = new HashMap<>();
            chosenAction.put("chosen", choice.getChoice());
            chosenAction.put("result", result);

            logTrajectoryData(game, "choice_result", null, chosenAction, null);
        } catch (Exception e) {
            logger.warn("Failed to log post-choice trajectory: " + e.getMessage());
        }

        updatePerformanceMetrics(startTime);
        return result;
    }

    @Override
    public boolean chooseTarget(Outcome outcome, Cards cards, TargetCard target, Ability source, Game game) {
        long startTime = System.currentTimeMillis();

        // Log pre-target state
        try {
            Map<String, Object> availableTargets = new HashMap<>();
            availableTargets.put("outcome", outcome.toString());
            availableTargets.put("target_type", target.getClass().getSimpleName());
            availableTargets.put("target_name", target.getTargetName());
            availableTargets.put("possible_targets",
                    target.possibleTargets(source.getControllerId(), source, game).size());

            logTrajectoryData(game, "target", availableTargets, null, source);
        } catch (Exception e) {
            logger.warn("Failed to log pre-target trajectory: " + e.getMessage());
        }

        // Execute original targeting logic
        boolean result = super.chooseTarget(outcome, cards, target, source, game);

        // Log chosen targets
        try {
            Map<String, Object> chosenAction = new HashMap<>();
            chosenAction.put("targets", new ArrayList<>(target.getTargets()));
            chosenAction.put("result", result);

            logTrajectoryData(game, "target_result", null, chosenAction, source);
        } catch (Exception e) {
            logger.warn("Failed to log post-target trajectory: " + e.getMessage());
        }

        updatePerformanceMetrics(startTime);
        return result;
    }

    @Override
    public void selectAttackers(Game game, UUID attackingPlayerId) {
        long startTime = System.currentTimeMillis();

        // Log pre-attackers state
        try {
            List<Map<String, Object>> availableActions = new ArrayList<>();
            List<mage.game.permanent.Permanent> attackers = super.getAvailableAttackers(game);
            for (mage.game.permanent.Permanent attacker : attackers) {
                Map<String, Object> attackerAction = new HashMap<>();
                attackerAction.put("action", "Attack with " + attacker.getName());
                attackerAction.put("type", "attacker");
                attackerAction.put("id", attacker.getId().toString());
                attackerAction.put("name", attacker.getName());
                attackerAction.put("power", attacker.getPower().getValue());
                attackerAction.put("toughness", attacker.getToughness().getValue());
                availableActions.add(attackerAction);
            }

            logTrajectoryData(game, "attackers", availableActions, null, null);
        } catch (Exception e) {
            logger.warn("Failed to log pre-attackers trajectory: " + e.getMessage());
        }

        // Execute original attacker selection logic
        super.selectAttackers(game, attackingPlayerId);

        // Log chosen attackers
        try {
            Map<String, Object> chosenAction = new HashMap<>();
            chosenAction.put("declared_attackers", game.getCombat().getAttackers().size());

            logTrajectoryData(game, "attackers_result", null, chosenAction, null);
        } catch (Exception e) {
            logger.warn("Failed to log post-attackers trajectory: " + e.getMessage());
        }

        updatePerformanceMetrics(startTime);
    }

    @Override
    public void selectBlockers(Ability source, Game game, UUID defendingPlayerId) {
        long startTime = System.currentTimeMillis();

        // Log pre-blockers state
        try {
            List<Map<String, Object>> availableBlockers = new ArrayList<>();
            List<mage.game.permanent.Permanent> blockers = super.getAvailableBlockers(game);
            for (mage.game.permanent.Permanent blocker : blockers) {
                Map<String, Object> blockerInfo = new HashMap<>();
                blockerInfo.put("id", blocker.getId().toString());
                blockerInfo.put("name", blocker.getName());
                blockerInfo.put("power", blocker.getPower().getValue());
                blockerInfo.put("toughness", blocker.getToughness().getValue());
                availableBlockers.add(blockerInfo);
            }

            Map<String, Object> availableActions = new HashMap<>();
            availableActions.put("available_blockers", availableBlockers);
            availableActions.put("attacking_creatures", game.getCombat().getAttackers().size());
            availableActions.put("defending_player", defendingPlayerId.toString());

            logTrajectoryData(game, "blockers", availableActions, null, source);
        } catch (Exception e) {
            logger.warn("Failed to log pre-blockers trajectory: " + e.getMessage());
        }

        // Execute original blocker selection logic
        super.selectBlockers(source, game, defendingPlayerId);

        // Log chosen blockers
        try {
            Map<String, Object> chosenAction = new HashMap<>();
            chosenAction.put("declared_blockers", game.getCombat().getBlockers().size());

            logTrajectoryData(game, "blockers_result", null, chosenAction, source);
        } catch (Exception e) {
            logger.warn("Failed to log post-blockers trajectory: " + e.getMessage());
        }

        updatePerformanceMetrics(startTime);
    }

    /**
     * Core method to log trajectory data to the magellmfast server.
     */
    private void logTrajectoryData(Game game, String decisionType, Object availableActions,
            Map<String, Object> chosenAction, Ability sourceAbility) {
        try {
            // Build trajectory data payload
            Map<String, Object> payload = new HashMap<>();

            // Basic game context
            payload.put("gameId", game.getId().toString());
            payload.put("matchId", null); // Game interface doesn't have matchId directly
            payload.put("decisionType", decisionType);
            payload.put("turnNumber", game.getTurnNum());
            payload.put("phaseType", game.getTurnPhaseType() != null ? game.getTurnPhaseType().toString() : "UNKNOWN");
            payload.put("stepType", game.getTurnStepType() != null ? game.getTurnStepType().toString() : "UNKNOWN");

            // Game state (simplified)
            Map<String, Object> gameState = new HashMap<>();
            gameState.put("activePlayerId", game.getActivePlayerId().toString());
            gameState.put("priorityPlayerId", game.getPriorityPlayerId().toString());
            gameState.put("stackSize", game.getStack().size());
            gameState.put("gameOver", game.checkIfGameIsOver());
            payload.put("gameState", gameState);

            // Current player state (simplified)
            Map<String, Object> currentPlayer = new HashMap<>();
            currentPlayer.put("id", this.getId().toString());
            currentPlayer.put("name", this.getName());
            currentPlayer.put("life", this.getLife());
            currentPlayer.put("handSize", this.getHand().size());
            currentPlayer.put("librarySize", this.getLibrary().size());
            currentPlayer.put("graveyardSize", this.getGraveyard().size());
            payload.put("currentPlayer", currentPlayer);

            // Opponent player state (simplified - just first opponent)
            Map<String, Object> opponentPlayer = new HashMap<>();
            UUID opponentId = game.getOpponents(this.getId()).iterator().next();
            mage.players.Player opponent = game.getPlayer(opponentId);
            if (opponent != null) {
                opponentPlayer.put("id", opponent.getId().toString());
                opponentPlayer.put("name", opponent.getName());
                opponentPlayer.put("life", opponent.getLife());
                opponentPlayer.put("handSize", opponent.getHand().size());
                opponentPlayer.put("librarySize", opponent.getLibrary().size());
                opponentPlayer.put("graveyardSize", opponent.getGraveyard().size());
            }
            payload.put("opponentPlayer", opponentPlayer);

            // Available actions and chosen action
            payload.put("availableActions", availableActions);
            payload.put("chosenAction", chosenAction);

            // Additional context
            Map<String, Object> additionalContext = new HashMap<>();
            if (sourceAbility != null) {
                additionalContext.put("sourceAbility", sourceAbility.toString());
                additionalContext.put("sourceId", sourceAbility.getSourceId().toString());
            }
            additionalContext.put("loggedAt", System.currentTimeMillis());
            payload.put("additionalContext", additionalContext);

            // Send HTTP request asynchronously to avoid blocking game
            sendTrajectoryDataAsync(payload);

        } catch (Exception e) {
            logger.warn("Failed to prepare trajectory data: " + e.getMessage());
        }
    }

    /**
     * Send trajectory data asynchronously to avoid blocking the game.
     */
    private void sendTrajectoryDataAsync(Map<String, Object> payload) {
        // Use a separate thread to avoid blocking game execution
        Thread.ofVirtual().start(() -> {
            try {
                String jsonPayload = objectMapper.writeValueAsString(payload);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(LOG_TRAJECTORY_ENDPOINT))
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(3))
                        .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    logger.warn("Trajectory logging failed with status: " + response.statusCode());
                }

            } catch (IOException | InterruptedException e) {
                logger.debug("Trajectory logging request failed: " + e.getMessage());
                // Don't spam logs with network errors
            } catch (Exception e) {
                logger.warn("Unexpected error in trajectory logging: " + e.getMessage());
            }
        });
    }

    /**
     * Update performance metrics to ensure < 10% overhead.
     */
    private void updatePerformanceMetrics(long startTime) {
        long loggingTime = System.currentTimeMillis() - startTime;
        totalLoggingTime += loggingTime;
        loggingCallCount++;

        // Log performance stats occasionally
        if (loggingCallCount % 50 == 0) {
            double avgLoggingTime = (double) totalLoggingTime / loggingCallCount;
            logger.info(String.format("Trajectory logging performance: avg %.2fms over %d calls",
                    avgLoggingTime, loggingCallCount));
        }
    }

    /**
     * Get performance impact as percentage of total decision time.
     */
    public double getLoggingOverheadPercentage(long totalDecisionTime) {
        if (totalDecisionTime == 0) {
            return 0.0;
        }
        return (double) totalLoggingTime / totalDecisionTime * 100.0;
    }
}
