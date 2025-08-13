package mage.player.ai;

import mage.MageObject;
import mage.Mana;
// import mage.ObjectColor;
import mage.abilities.Ability;
import mage.abilities.TriggeredAbilities;
import mage.abilities.common.PassAbility;
import mage.abilities.costs.mana.ColoredManaCost;
import mage.abilities.costs.mana.GenericManaCost;
import mage.abilities.costs.mana.ManaCost;
import mage.cards.Card;
import mage.cards.Cards;
import mage.cards.CardsImpl;
import mage.cards.decks.Deck;
import mage.cards.o.Ornithopter;
import mage.choices.Choice;
import mage.choices.ComputerPlayer8Interface;
import mage.constants.ColoredManaSymbol;
import mage.constants.Outcome;
import mage.constants.RangeOfInfluence;
import mage.constants.Zone;
import mage.filter.FilterCard;
import mage.filter.FilterPermanent;
import mage.filter.common.FilterAnyTarget;
import mage.filter.common.FilterPermanentOrPlayer;
import mage.game.Game;
import mage.game.GameState;
import mage.game.events.GameEvent;
import mage.game.match.MatchPlayer;
import mage.game.permanent.Permanent;
import mage.game.stack.Spell;
import mage.game.stack.StackObject;
import mage.players.Player;
import mage.players.PlayerList;
import mage.players.Players;
import mage.target.Target;
import mage.target.TargetPermanent;
import mage.target.TargetPlayer;
import mage.target.TargetSpell;
import mage.target.TargetStackObject;
import mage.target.common.TargetActivatedAbility;
import mage.target.common.TargetActivatedOrTriggeredAbility;
import mage.target.common.TargetAnyTarget;
import mage.target.common.TargetCardInASingleGraveyard;
import mage.target.common.TargetCardInExile;
import mage.target.common.TargetCardInGraveyard;
import mage.target.common.TargetCardInGraveyardBattlefieldOrStack;
import mage.target.common.TargetCardInHand;
import mage.target.common.TargetCardInLibrary;
import mage.target.common.TargetCardInOpponentsGraveyard;
import mage.target.common.TargetCardInYourGraveyard;
import mage.target.common.TargetControlledPermanent;
import mage.target.common.TargetDefender;
import mage.target.common.TargetDiscard;
import mage.target.common.TargetOpponentOrPlaneswalker;
import mage.target.common.TargetPermanentOrPlayer;
import mage.target.common.TargetPermanentOrSuspendedCard;
import mage.target.common.TargetPlayerOrPlaneswalker;
import mage.target.common.TargetSacrifice;
import mage.target.common.TargetSpellOrPermanent;
import mage.util.RandomUtil;

import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
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
public class ComputerPlayer8 extends ComputerPlayer7 implements ComputerPlayer8Interface {

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

