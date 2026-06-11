package mage.player.ai;

import mage.MageObject;
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
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.io.OutputStream;
import java.net.URL;

/**
 * AI: server side bot with game simulations (mad bot, the latest version)
 *
 * @author ayratn
 */
public class ComputerPlayer8 extends ComputerPlayer7 implements ComputerPlayer8Interface {

    private static final Logger logger = Logger.getLogger(ComputerPlayer8.class);

    // CP7 shadow agreement probe (read-only research instrumentation).
    // When -Dmagellm.cp7Shadow=true, a sampled fraction of RL decisions also run a
    // shadow CP7 alpha-beta think on the same state; the expert's choice is logged
    // (POST /v1/shadow_agreement) but NEVER acted on. Default OFF: the only cost
    // when unset is this static boolean check.
    private static final boolean CP7_SHADOW_ENABLED = Boolean.getBoolean("magellm.cp7Shadow");
    private static final double CP7_SHADOW_RATE = parseShadowRate();

    private static double parseShadowRate() {
        try {
            return Double.parseDouble(System.getProperty("magellm.cp7ShadowRate", "0.25"));
        } catch (NumberFormatException e) {
            return 0.25;
        }
    }

    private boolean allowBadMoves;
    private final DecisionHandler decisionHandler;

    public ComputerPlayer8(String name, RangeOfInfluence range, int skill) {
        super(name, range, skill);
        this.decisionHandler = new DecisionHandler(
                System.getProperty("magellmfast.url", "http://localhost:9000"));
    }

    public ComputerPlayer8(final ComputerPlayer8 player) {
        super(player);
        this.allowBadMoves = player.allowBadMoves;
        this.decisionHandler = new DecisionHandler(
                System.getProperty("magellmfast.url", "http://localhost:9000"));
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
        PassAbility passAbility = new PassAbility();
        LinkedList<Ability> allActions = new LinkedList<>();
        allActions.add(passAbility);
        long _gpStart = System.nanoTime();
        allActions.addAll(this.getPlayable(game, false));
        DecisionStats.INSTANCE.recordGetPlayable(System.nanoTime() - _gpStart);

        int chosenActionIndex = 0;
        if (allActions.size() > 1) {
            chosenActionIndex = selectActionViaRL(game, allActions, this);
        }

        Ability chosenAction = allActions.get(chosenActionIndex);

        if (logger.isInfoEnabled()) {
            logger.info("LLM chosen action: " + chosenAction.toString());
        }

        // Shadow CP7 probe: run BEFORE the chosen action executes so the expert
        // thinks on exactly the state the RL agent saw. Only when a real decision
        // was made (forced single-Pass states never hit the RL server).
        if (CP7_SHADOW_ENABLED && allActions.size() > 1) {
            shadowProbePriority(game, allActions, chosenActionIndex);
        }

        if (chosenAction instanceof PassAbility) {
            pass(game);
        } else {
            // Clear pre-filled targets from alpha-beta evaluation so that the game engine
            // invokes chooseTarget() on this player, allowing the RL model to select targets
            // via the /choose_targets endpoint rather than using alpha-beta's choice.
            for (Target t : chosenAction.getTargets()) {
                t.clearChosen();
            }

            LinkedList<Ability> singleActionList = new LinkedList<>();
            singleActionList.add(chosenAction);
            this.actions = singleActionList;
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

    private int selectActionViaRL(Game game, LinkedList<Ability> allActions, ComputerPlayer currentPlayer) {
        // inform* is now called inside handleAction (with timing); do not call it again here
        DecisionResult result = decisionHandler.handleAction(game, currentPlayer, allActions,
                getStrategyFromEnvironment());
        return result.getChosenIndex() != null ? result.getChosenIndex() : 0;
    }

    @Override
    public int selectChoiceViaRL(Game game, Player currentPlayer, Outcome outcome, Choice choice,
            String[] allChoices) {
        // inform* is now called inside handleChoice (with timing); do not call it again here
        DecisionResult result = decisionHandler.handleChoice(game, currentPlayer, outcome, choice, allChoices,
                getStrategyFromEnvironment());
        return result.getChosenIndex() != null ? result.getChosenIndex() : 0;
    }

    @Override
    public void setAllowBadMoves(boolean allowBadMoves) {
        this.allowBadMoves = allowBadMoves;
    }

    @Override
    public boolean choose(Outcome outcome, Choice choice, Game game) {
        logger.debug("choose 8");
        // Delegate general choice selection to RL decision engine when multiple options exist
        if (!choice.isChosen() && choice.getChoices() != null && !choice.getChoices().isEmpty()) {
            try {
                Player currentPlayer = game.getPlayer(this.getId());
                String[] allChoices = choice.getChoices().toArray(new String[0]);
                int idx = selectChoiceViaRL(game, currentPlayer, outcome, choice, allChoices);
                if (idx >= 0 && idx < allChoices.length) {
                    choice.setChoice(allChoices[idx]);
                    return true;
                }
            } catch (Exception e) {
                logger.error("RL choice failed, falling back to super", e);
            }
        }
        // Fallbacks and special cases
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

                List<Permanent> selectedAttackers = selectAttackersViaRL(game, attackersList, possibleBlockers,
                        attackingPlayer);

                // Shadow CP7 probe: BEFORE declaring, so the expert sees the same
                // pre-declaration state the RL agent decided on.
                if (CP7_SHADOW_ENABLED) {
                    shadowProbeAttackers(game, attackersList, selectedAttackers);
                }

                for (Permanent attacker : selectedAttackers) {
                    attackingPlayer.declareAttacker(attacker.getId(), defenderId, game, true);
                }
            }
        }
    }

