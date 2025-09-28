package org.mage.test.serverside.base;

import mage.constants.PhaseStep;
import mage.constants.Zone;
import mage.game.permanent.Permanent;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.Assume;
import java.util.Set;
import java.util.HashSet;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

public class LLMPuzzles extends LLMPuzzlesBase {

    @Test
    public void test_MTGP_01_puzzle_llm_metrics() {
        beginPuzzle("test_MTGP_01_puzzle_llm_metrics", 1);

        // Setup MTGP_01 puzzle scenario (see https://mtgpuzzles.com/puzzle/1)
        // PlayerA (active): 5 life, hand: Banners Raised; Electrickery; Temporary
        // Insanity
        // Graveyard: Opt; Volcanic Spray
        // Battlefield: Mizzix of the Izmagnus (Tapped), Mountain, Mountain, Sulfur
        // Falls, Sulfur Falls
        // PlayerB: 4 life, battlefield: Nebelgast Herald, Treetop Ambusher

        // Set up PlayerA
        setLife(playerA, 5);
        addCard(Zone.HAND, playerA, "Banners Raised");
        addCard(Zone.HAND, playerA, "Electrickery");
        addCard(Zone.HAND, playerA, "Temporary Insanity");
        addCard(Zone.GRAVEYARD, playerA, "Opt");
        addCard(Zone.GRAVEYARD, playerA, "Volcanic Spray");
        addCard(Zone.BATTLEFIELD, playerA, "Mizzix of the Izmagnus", 1, true); // tapped
        addCard(Zone.BATTLEFIELD, playerA, "Mountain");
        addCard(Zone.BATTLEFIELD, playerA, "Mountain");
        addCard(Zone.BATTLEFIELD, playerA, "Sulfur Falls");
        addCard(Zone.BATTLEFIELD, playerA, "Sulfur Falls");

        // Set up PlayerB
        setLife(playerB, 4);
        addCard(Zone.BATTLEFIELD, playerB, "Nebelgast Herald");
        addCard(Zone.BATTLEFIELD, playerB, "Treetop Ambusher");

        setStrictChooseMode(false);

        // Run for one turn (as puzzle specifies "Win in 1 turn")
        setStopAt(1, PhaseStep.END_TURN);
        execute();

        // Wait for async ops
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("MTGP_01", 1);
    }

    @Test
    public void test_PS_M207_puzzle_llm_metrics() {
        // PS_M207.pzl: Possibility Storm - Magic Core Set 2020 #07
        // Goal: Win this turn. Your Dauntless Bodyguard chose Shanna, Sisay's Legacy
        // when it entered the battlefield.
        // humanlife=20
        // ailife=7
        // turn=1
        // activeplayer=human
        // activephase=MAIN1
        // humanhand=Strength of the Pack;Depose // Deploy;Storm the Citadel;Masterful
        // Replication;Short Sword
        // humanbattlefield=Gideon Blackblade|Counters:LOYALTY=6;Blackblade
        // Reforged;Omnispell Adept;Shanna, Sisay's Legacy|Id:9;Sigiled Sword of
        // Valeron|AttachedTo:9;Dauntless
        // Bodyguard|ChosenCards:9|Id:10|NoETBTrigs;Forebear's
        // Blade|AttachedTo:10;Hallowed Fountain|NoETBTrigs;Hallowed
        // Fountain|NoETBTrigs;Hallowed Fountain|NoETBTrigs;Temple
        // Garden|NoETBTrigs;Temple Garden|NoETBTrigs;Temple Garden|NoETBTrigs
        // aibattlefield=Charity Extractor;Looming Altisaur;Gate Colossus;Looming
        // Altisaur;Charity Extractor

        setLife(playerA, 20);
        setLife(playerB, 7);

        addCard(Zone.HAND, playerA, "Strength of the Pack");
        addCard(Zone.HAND, playerA, "Depose // Deploy");
        addCard(Zone.HAND, playerA, "Storm the Citadel");
        addCard(Zone.HAND, playerA, "Masterful Replication");
        addCard(Zone.HAND, playerA, "Short Sword");

        addCard(Zone.BATTLEFIELD, playerA, "Gideon Blackblade");
        addCounters(Zone.BATTLEFIELD, playerA, "Gideon Blackblade", CounterType.LOYALTY, 6);
        addCard(Zone.BATTLEFIELD, playerA, "Blackblade Reforged");
        addCard(Zone.BATTLEFIELD, playerA, "Omnispell Adept");
        addCard(Zone.BATTLEFIELD, playerA, "Shanna, Sisay's Legacy"); // ID:9
        addCard(Zone.BATTLEFIELD, playerA, "Sigiled Sword of Valeron"); // AttachedTo:9
        addCard(Zone.BATTLEFIELD, playerA, "Dauntless Bodyguard"); // ID:10, ChosenCards:9
        addCard(Zone.BATTLEFIELD, playerA, "Forebear's Blade"); // AttachedTo:10
        addCard(Zone.BATTLEFIELD, playerA, "Hallowed Fountain");
        addCard(Zone.BATTLEFIELD, playerA, "Hallowed Fountain");
        addCard(Zone.BATTLEFIELD, playerA, "Hallowed Fountain");
        addCard(Zone.BATTLEFIELD, playerA, "Temple Garden");
        addCard(Zone.BATTLEFIELD, playerA, "Temple Garden");
        addCard(Zone.BATTLEFIELD, playerA, "Temple Garden");

        addCard(Zone.BATTLEFIELD, playerB, "Charity Extractor");
        addCard(Zone.BATTLEFIELD, playerB, "Looming Altisaur");
        addCard(Zone.BATTLEFIELD, playerB, "Gate Colossus");
        addCard(Zone.BATTLEFIELD, playerB, "Looming Altisaur");
        addCard(Zone.BATTLEFIELD, playerB, "Charity Extractor");

        // Attach equipment as per .pzl
        runCode("Attach equipment", 1, PhaseStep.PRECOMBAT_MAIN, playerA, (info, player, game) -> {
            Permanent shanna = game.getBattlefield().getAllPermanents().stream()
                    .filter(p -> p.getName().equals("Shanna, Sisay's Legacy"))
                    .findFirst().orElse(null);
            Permanent sword = game.getBattlefield().getAllPermanents().stream()
                    .filter(p -> p.getName().equals("Sigiled Sword of Valeron"))
                    .findFirst().orElse(null);
            if (shanna != null && sword != null) {
                sword.attachTo(shanna.getId(), null, game);
            }

            Permanent bodyguard = game.getBattlefield().getAllPermanents().stream()
                    .filter(p -> p.getName().equals("Dauntless Bodyguard"))
                    .findFirst().orElse(null);
            Permanent blade = game.getBattlefield().getAllPermanents().stream()
                    .filter(p -> p.getName().equals("Forebear's Blade"))
                    .findFirst().orElse(null);
            if (bodyguard != null && blade != null) {
                blade.attachTo(bodyguard.getId(), null, game);
            }
        });

        execute();

        assertWonTheGame(playerA);
    }

