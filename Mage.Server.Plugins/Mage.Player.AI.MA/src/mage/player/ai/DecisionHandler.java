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
            JSONObject payload = buildBasePayload(game, currentPlayer, strategy);
            payload.put("allActions", convertObjectToJson(allActions));

            DecisionPayload dp = new DecisionPayload("/api/mtg_llm/choose_from_all_actions/", payload);
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
            JSONObject payload = buildBasePayload(game, currentPlayer, strategy);
            payload.put("outcome", convertObjectToJson(outcome));
            payload.put("choice", convertObjectToJson(choice));
            payload.put("allChoices", convertObjectToJson(allChoices));

            DecisionPayload dp = new DecisionPayload("/api/mtg_llm/choose_from_choices/", payload);
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
            JSONObject payload = buildBasePayload(game, currentPlayer, strategy);
            payload.put("possibleAttackers", convertObjectToJson(possibleAttackers));
            payload.put("possibleBlockers", convertObjectToJson(possibleBlockers));

            DecisionPayload dp = new DecisionPayload("/api/mtg_llm/choose_attackers/", payload);
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
            JSONObject payload = buildTargetPayload(game, currentPlayer, outcome, allChoices, strategy);

            DecisionPayload dp = new DecisionPayload("/api/mtg_llm/choose_targets/", payload);
            DecisionResult result = client.requestDecision(dp);

            logDecision("TARGET", allChoices.length, result);
            return result;
        } catch (Exception e) {
            logger.error("Failed to handle target decision", e);
            return new DecisionResult(0, null, "fallback_to_first_target");
        }
    }

    /**
     * Build trajectory logging payload for RL training data collection.
     * This is used by ComputerPlayer7Instrumented to log decision trajectories.
     */
    public JSONObject buildTrajectoryPayload(Game game, Player currentPlayer, String decisionType,
            Object availableActions, Map<String, Object> chosenAction, Map<String, Object> additionalContext) {
        try {
            JSONObject payload = new JSONObject();

            // Basic request context
            payload.put("request_id", java.util.UUID.randomUUID().toString());
            payload.put("gameId", game.getId().toString());
            payload.put("matchId", (Object) null); // Game interface doesn't have matchId directly

            // Decision context
            payload.put("decisionType", decisionType);

            // Full game state and game over status
            JSONObject gameData = new JSONObject();
            gameData.put("id", game.getId().toString());
            gameData.put("numPlayers", game.getNumPlayers());
            gameData.put("startingLife", game.getStartingLife());
            gameData.put("currentTurn", game.getTurnNum());
            gameData.put("activePlayerId",
                    game.getActivePlayerId() != null ? game.getActivePlayerId().toString() : null);
            gameData.put("priorityPlayerId",
                    game.getPriorityPlayerId() != null ? game.getPriorityPlayerId().toString() : null);
            payload.put("game", gameData);
            payload.put("gameIsOver", game.checkIfGameIsOver());

            // Available actions and chosen action
            Object availableActionsJson = convertObjectToJson(availableActions);
            Object chosenActionJson = convertObjectToJson(chosenAction);
            Object additionalContextJson = convertObjectToJson(additionalContext);

            if (availableActionsJson instanceof JSONObject) {
                payload.put("availableActions", (JSONObject) availableActionsJson);
            } else if (availableActionsJson instanceof JSONArray) {
                payload.put("availableActions", (JSONArray) availableActionsJson);
            } else {
                payload.put("availableActions", availableActionsJson.toString());
            }

            if (chosenActionJson instanceof JSONObject) {
                payload.put("chosenAction", (JSONObject) chosenActionJson);
            } else if (chosenActionJson instanceof JSONArray) {
                payload.put("chosenAction", (JSONArray) chosenActionJson);
            } else {
                payload.put("chosenAction", chosenActionJson.toString());
            }

            if (additionalContextJson instanceof JSONObject) {
                payload.put("additionalContext", (JSONObject) additionalContextJson);
            } else if (additionalContextJson instanceof JSONArray) {
                payload.put("additionalContext", (JSONArray) additionalContextJson);
            } else {
                payload.put("additionalContext", additionalContextJson.toString());
            }

            return payload;
        } catch (Exception e) {
            logger.error("Failed to build trajectory payload", e);
            return new JSONObject().put("error", "Failed to build trajectory payload: " + e.getMessage());
        }
    }

    /**
     * Build base payload with common game state and player information
     */
    private JSONObject buildBasePayload(Game game, Player currentPlayer, String strategy) {
        JSONObject payload = new JSONObject();

        // Add game cards
        payload.put("gameCards", convertObjectToJson(game.getCards()));

        // Add game state
        GameState gameState = game.getState();
        payload.put("gameState", convertObjectToJson(gameState));

        // Add current player
        payload.put("currentPlayer", convertObjectToJson(currentPlayer));

        // Add opponent player
        Player opponentPlayer = findOpponent(game, currentPlayer);
        payload.put("opponentPlayer", convertObjectToJson(opponentPlayer));

        // Add game view (simplified for now)
        payload.put("gameView", new JSONObject());

        // Add strategy and IDs
        payload.put("strategy", strategy);
        payload.put("game_id", getGameId(game));
        payload.put("match_id", getMatchId(game));

        return payload;
    }

    /**
     * Build specialized payload for target selection
     */
    private JSONObject buildTargetPayload(Game game, Player currentPlayer, Outcome outcome,
            String[] allChoices, String strategy) {
        try {
            JSONObject payload = new JSONObject();

            // Build game cards
            JSONArray gameCards = new JSONArray();
            for (Card card : game.getCards()) {
                JSONObject cardObj = new JSONObject();
                cardObj.put("id", card.getId().toString());
                cardObj.put("name", card.getName());
                cardObj.put("type", card.getCardType().toString());
                gameCards.put(cardObj);
            }
            payload.put("gameCards", gameCards);

            // Build game state
            JSONObject gameState = new JSONObject();
            gameState.put("turn", game.getTurnNum());
            gameState.put("phase", game.getPhase().getType().toString());
            gameState.put("step", game.getStep().getType().toString());
            payload.put("gameState", gameState);

            // Build choices
            JSONArray choices = new JSONArray();
            for (String choice : allChoices) {
                choices.put(choice);
            }
            payload.put("allChoices", choices);

            // Build current player
            JSONObject currentPlayerObj = new JSONObject();
            currentPlayerObj.put("id", currentPlayer.getId().toString());
            currentPlayerObj.put("life", currentPlayer.getLife());
            currentPlayerObj.put("handSize", currentPlayer.getHand().size());
            payload.put("currentPlayer", currentPlayerObj);

            // Build opponent player
            UUID opponentId = game.getOpponents(currentPlayer.getId()).iterator().next();
            Player opponent = game.getPlayer(opponentId);
            JSONObject opponentObj = new JSONObject();
            opponentObj.put("id", opponent.getId().toString());
            opponentObj.put("life", opponent.getLife());
            opponentObj.put("handSize", opponent.getHand().size());
            payload.put("opponentPlayer", opponentObj);

            // Build outcome
            JSONObject outcomeObj = new JSONObject();
            outcomeObj.put("isGood", outcome.isGood());
            outcomeObj.put("toString", outcome.toString());
            payload.put("outcome", outcomeObj);

            // Build choice
            JSONObject choiceObj = new JSONObject();
            choiceObj.put("message", "Choose target");
            payload.put("choice", choiceObj);

            // Build game view
            JSONObject gameView = new JSONObject();
            gameView.put("battlefieldSize", game.getBattlefield().getAllActivePermanents().size());
            payload.put("gameView", gameView);
            payload.put("strategy", strategy);
            payload.put("game_id", getGameId(game));
            payload.put("match_id", getMatchId(game));

            return payload;
        } catch (Exception e) {
            logger.error("Failed to build target payload", e);
            return new JSONObject();
        }
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
            String jsonString = objectMapper.writeValueAsString(obj);
            if (jsonString.startsWith("{")) {
                return new JSONObject(jsonString);
            } else if (jsonString.startsWith("[")) {
                return new JSONArray(jsonString);
            } else {
                throw new JsonProcessingException("Invalid JSON string: " + jsonString) {
                };
            }
        } catch (JsonProcessingException e) {
            logger.error("Error converting object to JSON: " + obj.getClass().getName(), e);
            return new JSONObject().put("error", "Failed to convert " + obj.getClass().getName() + " to JSON")
                    .put("message", e.getMessage().replace("\"", "\\\""));
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
