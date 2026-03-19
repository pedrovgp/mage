package org.mage.test.serverside.base;

import mage.cards.decks.Deck;
import mage.constants.MultiplayerAttackOption;
import mage.constants.RangeOfInfluence;
import mage.game.Game;
import mage.game.TwoPlayerDuel;
import mage.game.TwoPlayerMatch;
import mage.game.match.MatchOptions;
import mage.game.mulligan.MulliganType;
import mage.players.Player;
import mage.player.ai.ComputerPlayer8;

import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

/**
 * Factory class to create minimal game and player objects for testing
 * DecisionHandler serialization.
 * Creates realistic XMage objects that can be properly serialized by Jackson
 * mixins.
 */
public class TestGameFactory {

    /**
     * Creates a minimal TwoPlayerDuel game with ComputerPlayer8 instances.
     *
     * Uses a TwoPlayerMatch to properly set up MatchPlayer on each player,
     * which is required for GameView / PlayerView construction (PlayerView
     * calls player.getMatchPlayer().getWins() during construction).
     */
    public static Game createMinimalGame() {
        // Create minimal decks
        Deck deckA = new Deck();
        Deck deckB = new Deck();

        // Create players
        ComputerPlayer8 playerA = new ComputerPlayer8("TestPlayerA", RangeOfInfluence.ONE, 8);
        ComputerPlayer8 playerB = new ComputerPlayer8("TestPlayerB", RangeOfInfluence.ONE, 8);

        // Create a TwoPlayerMatch so that addPlayer() sets matchPlayer on each player.
        // This is required for PlayerView (used by GameView) which calls
        // player.getMatchPlayer().getWins() in its constructor.
        MatchOptions matchOptions = new MatchOptions("TestMatch", "Two Player Duel", false, 2);
        matchOptions.setWinsNeeded(1);
        TwoPlayerMatch match = new TwoPlayerMatch(matchOptions);
        match.addPlayer(playerA, deckA);
        match.addPlayer(playerB, deckB);

        // Create the game directly for serialization testing
        TwoPlayerDuel game = new TwoPlayerDuel(
                MultiplayerAttackOption.LEFT,
                RangeOfInfluence.ONE,
                MulliganType.GAME_DEFAULT.getMulligan(0),
                60, // starting life
                20, // starting hand size
                7   // max hand size
        );

        // Add players to the game after matchPlayer is set
        game.addPlayer(playerA, deckA);
        game.addPlayer(playerB, deckB);

        return game;
    }

    /**
     * Gets the first player from a game created by createMinimalGame().
     */
    public static Player getPlayerA(Game game) {
        // Get the first player ID from the game state
        Set<UUID> playerIds = game.getState().getPlayers().keySet();
        Iterator<UUID> iterator = playerIds.iterator();
        if (iterator.hasNext()) {
            UUID firstPlayerId = iterator.next();
            return game.getPlayer(firstPlayerId);
        }
        return null;
    }

    /**
     * Gets the second player from a game created by createMinimalGame().
     */
    public static Player getPlayerB(Game game) {
        // Get the second player ID from the game state
        Set<UUID> playerIds = game.getState().getPlayers().keySet();
        Iterator<UUID> iterator = playerIds.iterator();
        if (iterator.hasNext()) {
            iterator.next(); // skip first
            if (iterator.hasNext()) {
                UUID secondPlayerId = iterator.next();
                return game.getPlayer(secondPlayerId);
            }
        }
        return null;
    }
}
