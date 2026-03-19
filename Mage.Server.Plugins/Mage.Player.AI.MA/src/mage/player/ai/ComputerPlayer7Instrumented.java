package mage.player.ai;

import mage.MageObject;
import mage.abilities.Ability;
import mage.abilities.common.PassAbility;
import mage.cards.Cards;
import mage.choices.Choice;
import mage.constants.Outcome;
import mage.constants.RangeOfInfluence;
import mage.game.Game;
import mage.game.permanent.Permanent;
import mage.players.Player;
import mage.target.Target;
import mage.target.TargetCard;
import org.apache.log4j.Logger;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
            System.getProperty("magellmfast.url", "http://localhost:9000"));

    // Decision handler for centralized payload generation
    private static final DecisionHandler decisionHandler = new DecisionHandler(decisionClient);

    // Performance tracking
    private long totalLoggingTime = 0;
    private int loggingCallCount = 0;

    // Game termination tracking
    private boolean gameEndLogged = false;

    // Monotonically-increasing sequence number assigned synchronously before each async log send.
    // Enables the Python filter/parser to sort entries by logical order even when async HTTP
    // delivery causes entries to arrive at the server out of order.
    private int logSeq = 0;

    // Capture of actions chosen by CP7's calculateActions() before act() consumes them.
    // act() is called inside super.priority() after calculateActions() populates this.actions.
    // We override act() to snapshot the queue contents, then let super.act() consume them.
    private List<Map<String, Object>> lastCapturedActions = new ArrayList<>();

    public ComputerPlayer7Instrumented(String name, RangeOfInfluence range, int skill) {
        super(name, range, skill);
    }

    public ComputerPlayer7Instrumented(final ComputerPlayer7Instrumented player) {
        super(player);
        this.totalLoggingTime = player.totalLoggingTime;
        this.loggingCallCount = player.loggingCallCount;
        this.lastCapturedActions = new ArrayList<>(player.lastCapturedActions);
        this.logSeq = player.logSeq;
    }

    @Override
    public ComputerPlayer7Instrumented copy() {
        return new ComputerPlayer7Instrumented(this);
    }

    /**
     * Override act() to capture the actions CP7 chose BEFORE they are consumed from the queue.
     * CP7's flow: calculateActions() fills this.actions → act() polls and executes each one.
     * By overriding act(), we snapshot this.actions before super.act() drains the queue.
     *
     * IMPORTANT: For spells/abilities with pre-populated targets (set by alpha-beta search),
     * player.chooseTarget() is never called (Targets.chooseTargets() exits early when targets
     * are already satisfied). We log those target choices here, before super.act() commits them.
     */
    @Override
    protected void act(Game game) {
        lastCapturedActions.clear();
        try {
            if (actions != null && !actions.isEmpty()) {
                for (Ability a : actions) {
                    Map<String, Object> aMap = new HashMap<>();
                    aMap.put("description", a.toString());
                    aMap.put("className", a.getClass().getName());
                    aMap.put("rule", a.getRule());
                    if (a.getSourceId() != null) {
                        aMap.put("sourceId", a.getSourceId().toString());
                    }
                    if (a.getAbilityType() != null) {
                        aMap.put("abilityType", a.getAbilityType().toString());
                    }
                    lastCapturedActions.add(aMap);
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to capture actions in act(): " + e.getMessage());
        }

        // Log pre-populated target decisions for non-simulation games.
        // CP7's alpha-beta pre-populates targets in each ability before act() is called.
        // Since Targets.chooseTargets() exits immediately when targets are pre-satisfied,
        // our chooseTarget() override is never invoked. We log them here instead.
        if (!game.isSimulation() && actions != null && !actions.isEmpty()) {
            for (Ability a : actions) {
                for (mage.target.Target t : a.getTargets()) {
                    Set<UUID> chosenIds = new HashSet<>(t.getTargets());
                    if (chosenIds.isEmpty()) continue; // No pre-chosen target

                    // Build the full candidate set: possible targets + pre-chosen targets.
                    Set<UUID> possibleTargetIds = t.possibleTargets(this.getId(), a, game);
                    Set<UUID> allCandidates = new java.util.LinkedHashSet<>(possibleTargetIds);
                    allCandidates.addAll(chosenIds);

                    if (allCandidates.size() < 2) continue; // Only 1 option, skip logging

                    // Sort candidates alphabetically by MageObject.toString()
                    List<Map.Entry<UUID, String>> sortedCandidates = new ArrayList<>();
                    for (UUID uuid : allCandidates) {
                        MageObject mo = game.getObject(uuid);
                        String desc = mo != null ? mo.toString() : "unknown";
                        sortedCandidates.add(new AbstractMap.SimpleEntry<>(uuid, desc));
                    }
                    sortedCandidates.sort(Comparator.comparing(Map.Entry::getValue));

                    // Build available_actions in Action-like format
                    List<Map<String, Object>> availableActions = new ArrayList<>();
                    for (Map.Entry<UUID, String> entry : sortedCandidates) {
                        Map<String, Object> actionMap = new HashMap<>();
                        actionMap.put("description", entry.getValue());
                        actionMap.put("sourceId", entry.getKey().toString());
                        actionMap.put("abilityType", "Target");
                        actionMap.put("className", t.getClass().getSimpleName());
                        actionMap.put("costs", "[]");
                        actionMap.put("targets", "[]");
                        availableActions.add(actionMap);
                    }

                    // Determine chosen descriptions, UUIDs, and indices
                    List<String> chosenDescriptions = new ArrayList<>();
                    List<String> chosenUuids = new ArrayList<>();
                    List<Integer> chosenIndices = new ArrayList<>();
                    for (UUID chosen : chosenIds) {
                        String chosenDesc = "unknown";
                        int chosenIdx = -1;
                        for (int i = 0; i < sortedCandidates.size(); i++) {
                            if (sortedCandidates.get(i).getKey().equals(chosen)) {
                                chosenDesc = sortedCandidates.get(i).getValue();
                                chosenIdx = i;
                                break;
                            }
                        }
                        chosenDescriptions.add(chosenDesc);
                        chosenUuids.add(chosen.toString());
                        chosenIndices.add(chosenIdx);
                    }

                    // Log pre-target decision
                    try {
                        Map<String, Object> ctx = new HashMap<>();
                        ctx.put("targetName", t.getTargetName());
                        ctx.put("targetType", t.getClass().getSimpleName());
                        ctx.put("parentAbility", a.getRule());
                        logTrajectoryData(game, "target", availableActions, null, a, ctx);
                    } catch (Exception e) {
                        logger.warn("Failed to log pre-target (act): " + e.getMessage());
                    }

                    // Log target result
                    try {
                        Map<String, Object> chosenAction = new HashMap<>();
                        chosenAction.put("chosen_descriptions", chosenDescriptions);
                        chosenAction.put("chosen_uuids", chosenUuids);
                        chosenAction.put("chosen_indices", chosenIndices);
                        chosenAction.put("result", true);
                        logTrajectoryData(game, "target_result", null, chosenAction, a);
                    } catch (Exception e) {
                        logger.warn("Failed to log post-target (act): " + e.getMessage());
                    }
                }
            }
        }

        super.act(game);
    }

    @Override
    public boolean priority(Game game) {
        lastCapturedActions.clear(); // Reset before each priority call
        long startTime = System.currentTimeMillis();

        // Set up the minimal game state that getPlayable() requires before calling it.
        // Without this, getPlayable() returns 0 actions because the timer is paused
        // and priorityPlayerId is not set. super.priority() (CP7) does both of these
        // at the start of its own flow; we mirror that here so getPlayable() sees the
        // same game state as CP8 / inference would.
        game.resumeTimer(getTurnControlledBy());
        game.getState().setPriorityPlayerId(playerId);

        // Build the action list identically to the inference path (ComputerPlayer8 /
        // DecisionHandler.buildChooseFromAllActionsPayload) so that trajectory actions
        // serialize with the same fields the Action Pydantic model expects.
        LinkedList<Ability> playableActions = new LinkedList<>();
        try {
            playableActions.add(new PassAbility());
            playableActions.addAll(this.getPlayable(game, false));
        } catch (Exception e) {
            logger.warn("Failed to build action list for priority logging: " + e.getMessage());
        }
        try {
            logTrajectoryData(game, "priority", playableActions, null, null);
        } catch (Exception e) {
            logger.warn("Failed to log pre-priority trajectory: " + e.getMessage());
        }

        // Execute original priority logic (CP7 game-tree search).
        // super.priority() calls resumeTimer again (idempotent) then priorityPlay()
        // which sets priorityPlayerId again (also idempotent).
        boolean result = super.priority(game);

        // Log post-decision state with chosen actions.
        // CP7 populates this.actions in calculateActions(), then act() consumes them.
        // Our act() override captured them in lastCapturedActions before consumption.
        try {
            Map<String, Object> chosenAction = new HashMap<>();
            chosenAction.put("result", result);
            chosenAction.put("step_type", game.getTurnStepType().toString());
            chosenAction.put("actions_taken", lastCapturedActions.size());

            if (!lastCapturedActions.isEmpty()) {
                chosenAction.put("chosen_actions", new ArrayList<>(lastCapturedActions));
                chosenAction.put("passed", false);
            } else {
                // CP7 chose to pass priority (no actions were queued)
                chosenAction.put("chosen_actions", new ArrayList<>());
                chosenAction.put("passed", true);
            }

            chosenAction.put("available_actions_count", playableActions.size());

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

    /**
     * Override general target selection to log targeting decisions for trajectory comparison.
     * Possible targets are sorted alphabetically by MageObject.toString() so that the index
     * of each target is stable and matches how CP8 (rl_trained) orders them.
     * Each call to chooseTarget is logged as one "target" / "target_result" pair; when an
     * effect requires multiple targets the game engine calls this method once per target.
     */
    @Override
    public boolean chooseTarget(Outcome outcome, Target target, Ability source, Game game) {
        UUID sourceId = source != null ? source.getSourceId() : null;
        UUID abilityControllerId = playerId;
        if (target.getAbilityController() != null) {
            abilityControllerId = target.getAbilityController();
        }

        // Never log inside alpha-beta simulation copies.
        if (game.isSimulation()) {
            return super.chooseTarget(outcome, target, source, game);
        }

        Set<UUID> possibleTargetIds = target.possibleTargets(abilityControllerId, source, game);

        // Build the full candidate set: all valid targets plus any already-chosen targets
        // (CP7's alpha-beta may pre-populate the target set before player.chooseTarget is called).
        Set<UUID> alreadyChosen = new HashSet<>(target.getTargets());
        Set<UUID> allCandidates = new java.util.LinkedHashSet<>(possibleTargetIds);
        allCandidates.addAll(alreadyChosen);

        // Only log if there was a genuine multi-target choice (at least 2 candidates).
        if (allCandidates.size() < 2) {
            return super.chooseTarget(outcome, target, source, game);
        }

        // Build sorted list of (uuid, description) — alphabetical by MageObject.toString()
        // so the index matches the ordering CP8 sends to /choose_targets/.
        List<Map.Entry<UUID, String>> sortedTargets = new ArrayList<>();
        for (UUID uuid : allCandidates) {
            MageObject mo = game.getObject(uuid);
            String desc = mo != null ? mo.toString() : "unknown";
            sortedTargets.add(new AbstractMap.SimpleEntry<>(uuid, desc));
        }
        sortedTargets.sort(Comparator.comparing(Map.Entry::getValue));

        // Build available_actions in the same Action-like format used for priority logging
        List<Map<String, Object>> availableActions = new ArrayList<>();
        for (Map.Entry<UUID, String> entry : sortedTargets) {
            Map<String, Object> actionMap = new HashMap<>();
            actionMap.put("description", entry.getValue());
            actionMap.put("sourceId", entry.getKey().toString());
            actionMap.put("abilityType", "Target");
            actionMap.put("className", target.getClass().getSimpleName());
            actionMap.put("costs", "[]");
            actionMap.put("targets", "[]");
            availableActions.add(actionMap);
        }

        // Log pre-decision
        try {
            Map<String, Object> ctx = new HashMap<>();
            ctx.put("targetName", target.getTargetName());
            ctx.put("targetType", target.getClass().getSimpleName());
            ctx.put("outcome", outcome.toString());
            logTrajectoryData(game, "target", availableActions, null, source, ctx);
        } catch (Exception e) {
            logger.warn("Failed to log pre-target trajectory: " + e.getMessage());
        }

        // When CP7's alpha-beta already pre-populated the target (target satisfied), just
        // log the pre-chosen targets and return. Do NOT call super again or it may add more.
        boolean alreadySatisfied = !alreadyChosen.isEmpty()
                && alreadyChosen.size() >= target.getNumberOfTargets();
        boolean result;
        Set<UUID> newlyChosen;
        if (alreadySatisfied) {
            newlyChosen = alreadyChosen;
            result = true;
        } else {
            // Let CP7 make the targeting decision normally
            result = super.chooseTarget(outcome, target, source, game);
            newlyChosen = new HashSet<>(target.getTargets());
            newlyChosen.removeAll(alreadyChosen);
        }

        // Determine chosen target(s) description and index
        List<String> chosenDescriptions = new ArrayList<>();
        List<String> chosenUuids = new ArrayList<>();
        List<Integer> chosenIndices = new ArrayList<>();
        for (UUID chosen : newlyChosen) {
            String chosenDesc = "unknown";
            int chosenIdx = -1;
            for (int i = 0; i < sortedTargets.size(); i++) {
                if (sortedTargets.get(i).getKey().equals(chosen)) {
                    chosenDesc = sortedTargets.get(i).getValue();
                    chosenIdx = i;
                    break;
                }
            }
            chosenDescriptions.add(chosenDesc);
            chosenUuids.add(chosen.toString());
            chosenIndices.add(chosenIdx);
        }

        // Log post-decision
        try {
            Map<String, Object> chosenAction = new HashMap<>();
            chosenAction.put("chosen_descriptions", chosenDescriptions);
            chosenAction.put("chosen_uuids", chosenUuids);
            chosenAction.put("chosen_indices", chosenIndices);
            chosenAction.put("result", result);
            logTrajectoryData(game, "target_result", null, chosenAction, source);
        } catch (Exception e) {
            logger.warn("Failed to log post-target trajectory: " + e.getMessage());
        }

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

        // Collect possibleAttackers and possibleBlockers using the same logic as CP8
        // so the training pipeline can render gen_prompt_attackers() from the log.
        List<Permanent> possibleAttackers = new ArrayList<>();
        List<Permanent> possibleBlockers = new ArrayList<>();
        try {
            for (UUID defenderId : game.getOpponents(attackingPlayerId)) {
                Player defender = game.getPlayer(defenderId);
                if (defender == null || !defender.isInGame()) continue;
                possibleAttackers.addAll(super.getAvailableAttackers(defenderId, game));
                possibleBlockers.addAll(defender.getAvailableBlockers(game));
            }
        } catch (Exception e) {
            logger.warn("Failed to collect attackers/blockers: " + e.getMessage());
        }

        // Log pre-decision using simple serializable maps matching the Python Permanent
        // pydantic model (id, name, power, toughness, abilities, cardType, supertype,
        // subtype, color, manaCost, ownerId). Avoids the double-serialization bug where
        // pre-converting to org.json.JSONArray and then nesting inside a Map causes
        // ObjectMapper to fail — instead we let a single convertObjectToJson call in
        // buildTrajectoryPayload serialize the whole ctx at once.
        try {
            List<Map<String, Object>> atksJson = new ArrayList<>();
            for (Permanent atk : possibleAttackers) {
                atksJson.add(permanentToSimpleMap(atk));
            }
            List<Map<String, Object>> blksJson = new ArrayList<>();
            for (Permanent blk : possibleBlockers) {
                blksJson.add(permanentToSimpleMap(blk));
            }
            Map<String, Object> ctx = new HashMap<>();
            ctx.put("possibleAttackers", atksJson);
            ctx.put("possibleBlockers", blksJson);
            ctx.put("loggedAt", System.currentTimeMillis());
            logTrajectoryData(game, "attackers", null, null, null, ctx);
        } catch (Exception e) {
            logger.warn("Failed to log pre-attackers trajectory: " + e.getMessage());
        }

        // Execute original attacker selection logic (CP7 alpha-beta)
        super.selectAttackers(game, attackingPlayerId);

        // Log chosen attackers with their UUIDs
        try {
            Map<String, Object> chosenAction = new HashMap<>();
            List<UUID> declaredIds = new ArrayList<>(game.getCombat().getAttackers());
            chosenAction.put("declared_attackers", declaredIds.size());
            List<String> attackerUuids = new ArrayList<>();
            for (UUID id : declaredIds) {
                attackerUuids.add(id.toString());
            }
            chosenAction.put("attacker_uuids", attackerUuids);

            logTrajectoryData(game, "attackers_result", null, chosenAction, null);
        } catch (Exception e) {
            logger.warn("Failed to log post-attackers trajectory: " + e.getMessage());
        }

        updatePerformanceMetrics(startTime);
    }

    @Override
    public void selectBlockers(Ability source, Game game, UUID defendingPlayerId) {
        long startTime = System.currentTimeMillis();

        // Log pre-blockers state using permanentToSimpleMap for full field parity
        // with the attackers log (id, name, power, toughness, abilities, cardType,
        // supertype, subtype, color, manaCost, ownerId).
        try {
            List<Map<String, Object>> availableBlockers = new ArrayList<>();
            List<mage.game.permanent.Permanent> blockers = super.getAvailableBlockers(game);
            for (mage.game.permanent.Permanent blocker : blockers) {
                availableBlockers.add(permanentToSimpleMap(blocker));
            }

            List<Map<String, Object>> attackingCreatures = new ArrayList<>();
            for (UUID atkId : game.getCombat().getAttackers()) {
                mage.game.permanent.Permanent atk = game.getPermanent(atkId);
                if (atk != null) {
                    attackingCreatures.add(permanentToSimpleMap(atk));
                }
            }

            Map<String, Object> availableActions = new HashMap<>();
            availableActions.put("available_blockers", availableBlockers);
            availableActions.put("attacking_creatures", attackingCreatures);
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
     * Convert a Permanent to a simple Map with the fields the Python Permanent
     * pydantic model expects (id, name, power, toughness, abilities, cardType,
     * supertype, subtype, color, manaCost). Uses only primitive/String values so
     * the Map can be serialized by Jackson in a single convertObjectToJson call
     * without pre-conversion to org.json types.
     */
    private Map<String, Object> permanentToSimpleMap(Permanent p) {
        Map<String, Object> m = new HashMap<>();
        // permanentId: the unique UUID of this permanent instance on the battlefield.
        // id: also the same UUID string; the Python Permanent pydantic model uses id as
        // a Card/Token/str field looked up from gameCards. We store both so downstream
        // code can use permanentId for attacker UUID matching without ambiguity.
        m.put("permanentId", p.getId().toString());
        m.put("id", p.getId().toString());
        m.put("name", p.getName());
        m.put("power", p.getPower() != null ? p.getPower().getValue() : 0);
        m.put("toughness", p.getToughness() != null ? p.getToughness().getValue() : 0);
        m.put("abilities", p.getAbilities().stream()
                .map(a -> a.getRule())
                .filter(r -> r != null && !r.isEmpty())
                .collect(java.util.stream.Collectors.joining("; ")));
        m.put("cardType", p.getCardType() != null ? p.getCardType().toString() : "");
        m.put("supertype", p.getSuperType() != null ? p.getSuperType().toString() : "");
        m.put("subtype", p.getSubtype() != null ? p.getSubtype().toString() : "");
        m.put("color", p.getColor() != null ? p.getColor().toString() : "");
        m.put("manaCost", p.getManaCost() != null ? p.getManaCost().toString() : "");
        m.put("ownerId", p.getOwnerId() != null ? p.getOwnerId().toString() : "");
        return m;
    }

    /**
     * Log game termination as a standard trajectory entry.
     * Uses the same format as other trajectory logs to maintain consistency.
     * Includes error handling to prevent game disruption if logging fails.
     */
    public void logGameTermination(Game game) {
        // Add debug logging to see if this method is being called
        System.out.println("DEBUG: logGameTermination called for game: " +
                (game.getId() != null ? game.getId().toString() : "null"));
        logger.info("DEBUG: logGameTermination called for player " + getName() + " in game " +
                (game.getId() != null ? game.getId().toString() : "null"));

        if (gameEndLogged) {
            System.out.println("DEBUG: Game end already logged, skipping");
            return; // Prevent duplicate logging
        }
        gameEndLogged = true;

        try {
            System.out.println("DEBUG: About to log trajectory data for game_end");
            logTrajectoryData(game, "game_end", null, null, null);
            System.out.println("DEBUG: Successfully logged game_end trajectory data");
        } catch (Exception e) {
            System.err.println("DEBUG: Failed to log game termination: " + e.getMessage());
            logger.warn("Failed to log game termination: " + e.getMessage());
            // Game continues normally even if logging fails
        }
    }

    /**
     * Core method to log trajectory data to the magellmfast server.
     */
    private void logTrajectoryData(Game game, String decisionType, Object availableActions,
            Map<String, Object> chosenAction, Ability sourceAbility) {
        logTrajectoryData(game, decisionType, availableActions, chosenAction, sourceAbility, null);
    }

    /**
     * Log trajectory data with a custom additionalContext map (used by selectAttackers
     * to include possibleAttackers/possibleBlockers as serialized Permanent objects).
     */
    private void logTrajectoryData(Game game, String decisionType, Object availableActions,
            Map<String, Object> chosenAction, Ability sourceAbility,
            Map<String, Object> customContext) {
        try {
            Map<String, Object> additionalContext = customContext != null
                    ? new HashMap<>(customContext) : new HashMap<>();
            if (sourceAbility != null) {
                additionalContext.put("sourceAbility", sourceAbility.toString());
                additionalContext.put("sourceId", sourceAbility.getSourceId().toString());
            }
            if (!additionalContext.containsKey("loggedAt")) {
                additionalContext.put("loggedAt", System.currentTimeMillis());
            }
            // Assign seq SYNCHRONOUSLY (before the async send) so entries can be
            // sorted into logical order by the Python filter even when async HTTP
            // delivery causes out-of-order arrivals at the server.
            additionalContext.put("log_seq", logSeq++);

            JSONObject payload = decisionHandler.buildTrajectoryPayload(
                    game, this, decisionType, availableActions, chosenAction, additionalContext);

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
