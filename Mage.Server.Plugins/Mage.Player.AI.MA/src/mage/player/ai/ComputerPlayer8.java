package mage.player.ai;

import mage.Mana;
import mage.abilities.Ability;
import mage.abilities.ActivatedAbility;
import mage.abilities.StaticAbility;
import mage.abilities.TriggeredAbilities;
import mage.abilities.costs.mana.ColoredManaCost;
import mage.abilities.costs.mana.GenericManaCost;
import mage.abilities.costs.mana.ManaCost;
import mage.cards.Card;
import mage.cards.decks.Deck;
import mage.cards.o.Ornithopter;
import mage.constants.RangeOfInfluence;
import mage.game.Game;
import mage.game.GameState;
import mage.game.match.MatchPlayer;
import mage.players.Player;

import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.io.OutputStream;
import java.net.URL;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;

/**
 * AI: server side bot with game simulations (mad bot, the latest version)
 *
 * @author ayratn
 */
public class ComputerPlayer8 extends ComputerPlayer7 {

    private static final Logger logger = Logger.getLogger(ComputerPlayer8.class);

    private boolean allowBadMoves;

    public ComputerPlayer8(String name, RangeOfInfluence range, int skill) {
        super(name, range, skill);
    }

    public ComputerPlayer8(final ComputerPlayer8 player) {
        super(player);
        this.allowBadMoves = player.allowBadMoves;
    }

    @Override
    public ComputerPlayer8 copy() {
        return new ComputerPlayer8(this);
    }

    @Override
    public boolean priority(Game game) {
        game.resumeTimer(getTurnControlledBy());
        boolean result = priorityPlay(game);
        game.pauseTimer(getTurnControlledBy());
        return result;
    }

    private boolean priorityPlay(Game game) {
        game.getState().setPriorityPlayerId(playerId);
        game.firePriorityEvent(playerId);
        switch (game.getTurnStepType()) {
            case UPKEEP:
            case DRAW:
                pass(game);
                return false;
            case PRECOMBAT_MAIN:
                // 09.03.2020:
                // in old version it passes opponent's pre-combat step
                // (game.isActivePlayer(playerId) -> pass(game))
                // why?!
                printBattlefieldScore(game, "Sim PRIORITY on MAIN 1");
                if (actions.isEmpty()) {
                    calculateActions(game);
                } else {
                    // TODO: is it possible non empty actions without calculation?!
                    throw new IllegalStateException("wtf");
                }
                act(game);
                return true;
            case BEGIN_COMBAT:
                pass(game);
                return false;
            case DECLARE_ATTACKERS:
                printBattlefieldScore(game, "Sim PRIORITY on DECLARE ATTACKERS");
                if (actions.isEmpty()) {
                    calculateActions(game);
                } else {
                    // TODO: is it possible non empty actions without calculation?!
                    throw new IllegalStateException("wtf");
                }
                act(game);
                return true;
            case DECLARE_BLOCKERS:
                printBattlefieldScore(game, "Sim PRIORITY on DECLARE BLOCKERS");
                if (actions.isEmpty()) {
                    calculateActions(game);
                } else {
                    // TODO: is it possible non empty actions without calculation?!
                    throw new IllegalStateException("wtf");
                }
                act(game);
                return true;
            case FIRST_COMBAT_DAMAGE:
            case COMBAT_DAMAGE:
            case END_COMBAT:
                pass(game);
                return false;
            case POSTCOMBAT_MAIN:
                printBattlefieldScore(game, "Sim PRIORITY on MAIN 2");
                if (actions.isEmpty()) {
                    calculateActions(game);
                } else {
                    // TODO: is it possible non empty actions without calculation?!
                    throw new IllegalStateException("wtf");
                }
                act(game);
                return true;
            case END_TURN:
            case CLEANUP:
                actionCache.clear();
                pass(game);
                return false;
        }
        return false;
    }

    protected int simulatePriority(SimulationNode2 node, Game game, int depth, int alpha, int beta) {
        // TODO PV this is where we can add the LLM, allActions are listed here. There
        // is a
        // complicated
        // simulation logic going on here, which we could completely replace by an LLM
        // receiving
        // a text description of the game state and having to choose a number from a
        // list
        // corresponding to one of the possible allActions
        if (!COMPUTER_DISABLE_TIMEOUT_IN_GAME_SIMULATIONS
                && Thread.interrupted()) {
            Thread.currentThread().interrupt();
            logger.info("interrupted");
            return GameStateEvaluator2.evaluate(playerId, game).getTotalScore();
        }
        node.setGameValue(game.getState().getValue(true).hashCode());
        SimulatedPlayer2 currentPlayer = (SimulatedPlayer2) game.getPlayer(game.getPlayerList().get());
        SimulationNode2 bestNode = null;
        List<Ability> allActions = currentPlayer.simulatePriority(game);
        optimize(game, allActions);

        // Call the LLM to choose an action
        int chosenActionIndex = callLLMToChooseAction(game, allActions, currentPlayer);

        // Log the chosen action
        if (logger.isInfoEnabled()) {
            logger.info("LLM chosen action: " + allActions.get(chosenActionIndex).toString());
        }

        // Execute the chosen action
        if (chosenActionIndex >= 0 && chosenActionIndex < allActions.size()) {
            Ability chosenAction = allActions.get(chosenActionIndex);
            if (!(chosenAction instanceof StaticAbility)) {
                currentPlayer.activateAbility((ActivatedAbility) chosenAction, game);
            }

            // Create a new SimulationNode2 for the chosen action
            SimulationNode2 newNode = new SimulationNode2(node, game, chosenAction, depth, currentPlayer.getId());

            // Update the node with the new child node
            node.children.clear();
            node.children.add(newNode);
            node.setScore(GameStateEvaluator2.evaluate(playerId, game).getTotalScore());
        }

        // Return the score after executing the chosen action
        return GameStateEvaluator2.evaluate(playerId, game).getTotalScore();

    }

