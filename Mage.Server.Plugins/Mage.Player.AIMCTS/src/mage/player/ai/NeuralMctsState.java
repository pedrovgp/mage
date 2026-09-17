package mage.player.ai;

import mage.abilities.Ability;
import mage.abilities.ActivatedAbility;
import mage.abilities.Mode;
import mage.abilities.common.PassAbility;
import mage.cards.Card;
import mage.cards.Cards;
import mage.constants.PhaseStep;
import mage.constants.Zone;
import mage.game.Game;
import mage.game.combat.CombatGroup;
import mage.game.match.MatchPlayer;
import mage.game.turn.Step.StepPart;
import mage.players.Player;
import mage.target.Target;
import mage.util.RandomUtil;
import org.json.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** XMage adapter. Nodes stop at priority/attack/block boundaries, never live games. */
final class NeuralMctsState implements NeuralMctsSearch.State {
    static final int MAX_ACTIONS = 4096;
    final Game game;
    final UUID rootPlayer;
    final UUID actor;
    final MCTSPlayer.NextAction kind;
    final long seed, deadline;
    final String checkpoint;
    private final DecisionHandler serializer;
    private List<Move> moves;
    int rejectedCandidates;

    static final class SearchPlayer extends MCTSPlayer {
        private final long deadline;
        SearchPlayer(Player original, long deadline) {
            super(original.getId());
            restore(original.getRealPlayer());
            if (original.getMatchPlayer() != null) setMatchPlayer(new MatchPlayer(original.getMatchPlayer(), this));
            this.deadline = deadline;
        }
        SearchPlayer(SearchPlayer original) { super(original); deadline = original.deadline; }
        @Override public SearchPlayer copy() { return new SearchPlayer(this); }
        void checkDeadline() {
            if (System.nanoTime() >= deadline || Thread.currentThread().isInterrupted())
                throw new NeuralMctsSearch.DeadlineExceeded();
        }
        @Override public boolean priority(Game game) { checkDeadline(); return super.priority(game); }
        @Override public void selectAttackers(Game game, UUID player) { checkDeadline(); super.selectAttackers(game, player); }
        @Override public void selectBlockers(Ability source, Game game, UUID player) { checkDeadline(); super.selectBlockers(source, game, player); }
        @Override public List<List<UUID>> getAttacks(Game game) {
            // Do not allocate an unbounded powerset or silently prune legal moves.
            if (getAvailableAttackers(game).size() > 12) throw new SearchAbort("combat candidate limit");
            return super.getAttacks(game);
        }
        @Override public List<List<List<UUID>>> getBlocks(Game game) {
            double count = Math.pow(game.getCombat().getGroups().size() + 1, getAvailableBlockers(game).size());
            if (count > MAX_ACTIONS) throw new SearchAbort("combat candidate limit");
            return super.getBlocks(game);
        }
    }
    /** Error escapes XMage's catch(Exception) recovery, which otherwise hides timeouts. */
    static final class SearchAbort extends Error {
        SearchAbort(String message) { super(message); }
    }

