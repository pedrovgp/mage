package mage.player.ai;

import mage.Mana;
// import mage.ObjectColor;
import mage.abilities.Ability;
import mage.abilities.TriggeredAbilities;
import mage.abilities.common.PassAbility;
import mage.abilities.costs.mana.ColoredManaCost;
import mage.abilities.costs.mana.GenericManaCost;
import mage.abilities.costs.mana.ManaCost;
import mage.abilities.keyword.DeathtouchAbility;
import mage.abilities.keyword.DoubleStrikeAbility;
import mage.abilities.keyword.ExaltedAbility;
import mage.abilities.keyword.FirstStrikeAbility;
import mage.abilities.keyword.FlyingAbility;
import mage.abilities.keyword.IndestructibleAbility;
import mage.abilities.keyword.ReachAbility;
import mage.cards.Card;
import mage.cards.decks.Deck;
import mage.cards.o.Ornithopter;
import mage.choices.Choice;
import mage.constants.Outcome;
import mage.constants.RangeOfInfluence;
import mage.counters.CounterType;
import mage.filter.StaticFilters;
import mage.game.Game;
import mage.game.GameState;
import mage.game.events.GameEvent;
import mage.game.match.MatchPlayer;
import mage.game.permanent.Permanent;
import mage.player.ai.util.CombatInfo;
import mage.player.ai.util.CombatUtil;
import mage.players.Player;
import mage.players.PlayerList;
import mage.players.Players;