    @Test
    public void test_MTGP_04_puzzle_llm_metrics() {
        beginPuzzle("test_MTGP_04_puzzle_llm_metrics", 1);

        // Setup MTGP_04 puzzle scenario (see https://mtgpuzzles.com/puzzle/4)
        // PlayerA (active): 5 life, hand: Dark Withering; Tahngarth's Rage; Alms of the
        // Vein
        // Battlefield: Frilled Deathspitter, Niblis of Dusk, Supreme Phantom,
        // Pyrohemia, Mountain x5, Watery Grave x2
        // PlayerB: 23 life, battlefield: Jin-Gitaxias, Core Augur

        // Set up PlayerA
        setLife(playerA, 5);
        addCard(Zone.HAND, playerA, "Dark Withering");
        addCard(Zone.HAND, playerA, "Tahngarth's Rage");
        addCard(Zone.HAND, playerA, "Alms of the Vein");
        addCard(Zone.BATTLEFIELD, playerA, "Frilled Deathspitter");
        addCard(Zone.BATTLEFIELD, playerA, "Niblis of Dusk");
        addCard(Zone.BATTLEFIELD, playerA, "Supreme Phantom");
        addCard(Zone.BATTLEFIELD, playerA, "Pyrohemia");
        addCard(Zone.BATTLEFIELD, playerA, "Mountain");
        addCard(Zone.BATTLEFIELD, playerA, "Mountain");
        addCard(Zone.BATTLEFIELD, playerA, "Mountain");
        addCard(Zone.BATTLEFIELD, playerA, "Mountain");
        addCard(Zone.BATTLEFIELD, playerA, "Mountain");
        addCard(Zone.BATTLEFIELD, playerA, "Watery Grave");
        addCard(Zone.BATTLEFIELD, playerA, "Watery Grave");

        // Set up PlayerB
        setLife(playerB, 23);
        addCard(Zone.BATTLEFIELD, playerB, "Jin-Gitaxias, Core Augur");

        setStrictChooseMode(false);

        // Run for one turn (as puzzle specifies "Win before opponent's next turn")
        setStopAt(1, PhaseStep.END_TURN);
        execute();

        // Wait for async ops
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("MTGP_04", 1);
    }

    @Test
    public void test_MTGP_06_puzzle_llm_metrics() {
        beginPuzzle("test_MTGP_06_puzzle_llm_metrics", 1);

        // Setup MTGP_06 puzzle scenario (see https://mtgpuzzles.com/puzzle/6)
        // PlayerA (active): 10 life, hand: Blazing Salvo
        // Battlefield: Oreskos Sun Guide (tapped), Nocturnal Feeder, Axis of Mortality,
        // Triskaidekaphobia, Savai Triome x3 (tapped)
        // PlayerB: 14 life, battlefield: Bogardan Firefiend

        // Set up PlayerA
        setLife(playerA, 10);
        addCard(Zone.HAND, playerA, "Blazing Salvo");
        addCard(Zone.BATTLEFIELD, playerA, "Oreskos Sun Guide", 1, true); // tapped
        addCard(Zone.BATTLEFIELD, playerA, "Nocturnal Feeder");
        addCard(Zone.BATTLEFIELD, playerA, "Axis of Mortality");
        addCard(Zone.BATTLEFIELD, playerA, "Triskaidekaphobia");
        addCard(Zone.BATTLEFIELD, playerA, "Savai Triome", 3, true); // three tapped copies

        // Set up PlayerB
        setLife(playerB, 14);
        addCard(Zone.BATTLEFIELD, playerB, "Bogardan Firefiend");

        setStrictChooseMode(false);

        // Run for one turn (puzzle specifies "Win this turn")
        setStopAt(1, PhaseStep.END_TURN);
        execute();

        // Wait for async ops
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("MTGP_06", 1);
    }