    static final class Move {
        final Ability ability;
        final Map<UUID, UUID> attacks;
        final Map<UUID, UUID> blocks;
        final String key, fingerprint;
        Move(Ability ability, Map<UUID, UUID> attacks, Map<UUID, UUID> blocks) {
            this.ability = ability; this.attacks = attacks; this.blocks = blocks;
            if (ability instanceof PassAbility) key = "priority:pass";
            else if (ability != null) key = abilityKey(ability);
            else key = attacks != null ? "attack:" + new TreeMap<>(attacks) : "block:" + new TreeMap<>(blocks);
            fingerprint = hash(key);
        }
        private static String abilityKey(Ability ability) {
            // Target.toString() contains Java identity hashes, not selected targets.
            StringBuilder key = new StringBuilder("priority:").append(ability.getSourceId())
                    .append(':').append(ability.getId()).append(':').append(ability.getRule())
                    .append(':').append(ability.getManaCostsToPay().getText())
                    .append(':').append(ability.getModes().getSelectedModes());
            for (Mode mode : new TreeMap<>(ability.getModes()).values()) {
                key.append(":mode:").append(mode.getId());
                for (Target target : mode.getTargets()) {
                    key.append(':').append(target.getClass().getName());
                    for (UUID id : target.getTargets()) key.append(':').append(id).append('=')
                            .append(target.getTargetAmount(id));
                }
            }
            return key.toString();
        }
        void apply(Game game, UUID actor) {
            Player player = game.getPlayer(actor);
            if (ability != null) {
                boolean success = player.activateAbility((ActivatedAbility) ability.copy(), game);
                if (!success && !(ability instanceof PassAbility)) throw new SearchAbort("ability activation rejected: " + key);
            } else if (attacks != null) {
                for (Map.Entry<UUID, UUID> a : attacks.entrySet()) {
                    player.declareAttacker(a.getKey(), a.getValue(), game, false);
                    if (!game.getCombat().getAttackers().contains(a.getKey())) throw new SearchAbort("attacker rejected");
                }
            } else {
                for (Map.Entry<UUID, UUID> b : blocks.entrySet()) {
                    player.declareBlocker(actor, b.getKey(), b.getValue(), game);
                    if (!game.getCombat().findGroup(b.getValue()).getBlockers().contains(b.getKey()))
                        throw new SearchAbort("blocker rejected");
                }
            }
        }
        boolean legalCombat(Game original, UUID actor) {
            Game probe = original.createSimulationForAI();
            try { apply(probe, actor); } catch (SearchAbort rejected) { return false; }
            String before = combatKey(probe);
            Player player = probe.getPlayer(actor);
            boolean legal = attacks != null ? probe.getCombat().checkAttackRestrictions(player, probe)
                    : probe.getCombat().checkBlockRestrictions(player, probe)
                    && probe.getCombat().checkBlockRequirementsAfter(player, player, probe)
                    && probe.getCombat().checkBlockRestrictionsAfter(player, player, probe);
            // Some XMage AI checks repair an invalid declaration and return true.
            return legal && before.equals(combatKey(probe));
        }
        boolean legalPriority(Game original, UUID actor, long childSeed) {
            if (ability instanceof PassAbility) return true;
            try (RandomUtil.RandomScope scope = RandomUtil.searchScope(childSeed)) {
                Game probe = original.createSimulationForAI();
                int errors = probe.getTotalErrorsCount();
                try { apply(probe, actor); }
                catch (SearchAbort rejected) { return false; }
                if (probe.getTotalErrorsCount() != errors) throw new SearchAbort("activation probe engine error: " + key);
                return true;
            }
        }
        long activationSeed() {
            // Auxiliary payment choices are part of replay, not of the sampled
            // hidden world. Fireblast's alternative-cost chooser uses RNG: using
            // the world seed here randomly removes different target variants.
            return Long.parseUnsignedLong(fingerprint.substring(0, 16), 16);
        }
        private static String combatKey(Game game) {
            Map<UUID, String> groups = new TreeMap<>();
            for (CombatGroup group : game.getCombat().getGroups())
                for (UUID attacker : group.getAttackers()) groups.put(attacker,
                        group.getDefenderId() + ":" + new TreeSet<>(group.getBlockers()));
            return groups.toString();
        }
        JSONObject payload(DecisionHandler serializer) {
            JSONObject result = new JSONObject().put("key", key).put("fingerprint", fingerprint);
            result.put("action", ability == null ? new JSONObject() : serializer.serializeSearchAbility(ability));
            return result;
        }
    }

