package mage.game.permanent.token;

import mage.MageInt;
import mage.constants.CardType;
import mage.constants.SubType;

/**
 * @author spjspj
 */
public final class GermToken extends TokenImpl {

    public GermToken() {
        super("Germ Token", "0/0 colorless Germ creature token");
        cardType.add(CardType.CREATURE);
        subtype.add(SubType.GERM);
        power = new MageInt(0);
        toughness = new MageInt(0);
    }

    private GermToken(final GermToken token) {
        super(token);
    }

    public GermToken copy() {
        return new GermToken(this);
    }
}
