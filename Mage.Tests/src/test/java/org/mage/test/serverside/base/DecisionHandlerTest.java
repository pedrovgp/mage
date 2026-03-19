package org.mage.test.serverside.base;

import mage.abilities.Ability;
import mage.abilities.common.PassAbility;
import mage.choices.Choice;
import mage.choices.ChoiceImpl;
import mage.constants.Outcome;
import mage.game.Game;
import mage.game.GameState;
import mage.players.Player;
import mage.players.Players;
import mage.player.ai.DecisionHandler;
import mage.player.ai.DecisionResult;
import mage.player.ai.LlmDecisionClient;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DecisionHandler class
 */
public class DecisionHandlerTest {

    private DecisionHandler decisionHandler;

    @Mock
    private LlmDecisionClient mockClient;

    @Mock
    private Game mockGame;

    @Mock
    private Player mockPlayer;

    @Mock
    private GameState mockGameState;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        decisionHandler = new DecisionHandler(mockClient);

        // Setup basic mock behaviors
        when(mockGame.getState()).thenReturn(mockGameState);
        UUID playerId = java.util.UUID.randomUUID();
        when(mockPlayer.getId()).thenReturn(playerId);
        when(mockPlayer.getName()).thenReturn("TestPlayer");

        // Setup players map to avoid NullPointerException
        Map<UUID, Player> players = new HashMap<>();
        players.put(playerId, mockPlayer);
        Players playersObj = mock(Players.class);
        when(mockGameState.getPlayers()).thenReturn(playersObj);
        when(playersObj.keySet()).thenReturn(players.keySet());
        when(mockGame.getPlayer(any(UUID.class))).thenReturn(mockPlayer);

        // Setup game state properties
        when(mockGameState.getTurnNum()).thenReturn(1);
        when(mockGame.getTurnNum()).thenReturn(1);
        when(mockGame.getPhase()).thenReturn(null); // Will be handled by fallback in DecisionHandler
        when(mockGame.getStep()).thenReturn(null); // Will be handled by fallback in DecisionHandler