    static String hash(String text) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte b : bytes) out.append(String.format("%02x", b & 255));
            return out.toString();
        } catch (Exception exc) { throw new IllegalStateException(exc); }
    }

    static NeuralMctsState root(Game live, UUID player, MCTSPlayer.NextAction kind, long seed,
                                long deadline, String checkpoint, DecisionHandler serializer) {
        if (live.getPlayers().size() != 2) throw new SearchAbort("neural MCTS supports two-player games only");
        try (RandomUtil.RandomScope scope = RandomUtil.searchScope(seed)) {
            Game copy = live.createSimulationForAI();
            // Uniform known-deck sampling does not model unknown morph/exile identities.
            for (Card card : copy.getCards()) if (card.isFaceDown(copy))
                throw new SearchAbort("face-down hidden objects require belief sampling");
            Set<UUID> known = new HashSet<>();
            for (Cards cards : copy.getState().getRevealed().values()) known.addAll(cards);
            for (Cards cards : copy.getState().getLookedAt(player).values()) known.addAll(cards);
            JSONObject view = serializer.buildSearchObservation(copy, copy.getPlayer(player)).getJSONObject("game_view");
            for (Player original : new ArrayList<>(copy.getPlayers().values())) {
                SearchPlayer simulation = new SearchPlayer(original, deadline);
                JSONObject playerView = view.getJSONObject(original.getId().equals(player) ? "myPlayer" : "opponentPlayer");
                JSONObject top = playerView.optJSONObject("topCard");
                Card visibleTop = top == null ? null : copy.getCard(UUID.fromString(top.getString("id")));
                if (visibleTop != null) simulation.getLibrary().remove(visibleTop.getId(), copy);
                if (!original.getId().equals(player)) {
                    // KNOWN-DECK benchmark mode only. No draw order or hand assignment retained.
                    int handSize = simulation.getHand().size();
                    for (Card card : new ArrayList<>(simulation.getHand().getCards(copy))) {
                        if (known.contains(card.getId())) continue;
                        simulation.getHand().remove(card);
                        simulation.getLibrary().putOnBottom(card, copy);
                    }
                    canonicalShuffle(simulation, copy);
                    while (simulation.getHand().size() < handSize) {
                        Card card = simulation.getLibrary().drawFromTop(copy);
                        if (card == null) throw new SearchAbort("invalid determinization");
                        card.setZone(Zone.HAND, copy);
                        simulation.getHand().add(card);
                    }
                } else canonicalShuffle(simulation, copy);
                if (visibleTop != null) simulation.getLibrary().putOnTop(visibleTop, copy);
                copy.getState().getPlayers().put(simulation.getId(), simulation);
            }
            ((SearchPlayer) copy.getPlayer(player)).setNextAction(kind);
            copy.pause();
            return new NeuralMctsState(copy, player, player, kind, seed, deadline, checkpoint, serializer);
        }
    }

    private static void canonicalShuffle(Player player, Game game) {
        // A fixed search seed must not depend on the actual secret library order.
        List<UUID> ids = player.getLibrary().getCardList();
        ids.sort(Comparator.comparing((UUID id) -> game.getCard(id).getName()).thenComparing(id -> id));
        player.getLibrary().clear();
        for (UUID id : ids) player.getLibrary().putOnBottom(game.getCard(id), game);
        player.getLibrary().shuffle();
    }

    private NeuralMctsState(Game game, UUID rootPlayer, UUID actor, MCTSPlayer.NextAction kind,
                            long seed, long deadline, String checkpoint, DecisionHandler serializer) {
        this.game = game; this.rootPlayer = rootPlayer; this.actor = actor; this.kind = kind;
        this.seed = seed; this.deadline = deadline; this.checkpoint = checkpoint; this.serializer = serializer;
    }
    @Override public UUID actor() { return actor; }
    @Override public Double terminalValue() {
        if (!game.checkIfGameIsOver()) return null;
        if (game.getPlayer(rootPlayer).hasWon()) return 1.0;
        for (Player p : game.getPlayers().values()) if (p.hasWon()) return -1.0;
        return 0.0;
    }
    List<Move> moves() {
        if (moves != null) return moves;
        SearchPlayer player = (SearchPlayer) game.getPlayer(actor);
        player.checkDeadline();
        Map<String, Move> unique = new TreeMap<>();
        try (RandomUtil.RandomScope scope = RandomUtil.searchScope(seed)) {
            if (kind == MCTSPlayer.NextAction.PRIORITY) {
                for (Ability a : player.getPlayableOptions(game)) {
                    player.checkDeadline();
                    Move m = new Move(a.copy(), null, null);
                    // getPlayableOptions is advisory: some optional-cost/target
                    // variants still fail activation. Never create those edges.
                    if (m.legalPriority(game, actor, m.activationSeed())) unique.put(m.fingerprint, m);
                    else rejectedCandidates++;
                    if (unique.size() > MAX_ACTIONS) throw new SearchAbort("priority candidate limit");
                }
            } else if (kind == MCTSPlayer.NextAction.SELECT_ATTACKERS) {
                if (game.getCombat().getDefenders().size() != 1)
                    throw new SearchAbort("multiple attack destinations are not yet supported");
                UUID defender = game.getOpponents(actor).iterator().next();
                for (List<UUID> set : player.getAttacks(game)) {
                    Map<UUID, UUID> assignments = new LinkedHashMap<>();
                    for (UUID id : set) assignments.put(id, defender);
                    Move m = new Move(null, assignments, null);
                    player.checkDeadline();
                    if (m.legalCombat(game, actor)) unique.put(m.fingerprint, m);
                }
            } else {
                List<CombatGroup> groups = game.getCombat().getGroups();
                for (List<List<UUID>> set : player.getBlocks(game)) {
                    Map<UUID, UUID> assignments = new LinkedHashMap<>();
                    for (int i = 0; i < set.size(); i++)
                        for (UUID id : set.get(i)) assignments.put(id, groups.get(i).getAttackers().get(0));
                    Move m = new Move(null, null, assignments);
                    player.checkDeadline();
                    if (m.legalCombat(game, actor)) unique.put(m.fingerprint, m);
                }
                if (unique.isEmpty()) {
                    Move m = new Move(null, null, Collections.emptyMap());
                    if (m.legalCombat(game, actor)) unique.put(m.fingerprint, m);
                }
            }
        }
        moves = new ArrayList<>(unique.values());
        if (moves.isEmpty()) throw new SearchAbort("no supported legal actions");
        return moves;
    }
    @Override public List<String> actions() {
        List<String> keys = new ArrayList<>();
        for (Move m : moves()) keys.add(m.fingerprint);
        return keys;
    }
    @Override public NeuralMctsState next(int index) {
        long childSeed = childSeed(moves().get(index));
        try (RandomUtil.RandomScope scope = RandomUtil.searchScope(childSeed)) {
            Game child = game.createSimulationForAI();
            int errors = child.getTotalErrorsCount();
            try (RandomUtil.RandomScope activation = RandomUtil.searchScope(moves().get(index).activationSeed())) {
                moves().get(index).apply(child, actor);
            }
            child.resume();
            if (child.getTotalErrorsCount() != errors) throw new SearchAbort("simulation engine error");
            UUID nextActor = rootPlayer;
            MCTSPlayer.NextAction nextKind = MCTSPlayer.NextAction.PRIORITY;
            if (!child.checkIfGameIsOver()) {
                if (!child.isPaused()) throw new SearchAbort("simulation did not stop at a decision");
                if (child.getStep().getStepPart() == StepPart.PRIORITY) nextActor = child.getPriorityPlayerId();
                else if (child.getTurnStepType() == PhaseStep.DECLARE_BLOCKERS)
                    nextActor = child.getCombat().getDefenders().iterator().next();
                else nextActor = child.getActivePlayerId();
                nextKind = ((SearchPlayer) child.getPlayer(nextActor)).getNextAction();
                if (nextKind == null) throw new SearchAbort("missing simulation decision kind");
            }
            return new NeuralMctsState(child, rootPlayer, nextActor, nextKind, childSeed, deadline, checkpoint, serializer);
        }
    }
    private long childSeed(Move move) {
        return seed * 6364136223846793005L + Long.parseUnsignedLong(move.fingerprint.substring(0, 16), 16);
    }
    @Override public JSONObject request() {
        JSONObject request = serializer.buildSearchObservation(game, game.getPlayer(actor));
        request.put("schema_version", 1).put("checkpoint_id", checkpoint);
        request.put("decision_type", kind == MCTSPlayer.NextAction.PRIORITY ? "priority"
                : kind == MCTSPlayer.NextAction.SELECT_ATTACKERS ? "attackers" : "blockers");
        request.put("policy_temperature", Double.parseDouble(System.getProperty("neuralMcts.policyTemperature", "0.02")));
        JSONArray candidates = new JSONArray();
        for (Move m : moves()) candidates.put(m.payload(serializer));
        return request.put("all_actions", candidates);
    }
}
