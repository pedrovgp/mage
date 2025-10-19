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

/**
 * Unified decision handler that consolidates all LLM decision logic.
 * Handles payload building, communication with magellmfast, and result
 * processing.
 */
public class DecisionHandler {
    private static final Logger logger = Logger.getLogger(DecisionHandler.class);

    // Endpoint constants aligned with magellmfast routes (relative paths, base URL
    // added by LlmDecisionClient)
    private static final String ENDPOINT_CHOOSE_FROM_ALL_ACTIONS = "/choose_from_all_actions/";
    private static final String ENDPOINT_CHOOSE_ATTACKERS = "/choose_attackers/";
    private static final String ENDPOINT_CHOOSE_FROM_CHOICES = "/choose_from_choices/";
    private static final String ENDPOINT_CHOOSE_TARGETS = "/choose_targets/";
    private static final String ENDPOINT_CHOOSE_TARGET_AMOUNT = "/chooseTargetAmount/";
    private static final String ENDPOINT_LOG_TRAJECTORY = "/v1/log_trajectory";

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
     * Handle action selection decisions (choosing from list of abilities)
     */
    public DecisionResult handleAction(Game game, Player currentPlayer, List<Ability> allActions, String strategy) {
        try {
            JSONObject payload = buildChooseFromAllActionsPayload(game, currentPlayer, allActions, strategy);
            DecisionPayload dp = new DecisionPayload(ENDPOINT_CHOOSE_FROM_ALL_ACTIONS, payload);
            DecisionResult result = client.requestDecision(dp);

            logDecision("ACTION", allActions.size(), result);
            return result;
        } catch (Exception e) {
            logger.error("Failed to handle action decision", e);
            return new DecisionResult(0, null, "fallback_to_first_action");
        }
    }

    /**
     * Handle choice selection decisions (choosing from array of choices)
     */
    public DecisionResult handleChoice(Game game, Player currentPlayer, Outcome outcome,
            Choice choice, String[] allChoices, String strategy) {
        try {
            JSONObject payload = buildChooseFromChoicesPayload(game, currentPlayer, outcome, choice, allChoices,
                    strategy);
            DecisionPayload dp = new DecisionPayload(ENDPOINT_CHOOSE_FROM_CHOICES, payload);
            DecisionResult result = client.requestDecision(dp);

            logDecision("CHOICE", allChoices.length, result);
            return result;
        } catch (Exception e) {
            logger.error("Failed to handle choice decision", e);
            return new DecisionResult(0, null, "fallback_to_first_choice");
        }
    }

    /**
     * Handle attacker selection decisions
     */
    public DecisionResult handleAttackers(Game game, Player currentPlayer,
            List<Permanent> possibleAttackers,
            List<Permanent> possibleBlockers, String strategy) {
        try {
            JSONObject payload = buildChooseAttackersPayload(game, currentPlayer, possibleAttackers, possibleBlockers,
                    strategy);
            DecisionPayload dp = new DecisionPayload(ENDPOINT_CHOOSE_ATTACKERS, payload);
            DecisionResult result = client.requestDecision(dp);

            logDecision("ATTACKERS", possibleAttackers.size(), result);
            return result;
        } catch (Exception e) {
            logger.error("Failed to handle attacker decision", e);
            return new DecisionResult(null, List.of(), "fallback_no_attackers");
        }
    }

    /**
     * Handle target selection decisions
     */
    public DecisionResult handleTargets(Game game, Player currentPlayer, Outcome outcome,
            String[] allChoices, String strategy) {
        try {
            // Create a dummy choice object since the endpoint requires it
            Choice choice = new mage.choices.ChoiceImpl(true);
            choice.setMessage("Choose target");

            JSONObject payload = buildChooseFromChoicesPayload(game, currentPlayer, outcome, choice, allChoices,
                    strategy);
            DecisionPayload dp = new DecisionPayload(ENDPOINT_CHOOSE_TARGETS, payload);
            DecisionResult result = client.requestDecision(dp);

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
            // Fallback to first target with index 0 and populate chosenUuids
            List<UUID> fallbackUuids = targetIds.isEmpty() ? List.of() : List.of(UUID.fromString(targetIds.get(0)));
            return new DecisionResult(0, fallbackUuids, "fallback_to_first_target");
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

        // Handle additionalContext
        if (additionalContext != null && !additionalContext.isEmpty()) {
            payload.put("additionalContext", convertObjectToJson(additionalContext));
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

        // Add DecisionBase-specific fields
        payload.put("gameCards", convertObjectToJson(game.getCards()));
        payload.put("gameState", convertObjectToJson(game.getState()));
        payload.put("currentPlayer", convertObjectToJson(currentPlayer));
        payload.put("opponentPlayer", convertObjectToJson(findOpponent(game, currentPlayer)));
        payload.put("gameView", new JSONObject()); // Simplified for now

        return payload;
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
    private Object convertObjectToJson(Object obj) {
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