        // Setup game collections
        when(mockGame.getCards()).thenReturn(new java.util.ArrayList<>());
        when(mockGame.getBattlefield()).thenReturn(null); // Will be handled by fallback in DecisionHandler
    }

    @Test
    public void testConstructorWithBaseUrl() {
        // Test constructor with base URL
        DecisionHandler handler = new DecisionHandler("http://localhost:9000");
        assertNotNull("DecisionHandler should be created with base URL", handler);
    }

    @Test
    public void testInformChosenAction() {
        // Setup mock game - informPlayers is a void method
        doNothing().when(mockGame).informPlayers(anyString());

        // Create test abilities
        PassAbility passAbility = new PassAbility();
        List<Ability> actions = Arrays.asList(passAbility);
        DecisionResult result = new DecisionResult(0, null, "test_reason");

        // Test informChosenAction - should not throw exception
        try {
            decisionHandler.informChosenAction(mockGame, mockPlayer, actions, result);
            // If we get here, the method executed without error
            assertTrue("informChosenAction should execute without error", true);
        } catch (Exception e) {
            fail("informChosenAction should not throw exception: " + e.getMessage());
        }

        // Verify game.informPlayers was called
        verify(mockGame, atLeast(1)).informPlayers(anyString());
    }

    @Test
    public void testInformChosenChoice() {
        // Setup mock game - informPlayers is a void method
        doNothing().when(mockGame).informPlayers(anyString());

        // Create test data
        String[] choices = { "Choice 1", "Choice 2" };
        DecisionResult result = new DecisionResult(1, null, "choice_reason");

        // Test informChosenChoice - should not throw exception
        try {
            decisionHandler.informChosenChoice(mockGame, mockPlayer, choices, result);
            // If we get here, the method executed without error
            assertTrue("informChosenChoice should execute without error", true);
        } catch (Exception e) {
            fail("informChosenChoice should not throw exception: " + e.getMessage());
        }

        // Verify game.informPlayers was called
        verify(mockGame, atLeast(1)).informPlayers(anyString());
    }

    @Test
    public void testHandleActionSuccessPath() {
        // Setup mock client to return a successful decision
        DecisionResult expectedResult = new DecisionResult(0, null, "TestStrategy");
        when(mockClient.requestDecision(any())).thenReturn(expectedResult);

        // Create a real game and player using TestGameFactory
        Game game = TestGameFactory.createMinimalGame();
        Player player = TestGameFactory.getPlayerA(game);

        // Create test abilities
        PassAbility passAbility = new PassAbility();
        List<Ability> actions = Arrays.asList(passAbility);

        // Test handleAction with real objects - should succeed
        DecisionResult result = decisionHandler.handleAction(game, player, actions, "random");

        // Verify the result matches what the mock client returned
        assertNotNull("Result should not be null", result);
        assertEquals("Should return the mocked result", expectedResult.getChosenIndex(), result.getChosenIndex());
        assertEquals("Should return the mocked reason", expectedResult.getReason(), result.getReason());

        // Verify the client was called
        verify(mockClient, times(1)).requestDecision(any());

        // Verify the payload structure by capturing the DecisionPayload
        ArgumentCaptor<mage.player.ai.DecisionPayload> payloadCaptor = ArgumentCaptor
                .forClass(mage.player.ai.DecisionPayload.class);
        verify(mockClient).requestDecision(payloadCaptor.capture());

        mage.player.ai.DecisionPayload capturedPayload = payloadCaptor.getValue();
        assertNotNull("Payload should not be null", capturedPayload);

        // Verify payload contains expected JSON structure
        org.json.JSONObject payloadJson = capturedPayload.getBody();
        assertTrue("Payload should contain gameState", payloadJson.has("gameState"));
        assertTrue("Payload should contain currentPlayer", payloadJson.has("currentPlayer"));
        assertTrue("Payload should contain allActions", payloadJson.has("allActions"));
        assertTrue("Payload should contain opponentPlayer", payloadJson.has("opponentPlayer"));
        assertTrue("Payload should contain strategy", payloadJson.has("strategy"));
    }

    @Test
    public void testHandleChoiceSuccessPath() {
        // Setup mock client to return a successful decision
        DecisionResult expectedResult = new DecisionResult(1, null, "ChoiceStrategy");
        when(mockClient.requestDecision(any())).thenReturn(expectedResult);

        // Create a real game and player using TestGameFactory
        Game game = TestGameFactory.createMinimalGame();
        Player player = TestGameFactory.getPlayerA(game);

        // Create test choice
        Choice choice = new ChoiceImpl(true);
        choice.setMessage("Test choice");
        String[] choices = { "Option 1", "Option 2", "Option 3" };

        // Test handleChoice with real objects - should succeed
        DecisionResult result = decisionHandler.handleChoice(game, player, Outcome.Benefit, choice, choices, "random");

        // Verify the result matches what the mock client returned
        assertNotNull("Result should not be null", result);
        assertEquals("Should return the mocked result", expectedResult.getChosenIndex(), result.getChosenIndex());
        assertEquals("Should return the mocked reason", expectedResult.getReason(), result.getReason());

        // Verify the client was called
        verify(mockClient, times(1)).requestDecision(any());

        // Verify the payload structure by capturing the DecisionPayload
        ArgumentCaptor<mage.player.ai.DecisionPayload> payloadCaptor = ArgumentCaptor
                .forClass(mage.player.ai.DecisionPayload.class);
        verify(mockClient).requestDecision(payloadCaptor.capture());

        mage.player.ai.DecisionPayload capturedPayload = payloadCaptor.getValue();
        assertNotNull("Payload should not be null", capturedPayload);

        // Verify payload contains expected JSON structure
        org.json.JSONObject payloadJson = capturedPayload.getBody();
        assertTrue("Payload should contain gameState", payloadJson.has("gameState"));
        assertTrue("Payload should contain currentPlayer", payloadJson.has("currentPlayer"));
        assertTrue("Payload should contain allChoices", payloadJson.has("allChoices"));
        assertTrue("Payload should contain choice", payloadJson.has("choice"));
        assertTrue("Payload should contain outcome", payloadJson.has("outcome"));
        assertTrue("Payload should contain opponentPlayer", payloadJson.has("opponentPlayer"));
        assertTrue("Payload should contain strategy", payloadJson.has("strategy"));
    }

    @Test
    public void testSerializationProducesValidJson() {
        // Create a real game and player using TestGameFactory
        Game game = TestGameFactory.createMinimalGame();
        Player player = TestGameFactory.getPlayerA(game);

        // Create test abilities
        PassAbility passAbility = new PassAbility();
        List<Ability> actions = Arrays.asList(passAbility);

        // Test that serialization produces valid JSON by calling handleAction
        // This will internally use the ObjectMapper to serialize the game/player
        // objects
        DecisionResult expectedResult = new DecisionResult(0, null, "SerializationTest");
        when(mockClient.requestDecision(any())).thenReturn(expectedResult);

        DecisionResult result = decisionHandler.handleAction(game, player, actions, "random");

        // If we get here without exceptions, serialization worked
        assertNotNull("Result should not be null", result);
        assertEquals("Should return expected result", expectedResult.getChosenIndex(), result.getChosenIndex());

        // Verify the client was called (which means serialization succeeded)
        verify(mockClient, times(1)).requestDecision(any());
    }

    @Test
    public void testHandleChooseTargetAmount() {
        // Setup mock client to return a successful decision
        List<java.util.UUID> targetUuids = Arrays.asList(java.util.UUID.randomUUID());
        DecisionResult expectedResult = new DecisionResult(0, targetUuids, "TargetAmountStrategy");
        when(mockClient.requestDecision(any())).thenReturn(expectedResult);

        // Create a real game and player using TestGameFactory
        Game game = TestGameFactory.createMinimalGame();
        Player player = TestGameFactory.getPlayerA(game);

        // Create test target IDs
        List<String> targetIds = Arrays.asList("target1", "target2");

        // Test handleChooseTargetAmount with real objects - should succeed
        DecisionResult result = decisionHandler.handleChooseTargetAmount(game, player, targetIds, 1, 3, "random");

        // Verify the result matches what the mock client returned
        assertNotNull("Result should not be null", result);
        assertEquals("Should return the mocked result", expectedResult.getChosenIndex(), result.getChosenIndex());
        assertEquals("Should return the mocked UUIDs", expectedResult.getChosenUuids(), result.getChosenUuids());
        assertEquals("Should return the mocked reason", expectedResult.getReason(), result.getReason());

        // Verify the client was called
        verify(mockClient, times(1)).requestDecision(any());

        // Verify the payload structure by capturing the DecisionPayload
        ArgumentCaptor<mage.player.ai.DecisionPayload> payloadCaptor = ArgumentCaptor
                .forClass(mage.player.ai.DecisionPayload.class);
        verify(mockClient).requestDecision(payloadCaptor.capture());

        mage.player.ai.DecisionPayload capturedPayload = payloadCaptor.getValue();
        assertNotNull("Payload should not be null", capturedPayload);

        // Verify payload contains expected JSON structure
        org.json.JSONObject payloadJson = capturedPayload.getBody();
        assertTrue("Payload should contain targetIds", payloadJson.has("targetIds"));
        assertTrue("Payload should contain minAmount", payloadJson.has("minAmount"));
        assertTrue("Payload should contain maxAmount", payloadJson.has("maxAmount"));
        assertTrue("Payload should contain strategy", payloadJson.has("strategy"));
        assertTrue("Payload should contain gameId", payloadJson.has("gameId"));
        assertTrue("Payload should contain matchId", payloadJson.has("matchId"));
    }

    @Test
    public void testHandleChooseTargetAmountWithException() {
        // Setup mock client to throw exception
        when(mockClient.requestDecision(any())).thenThrow(new RuntimeException("Test exception"));

        // Create a real game and player using TestGameFactory
        Game game = TestGameFactory.createMinimalGame();
        Player player = TestGameFactory.getPlayerA(game);

        // Create test target IDs - use empty list to test edge case
        List<String> targetIds = Arrays.asList();

        // Test handleChooseTargetAmount with exception
        DecisionResult result = decisionHandler.handleChooseTargetAmount(game, player, targetIds, 1, 2, "random");

        assertNotNull("Result should not be null even with exception", result);
        assertNull("Should not have chosenIndex for target amount", result.getChosenIndex());
        assertNotNull("Should have fallback UUIDs", result.getChosenUuids());
        assertTrue("Should have empty UUIDs for empty targetIds", result.getChosenUuids().isEmpty());
        assertTrue("Reason should indicate fallback", result.getReason().contains("fallback"));
    }

    @Test
    public void testHandleLogTrajectory() {
        // Setup mock client to return a successful decision
        DecisionResult expectedResult = new DecisionResult(null, null, "TrajectoryLogged");
        when(mockClient.requestDecision(any())).thenReturn(expectedResult);

        // Create a real game and player using TestGameFactory
        Game game = TestGameFactory.createMinimalGame();
        Player player = TestGameFactory.getPlayerA(game);

        // Create test trajectory data
        Object availableActions = Arrays.asList("action1", "action2");
        Map<String, Object> chosenAction = new HashMap<>();
        chosenAction.put("actionIndex", 0);
        chosenAction.put("actionName", "action1");
        Map<String, Object> additionalContext = new HashMap<>();
        additionalContext.put("turnNumber", 1);

        // Test handleLogTrajectory with real objects - should succeed
        DecisionResult result = decisionHandler.handleLogTrajectory(game, player, "action",
                availableActions, chosenAction, additionalContext, "random");

        // Verify the result matches what the mock client returned
        assertNotNull("Result should not be null", result);
        assertEquals("Should return the mocked reason", expectedResult.getReason(), result.getReason());

        // Verify the client was called
        verify(mockClient, times(1)).requestDecision(any());

        // Verify the payload structure by capturing the DecisionPayload
        ArgumentCaptor<mage.player.ai.DecisionPayload> payloadCaptor = ArgumentCaptor
                .forClass(mage.player.ai.DecisionPayload.class);
        verify(mockClient).requestDecision(payloadCaptor.capture());

        mage.player.ai.DecisionPayload capturedPayload = payloadCaptor.getValue();
        assertNotNull("Payload should not be null", capturedPayload);
        assertEquals("Should use correct endpoint", "/v1/log_trajectory",
                capturedPayload.getEndpointPath());

        // Verify payload contains expected JSON structure
        org.json.JSONObject payloadJson = capturedPayload.getBody();
        assertTrue("Payload should contain decisionType", payloadJson.has("decisionType"));
        assertTrue("Payload should contain gameIsOver", payloadJson.has("gameIsOver"));
        assertTrue("Payload should contain availableActions", payloadJson.has("availableActions"));
        assertTrue("Payload should contain chosenAction", payloadJson.has("chosenAction"));
        assertTrue("Payload should contain additionalContext", payloadJson.has("additionalContext"));
        assertTrue("Payload should contain game", payloadJson.has("game"));
        assertTrue("Payload should contain gameCards", payloadJson.has("gameCards"));
        assertTrue("Payload should contain gameState", payloadJson.has("gameState"));
        assertTrue("Payload should contain currentPlayer", payloadJson.has("currentPlayer"));
        assertTrue("Payload should contain opponentPlayer", payloadJson.has("opponentPlayer"));
    }

    // =========================================================================
    // DN1a: GameView / Information State Tests
    // =========================================================================

    @Test
    public void testGameViewConstructionDebug() {
        // Temporary debug test to expose GameView construction exception
        Game game = TestGameFactory.createMinimalGame();
        Player player = TestGameFactory.getPlayerA(game);
        try {
            mage.view.GameView gv = new mage.view.GameView(game.getState(), game, player.getId(), null);
            System.out.println("[DEBUG] GameView constructed OK. Players: " + gv.getPlayers().size());
            System.out.println("[DEBUG] Phase: " + gv.getPhase() + " Step: " + gv.getStep() + " Turn: " + gv.getTurn());
            System.out.println("[DEBUG] MyPlayer: " + gv.getMyPlayer());
        } catch (Exception e) {
            System.out.println("[DEBUG] GameView construction FAILED: " + e.getClass().getName() + ": " + e.getMessage());
            e.printStackTrace(System.out);
        }
        // This test always passes — it's just for debugging
        assertTrue("Debug test", true);
    }

    @Test
    public void testGameViewPopulated() {
        // After DN1a, buildDecisionBasePayload should include a populated gameView
        DecisionResult expectedResult = new DecisionResult(0, null, "GameViewTest");
        when(mockClient.requestDecision(any())).thenReturn(expectedResult);

        Game game = TestGameFactory.createMinimalGame();
        Player player = TestGameFactory.getPlayerA(game);

        mage.abilities.common.PassAbility passAbility = new mage.abilities.common.PassAbility();
        java.util.List<mage.abilities.Ability> actions = Arrays.asList(passAbility);

        decisionHandler.handleAction(game, player, actions, "random");

        ArgumentCaptor<mage.player.ai.DecisionPayload> payloadCaptor = ArgumentCaptor
                .forClass(mage.player.ai.DecisionPayload.class);
        verify(mockClient).requestDecision(payloadCaptor.capture());

        org.json.JSONObject payloadJson = payloadCaptor.getValue().getBody();
        assertTrue("Payload should contain gameView", payloadJson.has("gameView"));
        org.json.JSONObject gameView = payloadJson.optJSONObject("gameView");
        assertNotNull("gameView should be a JSONObject", gameView);
        // Should have phase, step, turn at minimum (even for minimal game)
        assertTrue("gameView should have phase key", gameView.has("phase"));
        assertTrue("gameView should have step key", gameView.has("step"));
        assertTrue("gameView should have turn key", gameView.has("turn"));
        assertTrue("gameView should have myPlayer key", gameView.has("myPlayer"));
        assertTrue("gameView should have opponentPlayer key", gameView.has("opponentPlayer"));
    }

    @Test
    public void testGameViewMyPlayerHasHandFields() {
        DecisionResult expectedResult = new DecisionResult(0, null, "HandFieldsTest");
        when(mockClient.requestDecision(any())).thenReturn(expectedResult);

        Game game = TestGameFactory.createMinimalGame();
        Player player = TestGameFactory.getPlayerA(game);

        mage.abilities.common.PassAbility passAbility = new mage.abilities.common.PassAbility();
        decisionHandler.handleAction(game, player, Arrays.asList(passAbility), "random");

        ArgumentCaptor<mage.player.ai.DecisionPayload> payloadCaptor = ArgumentCaptor
                .forClass(mage.player.ai.DecisionPayload.class);
        verify(mockClient).requestDecision(payloadCaptor.capture());

        org.json.JSONObject payloadJson = payloadCaptor.getValue().getBody();
        org.json.JSONObject gameView = payloadJson.optJSONObject("gameView");
        assertNotNull("gameView should not be null", gameView);

        org.json.JSONObject myPlayer = gameView.optJSONObject("myPlayer");
        assertNotNull("myPlayer should not be null", myPlayer);
        assertTrue("myPlayer should have handCount", myPlayer.has("handCount"));
        assertTrue("myPlayer should have handCards", myPlayer.has("handCards"));
        assertTrue("handCount should be >= 0", myPlayer.getInt("handCount") >= 0);
        assertNotNull("handCards should be a JSONArray", myPlayer.optJSONArray("handCards"));
    }

    @Test
    public void testGameViewOpponentHandHidden() {
        DecisionResult expectedResult = new DecisionResult(0, null, "OpponentHandTest");
        when(mockClient.requestDecision(any())).thenReturn(expectedResult);

        Game game = TestGameFactory.createMinimalGame();
        Player player = TestGameFactory.getPlayerA(game);

        mage.abilities.common.PassAbility passAbility = new mage.abilities.common.PassAbility();
        decisionHandler.handleAction(game, player, Arrays.asList(passAbility), "random");

        ArgumentCaptor<mage.player.ai.DecisionPayload> payloadCaptor = ArgumentCaptor
                .forClass(mage.player.ai.DecisionPayload.class);
        verify(mockClient).requestDecision(payloadCaptor.capture());

        org.json.JSONObject payloadJson = payloadCaptor.getValue().getBody();
        org.json.JSONObject gameView = payloadJson.optJSONObject("gameView");
        assertNotNull("gameView should not be null", gameView);

        org.json.JSONObject opponentPlayer = gameView.optJSONObject("opponentPlayer");
        assertNotNull("opponentPlayer should not be null", opponentPlayer);
        assertTrue("opponentPlayer should have handCount", opponentPlayer.has("handCount"));
        assertTrue("opponentPlayer handCount should be >= 0", opponentPlayer.getInt("handCount") >= 0);
        // Opponent hand cards must be empty (information boundary)
        org.json.JSONArray handCards = opponentPlayer.optJSONArray("handCards");
        assertNotNull("handCards should be a JSONArray (not null)", handCards);
        assertEquals("opponentPlayer.handCards should be empty (information boundary)", 0, handCards.length());
    }

    @Test
    public void testGameViewOpponentLibraryHidden() {
        DecisionResult expectedResult = new DecisionResult(0, null, "OpponentLibraryTest");
        when(mockClient.requestDecision(any())).thenReturn(expectedResult);

        Game game = TestGameFactory.createMinimalGame();
        Player player = TestGameFactory.getPlayerA(game);

        mage.abilities.common.PassAbility passAbility = new mage.abilities.common.PassAbility();
        decisionHandler.handleAction(game, player, Arrays.asList(passAbility), "random");

        ArgumentCaptor<mage.player.ai.DecisionPayload> payloadCaptor = ArgumentCaptor
                .forClass(mage.player.ai.DecisionPayload.class);
        verify(mockClient).requestDecision(payloadCaptor.capture());

        org.json.JSONObject payloadJson = payloadCaptor.getValue().getBody();
        org.json.JSONObject gameView = payloadJson.optJSONObject("gameView");
        org.json.JSONObject opponentPlayer = gameView.optJSONObject("opponentPlayer");
        assertNotNull("opponentPlayer should not be null", opponentPlayer);
        // Opponent library count is public info
        assertTrue("opponentPlayer should have libraryCount", opponentPlayer.has("libraryCount"));
        assertTrue("opponentPlayer.libraryCount should be >= 0", opponentPlayer.getInt("libraryCount") >= 0);
        // No libraryCards field should be present (library order is hidden)
        assertFalse("opponentPlayer should NOT have libraryCards array", opponentPlayer.has("libraryCards"));
    }

    @Test
    public void testGameViewBackwardCompatible() {
        // Pre-existing payload keys must still be present and unchanged after DN1a
        DecisionResult expectedResult = new DecisionResult(0, null, "BackwardCompatTest");
        when(mockClient.requestDecision(any())).thenReturn(expectedResult);

        Game game = TestGameFactory.createMinimalGame();
        Player player = TestGameFactory.getPlayerA(game);

        mage.abilities.common.PassAbility passAbility = new mage.abilities.common.PassAbility();
        decisionHandler.handleAction(game, player, Arrays.asList(passAbility), "random");

        ArgumentCaptor<mage.player.ai.DecisionPayload> payloadCaptor = ArgumentCaptor
                .forClass(mage.player.ai.DecisionPayload.class);
        verify(mockClient).requestDecision(payloadCaptor.capture());

        org.json.JSONObject payloadJson = payloadCaptor.getValue().getBody();
        // All pre-existing keys must be present
        assertTrue("Should contain gameState", payloadJson.has("gameState"));
        assertTrue("Should contain currentPlayer", payloadJson.has("currentPlayer"));
        assertTrue("Should contain opponentPlayer", payloadJson.has("opponentPlayer"));
        assertTrue("Should contain gameCards", payloadJson.has("gameCards"));
        assertTrue("Should contain strategy", payloadJson.has("strategy"));
        assertTrue("Should contain gameId", payloadJson.has("gameId"));
        assertTrue("Should contain matchId", payloadJson.has("matchId"));
        // gameView should now be a populated JSONObject, not an empty one
        assertTrue("Should contain gameView", payloadJson.has("gameView"));
        org.json.JSONObject gameView = payloadJson.optJSONObject("gameView");
        assertNotNull("gameView should be JSONObject", gameView);
    }

    @Test
    public void testGameViewPlayerHasBattlefieldManaPool() {
        // Verify myPlayer has expected structural fields
        DecisionResult expectedResult = new DecisionResult(0, null, "PlayerFieldsTest");
        when(mockClient.requestDecision(any())).thenReturn(expectedResult);

        Game game = TestGameFactory.createMinimalGame();
        Player player = TestGameFactory.getPlayerA(game);

        mage.abilities.common.PassAbility passAbility = new mage.abilities.common.PassAbility();
        decisionHandler.handleAction(game, player, Arrays.asList(passAbility), "random");

        ArgumentCaptor<mage.player.ai.DecisionPayload> payloadCaptor = ArgumentCaptor
                .forClass(mage.player.ai.DecisionPayload.class);
        verify(mockClient).requestDecision(payloadCaptor.capture());

        org.json.JSONObject payloadJson = payloadCaptor.getValue().getBody();
        org.json.JSONObject gameView = payloadJson.optJSONObject("gameView");
        org.json.JSONObject myPlayer = gameView.optJSONObject("myPlayer");
        assertNotNull("myPlayer should not be null", myPlayer);
        assertTrue("myPlayer should have battlefield", myPlayer.has("battlefield"));
        assertTrue("myPlayer should have graveyard", myPlayer.has("graveyard"));
        assertTrue("myPlayer should have exile", myPlayer.has("exile"));
        assertTrue("myPlayer should have manaPool", myPlayer.has("manaPool"));
        assertTrue("myPlayer should have life", myPlayer.has("life"));
        assertTrue("myPlayer should have id", myPlayer.has("id"));
        assertTrue("myPlayer should have name", myPlayer.has("name"));
        // manaPool should have color fields
        org.json.JSONObject manaPool = myPlayer.optJSONObject("manaPool");
        assertNotNull("manaPool should be JSONObject", manaPool);
        assertTrue("manaPool should have red", manaPool.has("red"));
        assertTrue("manaPool should have green", manaPool.has("green"));
    }

    @Test
    public void testTrajectoryPayloadHasGameView() {
        // Trajectory payload should inherit gameView from buildDecisionBasePayload
        Game game = TestGameFactory.createMinimalGame();
        Player player = TestGameFactory.getPlayerA(game);

        java.util.Map<String, Object> chosenAction = new java.util.HashMap<>();
        chosenAction.put("actionIndex", 0);
        java.util.Map<String, Object> additionalContext = new java.util.HashMap<>();

        org.json.JSONObject payload = decisionHandler.buildTrajectoryPayload(
                game, player, "priority", Arrays.asList("action1"), chosenAction, additionalContext);

        assertNotNull("Trajectory payload should not be null", payload);
        assertTrue("Trajectory payload should contain gameView", payload.has("gameView"));
        org.json.JSONObject gameView = payload.optJSONObject("gameView");
        assertNotNull("gameView should be a JSONObject", gameView);
        assertTrue("gameView should have myPlayer", gameView.has("myPlayer"));
        assertTrue("gameView should have opponentPlayer", gameView.has("opponentPlayer"));
    }

    @Test
    public void testActionPayloadHasPopulatedGameView() {
        // Verify action endpoint payload has populated gameView
        DecisionResult expectedResult = new DecisionResult(0, null, "ActionGameViewTest");
        when(mockClient.requestDecision(any())).thenReturn(expectedResult);

        Game game = TestGameFactory.createMinimalGame();
        Player player = TestGameFactory.getPlayerA(game);

        mage.abilities.common.PassAbility passAbility = new mage.abilities.common.PassAbility();
        decisionHandler.handleAction(game, player, Arrays.asList(passAbility), "random");

        ArgumentCaptor<mage.player.ai.DecisionPayload> payloadCaptor = ArgumentCaptor
                .forClass(mage.player.ai.DecisionPayload.class);
        verify(mockClient).requestDecision(payloadCaptor.capture());

        org.json.JSONObject payloadJson = payloadCaptor.getValue().getBody();
        org.json.JSONObject gameView = payloadJson.optJSONObject("gameView");
        assertNotNull("Action payload gameView should be a JSONObject", gameView);
        assertTrue("Action payload gameView should have myPlayer", gameView.has("myPlayer"));
    }

    @Test
    public void testChoicePayloadHasPopulatedGameView() {
        // Verify choice endpoint payload has populated gameView
        DecisionResult expectedResult = new DecisionResult(0, null, "ChoiceGameViewTest");
        when(mockClient.requestDecision(any())).thenReturn(expectedResult);

        Game game = TestGameFactory.createMinimalGame();
        Player player = TestGameFactory.getPlayerA(game);

        mage.choices.Choice choice = new mage.choices.ChoiceImpl(true);
        choice.setMessage("Test");
        String[] choices = {"A", "B"};
        decisionHandler.handleChoice(game, player, Outcome.Benefit, choice, choices, "random");

        ArgumentCaptor<mage.player.ai.DecisionPayload> payloadCaptor = ArgumentCaptor
                .forClass(mage.player.ai.DecisionPayload.class);
        verify(mockClient).requestDecision(payloadCaptor.capture());

        org.json.JSONObject payloadJson = payloadCaptor.getValue().getBody();
        org.json.JSONObject gameView = payloadJson.optJSONObject("gameView");
        assertNotNull("Choice payload gameView should be a JSONObject", gameView);
        assertTrue("Choice payload gameView should have opponentPlayer", gameView.has("opponentPlayer"));
    }

    @Test
    public void testAttackersPayloadHasPopulatedGameView() {
        // Verify attackers endpoint payload has populated gameView
        DecisionResult expectedResult = new DecisionResult(null, java.util.List.of(), "AttackersGameViewTest");
        when(mockClient.requestDecision(any())).thenReturn(expectedResult);

        Game game = TestGameFactory.createMinimalGame();
        Player player = TestGameFactory.getPlayerA(game);

        decisionHandler.handleAttackers(game, player,
                java.util.List.of(), java.util.List.of(), "random");

        ArgumentCaptor<mage.player.ai.DecisionPayload> payloadCaptor = ArgumentCaptor
                .forClass(mage.player.ai.DecisionPayload.class);
        verify(mockClient).requestDecision(payloadCaptor.capture());

        org.json.JSONObject payloadJson = payloadCaptor.getValue().getBody();
        org.json.JSONObject gameView = payloadJson.optJSONObject("gameView");
        assertNotNull("Attackers payload gameView should be a JSONObject", gameView);
        assertTrue("Attackers payload gameView should have phase", gameView.has("phase"));
    }

    @Test
    public void testTargetAmountPayloadDoesNotHaveGameView() {
        // chooseTargetAmount uses buildBaseRequestPayload (not buildDecisionBasePayload)
        // so it should NOT have gameView
        DecisionResult expectedResult = new DecisionResult(0, java.util.List.of(), "TargetAmountTest");
        when(mockClient.requestDecision(any())).thenReturn(expectedResult);

        Game game = TestGameFactory.createMinimalGame();
        Player player = TestGameFactory.getPlayerA(game);

        decisionHandler.handleChooseTargetAmount(game, player,
                java.util.List.of(), 1, 1, "random");

        ArgumentCaptor<mage.player.ai.DecisionPayload> payloadCaptor = ArgumentCaptor
                .forClass(mage.player.ai.DecisionPayload.class);
        verify(mockClient).requestDecision(payloadCaptor.capture());

        org.json.JSONObject payloadJson = payloadCaptor.getValue().getBody();
        // chooseTargetAmount intentionally does NOT include gameView (uses BaseRequest, not DecisionBase)
        assertFalse("TargetAmount payload should NOT have gameView (uses BaseRequest)", payloadJson.has("gameView"));
    }

    @Test
    public void testBuildTrajectoryPayload() {
        // Create a real game and player using TestGameFactory
        Game game = TestGameFactory.createMinimalGame();
        Player player = TestGameFactory.getPlayerA(game);

        // Create test trajectory data
        Object availableActions = Arrays.asList("action1", "action2");
        Map<String, Object> chosenAction = new HashMap<>();
        chosenAction.put("actionIndex", 0);
        Map<String, Object> additionalContext = new HashMap<>();
        additionalContext.put("turnNumber", 1);

        // Test buildTrajectoryPayload - should not throw exception
        try {
            org.json.JSONObject payload = decisionHandler.buildTrajectoryPayload(game, player, "action",
                    availableActions, chosenAction, additionalContext);

            assertNotNull("Payload should not be null", payload);
            assertTrue("Payload should contain decisionType", payload.has("decisionType"));
            assertTrue("Payload should contain gameIsOver", payload.has("gameIsOver"));
            assertTrue("Payload should contain availableActions", payload.has("availableActions"));
            assertTrue("Payload should contain chosenAction", payload.has("chosenAction"));
            assertTrue("Payload should contain additionalContext", payload.has("additionalContext"));
            assertEquals("Decision type should match", "action", payload.getString("decisionType"));
        } catch (Exception e) {
            fail("buildTrajectoryPayload should not throw exception: " + e.getMessage());
        }
    }
}
