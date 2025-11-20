package org.mage.test.serverside.base;

import mage.cards.decks.importer.DeckImporter;
import mage.cards.repository.CardScanner;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.junit.Assert.assertTrue;

public class ValidatePremodernDecksTest extends MageTestPlayerBase {

    private static final File DECKS_DIRECTORY = new File("premodern_decks");
    private static final int MIN_DECKS = 10;
    private static final String[] SUPPORTED_EXTENSIONS = new String[] { ".dck", ".txt" };

    @Test
    public void testDecks() {
        CardScanner.scan();
        if (!DECKS_DIRECTORY.exists() || !DECKS_DIRECTORY.isDirectory()) {
            throw new RuntimeException("Deck directory not found: " + DECKS_DIRECTORY.getAbsolutePath());
        }

        int deckCount = 0;
        List<String> failures = new ArrayList<>();

        for (File deckFile : Objects.requireNonNull(DECKS_DIRECTORY.listFiles())) {
            if (isSupportedDeckFile(deckFile)) {
                deckCount++;
                StringBuilder errors = new StringBuilder();
                DeckImporter.importDeckFromFile(deckFile.getAbsolutePath(), errors, false);
                if (errors.length() > 0) {
                    failures.add("Failed to load deck " + deckFile.getName() + ": " + errors);
                }
            }
        }

        assertTrue("Expected at least " + MIN_DECKS + " decks, found " + deckCount, deckCount >= MIN_DECKS);
        assertTrue(String.join(System.lineSeparator(), failures), failures.isEmpty());
    }

    private static boolean isSupportedDeckFile(File file) {
        for (String extension : SUPPORTED_EXTENSIONS) {
            if (file.getName().endsWith(extension)) {
                return true;
            }
        }
        return false;
    }
}