import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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

    private boolean llmPlay(Game game) {
        // printBattlefieldScore(game, "Sim PRIORITY on MAIN 1");
        PassAbility passAbility = new PassAbility();
        LinkedList<Ability> allActions = new LinkedList<>();
        allActions.add(passAbility);
        allActions.addAll(this.getPlayable(game, false));

        // Call the LLM to choose an action
        // If there is only one action (pass), then just pass
        int chosenActionIndex = 0;
        if (allActions.size() > 1) {
            chosenActionIndex = callLLMToChooseAction(game, allActions, this);
        }

        Ability chosenAction = allActions.get(chosenActionIndex);

        // Log the chosen action
        if (logger.isInfoEnabled()) {
            logger.info("LLM chosen action: " +
                    chosenAction.toString());
        }

        if (chosenAction instanceof PassAbility) {
            pass(game);
        } else {
            LinkedList<Ability> singleActionList = new LinkedList<>();
            singleActionList.add(chosenAction);
            this.actions = singleActionList; // this.actions is always a single item list when act is called
            act(game);
        }

        return true;
    }

    private boolean priorityPlay(Game game) {
        game.getState().setPriorityPlayerId(playerId);
        game.firePriorityEvent(playerId);

        switch (game.getTurnStepType()) {
            case UNTAP:
                pass(game);
                return false;
            case UPKEEP:
                return llmPlay(game);
            case DRAW:
                pass(game);
                return false;
            case PRECOMBAT_MAIN:
                return llmPlay(game);

            case BEGIN_COMBAT:
                return llmPlay(game);
            case DECLARE_ATTACKERS:
                return llmPlay(game);
            case DECLARE_BLOCKERS:
                return llmPlay(game);
            case FIRST_COMBAT_DAMAGE:
            case COMBAT_DAMAGE:
            case END_COMBAT:
                pass(game);
                return false;
            case POSTCOMBAT_MAIN:
                return llmPlay(game);
            case END_TURN:
                return llmPlay(game);
            case CLEANUP:
                actionCache.clear();
                pass(game);
                return false;
        }
        return false;
    }

    // MixIn classes to ignore the problematic fields
    public abstract class GenericManaCostMixIn {
        @JsonIgnore
        public GenericManaCost getUnpaid;
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

    public class RealPlayerUuidSerializer extends JsonSerializer<Player> {
        @Override
        public void serialize(Player player, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            // Write only the player's id (UUID) as a string
            gen.writeString(player.getId().toString());
        }
    }

    @JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
    public abstract class SimulatedPlayer2MixIn {
        @JsonIgnore
        private MatchPlayer matchPlayer;
        @JsonIgnore
        private Player realPlayer; // Add this line to ignore the realPlayer field
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
        // Instead of ignoring realPlayer, serialize it as an UUID
        @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = RealPlayerUuidSerializer.class)
        private Player realPlayer;
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
        @JsonIgnore
        private Players players;
        @JsonIgnore
        private PlayerList playersList;
    }

    // Helper method to find the opponent
    private Player findOpponent(Game game, Player currentPlayer) {
        for (UUID opponentId : game.getState().getPlayers().keySet()) {
            if (!opponentId.equals(currentPlayer.getId())) {
                return game.getPlayer(opponentId);
            }
        }
        return null;
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
        objectMapper.addMixIn(Player.class, HumanPlayerMixIn.class);
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
        // module.addSerializer(ObjectColor.class, new ObjectColorSerializer());
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

    private void sendMsgWithLLMChosenAction(Game game, Player player, List<Ability> allActions,
            LLMResponse parsedResponse) {
        StringBuilder message = new StringBuilder("Possible actions: \n");
        game.informPlayers(player.getLogName() + " POSSIBLE ACTIONS:");
        for (int i = 0; i < allActions.size(); i++) {
            game.informPlayers(i + ": " + allActions.get(i).toString());
        }

        int chosenActionIndex = parsedResponse.getChosenActionIndex();

        game.informPlayers("LLM CHOSEN ACTION:");
        game.informPlayers(chosenActionIndex + ": " + allActions.get(chosenActionIndex).toString());
        game.informPlayers(" REASON:");
        game.informPlayers(parsedResponse.getReason());
    }

    private int callLLMToChooseAction(Game game, LinkedList<Ability> allActions, ComputerPlayer currentPlayer) {
        // Prepare the context for the LLM
        JSONObject payload = new JSONObject();

        // GameView gameView = GameSessionPlayer.prepareGameView(game,
        // currentPlayer.getId(), currentPlayer.getId());

        GameState gameState = game.getState();
        Player opponentPlayer = findOpponent(game, currentPlayer);

        payload.put("gameCards", convertObjectToJson(game.getCards()));
        payload.put("gameState", convertObjectToJson(gameState));
        payload.put("allActions", convertObjectToJson(allActions));
        payload.put("currentPlayer", convertObjectToJson(currentPlayer));
        payload.put("opponentPlayer", convertObjectToJson(opponentPlayer));
        // payload.put("gameView", convertObjectToJson(gameView));
        payload.put("gameView", new JSONObject());

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
        HttpURLConnection llmResponse = sendContextToLLM(payload.toString(), null);

        LLMResponse parsedResponse = parseLLMResponse(llmResponse);

        sendMsgWithLLMChosenAction(game, currentPlayer, allActions, parsedResponse);

        // Parse the response to get the chosen action index
        int chosenActionIndex = parsedResponse.getChosenActionIndex();

        // Log the random action chosen
        if (logger.isInfoEnabled()) {
            logger.info("Random action chosen: " +
                    allActions.get(chosenActionIndex).toString());
        }

        return chosenActionIndex;
    }

    private HttpURLConnection sendContextToLLM(String contextJson, String urlString) {
        if (urlString == null || urlString.isEmpty()) {
            urlString = "http://localhost:9000/api/mtg_llm/choose_from_all_actions/";
        }
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlString);
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
                    // Parse the JSON response
                    String responseString = response.toString();
                    JSONObject jsonResponse = new JSONObject(responseString);
                    chosenActionIndex = jsonResponse.getInt("chosen_action");
                    reason = jsonResponse.getString("reason");
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

    private void sendMsgWithLLMChosenReason(Game game, Player player, String reasonFor, String reason) {
        game.informPlayers(player.getLogName() + " REASON FOR " + reasonFor);
        game.informPlayers(reason);
    }

    private LLMResponseAttackers parseLLMResponseAttackers(HttpURLConnection llmResponse) {
        List<UUID> chosenAttackersUUIDs = new ArrayList<>();
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
                    // Parse the JSON response
                    String responseString = response.toString();
                    JSONObject jsonResponse = new JSONObject(responseString);
                    List<Object> chosenAttackersUUIDsStrings = jsonResponse.getJSONArray("chosen_attackers").toList();
                    chosenAttackersUUIDs = new ArrayList<>();
                    for (Object uuidString : chosenAttackersUUIDsStrings) {
                        chosenAttackersUUIDs.add(UUID.fromString(uuidString.toString()));
                    }
                    reason = jsonResponse.getString("reason");
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
        } catch (Exception e) {
            logger.error("Exception while parsing LLM response", e);
        }
        return new LLMResponseAttackers(chosenAttackersUUIDs, reason);
    }

    @Override
    public void setAllowBadMoves(boolean allowBadMoves) {
        this.allowBadMoves = allowBadMoves;
    }

    @Override
    public boolean choose(Outcome outcome, Choice choice, Game game) {
        if (choices.isEmpty()) {
            return super.choose(outcome, choice, game);
        }
        if (!choice.isChosen()) {
            if (!choice.setChoiceByAnswers(choices, true)) {
                // TODO PV use setLLMChoice, after its implemented
                choice.setRandomChoice();
            }
        }
        return true;
    }

    @Override
    public void selectAttackers(Game game, UUID attackingPlayerId) {
        logger.debug("selectAttackers");
        declareAttackers(game, playerId);
    }

    private void declareAttackers(Game game, UUID activePlayerId) {
        attackersToCheck.clear();
        attackersList.clear();
        game.fireEvent(new GameEvent(GameEvent.EventType.DECLARE_ATTACKERS_STEP_PRE, null, null, activePlayerId));
        if (!game.replaceEvent(
                GameEvent.getEvent(GameEvent.EventType.DECLARING_ATTACKERS, activePlayerId, activePlayerId))) {
            Player attackingPlayer = game.getPlayer(activePlayerId);

            // find safe attackers (can't be killed by blockers)
            for (UUID defenderId : game.getOpponents(playerId)) {
                Player defender = game.getPlayer(defenderId);
                if (!defender.isInGame()) {
                    continue;
                }
                attackersList = super.getAvailableAttackers(defenderId, game);
                if (attackersList.isEmpty()) {
                    continue;
                }
                List<Permanent> possibleBlockers = defender.getAvailableBlockers(game);

                List<Permanent> selectedAttackers = callLLMToChooseAttackers(game, attackersList, possibleBlockers,
                        attackingPlayer);

                for (Permanent attacker : selectedAttackers) {
                    attackingPlayer.declareAttacker(attacker.getId(), defenderId, game, true);
                }

            }
        }
    }

    private List<Permanent> callLLMToChooseAttackers(Game game, List<Permanent> possibleAttackers,
            List<Permanent> possibleBlockers, Player currentPlayer) {
        // Prepare the context for the LLM
        JSONObject payload = new JSONObject();

        // GameView gameView = GameSessionPlayer.prepareGameView(game,
        // currentPlayer.getId(), currentPlayer.getId());

        GameState gameState = game.getState();
        Player opponentPlayer = findOpponent(game, currentPlayer);

        payload.put("gameCards", convertObjectToJson(game.getCards()));
        payload.put("gameState", convertObjectToJson(gameState));
        payload.put("possibleAttackers", convertObjectToJson(possibleAttackers));
        payload.put("possibleBlockers", convertObjectToJson(possibleBlockers));
        payload.put("currentPlayer", convertObjectToJson(currentPlayer));
        payload.put("opponentPlayer", convertObjectToJson(opponentPlayer));
        // payload.put("gameView", convertObjectToJson(gameView));
        payload.put("gameView", new JSONObject());

        // Send the context to the LLM and get the response
        HttpURLConnection llmResponse = sendContextToLLM(payload.toString(),
                "http://localhost:9000/api/mtg_llm/choose_attackers/");

        LLMResponseAttackers parsedResponse = parseLLMResponseAttackers(llmResponse);

        sendMsgWithLLMChosenReason(game, currentPlayer, "CHOOSING THESE ATTACKERS",
                parsedResponse.getReason());

        // Parse the response to get the chosen action index
        List<UUID> chosenAttackers = parsedResponse.getChosenAttackersUUIDs();
        // Use game.getPermanent() to get the actual Permanent objects in a list to be
        // returned
        List<Permanent> chosenAttackersList = new ArrayList<>();
        for (UUID attackerId : chosenAttackers) {
            chosenAttackersList.add(game.getPermanent(attackerId));
        }

        // Log the random action chosen
        if (logger.isInfoEnabled()) {
            logger.info("Attackers chosen: " +
                    chosenAttackers.toString());
        }

        return chosenAttackersList;

    }

    // private void declareBlockers(Game game, UUID activePlayerId) {
    // // TODO call llm with getAttackers (filterOutUnblockable) and
    // // getAvailableBlockers
    // // TODO request that the response is like {attackerId: List[blockerId]}

    // try {
    // // Call the LLM to get the blocking assignments
    // Map<Permanent, List<Permanent>> llmResponse = callLLMForBlockers(game);

    // Player player = game.getPlayer(playerId);

    // boolean blocked = false;
    // for (Map.Entry<Permanent, List<Permanent>> entry : llmResponse.entrySet()) {
    // UUID attackerId = entry.getKey().getId();
    // List<Permanent> blockers = entry.getValue();
    // if (blockers != null) {
    // for (Permanent blocker : blockers) {
    // // Attempt to declare blockers
    // player.declareBlocker(player.getId(), blocker.getId(), attackerId, game);
    // blocked = true;
    // }
    // }
    // }
    // if (blocked) {
    // game.getPlayers().resetPassed();
    // }
    // } catch (Exception e) {
    // // Log the exception and fall back to the default behavior
    // logger.error("Exception while calling LLM for blockers", e);
    // super.selectBlockers(null, game, activePlayerId);
    // }
    // }
}
