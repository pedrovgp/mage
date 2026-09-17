package org.mage.test.serverside.base;

import mage.cards.Card;
import mage.constants.MultiplayerAttackOption;
import mage.constants.RangeOfInfluence;
import mage.game.TwoPlayerDuel;
import mage.game.mulligan.MulliganType;
import mage.players.Player;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Test-only paired-game setup. UUIDs are intentionally excluded from receipts. */
public class SeededBenchmarkGame extends TwoPlayerDuel {
    public final JSONObject receipt;

    public SeededBenchmarkGame(long seed) {
        super(MultiplayerAttackOption.LEFT, RangeOfInfluence.ONE,
                MulliganType.GAME_DEFAULT.getMulligan(0), 60, 20, 7);
        receipt = new JSONObject().put("engine_seed", seed).put("complete", false);
    }

    @Override protected void init(UUID choosingPlayerId) {
        // Deck loading uses fresh UUIDs. Canonical name order before Fisher-Yates
        // gives identical deals even if a collection's iteration order changes.
        for (Player player : getPlayers().values()) {
            List<Card> cards = new ArrayList<>(player.getLibrary().getCards(this));
            cards.sort(Comparator.comparing(Card::getName));
            player.getLibrary().clear();
            cards.forEach(card -> player.getLibrary().putOnBottom(card, this));
        }
        startingPlayerId = choosingPlayerId; // same seat starts in both arms
        super.init(choosingPlayerId);
        receipt.put("starting_player", getPlayer(startingPlayerId).getName());
        for (Player player : getPlayers().values()) {
            List<String> hand = new ArrayList<>(), library = new ArrayList<>();
            player.getHand().forEach(id -> hand.add(getCard(id).getName()));
            Collections.sort(hand);
            player.getLibrary().getCardList().forEach(id -> library.add(getCard(id).getName()));
            receipt.put(player.getName(), new JSONObject()
                    .put("hand_hash", hash(hand)).put("library_hash", hash(library))
                    .put("hand_size", hand.size()).put("library_size", library.size()));
        }
        receipt.put("complete", true);
        // Preserve the initial deal even if a later game hits its wall timeout.
        String metrics = System.getProperty("metrics_output_path");
        if (metrics != null) {
            try {
                java.nio.file.Path directory = java.nio.file.Paths.get(metrics);
                java.nio.file.Files.createDirectories(directory);
                java.nio.file.Files.writeString(directory.resolve("seed_receipt_" + getId() + ".json"),
                        receipt.toString(2) + "\n", StandardCharsets.UTF_8);
            } catch (java.io.IOException exc) {
                throw new IllegalStateException("cannot preserve seeded deal receipt", exc);
            }
        }
    }

    private static String hash(List<String> names) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(new org.json.JSONArray(names).toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte b : bytes) result.append(String.format("%02x", b & 0xff));
            return result.toString();
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