    public class OutcomeSerializer extends JsonSerializer<Outcome> {
        @Override
        public void serialize(Outcome outcome, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeStartObject(); // Start JSON object
            gen.writeStringField("name", outcome.name()); // Serialize the name of the enum
            gen.writeBooleanField("good", outcome.isGood()); // Serialize the 'good' field
            gen.writeBooleanField("canTargetAll", outcome.isCanTargetAll()); // Serialize the 'canTargetAll' field
            gen.writeEndObject(); // End JSON object
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
        module.addSerializer(Outcome.class, new OutcomeSerializer());
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

    private void sendMsgWithLLMChosenChoice(Game game, Player player, String[] allChoices,
            LLMResponse parsedResponse) {
        game.informPlayers(player.getLogName() + " POSSIBLE CHOICES:");
        for (int i = 0; i < allChoices.length; i++) {
            game.informPlayers(i + ": " + allChoices[i].toString());
        }

        int chosenActionIndex = parsedResponse.getChosenActionIndex();

        game.informPlayers("LLM CHOSEN ACTION:");
        game.informPlayers(chosenActionIndex + ": " + allChoices[chosenActionIndex].toString());
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

        // Unified helper call
        DecisionPayload dp = new DecisionPayload("/api/mtg_llm/choose_from_all_actions/", payload);
        DecisionResult dr = new LlmDecisionClient("http://localhost:9000").requestDecision(dp);
        int chosenActionIndex = dr.getChosenIndex() != null ? dr.getChosenIndex() : 0;

        // Log the random action chosen
        if (logger.isInfoEnabled()) {
            logger.info("Random action chosen: " +
                    allActions.get(chosenActionIndex).toString());
        }

        return chosenActionIndex;
    }

    public int callLLMToChooseFromChoices(Game game, Player currentPlayer, Outcome outcome, Choice choice,
            String[] allChoices) {
        // Prepare the context for the LLM
        JSONObject payload = new JSONObject();

        // GameView gameView = GameSessionPlayer.prepareGameView(game,
        // currentPlayer.getId(), currentPlayer.getId());

        GameState gameState = game.getState();
        Player opponentPlayer = findOpponent(game, currentPlayer);

        payload.put("gameCards", convertObjectToJson(game.getCards()));
        payload.put("gameState", convertObjectToJson(gameState));
        payload.put("outcome", convertObjectToJson(outcome));
        payload.put("choice", convertObjectToJson(choice));
        payload.put("allChoices", convertObjectToJson(allChoices));
        payload.put("currentPlayer", convertObjectToJson(currentPlayer));
        payload.put("opponentPlayer", convertObjectToJson(opponentPlayer));
        // payload.put("gameView", convertObjectToJson(gameView));
        payload.put("gameView", new JSONObject());

        DecisionPayload dp = new DecisionPayload("/api/mtg_llm/choose_from_choices/", payload);
        DecisionResult dr = new LlmDecisionClient("http://localhost:9000").requestDecision(dp);
        int chosenChoiceIndex = dr.getChosenIndex() != null ? dr.getChosenIndex() : 0;

        // Log the random choice chosen
        if (logger.isInfoEnabled()) {
            logger.info("Random choice chosen: " +
                    allChoices[chosenChoiceIndex].toString());
        }

        return chosenChoiceIndex;
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
                    chosenActionIndex = jsonResponse.getInt("chosen_idx");
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
        logger.debug("choose 8");
        if (choice.getMessage() != null && ("Choose creature type".equals(choice.getMessage())
                || "Choose a creature type".equals(choice.getMessage()))) {
            chooseCreatureType(outcome, choice, game);
            return true;
        }
        return super.choose(outcome, choice, game);
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

        DecisionPayload dp = new DecisionPayload("/api/mtg_llm/choose_attackers/", payload);
        DecisionResult dr = new LlmDecisionClient("http://localhost:9000").requestDecision(dp);
        // keep debug message with reasoning
        sendMsgWithLLMChosenReason(game, currentPlayer, "CHOOSING THESE ATTACKERS", dr.getReason());
        List<UUID> chosenAttackers = dr.getChosenUuids();
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

    @Override
    public boolean chooseTarget(Outcome outcome, Target target, Ability source, Game game) {
        if (logger.isDebugEnabled()) {
            logger.debug("chooseTarget: " + outcome.toString() + ':' + target.toString());
        }

        // target - real target, make all changes and add targets to it
        // target.getOriginalTarget() - copy spell effect replaces original target with
        // TargetWithAdditionalFilter
        // use originalTarget to get filters and target class info
        // source can be null (as example: legendary rule permanent selection)
        UUID sourceId = source != null ? source.getSourceId() : null;

        // sometimes a target selection can be made from a player that does not control
        // the ability
        UUID abilityControllerId = playerId;
        if (target.getAbilityController() != null) {
            abilityControllerId = target.getAbilityController();
        }

        boolean required = target.isRequired(sourceId, game);
        Set<UUID> possibleTargets = target.possibleTargets(abilityControllerId, source, game);
        if (possibleTargets.isEmpty() || target.getTargets().size() >= target.getNumberOfTargets()) {
            required = false;
        }

        UUID[] possibleTargetsUUIDArray = possibleTargets.toArray(new UUID[0]); // Convert to array
        // Create new empty array of mage objects to be filled
        MageObject[] possibleTargetsArray = new MageObject[possibleTargetsUUIDArray.length];
        int index = 0; // Initialize an index for the array
        for (UUID possibleTargetUUID : possibleTargetsUUIDArray) {
            // Fill the array with the MageObjects corresponding to the UUIDs
            possibleTargetsArray[index++] = game.getObject(possibleTargetUUID);
        }

        // TODO PV
        // Call LLM with outcome, target (required), source and possibleTargetsArray
        // the response should be an index of possibleTargetsArray (or -1 if target is
        // not required and None was chosen)

        List<Permanent> goodList = new ArrayList<>();
        List<Permanent> badList = new ArrayList<>();
        List<Permanent> allList = new ArrayList<>();

        // TODO: improve to process multiple opponents instead random
        UUID randomOpponentId;
        if (target.getTargetController() != null) {
            randomOpponentId = getRandomOpponent(target.getTargetController(), game);
        } else if (source != null && source.getControllerId() != null) {
            randomOpponentId = getRandomOpponent(source.getControllerId(), game);
        } else {
            randomOpponentId = getRandomOpponent(playerId, game);
        }

        if (target.getOriginalTarget() instanceof TargetPlayer) {
            return setTargetPlayer(outcome, target, source, abilityControllerId, randomOpponentId, game, required);
        }

        // Angel of Serenity trigger
        if (target.getOriginalTarget() instanceof TargetCardInGraveyardBattlefieldOrStack) {
            Cards cards = new CardsImpl(possibleTargets);
            List<Card> possibleCards = new ArrayList<>(cards.getCards(game));
            for (Card card : possibleCards) {
                // check permanents first; they have more intrinsic worth
                if (card instanceof Permanent) {
                    Permanent p = ((Permanent) card);
                    if (outcome.isGood()
                            && p.isControlledBy(abilityControllerId)) {
                        if (target.canTarget(abilityControllerId, p.getId(), source, game)) {
                            if (target.getTargets().size() >= target.getMaxNumberOfTargets()) {
                                break;
                            }
                            target.addTarget(p.getId(), source, game);
                        }
                    }
                    if (!outcome.isGood()
                            && !p.isControlledBy(abilityControllerId)) {
                        if (target.canTarget(abilityControllerId, p.getId(), source, game)) {
                            if (target.getTargets().size() >= target.getMaxNumberOfTargets()) {
                                break;
                            }
                            target.addTarget(p.getId(), source, game);
                        }
                    }
                }
                // check the graveyards last
                if (game.getState().getZone(card.getId()) == Zone.GRAVEYARD) {
                    if (outcome.isGood()
                            && card.isOwnedBy(abilityControllerId)) {
                        if (target.canTarget(abilityControllerId, card.getId(), source, game)) {
                            if (target.getTargets().size() >= target.getMaxNumberOfTargets()) {
                                break;
                            }
                            target.addTarget(card.getId(), source, game);
                        }
                    }
                    if (!outcome.isGood()
                            && !card.isOwnedBy(abilityControllerId)) {
                        if (target.canTarget(abilityControllerId, card.getId(), source, game)) {
                            if (target.getTargets().size() >= target.getMaxNumberOfTargets()) {
                                break;
                            }
                            target.addTarget(card.getId(), source, game);
                        }
                    }
                }
            }
            return target.isChosen(game);
        }

        if (target.getOriginalTarget() instanceof TargetDiscard
                || target.getOriginalTarget() instanceof TargetCardInHand) {
            if (outcome.isGood()) {
                // good
                Cards cards = new CardsImpl(possibleTargets);
                List<Card> cardsInHand = new ArrayList<>(cards.getCards(game));
                while (!target.isChosen(game)
                        && !cardsInHand.isEmpty()
                        && target.getMaxNumberOfTargets() > target.getTargets().size()) {
                    Card card = pickBestCard(cardsInHand, Collections.emptyList(), target, source, game);
                    if (card != null) {
                        if (target.canTarget(abilityControllerId, card.getId(), source, game)) {
                            target.addTarget(card.getId(), source, game);
                            cardsInHand.remove(card);
                            if (target.isChosen(game)) {
                                return true;
                            }
                        }
                    }
                }
            } else {
                // bad
                findPlayables(game);
                for (Card card : unplayable.values()) {
                    if (possibleTargets.contains(card.getId())
                            && target.canTarget(abilityControllerId, card.getId(), source, game)) {
                        target.addTarget(card.getId(), source, game);
                        if (target.isChosen(game)) {
                            return true;
                        }
                    }
                }
                if (!hand.isEmpty()) {
                    for (Card card : hand.getCards(game)) {
                        if (possibleTargets.contains(card.getId())
                                && target.canTarget(abilityControllerId, card.getId(), source, game)) {
                            target.addTarget(card.getId(), source, game);
                            if (target.isChosen(game)) {
                                return true;
                            }
                        }
                    }
                }
            }
            return false;
        }

        if (target.getOriginalTarget() instanceof TargetControlledPermanent
                || target.getOriginalTarget() instanceof TargetSacrifice) {
            TargetPermanent origTarget = (TargetPermanent) target.getOriginalTarget();
            List<Permanent> targets;
            targets = threats(abilityControllerId, source, origTarget.getFilter(), game, target.getTargets());
            if (!outcome.isGood()) {
                Collections.reverse(targets);
            }
            for (Permanent permanent : targets) {
                if (target.canTarget(abilityControllerId, permanent.getId(), source, game)) {
                    target.addTarget(permanent.getId(), source, game);
                    if (target.getNumberOfTargets() <= target.getTargets().size()
                            && (!outcome.isGood() || target.getMaxNumberOfTargets() <= target.getTargets().size())) {
                        return true;
                    }
                }
            }
            return target.isChosen(game);

        }

        // TODO: implemented findBestPlayerTargets
        // TODO: add findBest*Targets for all target types
        // TODO: Much of this code needs to be re-written to move code into
        // Target.possibleTargets
        // A) Having it here makes this function ridiculously long
        // B) Each time a new target type is added, people must remember to add it here
        if (target.getOriginalTarget() instanceof TargetPermanent) {

            FilterPermanent filter = null;
            if (target.getOriginalTarget().getFilter() instanceof FilterPermanent) {
                filter = (FilterPermanent) target.getOriginalTarget().getFilter();
            }
            if (filter == null) {
                throw new IllegalStateException("Unsupported permanent filter in computer's chooseTarget method: "
                        + target.getOriginalTarget().getClass().getCanonicalName());
            }

            findBestPermanentTargets(outcome, abilityControllerId, sourceId, source, filter,
                    game, target, goodList, badList, allList);

            // use good list all the time and add maximum targets
            for (Permanent permanent : goodList) {
                if (target.canTarget(abilityControllerId, permanent.getId(), source, game)) {
                    if (target.getTargets().size() >= target.getMaxNumberOfTargets()) {
                        break;
                    }
                    target.addTarget(permanent.getId(), source, game);
                }
            }

            // use bad list only on required target and add minimum targets
            if (required) {
                for (Permanent permanent : badList) {
                    if (target.getTargets().size() >= target.getMinNumberOfTargets()) {
                        break;
                    }
                    target.addTarget(permanent.getId(), source, game);
                }
            }
            return target.isChosen(game);
        }

        if (target.getOriginalTarget() instanceof TargetAnyTarget) {
            List<Permanent> targets;
            TargetAnyTarget origTarget = ((TargetAnyTarget) target.getOriginalTarget());
            if (outcome.isGood()) {
                targets = threats(abilityControllerId, source,
                        ((FilterAnyTarget) origTarget.getFilter()).getPermanentFilter(), game, target.getTargets());
            } else {
                targets = threats(randomOpponentId, source,
                        ((FilterAnyTarget) origTarget.getFilter()).getPermanentFilter(), game, target.getTargets());
            }

            if (targets.isEmpty()) {
                if (outcome.isGood()) {
                    if (target.canTarget(abilityControllerId, abilityControllerId, source, game)) {
                        return tryAddTarget(target, abilityControllerId, source, game);
                    }
                } else if (target.canTarget(abilityControllerId, randomOpponentId, source, game)) {
                    return tryAddTarget(target, randomOpponentId, source, game);
                }
            }

            if (targets.isEmpty() && required) {
                targets = game.getBattlefield().getActivePermanents(
                        ((FilterAnyTarget) origTarget.getFilter()).getPermanentFilter(), playerId, game);
            }
            for (Permanent permanent : targets) {
                List<UUID> alreadyTargeted = target.getTargets();
                if (target.canTarget(abilityControllerId, permanent.getId(), source, game)) {
                    if (alreadyTargeted != null && !alreadyTargeted.contains(permanent.getId())) {
                        tryAddTarget(target, permanent.getId(), source, game);
                    }
                }
            }

            if (outcome.isGood()) {
                if (target.canTarget(abilityControllerId, abilityControllerId, source, game)) {
                    return tryAddTarget(target, abilityControllerId, source, game);
                }
            } else if (target.canTarget(abilityControllerId, randomOpponentId, source, game)) {
                return tryAddTarget(target, randomOpponentId, source, game);
            }

            // if (!target.isRequired())
            return false;
        }

        if (target.getOriginalTarget() instanceof TargetPermanentOrPlayer) {
            List<Permanent> targets;
            TargetPermanentOrPlayer origTarget = ((TargetPermanentOrPlayer) target.getOriginalTarget());
            if (outcome.isGood()) {
                targets = threats(abilityControllerId, source,
                        ((FilterPermanentOrPlayer) origTarget.getFilter()).getPermanentFilter(), game,
                        target.getTargets());
            } else {
                targets = threats(randomOpponentId, source,
                        ((FilterPermanentOrPlayer) origTarget.getFilter()).getPermanentFilter(), game,
                        target.getTargets());
            }

            if (targets.isEmpty()) {
                if (outcome.isGood()) {
                    if (target.canTarget(abilityControllerId, abilityControllerId, source, game)) {
                        return tryAddTarget(target, abilityControllerId, source, game);
                    }
                } else if (target.canTarget(abilityControllerId, randomOpponentId, source, game)) {
                    return tryAddTarget(target, randomOpponentId, source, game);
                }
            }

            if (targets.isEmpty() && target.isRequired(source)) {
                targets = game.getBattlefield().getActivePermanents(
                        ((FilterPermanentOrPlayer) origTarget.getFilter()).getPermanentFilter(), playerId, game);
            }
            for (Permanent permanent : targets) {
                List<UUID> alreadyTargeted = target.getTargets();
                if (target.canTarget(abilityControllerId, permanent.getId(), source, game)) {
                    if (alreadyTargeted != null && !alreadyTargeted.contains(permanent.getId())) {
                        return tryAddTarget(target, permanent.getId(), source, game);
                    }
                }
            }
        }

        if (target.getOriginalTarget() instanceof TargetPlayerOrPlaneswalker
                || target.getOriginalTarget() instanceof TargetOpponentOrPlaneswalker) {
            List<Permanent> targets;
            TargetPermanentOrPlayer origTarget = ((TargetPermanentOrPlayer) target.getOriginalTarget());

            // TODO: in multiplayer game there many opponents - if random opponents don't
            // have targets then AI must use next opponent, but it skips
            // (e.g. you randomOpponentId must be replaced by List<UUID> randomOpponents)
            // normal cycle (good for you, bad for opponents)
            // possible good/bad permanents
            if (outcome.isGood()) {
                targets = threats(abilityControllerId, source,
                        ((FilterPermanentOrPlayer) target.getFilter()).getPermanentFilter(), game, target.getTargets());
            } else {
                targets = threats(randomOpponentId, source,
                        ((FilterPermanentOrPlayer) target.getFilter()).getPermanentFilter(), game, target.getTargets());
            }

            // possible good/bad players
            if (targets.isEmpty()) {
                if (outcome.isGood()) {
                    if (target.canTarget(abilityControllerId, abilityControllerId, source, game)) {
                        return tryAddTarget(target, abilityControllerId, source, game);
                    }
                } else if (target.canTarget(abilityControllerId, randomOpponentId, source, game)) {
                    return tryAddTarget(target, randomOpponentId, source, game);
                }
            }

            // can't find targets (e.g. effect is bad, but you need take targets from
            // yourself)
            if (targets.isEmpty() && required) {
                targets = game.getBattlefield().getActivePermanents(origTarget.getFilterPermanent(), playerId, game);
            }

            // try target permanent
            for (Permanent permanent : targets) {
                List<UUID> alreadyTargeted = target.getTargets();
                if (target.canTarget(abilityControllerId, permanent.getId(), source, game)) {
                    if (alreadyTargeted != null && !alreadyTargeted.contains(permanent.getId())) {
                        return tryAddTarget(target, permanent.getId(), source, game);
                    }
                }
            }

            // try target player as normal
            if (outcome.isGood()) {
                if (target.canTarget(abilityControllerId, abilityControllerId, source, game)) {
                    return tryAddTarget(target, abilityControllerId, source, game);
                }
            } else if (target.canTarget(abilityControllerId, randomOpponentId, source, game)) {
                return tryAddTarget(target, randomOpponentId, source, game);
            }

            // try target player as bad (bad on itself, good on opponent)
            for (UUID opponentId : game.getOpponents(abilityControllerId)) {
                if (target.canTarget(abilityControllerId, opponentId, source, game)) {
                    return tryAddTarget(target, opponentId, source, game);
                }
            }
            if (target.canTarget(abilityControllerId, abilityControllerId, source, game)) {
                return tryAddTarget(target, abilityControllerId, source, game);
            }

            return false;
        }

        if (target.getOriginalTarget() instanceof TargetCardInGraveyard) {
            List<Card> cards = new ArrayList<>();
            for (Player player : game.getPlayers().values()) {
                cards.addAll(player.getGraveyard().getCards(game));
            }
            Card card = pickTarget(abilityControllerId, cards, outcome, target, source, game);
            if (card != null) {
                return tryAddTarget(target, card.getId(), source, game);
            }
            // if (!target.isRequired())
            return false;
        }

        if (target.getOriginalTarget() instanceof TargetCardInLibrary) {
            List<Card> cards = new ArrayList<>(game.getPlayer(abilityControllerId).getLibrary().getCards(game));
            Card card = pickTarget(abilityControllerId, cards, outcome, target, source, game);
            if (card != null) {
                return tryAddTarget(target, card.getId(), source, game);
            }
            return false;
        }

        if (target.getOriginalTarget() instanceof TargetCardInYourGraveyard) {
            List<Card> cards = new ArrayList<>(
                    game.getPlayer(abilityControllerId).getGraveyard().getCards((FilterCard) target.getFilter(), game));
            while (!target.isChosen(game) && !cards.isEmpty()) {
                Card card = pickTarget(abilityControllerId, cards, outcome, target, source, game);
                if (card != null) {
                    target.addTarget(card.getId(), source, game);
                    cards.remove(card); // pickTarget don't remove cards (only on second+ tries)
                }
            }
            return target.isChosen(game);
        }

        if (target.getOriginalTarget() instanceof TargetSpell
                || target.getOriginalTarget() instanceof TargetStackObject) {
            if (!game.getStack().isEmpty()) {
                for (StackObject o : game.getStack()) {
                    if (o instanceof Spell
                            && !source.getId().equals(o.getStackAbility().getId())
                            && target.canTarget(abilityControllerId, o.getStackAbility().getId(), source, game)) {
                        return tryAddTarget(target, o.getId(), source, game);
                    }
                }
            }
            return false;
        }

        if (target.getOriginalTarget() instanceof TargetSpellOrPermanent) {
            // TODO: Also check if a spell should be selected
            TargetSpellOrPermanent origTarget = (TargetSpellOrPermanent) target.getOriginalTarget();
            List<Permanent> targets;
            boolean outcomeTargets = true;
            if (outcome.isGood()) {
                targets = threats(abilityControllerId, source, origTarget.getPermanentFilter(), game,
                        target.getTargets());
            } else {
                targets = threats(randomOpponentId, source, origTarget.getPermanentFilter(), game, target.getTargets());
            }
            if (targets.isEmpty() && required) {
                targets = threats(null, source, origTarget.getPermanentFilter(), game, target.getTargets());
                Collections.reverse(targets);
                outcomeTargets = false;
            }
            for (Permanent permanent : targets) {
                if (target.canTarget(abilityControllerId, permanent.getId(), source, game)) {
                    target.addTarget(permanent.getId(), source, game);
                    if (!outcomeTargets || target.getMaxNumberOfTargets() <= target.getTargets().size()) {
                        return true;
                    }
                }
            }
            if (!game.getStack().isEmpty()) {
                for (StackObject stackObject : game.getStack()) {
                    if (stackObject instanceof Spell && source != null
                            && !source.getId().equals(stackObject.getStackAbility().getId())) {
                        if (target.getFilter().match(stackObject, game)) {
                            return tryAddTarget(target, stackObject.getId(), source, game);
                        }
                    }
                }
            }
            return false;
        }

        if (target.getOriginalTarget() instanceof TargetCardInOpponentsGraveyard) {
            List<Card> cards = new ArrayList<>();
            for (UUID uuid : game.getOpponents(abilityControllerId)) {
                Player player = game.getPlayer(uuid);
                if (player != null) {
                    cards.addAll(player.getGraveyard().getCards(game));
                }
            }
            Card card = pickTarget(abilityControllerId, cards, outcome, target, source, game);
            if (card != null) {
                return tryAddTarget(target, card.getId(), source, game);
            }
            // if (!target.isRequired())
            return false;
        }

        if (target.getOriginalTarget() instanceof TargetDefender) {
            UUID randomDefender = RandomUtil.randomFromCollection(possibleTargets);
            target.addTarget(randomDefender, source, game);
            return target.isChosen(game);
        }

        if (target.getOriginalTarget() instanceof TargetCardInASingleGraveyard) {
            List<Card> cards = new ArrayList<>();
            for (Player player : game.getPlayers().values()) {
                cards.addAll(player.getGraveyard().getCards(game));
            }
            while (!target.isChosen(game) && !cards.isEmpty()) {
                Card pick = pickTarget(abilityControllerId, cards, outcome, target, source, game);
                if (pick != null) {
                    target.addTarget(pick.getId(), source, game);
                    cards.remove(pick); // pickTarget don't remove cards (only on second+ tries)
                }
            }
            return target.isChosen(game);
        }

        if (target.getOriginalTarget() instanceof TargetCardInExile) {

            FilterCard filter = null;
            if (target.getOriginalTarget().getFilter() instanceof FilterCard) {
                filter = (FilterCard) target.getOriginalTarget().getFilter();
            }
            if (filter == null) {
                throw new IllegalStateException("Unsupported exile target filter in computer's chooseTarget method: "
                        + target.getOriginalTarget().getClass().getCanonicalName());
            }

            List<Card> cards = new ArrayList<>();
            for (UUID uuid : target.possibleTargets(source.getControllerId(), source, game)) {
                Card card = game.getCard(uuid);
                if (card != null && game.getState().getZone(card.getId()) == Zone.EXILED) {
                    cards.add(card);
                }
            }
            while (!target.isChosen(game) && !cards.isEmpty()) {
                Card pick = pickTarget(abilityControllerId, cards, outcome, target, source, game);
                if (pick != null) {
                    target.addTarget(pick.getId(), source, game);
                    cards.remove(pick); // pickTarget don't remove cards (only on second+ tries)
                }
            }
            return target.isChosen(game);
        }

        if (target.getOriginalTarget() instanceof TargetActivatedAbility) {
            List<StackObject> stackObjects = new ArrayList<>();
            for (UUID uuid : target.possibleTargets(source.getControllerId(), source, game)) {
                StackObject stackObject = game.getStack().getStackObject(uuid);
                if (stackObject != null) {
                    stackObjects.add(stackObject);
                }
            }
            while (!target.isChosen(game) && !stackObjects.isEmpty()) {
                StackObject pick = stackObjects.get(0);
                if (pick != null) {
                    target.addTarget(pick.getId(), source, game);
                    stackObjects.remove(0);
                }
            }
            return target.isChosen(game);
        }

        if (target.getOriginalTarget() instanceof TargetActivatedOrTriggeredAbility) {
            Iterator<UUID> iterator = target.possibleTargets(source.getControllerId(), source, game).iterator();
            while (!target.isChosen(game) && iterator.hasNext()) {
                target.addTarget(iterator.next(), source, game);
            }
            return target.isChosen(game);
        }

        if (target.getOriginalTarget() instanceof TargetCardInGraveyardBattlefieldOrStack) {
            List<Card> cards = new ArrayList<>();
            for (Player player : game.getPlayers().values()) {
                cards.addAll(player.getGraveyard().getCards(game));
                cards.addAll(game.getBattlefield().getAllActivePermanents(new FilterPermanent(), player.getId(), game));
            }
            Card card = pickTarget(abilityControllerId, cards, outcome, target, source, game);
            if (card != null) {
                return tryAddTarget(target, card.getId(), source, game);
            }
        }

        if (target.getOriginalTarget() instanceof TargetPermanentOrSuspendedCard) {
            Cards cards = new CardsImpl(possibleTargets);
            List<Card> possibleCards = new ArrayList<>(cards.getCards(game));
            while (!target.isChosen(game) && !possibleCards.isEmpty()) {
                Card pick = pickTarget(abilityControllerId, possibleCards, outcome, target, source, game);
                if (pick != null) {
                    target.addTarget(pick.getId(), source, game);
                    possibleCards.remove(pick);
                }
            }
            return target.isChosen(game);
        }

        throw new IllegalStateException(
                "Target wasn't handled in computer's chooseTarget method: " + target.getClass().getCanonicalName());
    } // end of chooseTarget method

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