    private List<Permanent> selectAttackersViaRL(Game game, List<Permanent> possibleAttackers,
            List<Permanent> possibleBlockers, Player currentPlayer) {
        // inform* is now called inside handleAttackers (with timing); do not call it again here
        DecisionResult dr = decisionHandler.handleAttackers(game, currentPlayer, possibleAttackers, possibleBlockers,
                getStrategyFromEnvironment());
        List<Permanent> chosen = new ArrayList<>();
        if (dr.getChosenUuids() != null) {
            for (UUID id : dr.getChosenUuids()) {
                Permanent p = game.getPermanent(id);
                if (p != null)
                    chosen.add(p);
            }
        }
        return chosen;
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

        // Build paired list sorted alphabetically by MageObject.toString() so that
        // the action index in /choose_targets/ is stable and matches the index logged
        // by CP7Instrumented (which also sorts alphabetically).
        List<java.util.AbstractMap.SimpleEntry<UUID, MageObject>> sortedPairs = new java.util.ArrayList<>();
        for (UUID possibleTargetUUID : possibleTargets) {
            sortedPairs.add(new java.util.AbstractMap.SimpleEntry<>(
                    possibleTargetUUID, game.getObject(possibleTargetUUID)));
        }
        sortedPairs.sort(java.util.Comparator.comparing(
                e -> e.getValue() != null ? e.getValue().toString() : ""));
        UUID[] possibleTargetsUUIDArray = new UUID[sortedPairs.size()];
        MageObject[] possibleTargetsArray = new MageObject[sortedPairs.size()];
        for (int i = 0; i < sortedPairs.size(); i++) {
            possibleTargetsUUIDArray[i] = sortedPairs.get(i).getKey();
            possibleTargetsArray[i] = sortedPairs.get(i).getValue();
        }

        // LLM single-target selection (smoke coverage): if there is exactly one pick to
        // make, ask LLM once
        try {
            if (possibleTargetsArray.length > 0
                    && target.getMaxNumberOfTargets() == 1
                    && target.getTargets().isEmpty()) {
                String[] allChoices = new String[possibleTargetsArray.length];
                for (int i2 = 0; i2 < possibleTargetsArray.length; i2++) {
                    MageObject mo = possibleTargetsArray[i2];
                    allChoices[i2] = mo != null ? mo.toString() : "unknown";
                }
                Player currentPlayer = game.getPlayer(this.getId());
                // inform* is now called inside handleTargets (with timing); do not call it again here
                DecisionResult dr = decisionHandler.handleTargets(game, currentPlayer, outcome, allChoices,
                        getStrategyFromEnvironment());
                int idx = dr.getChosenIndex() != null ? dr.getChosenIndex() : 0;
                if (idx >= 0 && idx < possibleTargetsUUIDArray.length) {
                    UUID chosen = possibleTargetsUUIDArray[idx];
                    if (target.canTarget(abilityControllerId, chosen, source, game)) {
                        target.addTarget(chosen, source, game);
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            logger.error("LLM target choose failed, fallback to built-in", e);
        }

        // Local heuristic fallback — time how long this takes.
        // try-finally ensures every return and throw in the section is counted.
        long _localTargetStart = System.nanoTime();
        try {

        // TODO PV: Multi-target LLM selection can iterate until min/max satisfied

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

        } finally {
            DecisionStats.INSTANCE.recordLocalTarget(System.nanoTime() - _localTargetStart);
        }
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

    @Override
    public void selectBlockers(mage.abilities.Ability source, Game game, UUID defendingPlayerId) {
        long _t0 = System.nanoTime();
        super.selectBlockers(source, game, defendingPlayerId);
        DecisionStats.INSTANCE.recordLocalBlockers(System.nanoTime() - _t0);
    }

    // ================================================================================
    // CP7 SHADOW AGREEMENT PROBE (read-only; results are logged, never acted on)
    // ================================================================================

    /**
     * Build a fresh shadow CP7 instance for a probe think.
     *
     * The copy constructor shares playerId (so the shadow searches from this seat)
     * but the CP6 copy ctor SHARES the actionCache reference and COPIES
     * actions/combat. All three are reset so that:
     *  - the shadow's first choice is the raw alpha-beta answer (no repeat
     *    suppression from the live player's history),
     *  - the shadow can never write into the live player's actionCache,
     *  - a think that yields no result cannot leave a stale copied combat behind.
     * root is not copied by the ctor (null => calculateActions does a fresh search).
     */
    private ComputerPlayer7 newShadowCp7() {
        ComputerPlayer7 shadow = new ComputerPlayer7(this);
        shadow.actionCache = new HashSet<>();
        shadow.actions = new LinkedList<>();
        shadow.combat = null;
        shadow.root = null;
        return shadow;
    }

    /** Match key identical to the one actionCache uses (rule + source id). */
    private static String shadowAbilityKey(Ability ability) {
        return ability.getRule() + '_' + ability.getSourceId();
    }

    /**
     * Shadow probe for priority decisions. Sample-rate gated BEFORE the expensive
     * think; the gate uses ThreadLocalRandom so the game's RandomUtil sequences are
     * never advanced. calculateActions itself searches on createSimulation(game)
     * inside RandomUtil.enterSimulation()/exitSimulation(), so the live game is
     * not mutated. Any failure is swallowed (probe must never affect the game).
     */
    private void shadowProbePriority(Game game, List<Ability> allActions, int rlChosenIndex) {
        try {
            if (ThreadLocalRandom.current().nextDouble() >= CP7_SHADOW_RATE) {
                return;
            }
            ComputerPlayer7 shadow = newShadowCp7();
            shadow.calculateActions(game);
            // Take the first/root choice directly from the shadow's actions queue.
            Ability cp7Choice = shadow.actions.isEmpty() ? null : shadow.actions.peek();
            // CP7 with no calculated actions passes (act() -> pass), so treat null as Pass.
            String cp7Text = cp7Choice != null ? cp7Choice.toString() : "Pass";
            Integer matchedIndex = null;
            for (int i = 0; i < allActions.size(); i++) {
                Ability candidate = allActions.get(i);
                if (cp7Choice == null) {
                    if (candidate instanceof PassAbility) {
                        matchedIndex = i;
                        break;
                    }
                } else if (shadowAbilityKey(candidate).equals(shadowAbilityKey(cp7Choice))) {
                    matchedIndex = i;
                    break;
                }
            }
            postShadowAgreement(game, "priority",
                    allActions.get(rlChosenIndex).toString(), rlChosenIndex,
                    cp7Text, matchedIndex, allActions.size());
        } catch (Throwable t) {
            logger.warn("CP7 shadow probe (priority) failed - ignored", t);
        }
    }

    /**
     * Shadow probe for the attackers declaration. The shadow think's combat field
     * (root.combat from the alpha-beta search) provides CP7's attacker set; both
     * sides are serialized as sorted attacker names and compared as text.
     * Convention: rl_choice_index = 0, matched_index = 0 on agreement else null.
     */
    private void shadowProbeAttackers(Game game, List<Permanent> possibleAttackers,
            List<Permanent> rlSelectedAttackers) {
        try {
            if (ThreadLocalRandom.current().nextDouble() >= CP7_SHADOW_RATE) {
                return;
            }
            ComputerPlayer7 shadow = newShadowCp7();
            shadow.calculateActions(game);
            List<String> cp7Names = new ArrayList<>();
            if (shadow.combat != null) {
                for (UUID attackerId : shadow.combat.getAttackers()) {
                    Permanent permanent = game.getPermanent(attackerId);
                    cp7Names.add(permanent != null ? permanent.getName() : attackerId.toString());
                }
            }
            Collections.sort(cp7Names);
            List<String> rlNames = new ArrayList<>();
            for (Permanent permanent : rlSelectedAttackers) {
                rlNames.add(permanent.getName());
            }
            Collections.sort(rlNames);
            String rlText = String.join(", ", rlNames);
            String cp7Text = String.join(", ", cp7Names);
            Integer matchedIndex = rlText.equals(cp7Text) ? Integer.valueOf(0) : null;
            postShadowAgreement(game, "attackers", rlText, 0, cp7Text, matchedIndex,
                    possibleAttackers.size());
        } catch (Throwable t) {
            logger.warn("CP7 shadow probe (attackers) failed - ignored", t);
        }
    }

    /** Build the comparison payload and POST it best-effort via DecisionHandler. */
    private void postShadowAgreement(Game game, String decisionType, String rlChoiceText,
            int rlChoiceIndex, String cp7ChoiceText, Integer matchedIndex, int nActions) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("game_id", game.getId().toString());
            payload.put("turn", game.getTurnNum());
            payload.put("phase", game.getPhase() != null && game.getPhase().getType() != null
                    ? game.getPhase().getType().toString() : "");
            payload.put("step", game.getTurnStepType() != null
                    ? game.getTurnStepType().toString() : "");
            payload.put("decision_type", decisionType);
            payload.put("rl_choice_text", rlChoiceText);
            payload.put("rl_choice_index", rlChoiceIndex);
            payload.put("cp7_choice_text", cp7ChoiceText);
            payload.put("matched_index", matchedIndex != null ? matchedIndex : JSONObject.NULL);
            payload.put("n_actions", nActions);
            decisionHandler.postShadowAgreement(payload);
        } catch (Throwable t) {
            logger.debug("CP7 shadow agreement post failed - ignored: " + t.getMessage());
        }
    }

    private String getStrategyFromEnvironment() {
        String strategy = System.getProperty("MAGELLM_STRATEGY", System.getenv("MAGELLM_STRATEGY"));
        if (strategy == null || strategy.trim().isEmpty()) {
            return "rl";
        }
        return strategy;
    }

}
