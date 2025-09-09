package org.mage.test.serverside.base;

import mage.abilities.Ability;
import mage.abilities.common.PassAbility;
import mage.choices.Choice;
import mage.choices.ChoiceImpl;
import mage.constants.Outcome;
import mage.game.Game;
import mage.game.GameState;
import mage.players.Player;
import mage.player.ai.DecisionHandler;
import mage.player.ai.DecisionResult;
import mage.player.ai.LlmDecisionClient;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.List;

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
        when(mockPlayer.getId()).thenReturn(java.util.UUID.randomUUID());
        when(mockPlayer.getName()).thenReturn("TestPlayer");
    }

    @Test
    public void testConstructorWithBaseUrl() {
        // Test constructor with base URL
        DecisionHandler handler = new DecisionHandler("http://localhost:9000");
        assertNotNull("DecisionHandler should be created with base URL", handler);
    }

    @Test
    public void testHandleActionWithValidInput() {
        // Setup mock client to return a decision result
        DecisionResult expectedResult = new DecisionResult(0, null, "test_reason");
        when(mockClient.requestDecision(any())).thenReturn(expectedResult);

        // Create test abilities
        PassAbility passAbility = new PassAbility();
        List<Ability> actions = Arrays.asList(passAbility);

        // Test handleAction
        DecisionResult result = decisionHandler.handleAction(mockGame, mockPlayer, actions, "random");

        assertNotNull("Result should not be null", result);
        assertEquals("Chosen index should match", 0, result.getChosenIndex().intValue());
        assertEquals("Reason should match", "test_reason", result.getReason());

        // Verify client was called
        verify(mockClient, times(1)).requestDecision(any());
    }

    @Test
    public void testHandleChoiceWithValidInput() {
        // Setup mock client to return a decision result
        DecisionResult expectedResult = new DecisionResult(1, null, "choice_reason");
        when(mockClient.requestDecision(any())).thenReturn(expectedResult);

        // Create test choice
        Choice choice = new ChoiceImpl();
        choice.setMessage("Test choice");
        String[] choices = { "Option 1", "Option 2", "Option 3" };

        // Test handleChoice
        DecisionResult result = decisionHandler.handleChoice(mockGame, mockPlayer, Outcome.Benefit, choice, choices,
                "random");

        assertNotNull("Result should not be null", result);
        assertEquals("Chosen index should match", 1, result.getChosenIndex().intValue());
        assertEquals("Reason should match", "choice_reason", result.getReason());

        // Verify client was called
        verify(mockClient, times(1)).requestDecision(any());
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
        Choice choice = new ChoiceImpl();
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
        // Setup mock game
        when(mockGame.informPlayers(anyString())).thenReturn(mockGame);

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
        // Setup mock game
        when(mockGame.informPlayers(anyString())).thenReturn(mockGame);

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
        // Setup mock client to return a decision result
        DecisionResult expectedResult = new DecisionResult(0, null, "target_reason");
        when(mockClient.requestDecision(any())).thenReturn(expectedResult);

        // Create test target choices
        String[] choices = { "Target 1", "Target 2" };

        // Test handleTargets
        DecisionResult result = decisionHandler.handleTargets(mockGame, mockPlayer, Outcome.Damage, choices, "random");

        assertNotNull("Result should not be null", result);
        assertEquals("Chosen index should match", 0, result.getChosenIndex().intValue());
        assertEquals("Reason should match", "target_reason", result.getReason());

        // Verify client was called
        verify(mockClient, times(1)).requestDecision(any());
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
}
