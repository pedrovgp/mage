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
        // [metadata]
        // Name:MTG Puzzles #01 - Gaining Experience
        // URL:https://mtgpuzzles.com/puzzle/1
        // Goal:Win
        // Turns:1
        // Difficulty:Medium
        // Description:Defeat your opponent before their next turn. You currently have 0
        // experience counters. Your opponent flashed in a Nebelgast Herald on your
        // upkeep step, tapping your Mizzix of the Izmagnus. Can you find 4 damage to
        // win?
        // [state]
        // turn=1
        // activeplayer=p0
        // activephase=MAIN1
        // p0life=5
        // p0hand=Banners Raised;Electrickery;Temporary Insanity
        // p0graveyard=Opt;Volcanic Spray
        // p0battlefield=Mizzix of the Izmagnus|Tapped;Mountain;Mountain;Sulfur
        // Falls|NoETBTrigs;Sulfur Falls|NoETBTrigs
        // p1life=4
        // p1landsplayed=0
        // p1landsplayedlastturn=0
        // p1battlefield=Nebelgast Herald;Treetop Ambusher

        setupPuzzle("test_MTGP_01_puzzle_llm_metrics", 1);

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
    public void test_MTGP_09_puzzle_llm_metrics() {
        // [metadata]
        // Name:MTG Puzzles #09 - Aqua Regia
        // URL:https://mtgpuzzles.com/puzzle/9
        // Goal:Win
        // Turns:1
        // Difficulty:Easy
        // Description:Win this turn. You've done the hard work of getting your opponent
        // below 0, now to actually win the game! Can you rustle up enough mana for your
        // ultimate weapon and win this turn? Your opponent's life total is -1 (yes it's
        // negative). Fungal Reaches does not have any storage counters.
        // [state]
        // turn=1
        // activeplayer=p0
        // activephase=MAIN1
        // p0life=3
        // p0landsplayed=0
        // p0landsplayedlastturn=0
        // p0hand=Watery Grave;Talisman of Impulse;Mana Crypt
        // p0battlefield=Legacy Weapon;Nimbus Maze;City of Traitors;Fungal Reaches;River
        // of Tears;Caves of Koilos
        // p1life=-1
        // p1landsplayed=0
        // p1landsplayedlastturn=0
        // p1battlefield=Platinum Angel

        setupPuzzle("test_MTGP_09_puzzle_llm_metrics", 1);

        // Set up PlayerA
        setLife(playerA, 8);
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.HAND, playerA, "Shock");
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 3);
        addCard(Zone.BATTLEFIELD, playerA, "Sulfuric Vortex");

        // Set up PlayerB
        setLife(playerB, 10);
        addCard(Zone.BATTLEFIELD, playerB, "Llanowar Elves");
        addCard(Zone.BATTLEFIELD, playerB, "Forest", 2);

        setStrictChooseMode(false);

        // Run for one turn (puzzle specifies "Win this turn")
        setStopAt(1, PhaseStep.END_TURN);
        execute();

        finishAndSave("MTGP_09", 1);
    }

    @Test
    public void test_MTGP_04_puzzle_llm_metrics() {
        // [metadata]
        // Name:MTG Puzzles #04 - Enrage Against the Machine
        // URL:https://mtgpuzzles.com/puzzle/4
        // Goal:Win before opponent's next turn
        // Turns:1
        // Difficulty:Very Hard
        // Description:Win this turn. Your spirit-engrage tribal deck is not doing as
        // well as you planned. Your opponent played a Jin-Gitaxias on their turn and
        // you must find a way to win before its too late! The solution should not
        // depend on how Jin-Gitaxias, Core Augur blocks.
        // [state]
        // turn=1
        // activeplayer=p0
        // activephase=MAIN1
        // p0life=5
        // p0landsplayed=0
        // p0landsplayedlastturn=0
        // p0hand=Dark Withering;Tahngarth's Rage;Alms of the Vein
        // p0battlefield=Frilled Deathspitter;Niblis of Dusk;Supreme
        // Phantom;Pyrohemia;Mountain;Mountain;Mountain;Mountain;Mountain;Watery
        // Grave|NoETBTrigs;Watery Grave|NoETBTrigs
        // p1life=23
        // p1landsplayed=0
        // p1landsplayedlastturn=0
        // p1battlefield=Jin-Gitaxias, Core Augur

        setupPuzzle("test_MTGP_04_puzzle_llm_metrics", 1);

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
        // [metadata]
        // Name:MTG Puzzles #06 - Lucky Number 13
        // URL:https://mtgpuzzles.com/puzzle/6
        // Goal:Win
        // Turns:1
        // Difficulty:Hard
        // Description:Win this turn. It is your untap step and you and your opponent
        // are dancing around 13 life. Can you find a way to win this turn? It's the
        // beginning of your Untap Step. Assume the card you draw in your draw step is
        // not necessary to solve this puzzle.
        // [state]
        // turn=1
        // activeplayer=p1
        // activephase=CLEANUP
        // p0life=10
        // p0landsplayed=0
        // p0landsplayedlastturn=0
        // p0hand=Blazing Salvo
        // p0battlefield=Oreskos Sun Guide|Tapped;Nocturnal Feeder;Axis of
        // Mortality;Triskaidekaphobia;Savai Triome|Tapped|NoETBTrigs;Savai
        // Triome|Tapped|NoETBTrigs;Savai Triome|Tapped|NoETBTrigs
        // p0library=Opt;Opt;Opt;Opt;Opt;Opt;Opt;Opt;Opt;Opt;Opt;Opt;Opt;Opt;Opt;Opt;Opt;Opt;Opt;Opt;Opt;Opt;Opt;Opt;Opt;Opt;Opt;Opt;Opt;Opt
        // p1life=14
        // p1landsplayed=0
        // p1landsplayedlastturn=0
        // p1battlefield=Bogardan Firefiend

        // Puzzle starts at CLEANUP of p1 (playerB), so set up initial state accordingly
        setupPuzzle("test_MTGP_06_puzzle_llm_metrics", playerB.getId(), PhaseStep.CLEANUP, 1);

        // Set up PlayerA
        setLife(playerA, 10);
        addCard(Zone.HAND, playerA, "Blazing Salvo");
        addCard(Zone.BATTLEFIELD, playerA, "Oreskos Sun Guide", 1, true); // tapped
        addCard(Zone.BATTLEFIELD, playerA, "Nocturnal Feeder");
        addCard(Zone.BATTLEFIELD, playerA, "Axis of Mortality");
        addCard(Zone.BATTLEFIELD, playerA, "Triskaidekaphobia");
        addCard(Zone.BATTLEFIELD, playerA, "Savai Triome", 3, true); // three tapped copies
        addCard(Zone.LIBRARY, playerA, "Opt", 30);

        // Set up PlayerB
        setLife(playerB, 14);
        addCard(Zone.BATTLEFIELD, playerB, "Bogardan Firefiend");

        setStrictChooseMode(false);

        // Run for one turn (puzzle specifies "Win this turn")
        setStopAt(2, PhaseStep.END_TURN);
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

        setupPuzzle("test_MTGP_02_puzzle_llm_metrics", 1);

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
        // [metadata]
        // Name:MTG Puzzles #08 - Counter Intelligence (*)
        // URL:https://mtgpuzzles.com/puzzle/8
        // Goal:Win
        // Turns:1
        // Difficulty:Medium
        // Description:Win this turn. It's the eternal struggle between +1/+1 counters
        // and -1/-1 counters, can you force your opponent's hand to secure the victory?
        // Your opponent could activate Ashling at any point they have priority.
        // # Needs AI support for Ashling the Pilgrim to work correctly (without human
        // control).
        // HumanControl:True
        // [state]
        // turn=1
        // activeplayer=p0
        // activephase=MAIN1
        // p0life=2
        // p0landsplayed=0
        // p0landsplayedlastturn=0
        // p0hand=Scar
        // p0battlefield=Dread Shade;Carnifex Demon|Counters:M1M1=2;Dread
        // Shade;Swamp;Swamp;Swamp
        // p1life=10
        // p1landsplayed=0
        // p1landsplayedlastturn=0
        // p1battlefield=Ashling the Pilgrim;Cascade Bluffs;Cascade Bluffs;Cascade
        // Bluffs;Shivan Reef;Shivan Reef;Shivan Reef

        setupPuzzle("test_MTGP_08_puzzle_llm_metrics", 1);

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

    @Test
    public void test_PC_051915_puzzle_llm_metrics() {
        // [metadata]
        // Name:Perplexing Chimera (GatheringMagic.com) 051915 - Weapon of Choice
        // URL:http://www.gatheringmagic.com/seanuy-051915-perplexing-chimera-8-weapon-of-choice/
        // Goal:Win
        // Turns:1
        // Difficulty:Hard
        // Description:Win this turn.
        // [state]
        // ActivePlayer=Human
        // ActivePhase=Main1
        // HumanLife=5
        // AILife=11
        // humanhand=Flayer Husk; Darksteel Axe; Nameless Inversion
        // humanbattlefield=Plains|Set:CHK; Plains|Set:CHK; Plains|Set:CHK;
        // Plains|Set:CHK; Swamp|Set:CHK; Swamp|Set:CHK; Swamp|Set:CHK; Kor Duelist;
        // Scavenger Drake; Moonlit Strider; Copper Carapace
        // humangraveyard=Thief of Hope
        // humanlibrary=
        // aibattlefield=Mountain|Set:ZEN|Tapped; Mountain|Set:ZEN|Tapped;
        // Mountain|Set:ZEN|Tapped; Mountain|Set:ZEN|Tapped; Forest|Set:ZEN|Tapped;
        // Forest|Set:ZEN|Tapped; Forest|Set:ZEN|Tapped; Forest|Set:ZEN|Tapped;
        // Bloodshot Trainee; Kozilek's Predator|Id:420; Kitesail|AttachedTo:420;
        // Gorehorn Minotaurs|Counters:P1P1=2; Kavu Primarch|Counters:P1P1=4|Tapped
        // aigraveyard=
        // ailibrary=

        setupPuzzle("test_PC_051915_puzzle_llm_metrics", 1);

        // Set up PlayerA (Human)
        setLife(playerA, 5);
        addCard(Zone.HAND, playerA, "Flayer Husk");
        addCard(Zone.HAND, playerA, "Darksteel Axe");
        addCard(Zone.HAND, playerA, "Nameless Inversion");
        addCard(Zone.BATTLEFIELD, playerA, "Plains", 4);
        addCard(Zone.BATTLEFIELD, playerA, "Swamp", 3);
        addCard(Zone.BATTLEFIELD, playerA, "Kor Duelist");
        addCard(Zone.BATTLEFIELD, playerA, "Scavenger Drake");
        addCard(Zone.BATTLEFIELD, playerA, "Moonlit Strider");
        addCard(Zone.BATTLEFIELD, playerA, "Copper Carapace");
        addCard(Zone.GRAVEYARD, playerA, "Thief of Hope");

        // Set up PlayerB (AI)
        setLife(playerB, 11);
        addCard(Zone.BATTLEFIELD, playerB, "Mountain", 4, true); // tapped
        addCard(Zone.BATTLEFIELD, playerB, "Forest", 4, true); // tapped
        addCard(Zone.BATTLEFIELD, playerB, "Bloodshot Trainee");
        addCard(Zone.BATTLEFIELD, playerB, "Kozilek's Predator");
        addCard(Zone.BATTLEFIELD, playerB, "Kitesail"); // attached to Kozilek's Predator
        addCard(Zone.BATTLEFIELD, playerB, "Gorehorn Minotaurs");
        addCard(Zone.BATTLEFIELD, playerB, "Kavu Primarch", 1, true); // tapped

        setStrictChooseMode(false);

        // Run for one turn (puzzle specifies "Win this turn")
        setStopAt(1, PhaseStep.END_TURN);
        execute();

        // Wait for async ops
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PC_051915", 1);
    }

    @Test
    public void test_PC_44_puzzle_llm_metrics() {
        // [metadata]
        // Name:Perplexing Chimera #44 - Herald of Defeat
        // URL:https://perplexingchimera.com/2015/03/18/44-herald-of-defeat/
        // Goal:Win
        // Turns:1
        // [state]
        // ActivePlayer=Human
        // ActivePhase=Main1
        // HumanLife=1
        // AILife=33
        // HumanPlay=Perilous Myr|Id:80; Grafted Exoskeleton|AttachedTo:80; Priests of
        // Norn|Counters:M1M1=1; Mycosynth Fiend; Core Prowler|Id:6; Viridian Betrayers;
        // Forest; Forest; Plains; Plains; Phyrexia's Core
        // HumanHand=Seize the Initiative
        // HumanLibrary=
        // HumanGraveyard=
        // AIPlay=Suture Priest|Id:83; Loxodon Wayfarer|Tapped; Mortis Dogs|Tapped;
        // Victory's Herald|Tapped; Plains; Plains|Tapped; Plains|Tapped; Swamp;
        // Swamp|Tapped; Swamp|Tapped; Darksteel Axe|AttachedTo:83; Arrest|AttachedTo:6
        // AIGraveyard=

        setupPuzzle("test_PC_44_puzzle_llm_metrics", 1);

        // Set up PlayerA (Human)
        setLife(playerA, 1);
        addCard(Zone.HAND, playerA, "Seize the Initiative");
        addCard(Zone.BATTLEFIELD, playerA, "Perilous Myr");
        addCard(Zone.BATTLEFIELD, playerA, "Grafted Exoskeleton"); // attached to Perilous Myr
        addCard(Zone.BATTLEFIELD, playerA, "Priests of Norn");
        addCard(Zone.BATTLEFIELD, playerA, "Mycosynth Fiend");
        addCard(Zone.BATTLEFIELD, playerA, "Core Prowler");
        addCard(Zone.BATTLEFIELD, playerA, "Viridian Betrayers");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 2);
        addCard(Zone.BATTLEFIELD, playerA, "Plains", 2);
        addCard(Zone.BATTLEFIELD, playerA, "Phyrexia's Core");

        // Set up PlayerB (AI)
        setLife(playerB, 33);
        addCard(Zone.BATTLEFIELD, playerB, "Suture Priest");
        addCard(Zone.BATTLEFIELD, playerB, "Loxodon Wayfarer", 1, true); // tapped
        addCard(Zone.BATTLEFIELD, playerB, "Mortis Dogs", 1, true); // tapped
        addCard(Zone.BATTLEFIELD, playerB, "Victory's Herald", 1, true); // tapped
        addCard(Zone.BATTLEFIELD, playerB, "Plains", 1);
        addCard(Zone.BATTLEFIELD, playerB, "Plains", 2, true); // two tapped
        addCard(Zone.BATTLEFIELD, playerB, "Swamp", 1);
        addCard(Zone.BATTLEFIELD, playerB, "Swamp", 2, true); // two tapped
        addCard(Zone.BATTLEFIELD, playerB, "Darksteel Axe"); // attached to Suture Priest
        addCard(Zone.BATTLEFIELD, playerB, "Arrest"); // attached to Core Prowler

        setStrictChooseMode(false);

        // Run for one turn (puzzle specifies "Win this turn")
        setStopAt(1, PhaseStep.END_TURN);
        execute();

        // Wait for async ops
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PC_44", 1);
    }

}
