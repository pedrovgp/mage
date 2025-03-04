package mage.player.ai;

import mage.abilities.Ability;
import mage.abilities.costs.mana.ColoredManaCost;
import mage.abilities.costs.mana.GenericManaCost;
import mage.abilities.costs.mana.ManaCost;
import mage.cards.Card;
import mage.cards.decks.Deck;
import mage.cards.o.Ornithopter;
import mage.constants.RangeOfInfluence;
import mage.game.Game;
import mage.game.match.MatchPlayer;

import org.apache.log4j.Logger;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.io.OutputStream;
import java.net.URL;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;

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
        return chosenActionIndex;
    }

    // MixIn class to ignore the problematic field
    public abstract class GenericManaCostMixIn {
        @JsonIgnore
        private GenericManaCost unpaid;
    }

    public abstract class OrnithopterMixIn {
        @JsonIgnore
        private Ability secondFaceSpellAbility;
    }

    public abstract class SimulatedPlayer2MixIn {
        @JsonIgnore
        private MatchPlayer matchPlayer;
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
    }

    public abstract class ManaCostMixIn {
        @JsonIgnore
        private List<ColoredManaCost> manaCosts;
    }

    public abstract class ColoredManaCostMixIn {
        @JsonIgnore
        private boolean unpaid;
    }

    private String convertObjectToJson(Object obj) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        objectMapper.addMixIn(GenericManaCost.class, GenericManaCostMixIn.class);
        objectMapper.addMixIn(Ornithopter.class, OrnithopterMixIn.class);
        objectMapper.addMixIn(SimulatedPlayer2.class, SimulatedPlayer2MixIn.class);
        objectMapper.addMixIn(MatchPlayer.class, MatchPlayerMixIn.class);
        objectMapper.addMixIn(Deck.class, DeckMixIn.class);
        objectMapper.addMixIn(Card.class, CardMixIn.class);
        objectMapper.addMixIn(ManaCost.class, ManaCostMixIn.class);
        objectMapper.addMixIn(ColoredManaCost.class, ColoredManaCostMixIn.class);
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            logger.error("Error converting object to JSON: " + obj.getClass().getName(), e);
            return String.format("{\"error\": \"Failed to convert %s to JSON\", \"message\": \"%s\"}",
                    obj.getClass().getName(), e.getMessage().replace("\"", "\\\""));
        }
    }

    private int callLLMToChooseAction(Game game, List<Ability> allActions, SimulatedPlayer2 currentPlayer) {
        // Prepare the context for the LLM
        Map<String, Object> context = new HashMap<>();
        context.put("gameState", convertObjectToJson(game.getState()));
        context.put("allActions", convertObjectToJson(allActions));
        context.put("playerContext", convertObjectToJson(currentPlayer));

        // Convert context to a format suitable for the LLM (e.g., JSON)
        String contextJson = convertContextToJson(context);

        // Send the context to the LLM and get the response
        HttpURLConnection llmResponse = sendContextToLLM(contextJson);

        // Parse the response to get the chosen action index
        int chosenActionIndex = parseLLMResponse(llmResponse);

        // TODO PV remove later this simple test later - BEGIN
        chosenActionIndex = Math.max(0, allActions.size() - 2);
        // Log the random action chosen
        if (logger.isInfoEnabled()) {
            logger.info("Random action chosen: " +
                    allActions.get(chosenActionIndex).toString());
        }

        return chosenActionIndex;
    }

    private String convertContextToJson(Map<String, Object> context) {
        // Implement the conversion to JSON (e.g., using a library like Gson or Jackson)
        // For example:
        // return new Gson().toJson(context);
        return context.toString(); // Placeholder
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

    private int parseLLMResponse(HttpURLConnection llmResponse) {
        int chosenActionIndex = 0; // Default value
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
                    // Assuming the responseString contains the chosen action index directly
                    chosenActionIndex = Integer.parseInt(responseString);
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
        return chosenActionIndex;
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