    // MixIn classes to ignore the problematic fields
    public abstract class GenericManaCostMixIn {
        @JsonIgnore
        private GenericManaCost unpaid;
        @JsonIgnore
        private List<Mana> options;
        @JsonIgnore
        private boolean paid;
    }

    public abstract class OrnithopterMixIn {
        @JsonIgnore
        private Ability secondFaceSpellAbility;
    }

    @JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
    public abstract class SimulatedPlayer2MixIn {
        @JsonIgnore
        private MatchPlayer matchPlayer;
        @JsonIgnore
        private Player realPlayer; // Add this line to ignore the realPlayer field
    }

    public abstract class MatchPlayerMixIn {
        @JsonIgnore
        private Deck deck;
    }

    public abstract class DeckMixIn {
        @JsonIgnore
        private Set<Card> cards;
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
        private Map<UUID, TriggeredAbilities> triggers;
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
            // gen.writeStringField("manaCost", card.getManaCost() != null ?
            // card.getManaCost().toString() : ""); // Very
            // likely generates infinite recursion issues
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

    private Object convertObjectToJson(Object obj) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        objectMapper.addMixIn(Ornithopter.class, OrnithopterMixIn.class);
        objectMapper.addMixIn(SimulatedPlayer2.class, SimulatedPlayer2MixIn.class);
        objectMapper.addMixIn(MatchPlayer.class, MatchPlayerMixIn.class);
        objectMapper.addMixIn(Deck.class, DeckMixIn.class);
        objectMapper.addMixIn(Card.class, CardMixIn.class);
        objectMapper.addMixIn(ManaCost.class, ManaCostMixIn.class);
        objectMapper.addMixIn(ColoredManaCost.class, ColoredManaCostMixIn.class);
        objectMapper.addMixIn(GameState.class, GameStateMixIn.class);
        objectMapper.addMixIn(GenericManaCost.class, GenericManaCostMixIn.class);

        // Register the custom serializer for the Card class
        SimpleModule module = new SimpleModule();
        module.addSerializer(Card.class, new CardSerializer());
        module.addSerializer(Ability.class, new AbilitySerializer());
        objectMapper.registerModule(module);

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

    private void sendMsgWithLLMChosenAction(List<Ability> allActions, LLMResponse parsedResponse) {
        StringBuilder message = new StringBuilder("Possible actions:\n");
        for (int i = 0; i < allActions.size(); i++) {
            message.append(i).append(": ").append(allActions.get(i).toString()).append("\n");
        }

        int chosenActionIndex = parsedResponse.getChosenActionIndex();

        message.append("LLM chosen action: ").append(allActions.get(chosenActionIndex).toString()).append(": ")
                .append(allActions.get(chosenActionIndex).toString());

        // Assuming there's a method to send a chat message
        sendChatMessage(message.toString());
    }

    private int callLLMToChooseAction(Game game, List<Ability> allActions, SimulatedPlayer2 currentPlayer) {
        // Prepare the context for the LLM
        JSONObject payload = new JSONObject();
        payload.put("gameCards", convertObjectToJson(game.getCards()));
        payload.put("gameState", convertObjectToJson(game.getState()));
        payload.put("allActions", convertObjectToJson(allActions));
        payload.put("currentPlayer", convertObjectToJson(currentPlayer));

        // Test
        // JSONObject payloadTest = new JSONObject()
        // .put("gameState",
        // new JSONObject().put("turn", 5).put("activePlayer",
        // "player1").put("lifeTotalPlayer1", 20)
        // .put("lifeTotalPlayer2", 18).toString())
        // .put("allActions", new JSONObject()
        // .put("actions",
        // new JSONObject[] {
        // new JSONObject().put("actionId", 1).put("description",
        // "Attack with creature A"),
        // new JSONObject().put("actionId", 2).put("description", "Cast spell B") })
        // .toString())
        // .put("playerContext", new JSONObject().put("playerId",
        // "player1").put("manaAvailable", 3).toString());
        // HttpURLConnection llmResponseTest = sendContextToLLM(payloadTest.toString());

        // Send the context to the LLM and get the response
        HttpURLConnection llmResponse = sendContextToLLM(payload.toString());

        LLMResponse parsedResponse = parseLLMResponse(llmResponse);

        sendMsgWithLLMChosenAction(allActions, parsedResponse);

        // Parse the response to get the chosen action index
        int chosenActionIndex = parsedResponse.getChosenActionIndex();

        // TODO PV remove later this simple test later - BEGIN
        chosenActionIndex = Math.max(0, allActions.size() - 2);
        // Log the random action chosen
        if (logger.isInfoEnabled()) {
            logger.info("Random action chosen: " +
                    allActions.get(chosenActionIndex).toString());
        }

        return chosenActionIndex;
    }

