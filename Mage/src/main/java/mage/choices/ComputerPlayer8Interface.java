package mage.choices;

import mage.constants.Outcome;
import mage.game.Game;
import mage.players.Player;

public interface ComputerPlayer8Interface extends Player {
    public int callLLMToChooseFromChoices(Game game, Player currentPlayer, Outcome outcome, Choice choice,
            String[] allChoices);
}
