package mage.player.ai;

import mage.abilities.Ability;
import mage.cards.Cards;
import mage.choices.Choice;
import mage.constants.Outcome;
import mage.constants.RangeOfInfluence;
import mage.game.Game;
import mage.target.TargetCard;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.json.JSONObject;

/**
 * ComputerPlayer7 instrumented for trajectory logging to support RL training.
 * Extends ComputerPlayer7 and logs all major decision points to the magellmfast
 * server.
 *
 * @author AI RL Training Team
 */
public class ComputerPlayer7Instrumented extends ComputerPlayer7 {

    private static final Logger logger = Logger.getLogger(ComputerPlayer7Instrumented.class);

    // HTTP client configuration using LlmDecisionClient
    private static final LlmDecisionClient decisionClient = new LlmDecisionClient(
            System.getProperty("magellmfast.url", "http://localhost:8000"));

    // Decision handler for centralized payload generation
    private static final DecisionHandler decisionHandler = new DecisionHandler(decisionClient);

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
            // Build additional context
            Map<String, Object> additionalContext = new HashMap<>();
            if (sourceAbility != null) {
                additionalContext.put("sourceAbility", sourceAbility.toString());
                additionalContext.put("sourceId", sourceAbility.getSourceId().toString());
            }
            additionalContext.put("loggedAt", System.currentTimeMillis());

            // Use DecisionHandler to build the trajectory payload centrally
            JSONObject payload = decisionHandler.buildTrajectoryPayload(
                    game, this, decisionType, availableActions, chosenAction, additionalContext);

            // Send HTTP request asynchronously to avoid blocking game
            sendTrajectoryDataAsync(payload);

        } catch (Exception e) {
            logger.warn("Failed to prepare trajectory data: " + e.getMessage());
        }
    }

    /**
     * Send trajectory data asynchronously to avoid blocking the game.
     */
    private void sendTrajectoryDataAsync(JSONObject payload) {
        // Use a separate thread to avoid blocking game execution
        Thread.ofVirtual().start(() -> {
            try {
                // Create a DecisionPayload for the trajectory logging endpoint
                DecisionPayload decisionPayload = new DecisionPayload("/v1/log_trajectory", payload);

                // Send the request using LlmDecisionClient
                DecisionResult result = decisionClient.requestDecision(decisionPayload);

                // Log success/failure based on the result
                if (result.getReason().equals("server_error") || result.getReason().equals("exception")) {
                    logger.warn("Trajectory logging failed: " + result.getReason());
                } else {
                    logger.debug("Trajectory logged successfully");
                }

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