    private String convertContextToJson(Map<String, String> context) {
        StringBuilder jsonBuilder = new StringBuilder();
        jsonBuilder.append("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : context.entrySet()) {
            if (!first) {
                jsonBuilder.append(",");
            }
            jsonBuilder.append("\"").append(entry.getKey()).append("\":").append(entry.getValue());
            first = false;
        }
        jsonBuilder.append("}");
        return jsonBuilder.toString();
    }

    private HttpURLConnection sendContextToLLM(String contextJson) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL("http://localhost:9000/api/mtg_llm/choose_from_all_actions/");
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json; utf-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setDoOutput(true);

            String jsonInputString = contextJson;

            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                // Log the error response
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8))) {
                    StringBuilder errorResponse = new StringBuilder();
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        errorResponse.append(responseLine.trim());
                    }
                    logger.error("Failed to get response from LLM. HTTP code: " + responseCode + ", Response: "
                            + errorResponse.toString());
                }
            }
        } catch (Exception e) {
            logger.error("Exception while sending context to LLM", e);
        }
        return connection;
    }

    private LLMResponse parseLLMResponse(HttpURLConnection llmResponse) {
        int chosenActionIndex = 0; // Default value
        String reason = "";
        try {
            int responseCode = llmResponse.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(llmResponse.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        response.append(responseLine.trim());
                    }
                    // Use the responseString directly
                    String responseString = response.toString();
                    // Assuming the responseString contains the chosen action index and reason
                    String[] parts = responseString.split(": ", 2);
                    if (parts.length == 2) {
                        chosenActionIndex = Integer.parseInt(parts[0]);
                        reason = parts[1];
                    }
                }
            } else {
                // Log the error response
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(llmResponse.getErrorStream(), StandardCharsets.UTF_8))) {
                    StringBuilder errorResponse = new StringBuilder();
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        errorResponse.append(responseLine.trim());
                    }
                    logger.error("Failed to get response from LLM. HTTP code: " + responseCode + ", Response: "
                            + errorResponse.toString());
                }
            }
        } catch (NumberFormatException e) {
            logger.error("NumberFormatException while parsing LLM response", e);
        } catch (Exception e) {
            logger.error("Exception while parsing LLM response", e);
        }
        return new LLMResponse(chosenActionIndex, reason);
    }

    protected void calculateActions(Game game) {
        if (!getNextAction(game)) {
            // logger.info("--- calculating possible actions for " + this.getName() + " on "
            // + game.toString());
            Date startTime = new Date();
            currentScore = GameStateEvaluator2.evaluate(playerId, game).getTotalScore();
            Game sim = createSimulation(game);
            SimulationNode2.resetCount();
            root = new SimulationNode2(null, sim, maxDepth, playerId);
            addActionsTimed(); // TODO: root can be null again after addActionsTimed O_o need to research (it's
                               // a CPU AI problem?)
            if (root != null && root.children != null && !root.children.isEmpty()) {
                logger.trace("After add actions timed: root.children.size = " + root.children.size());
                root = root.children.get(0);

                // prevent repeating always the same action with no cost
                boolean doThis = true;
                if (root.abilities.size() == 1) {
                    for (Ability ability : root.abilities) {
                        if (ability.getManaCosts().manaValue() == 0
                                && ability.getCosts().isEmpty()) {
                            if (actionCache.contains(ability.getRule() + '_' + ability.getSourceId())) {
                                doThis = false; // don't do it again
                            }
                        }
                    }
                }

                if (doThis) {
                    actions = new LinkedList<>(root.abilities);
                    combat = root.combat; // TODO: must use copy?!
                    for (Ability ability : actions) {
                        actionCache.add(ability.getRule() + '_' + ability.getSourceId());
                    }
                }
            } else {
                logger.info('[' + game.getPlayer(playerId).getName() + "][pre] Action: skip");
            }
            Date endTime = new Date();
            this.setLastThinkTime((endTime.getTime() - startTime.getTime()));

            /*
             * logger.warn("Last think time: " + this.getLastThinkTime()
             * + "; actions: " + actions.size()
             * + "; hand: " + this.getHand().size()
             * + "; permanents: " + game.getBattlefield().getAllPermanents().size());
             */
        } else {
            logger.debug("Next Action exists!");
        }
    }

    @Override
    public void setAllowBadMoves(boolean allowBadMoves) {
        this.allowBadMoves = allowBadMoves;
    }
}
