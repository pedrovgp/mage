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

    // DAgger collection: in mixture games (-Dstrategy=dagger) a PER-GAME
    // seeded draw at setup decides PlayerA's pilot for the ENTIRE game (see
    // FullGameSimulationInstrumentedBase).  When the STUDENT pilot is drawn,
    // PlayerA is this CP8 with -Dmagellm.dagger=true: the shadow CP7 runs on
    // EVERY decision (rate gate bypassed) and its choice is written as the
    // trajectory label via CP7Instrumented-style /v1/log_trajectory event
    // pairs (the RL's own action is executed but NEVER logged as the label —
    // learnings §15: RL-chosen actions are not expert labels).  The JVM runs
    // with -Dmagellmfast.logTrajectory=false so the inference server writes
    // no RL-side records; the explicit DAgger posts force logTrajectory=true
    // in the payload.  Agreement posts are SKIPPED in this mode so the
    // benchmark probe data stays clean.
    //
    // -Dmagellm.daggerFraction is the mixture parameter P(student game) in
    // effect at spawn (stamped into provenance for audit; the draw itself
    // happens in the test harness).
    private static final boolean DAGGER_MODE = Boolean.getBoolean("magellm.dagger");
    private static final double DAGGER_FRACTION = parseDoubleProp("magellm.daggerFraction", 1.0);

    private static double parseShadowRate() {
        return parseDoubleProp("magellm.cp7ShadowRate", 0.25);
    }

    private static double parseDoubleProp(String key, double dflt) {
        try {
            return Double.parseDouble(System.getProperty(key, Double.toString(dflt)));
        } catch (NumberFormatException e) {
            return dflt;
        }
    }

    // Synchronous sequence counter for DAgger trajectory entries (mirrors
    // CP7Instrumented.logSeq) so the Python filter can restore logical order
    // despite async HTTP delivery.
    private long daggerLogSeq = 0;

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
        if ((CP7_SHADOW_ENABLED || DAGGER_MODE) && allActions.size() > 1) {
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
        // DAgger: log this choice decision (with the expert/CP7-heuristic label)
        // BEFORE the student acts, so the logged state precedes the decision.
        if (DAGGER_MODE) {
            logDaggerChoice(game, outcome, choice);
        }
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
                if (CP7_SHADOW_ENABLED || DAGGER_MODE) {
                    shadowProbeAttackers(game, attackersList, possibleBlockers, selectedAttackers);
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

        // DAgger: log this target decision (with the expert/CP7-heuristic label)
        // BEFORE the student picks.  Mirrors CP7Instrumented's >=2-candidate gate.
        if (DAGGER_MODE && sortedPairs.size() >= 2) {
            logDaggerTarget(game, outcome, target, source, abilityControllerId,
                    possibleTargetsUUIDArray, possibleTargetsArray);
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
        // DAgger: log the pre-blocker state, then the declared blockers, mirroring
        // ComputerPlayer7Instrumented.  CP8's blocker logic IS the CP6 heuristic
        // (super.selectBlockers), so the student's own declaration is the label.
        if (DAGGER_MODE) {
            logDaggerBlockersPre(game, source, defendingPlayerId);
        }
        long _t0 = System.nanoTime();
        super.selectBlockers(source, game, defendingPlayerId);
        DecisionStats.INSTANCE.recordLocalBlockers(System.nanoTime() - _t0);
        if (DAGGER_MODE) {
            logDaggerBlockersResult(game, source);
        }
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
        // CRITICAL FIX (2026-06-12): the CP6 COPY constructor copies maxDepth
        // but NOT maxThinkTimeSecs / maxNodes — both stayed 0, so every
        // shadow alpha-beta think timed out instantly (task.get(0, SECONDS))
        // and/or aborted at the first node (nodeCount > 0), making the shadow
        // ALWAYS "pass".  Observed live: 49,556/49,556 probe rows with
        // matched_index==0 and 100% pass DAgger labels.  Give the shadow the
        // same budget a real CP7 of this skill gets (named ctor: skill*3 s).
        shadow.maxThinkTimeSecs = Math.max(shadow.maxThinkTimeSecs, maxDepth * 3);
        // 5000 = CP6.MAX_SIMULATED_NODES_PER_CALC (private there).
        shadow.maxNodes = Math.max(shadow.maxNodes, 5000);
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
            if (!DAGGER_MODE && ThreadLocalRandom.current().nextDouble() >= CP7_SHADOW_RATE) {
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
            if (DAGGER_MODE) {
                // Collection mode (student acted): the shadow choice IS the
                // training label.  We ALWAYS log multi-action priority
                // decisions now.  Previously an unmatched shadow choice
                // (matchedIndex == null) was silently dropped, discarding a
                // learnable priority decision on every serialization mismatch.
                // Instead, when matched we log the exact RL action (guarantees
                // serialization parity); when unmatched we log the shadow
                // ability directly and let the Python parser's fuzzy
                // match_chosen_to_available recover the index.
                if (matchedIndex == null) {
                    logger.debug("DAgger: shadow choice '" + cp7Text
                            + "' not in RL action list (" + allActions.size()
                            + " actions) - logging shadow ability for fuzzy match");
                }
                logDaggerPriority(game, allActions, cp7Choice, matchedIndex);
                return;
            }
            postShadowAgreement(game, "priority",
                    allActions.get(rlChosenIndex).toString(), rlChosenIndex,
                    cp7Text, matchedIndex, allActions.size());
        } catch (Throwable t) {
            logger.warn("CP7 shadow probe (priority) failed - ignored", t);
        }
    }

    /**
     * DAgger: emit the CP7Instrumented-style (priority, priority_result) event
     * pair with the SHADOW choice as the chosen action.  Payloads are built
     * synchronously (the game state must be captured before the RL action
     * executes); the HTTP posts are async best-effort.
     */
    private void logDaggerPriority(Game game, List<Ability> allActions,
            Ability cp7Choice, Integer matchedIndex) {
        try {
            Map<String, Object> triggerCtx = newDaggerContext();
            JSONObject trigger = decisionHandler.buildTrajectoryPayload(
                    game, this, "priority", allActions, null, triggerCtx);

            boolean passed = cp7Choice == null || cp7Choice instanceof PassAbility
                    || (matchedIndex != null && matchedIndex == 0);
            Map<String, Object> chosenAction = new java.util.HashMap<>();
            chosenAction.put("result", true);
            chosenAction.put("step_type", game.getTurnStepType() != null
                    ? game.getTurnStepType().toString() : "");
            chosenAction.put("passed", passed);
            List<Object> chosenList = new ArrayList<>();
            if (!passed) {
                // matched -> log the exact RL action (serialization parity);
                // unmatched -> log the shadow ability and rely on the parser's
                // fuzzy description match to recover the index downstream.
                if (matchedIndex != null) {
                    chosenList.add(allActions.get(matchedIndex));
                } else if (cp7Choice != null) {
                    chosenList.add(cp7Choice);
                }
            }
            chosenAction.put("chosen_actions", chosenList);
            chosenAction.put("actions_taken", chosenList.size());
            chosenAction.put("available_actions_count", allActions.size());
            Map<String, Object> resultCtx = newDaggerContext();
            JSONObject result = decisionHandler.buildTrajectoryPayload(
                    game, this, "priority_result", null, chosenAction, resultCtx);

            sendDaggerTrajectory(trigger);
            sendDaggerTrajectory(result);
        } catch (Throwable t) {
            logger.warn("DAgger priority trajectory logging failed - ignored", t);
        }
    }

    /**
     * Fresh additionalContext map carrying DAgger provenance + ordering seq.
     * CP8 only acts in STUDENT-pilot games (the per-game draw assigned CP8 as
     * PlayerA), so dagger_pilot is always "student" here; dagger_fraction is
     * the mixture parameter P(student game) in effect at spawn — both are
     * needed for later weighting/ablation and to audit the realized mix.
     */
    private Map<String, Object> newDaggerContext() {
        Map<String, Object> ctx = new java.util.HashMap<>();
        ctx.put("dagger", true);
        ctx.put("dagger_pilot", "student");
        ctx.put("dagger_fraction", DAGGER_FRACTION);
        ctx.put("loggedAt", System.currentTimeMillis());
        ctx.put("log_seq", daggerLogSeq++);
        return ctx;
    }

    /**
     * Async best-effort POST of a DAgger trajectory payload.  The JVM-wide
     * -Dmagellmfast.logTrajectory=false (which suppresses the inference
     * server's RL-side records) is overridden per-payload here: these explicit
     * shadow-labeled posts are the ONLY trajectory records DAgger games emit.
     */
    private void sendDaggerTrajectory(JSONObject payload) {
        try {
            payload.put("logTrajectory", true);
        } catch (Exception e) {
            logger.warn("DAgger: could not stamp logTrajectory=true: " + e.getMessage());
            return;
        }
        Thread.ofVirtual().start(() -> decisionHandler.postTrajectory(payload));
    }

    /**
     * Shadow probe for the attackers declaration. CP7's LIVE attacker behavior is
     * the CP6 heuristic (selectAttackers -> declareAttackers), NOT the alpha-beta
     * tree: calculateActions at the declare-attackers point leaves shadow.combat
     * null (observed live: 100% empty CP7 attacker sets). So run the real
     * heuristic on a simulation copy and read the declared attackers from the
     * SIM's combat — the live game is never mutated.
     * Convention: rl_choice_index = 0, matched_index = 0 on agreement else null.
     */
    private void shadowProbeAttackers(Game game, List<Permanent> possibleAttackers,
            List<Permanent> possibleBlockers, List<Permanent> rlSelectedAttackers) {
        try {
            if (!DAGGER_MODE && ThreadLocalRandom.current().nextDouble() >= CP7_SHADOW_RATE) {
                return;
            }
            ComputerPlayer7 shadow = newShadowCp7();
            Game sim = shadow.createSimulation(game);
            RandomUtil.enterSimulation();
            try {
                shadow.selectAttackers(sim, playerId);
            } finally {
                RandomUtil.exitSimulation();
            }
            if (DAGGER_MODE) {
                // Collection mode (student acted): the shadow's attacker set
                // IS the training label (ids are stable across the sim copy).
                List<UUID> cp7AttackerIds = new ArrayList<>(sim.getCombat().getAttackers());
                logDaggerAttackers(game, possibleAttackers, possibleBlockers, cp7AttackerIds);
                return;
            }
            List<String> cp7Names = new ArrayList<>();
            for (UUID attackerId : sim.getCombat().getAttackers()) {
                // ids are stable across the copy; resolve names on the live game
                Permanent permanent = game.getPermanent(attackerId);
                cp7Names.add(permanent != null ? permanent.getName() : attackerId.toString());
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

    /**
     * DAgger: emit the CP7Instrumented-style (attackers, attackers_result)
     * event pair with the SHADOW's declared attacker set as the label.
     * Context/result shapes mirror ComputerPlayer7Instrumented exactly so
     * parse_trajectories.py consumes these files unchanged.
     */
    private void logDaggerAttackers(Game game, List<Permanent> possibleAttackers,
            List<Permanent> possibleBlockers, List<UUID> cp7AttackerIds) {
        try {
            List<Map<String, Object>> atksJson = new ArrayList<>();
            for (Permanent atk : possibleAttackers) {
                atksJson.add(daggerPermanentToSimpleMap(atk));
            }
            List<Map<String, Object>> blksJson = new ArrayList<>();
            for (Permanent blk : possibleBlockers) {
                blksJson.add(daggerPermanentToSimpleMap(blk));
            }
            Map<String, Object> triggerCtx = newDaggerContext();
            triggerCtx.put("possibleAttackers", atksJson);
            triggerCtx.put("possibleBlockers", blksJson);
            JSONObject trigger = decisionHandler.buildTrajectoryPayload(
                    game, this, "attackers", null, null, triggerCtx);

            Map<String, Object> chosenAction = new java.util.HashMap<>();
            chosenAction.put("declared_attackers", cp7AttackerIds.size());
            List<String> attackerUuids = new ArrayList<>();
            for (UUID id : cp7AttackerIds) {
                attackerUuids.add(id.toString());
            }
            chosenAction.put("attacker_uuids", attackerUuids);
            Map<String, Object> resultCtx = newDaggerContext();
            JSONObject result = decisionHandler.buildTrajectoryPayload(
                    game, this, "attackers_result", null, chosenAction, resultCtx);

            sendDaggerTrajectory(trigger);
            sendDaggerTrajectory(result);
        } catch (Throwable t) {
            logger.warn("DAgger attackers trajectory logging failed - ignored", t);
        }
    }

    /**
     * Serialize a Permanent for the attackers trajectory context.  Field-for-
     * field copy of ComputerPlayer7Instrumented.permanentToSimpleMap (private
     * there; CP8 extends CP7, not CP7Instrumented) so the Python Permanent
     * pydantic model parses DAgger and mageai records identically.
     */
    private Map<String, Object> daggerPermanentToSimpleMap(Permanent p) {
        Map<String, Object> m = new java.util.HashMap<>();
        m.put("permanentId", p.getId().toString());
        m.put("id", p.getId().toString());
        m.put("name", p.getName());
        m.put("power", p.getPower() != null ? p.getPower().getValue() : 0);
        m.put("toughness", p.getToughness() != null ? p.getToughness().getValue() : 0);
        m.put("abilities", p.getAbilities().stream()
                .map(a -> a.getRule())
                .filter(r -> r != null && !r.isEmpty())
                .collect(java.util.stream.Collectors.joining("; ")));
        m.put("cardType", p.getCardType() != null ? p.getCardType().toString() : "");
        m.put("supertype", p.getSuperType() != null ? p.getSuperType().toString() : "");
        m.put("subtype", p.getSubtype() != null ? p.getSubtype().toString() : "");
        m.put("color", p.getColor() != null ? p.getColor().toString() : "");
        m.put("manaCost", p.getManaCost() != null ? p.getManaCost().toString() : "");
        m.put("ownerId", p.getOwnerId() != null ? p.getOwnerId().toString() : "");
        return m;
    }

    /**
     * DAgger: emit the CP7Instrumented-style (choice, choice_result) pair for a
     * general {@link Choice} decision.  The expert/teacher label is computed by
     * running the base CP heuristic ({@code super.choose}) on a COPY of the
     * choice so the live choice is never mutated; the run is wrapped in
     * RandomUtil.enterSimulation()/exitSimulation() because the heuristic may
     * fall back to a random pick that would otherwise advance the live game's
     * RNG sequence.  Only logged when there are >=2 options (single-option
     * "choices" are not learnable decisions).  Best-effort: any failure is
     * swallowed so a logging problem can never abort the game.
     */
    private void logDaggerChoice(Game game, Outcome outcome, Choice choice) {
        try {
            if (choice == null || choice.isChosen()
                    || choice.getChoices() == null || choice.getChoices().size() < 2) {
                return;
            }
            // Expert label on an isolated copy (no live mutation, RNG-guarded).
            String expert = null;
            try {
                Choice copy = choice.copy();
                copy.clearChoice();
                RandomUtil.enterSimulation();
                try {
                    super.choose(outcome, copy, game);
                } finally {
                    RandomUtil.exitSimulation();
                }
                expert = copy.getChoice();
            } catch (Throwable t) {
                logger.debug("DAgger choice expert probe failed - logging without label: "
                        + t.getMessage());
            }

            Map<String, Object> available = new java.util.HashMap<>();
            available.put("outcome", outcome != null ? outcome.toString() : "");
            available.put("choice_type", choice.getClass().getSimpleName());
            available.put("message", choice.getMessage());
            available.put("choices", choice.getChoices());
            Map<String, Object> triggerCtx = newDaggerContext();
            JSONObject trigger = decisionHandler.buildTrajectoryPayload(
                    game, this, "choice", available, null, triggerCtx);

            Map<String, Object> chosenAction = new java.util.HashMap<>();
            chosenAction.put("chosen", expert);
            chosenAction.put("result", expert != null);
            Map<String, Object> resultCtx = newDaggerContext();
            JSONObject result = decisionHandler.buildTrajectoryPayload(
                    game, this, "choice_result", null, chosenAction, resultCtx);

            sendDaggerTrajectory(trigger);
            sendDaggerTrajectory(result);
        } catch (Throwable t) {
            logger.warn("DAgger choice trajectory logging failed - ignored", t);
        }
    }

    /**
     * DAgger: emit the CP7Instrumented-style (target, target_result) pair for a
     * target selection.  The available_actions list mirrors CP7Instrumented's
     * "Target" Action-like shape (alphabetically sorted candidates).  The expert
     * label is the CP7 heuristic's target choice, computed by running a shadow
     * CP7 chooseTarget on a SIMULATION copy of the game + a copy of the target
     * (so neither the live game nor the live target is mutated; addTarget fires
     * its TARGET/TARGETED events only on the throwaway sim).  ids are stable
     * across the sim copy, so the chosen ids map back to the live candidate list.
     */
    private void logDaggerTarget(Game game, Outcome outcome, Target target, Ability source,
            UUID abilityControllerId, UUID[] candidateUuids, MageObject[] candidateObjects) {
        try {
            // available_actions: one map per candidate, CP7Instrumented format.
            List<Map<String, Object>> availableActions = new ArrayList<>();
            for (int i = 0; i < candidateUuids.length; i++) {
                Map<String, Object> actionMap = new java.util.HashMap<>();
                actionMap.put("description",
                        candidateObjects[i] != null ? candidateObjects[i].toString() : "unknown");
                actionMap.put("sourceId", candidateUuids[i].toString());
                actionMap.put("abilityType", "Target");
                actionMap.put("className", target.getClass().getSimpleName());
                actionMap.put("costs", "[]");
                actionMap.put("targets", "[]");
                availableActions.add(actionMap);
            }

            // Expert label: shadow CP7 targeting on an isolated simulation copy.
            List<UUID> expertChosen = new ArrayList<>();
            try {
                ComputerPlayer7 shadow = newShadowCp7();
                Game sim = shadow.createSimulation(game);
                Target targetCopy = target.copy();
                targetCopy.clearChosen();
                RandomUtil.enterSimulation();
                try {
                    shadow.chooseTarget(outcome, targetCopy, source, sim);
                } finally {
                    RandomUtil.exitSimulation();
                }
                expertChosen.addAll(targetCopy.getTargets());
            } catch (Throwable t) {
                logger.debug("DAgger target expert probe failed - logging without label: "
                        + t.getMessage());
            }

            Map<String, Object> triggerCtx = newDaggerContext();
            triggerCtx.put("targetName", target.getTargetName());
            triggerCtx.put("targetType", target.getClass().getSimpleName());
            triggerCtx.put("outcome", outcome != null ? outcome.toString() : "");
            if (source != null) {
                triggerCtx.put("sourceAbility", source.toString());
                if (source.getSourceId() != null) {
                    triggerCtx.put("sourceId", source.getSourceId().toString());
                }
            }
            JSONObject trigger = decisionHandler.buildTrajectoryPayload(
                    game, this, "target", availableActions, null, triggerCtx);

            // Map expert-chosen ids back to indices in the (live) candidate list.
            List<String> chosenDescriptions = new ArrayList<>();
            List<String> chosenUuids = new ArrayList<>();
            List<Integer> chosenIndices = new ArrayList<>();
            for (UUID chosen : expertChosen) {
                int idx = -1;
                String desc = "unknown";
                for (int i = 0; i < candidateUuids.length; i++) {
                    if (candidateUuids[i].equals(chosen)) {
                        idx = i;
                        desc = candidateObjects[i] != null ? candidateObjects[i].toString() : "unknown";
                        break;
                    }
                }
                chosenDescriptions.add(desc);
                chosenUuids.add(chosen.toString());
                chosenIndices.add(idx);
            }

            Map<String, Object> chosenAction = new java.util.HashMap<>();
            chosenAction.put("chosen_descriptions", chosenDescriptions);
            chosenAction.put("chosen_uuids", chosenUuids);
            chosenAction.put("chosen_indices", chosenIndices);
            chosenAction.put("result", !expertChosen.isEmpty());
            Map<String, Object> resultCtx = newDaggerContext();
            JSONObject result = decisionHandler.buildTrajectoryPayload(
                    game, this, "target_result", null, chosenAction, resultCtx);

            sendDaggerTrajectory(trigger);
            sendDaggerTrajectory(result);
        } catch (Throwable t) {
            logger.warn("DAgger target trajectory logging failed - ignored", t);
        }
    }

    /**
     * DAgger: emit the CP7Instrumented-style "blockers" pre-decision event with
     * the available blockers, attacking creatures, and defending player.  CP8's
     * blocker selection is the CP6 heuristic, so the student's own declaration
     * (read back in {@link #logDaggerBlockersResult}) is the label.
     */
    private void logDaggerBlockersPre(Game game, Ability source, UUID defendingPlayerId) {
        try {
            List<Map<String, Object>> availableBlockers = new ArrayList<>();
            for (Permanent blocker : super.getAvailableBlockers(game)) {
                availableBlockers.add(daggerPermanentToSimpleMap(blocker));
            }
            List<Map<String, Object>> attackingCreatures = new ArrayList<>();
            if (game.getCombat() != null) {
                for (UUID atkId : game.getCombat().getAttackers()) {
                    Permanent atk = game.getPermanent(atkId);
                    if (atk != null) {
                        attackingCreatures.add(daggerPermanentToSimpleMap(atk));
                    }
                }
            }
            Map<String, Object> available = new java.util.HashMap<>();
            available.put("available_blockers", availableBlockers);
            available.put("attacking_creatures", attackingCreatures);
            available.put("defending_player",
                    defendingPlayerId != null ? defendingPlayerId.toString() : "");

            Map<String, Object> triggerCtx = newDaggerContext();
            if (source != null) {
                triggerCtx.put("sourceAbility", source.toString());
                if (source.getSourceId() != null) {
                    triggerCtx.put("sourceId", source.getSourceId().toString());
                }
            }
            JSONObject trigger = decisionHandler.buildTrajectoryPayload(
                    game, this, "blockers", available, null, triggerCtx);
            sendDaggerTrajectory(trigger);
        } catch (Throwable t) {
            logger.warn("DAgger blockers (pre) trajectory logging failed - ignored", t);
        }
    }

    /** DAgger: emit the "blockers_result" event after the student declares blockers. */
    private void logDaggerBlockersResult(Game game, Ability source) {
        try {
            Map<String, Object> chosenAction = new java.util.HashMap<>();
            chosenAction.put("declared_blockers",
                    game.getCombat() != null ? game.getCombat().getBlockers().size() : 0);
            Map<String, Object> resultCtx = newDaggerContext();
            if (source != null) {
                resultCtx.put("sourceAbility", source.toString());
                if (source.getSourceId() != null) {
                    resultCtx.put("sourceId", source.getSourceId().toString());
                }
            }
            JSONObject result = decisionHandler.buildTrajectoryPayload(
                    game, this, "blockers_result", null, chosenAction, resultCtx);
            sendDaggerTrajectory(result);
        } catch (Throwable t) {
            logger.warn("DAgger blockers (result) trajectory logging failed - ignored", t);
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
