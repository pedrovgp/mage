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
    public void testHandleActionWithValidInput() {
        // For this test, we'll test the exception handling path since serialization of
        // mocks is complex
        // Setup mock client to throw exception (which should trigger fallback)
        when(mockClient.requestDecision(any())).thenThrow(new RuntimeException("Serialization test"));

        // Create test abilities
        PassAbility passAbility = new PassAbility();
        List<Ability> actions = Arrays.asList(passAbility);

        // Test handleAction - should fallback due to exception
        DecisionResult result = decisionHandler.handleAction(mockGame, mockPlayer, actions, "random");

        assertNotNull("Result should not be null", result);
        assertEquals("Should fallback to first action", 0, result.getChosenIndex().intValue());
        assertTrue("Reason should indicate fallback", result.getReason().contains("fallback"));

        // Note: Client is not called due to JSON serialization failure in payload
        // building
    }

    @Test
    public void testHandleChoiceWithValidInput() {
        // For this test, we'll test the exception handling path since serialization of
        // mocks is complex
        // Setup mock client to throw exception (which should trigger fallback)
        when(mockClient.requestDecision(any())).thenThrow(new RuntimeException("Serialization test"));

        // Create test choice
        Choice choice = new ChoiceImpl(true);
        choice.setMessage("Test choice");
        String[] choices = { "Option 1", "Option 2", "Option 3" };

        // Test handleChoice - should fallback due to exception
        DecisionResult result = decisionHandler.handleChoice(mockGame, mockPlayer, Outcome.Benefit, choice, choices,
                "random");

        assertNotNull("Result should not be null", result);
        assertEquals("Should fallback to first choice", 0, result.getChosenIndex().intValue());
        assertTrue("Reason should indicate fallback", result.getReason().contains("fallback"));

        // Note: Client is not called due to JSON serialization failure in payload
        // building
    }

    @Test
    public void testHandleActionWithException() {
        // Setup mock client to throw exception
        when(mockClient.requestDecision(any())).thenThrow(new RuntimeException("Test exception"));

        // Create test abilities
        PassAbility passAbility = new PassAbility();
        List<Ability> actions = Arrays.asList(passAbility);

        // Test handleAction with exception
        DecisionResult result = decisionHandler.handleAction(mockGame, mockPlayer, actions, "random");

        assertNotNull("Result should not be null even with exception", result);
        assertEquals("Should fallback to first action", 0, result.getChosenIndex().intValue());
        assertTrue("Reason should indicate fallback", result.getReason().contains("fallback"));
    }

    @Test
    public void testHandleChoiceWithException() {
        // Setup mock client to throw exception
        when(mockClient.requestDecision(any())).thenThrow(new RuntimeException("Test exception"));

        // Create test choice
        Choice choice = new ChoiceImpl(true);
        String[] choices = { "Option 1", "Option 2" };

        // Test handleChoice with exception
        DecisionResult result = decisionHandler.handleChoice(mockGame, mockPlayer, Outcome.Benefit, choice, choices,
                "random");

        assertNotNull("Result should not be null even with exception", result);
        assertEquals("Should fallback to first choice", 0, result.getChosenIndex().intValue());
        assertTrue("Reason should indicate fallback", result.getReason().contains("fallback"));
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
    public void testHandleTargets() {
        // For this test, we'll test the exception handling path since serialization of
        // mocks is complex
        // Setup mock client to throw exception (which should trigger fallback)
        when(mockClient.requestDecision(any())).thenThrow(new RuntimeException("Serialization test"));

        // Create test target choices
        String[] choices = { "Target 1", "Target 2" };

        // Test handleTargets - should fallback due to exception
        DecisionResult result = decisionHandler.handleTargets(mockGame, mockPlayer, Outcome.Damage, choices, "random");

        assertNotNull("Result should not be null", result);
        assertEquals("Should fallback to first target", 0, result.getChosenIndex().intValue());
        assertTrue("Reason should indicate fallback", result.getReason().contains("fallback"));

        // Note: Client is not called due to JSON serialization failure in payload
        // building
    }

    @Test
    public void testHandleTargetsWithException() {
        // Setup mock client to throw exception
        when(mockClient.requestDecision(any())).thenThrow(new RuntimeException("Test exception"));

        // Create test target choices
        String[] choices = { "Target 1" };

        // Test handleTargets with exception
        DecisionResult result = decisionHandler.handleTargets(mockGame, mockPlayer, Outcome.Damage, choices, "random");

        assertNotNull("Result should not be null even with exception", result);
        assertEquals("Should fallback to first target", 0, result.getChosenIndex().intValue());
        assertTrue("Reason should indicate fallback", result.getReason().contains("fallback"));
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
        DecisionResult result = decisionHandler.handleAction(game, player, actions, "llm");

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
        assertEquals("Should use correct endpoint", "/api/mtg_llm/choose_from_all_actions/",
                capturedPayload.getEndpointPath());

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
        DecisionResult result = decisionHandler.handleChoice(game, player, Outcome.Benefit, choice, choices, "llm");

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
        assertEquals("Should use correct endpoint", "/api/mtg_llm/choose_from_choices/",
                capturedPayload.getEndpointPath());

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

        DecisionResult result = decisionHandler.handleAction(game, player, actions, "llm");

        // If we get here without exceptions, serialization worked
        assertNotNull("Result should not be null", result);
        assertEquals("Should return expected result", expectedResult.getChosenIndex(), result.getChosenIndex());

        // Verify the client was called (which means serialization succeeded)
        verify(mockClient, times(1)).requestDecision(any());
    }
}