    @Test
    public void test_MTGP_02_puzzle_llm_metrics() {
        // MTGP_02.pzl: MTG Puzzles #02 - It's a Trap!
        // Goal: Survive until your next turn. It's your opponents turn and they have
        // attacked with 5 Construct tokens! It's their declare blockers step, can you
        // find a way to survive this turn? Don't forget about Throne of the
        // God-Pharaoh!
        // turn=1
        // activeplayer=p1
        // activephase=MAIN1
        // activephaseadvance=COMBAT_DECLARE_BLOCKERS
        // p0life=7
        // p0landsplayed=0
        // p0landsplayedlastturn=0
        // p0hand=Arrow Volley Trap;Pitfall Trap;Inferno Trap
        // p0battlefield=Viashino Fangtail;Rockslide
        // Sorcerer;Mountain;Mountain;Plains;Plains
        // p1life=20
        // p1landsplayed=0
        // p1landsplayedlastturn=0
        // p1battlefield=Throne of the
        // God-Pharaoh;T:c_4_4_a_construct;T:c_4_4_a_construct;T:c_4_4_a_construct;T:c_4_4_a_construct;T:c_4_4_a_construct

        beginPuzzle("test_MTGP_02_puzzle_llm_metrics", 1);

        // Set up PlayerA (p0 in .pzl)
        setLife(playerA, 7);
        addCard(Zone.HAND, playerA, "Arrow Volley Trap");
        addCard(Zone.HAND, playerA, "Pitfall Trap");
        addCard(Zone.HAND, playerA, "Inferno Trap");
        addCard(Zone.BATTLEFIELD, playerA, "Viashino Fangtail");
        addCard(Zone.BATTLEFIELD, playerA, "Rockslide Sorcerer");
        addCard(Zone.BATTLEFIELD, playerA, "Mountain");
        addCard(Zone.BATTLEFIELD, playerA, "Mountain");
        addCard(Zone.BATTLEFIELD, playerA, "Plains");
        addCard(Zone.BATTLEFIELD, playerA, "Plains");

        // Set up PlayerB (p1 in .pzl - attacking with 5 Construct tokens)
        setLife(playerB, 20);
        addCard(Zone.BATTLEFIELD, playerB, "Throne of the God-Pharaoh");
        addCard(Zone.BATTLEFIELD, playerB, "Construct Token", 5); // 5 Construct tokens attacking

        setStrictChooseMode(false);

        // Set up combat state - PlayerB is attacking with constructs, in declare
        // blockers step
        setStopAt(1, PhaseStep.END_TURN);
        execute();

        // Wait for async ops
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("MTGP_02", 1);
    }

    @Test
    public void test_MTGP_08_puzzle_llm_metrics() {
        beginPuzzle("test_MTGP_08_puzzle_llm_metrics", 1);

        // Setup MTGP_08 puzzle scenario (see https://mtgpuzzles.com/puzzle/8)
        // PlayerA (active): 2 life, hand: Scar
        // Battlefield: Dread Shade, Carnifex Demon (with 2 -1/-1 counters), Dread
        // Shade, Swamp x3
        // PlayerB: 10 life, battlefield: Ashling the Pilgrim, Cascade Bluffs x3, Shivan
        // Reef x3

        // Set up PlayerA
        setLife(playerA, 2);
        addCard(Zone.HAND, playerA, "Scar");
        addCard(Zone.BATTLEFIELD, playerA, "Dread Shade");
        addCard(Zone.BATTLEFIELD, playerA, "Carnifex Demon");
        addCard(Zone.BATTLEFIELD, playerA, "Dread Shade");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp");

        // Set up PlayerB
        setLife(playerB, 10);
        addCard(Zone.BATTLEFIELD, playerB, "Ashling the Pilgrim");
        addCard(Zone.BATTLEFIELD, playerB, "Cascade Bluffs");
        addCard(Zone.BATTLEFIELD, playerB, "Cascade Bluffs");
        addCard(Zone.BATTLEFIELD, playerB, "Cascade Bluffs");
        addCard(Zone.BATTLEFIELD, playerB, "Shivan Reef");
        addCard(Zone.BATTLEFIELD, playerB, "Shivan Reef");
        addCard(Zone.BATTLEFIELD, playerB, "Shivan Reef");

        setStrictChooseMode(false);

        // Run for one turn (puzzle specifies "Win this turn")
        setStopAt(1, PhaseStep.END_TURN);
        execute();

        // Wait for async ops
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("MTGP_08", 1);
    }

}
