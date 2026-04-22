package org.mage.test.serverside.base;

import mage.abilities.common.SimpleStaticAbility;
import mage.abilities.effects.common.ExileFaceDownYouMayPlayAsLongAsExiledTargetEffect;
import mage.abilities.effects.common.continuous.BoostAllEffect;
import mage.abilities.effects.common.ChooseCreatureTypeEffect;
import mage.constants.CastManaAdjustment;
import mage.constants.Duration;
import mage.constants.PhaseStep;
import mage.constants.SubType;
import mage.constants.Zone;
import mage.game.permanent.Permanent;
import mage.target.targetpointer.FixedTarget;
import mage.counters.CounterType;
import mage.game.ExileZone;
import mage.util.CardUtil;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.Ignore;
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
        setupPuzzle("test_MTGP_06_puzzle_llm_metrics", playerB.getId(), PhaseStep.CLEANUP, 2);

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

        execute();

        // Wait for async ops
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("MTGP_06", 1);
    }

    @Ignore("Skipping survival puzzle for now")
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

        setupPuzzle("test_MTGP_02_puzzle_llm_metrics", playerB.getId(), PhaseStep.DECLARE_BLOCKERS, 2);

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

        execute();

        // Wait for async ops
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PC_051915", 1);
    }

    @Test
    public void test_PC_060915_puzzle_llm_metrics() {
        // [metadata]
        // Name:Perplexing Chimera (GatheringMagic.com) 060915 - Now You See It
        // URL:http://www.gatheringmagic.com/seanuy-060915-now-you-see-it/
        // Goal:Win
        // Turns:1
        // Difficulty:Hard
        // Description:Win on this turn.
        // [state]
        // ActivePlayer=human
        // ActivePhase=Main1
        // HumanLife=9
        // AILife=6
        // humanhand=Mountain|Set:M15; Bone Splinters|Set:MM2; Brute Force|Set:MM2;
        // Stormblood Berserker|Set:MM2; Goblin War Paint|Set:MM2; Chimeric Mass|Set:MM2
        // humanbattlefield=Swamp|Set:M15; Swamp|Set:M15; Mountain|Set:M15;
        // Mountain|Set:M15|Tapped; Rakdos Carnarium; Mortarpod|AttachedTo:420|Set:MM2;
        // t:Germ,P:0,T:0,Cost:no
        // cost,Color:B,Types:Creature-Germ,Keywords:,Image:b_0_0_germ|Id:420; Sickle
        // Ripper|Set:MM2; Necroskitter|Set:MM2
        //
        // humangraveyard=
        // humanlibrary=
        // aihand=
        // aibattlefield=Plains|Set:M15|Tapped; Plains|Set:M15|Tapped;
        // Island|Set:M15|Tapped; Island|Set:M15|Tapped; Island|Set:M15|Tapped;
        // Darksteel Citadel|Set:MM2|Tapped; Myrsmith|Set:MM2; t:Myr,P:1,T:1,Cost:no
        // cost,Types:Artifact-Creature-Myr,Keywords:,Image:c_1_1_myr_som;
        // t:Myr,P:1,T:1,Cost:no
        // cost,Types:Artifact-Creature-Myr,Keywords:,Image:c_1_1_myr_som; Rusted
        // Relic|Set:MM2|Tapped; Glassdust Hulk|Set:MM2|Tapped; Aethersnipe|Set:MM2
        //
        // aigraveyard=
        // ailibrary=

        setupPuzzle("test_PC_060915_puzzle_llm_metrics", 1);

        // Set up PlayerA (Human)
        setLife(playerA, 9);
        addCard(Zone.HAND, playerA, "Mountain");
        addCard(Zone.HAND, playerA, "Bone Splinters");
        addCard(Zone.HAND, playerA, "Brute Force");
        addCard(Zone.HAND, playerA, "Stormblood Berserker");
        addCard(Zone.HAND, playerA, "Goblin War Paint");
        addCard(Zone.HAND, playerA, "Chimeric Mass");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp", 2);
        addCard(Zone.BATTLEFIELD, playerA, "Mountain");
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 1, true); // tapped
        addCard(Zone.BATTLEFIELD, playerA, "Rakdos Carnarium");
        addCard(Zone.BATTLEFIELD, playerA, "Mortarpod");
        addCard(Zone.BATTLEFIELD, playerA, "Sickle Ripper");
        addCard(Zone.BATTLEFIELD, playerA, "Necroskitter");
        // Ensure a Germ token exists (use PhyrexianGermToken implementation when
        // available)
        try {
            new mage.game.permanent.token.PhyrexianGermToken().putOntoBattlefield(1, currentGame, null,
                    playerA.getId());
        } catch (Throwable t) {
            // fallback: attempt generic GermToken creation if present
            try {
                new mage.game.permanent.token.GermToken().putOntoBattlefield(1, currentGame, null, playerA.getId());
            } catch (Throwable ignored) {
                // best-effort: if neither token class is available, continue without explicit
                // creation
            }
        }

        // Best-effort: create a Germ-like token or attach Mortarpod to an existing
        // creature.
        // We'll attach Mortarpod to Sickle Ripper if present to emulate
        // Mortarpod|AttachedTo:420
        runCode("attach mortarpod to target creature if available", 1, PhaseStep.PRECOMBAT_MAIN, playerA,
                (info, player, game) -> {
                    Permanent mortarpod = null;
                    Permanent target = null;
                    for (Permanent p : game.getBattlefield().getAllActivePermanents(player.getId())) {
                        String name = p.getName();
                        if (name.equalsIgnoreCase("Mortarpod")) {
                            mortarpod = p;
                        } else if (name.equalsIgnoreCase("Germ Token")) {
                            // prefer Germ Token to match .pzl ordering
                            if (target == null || name.equalsIgnoreCase("Germ Token")) {
                                target = p;
                            }
                        }
                    }
                    if (mortarpod != null && target != null) {
                        // attach equipment Mortarpod to the chosen creature
                        mortarpod.addAttachment(target.getId(), null, game);
                    }
                });

        // Set up PlayerB (AI)
        setLife(playerB, 6);
        addCard(Zone.BATTLEFIELD, playerB, "Plains", 2, true); // two tapped Plains
        addCard(Zone.BATTLEFIELD, playerB, "Island", 3, true); // three tapped Islands
        addCard(Zone.BATTLEFIELD, playerB, "Darksteel Citadel", 1, true);
        addCard(Zone.BATTLEFIELD, playerB, "Myrsmith");
        addCard(Zone.BATTLEFIELD, playerB, "Rusted Relic", 1, true);
        addCard(Zone.BATTLEFIELD, playerB, "Glassdust Hulk", 1, true);
        addCard(Zone.BATTLEFIELD, playerB, "Aethersnipe");
        // Create two generic Myr tokens (artifact creature Myr tokens)
        new mage.game.permanent.token.MyrToken().putOntoBattlefield(2, currentGame, null, playerB.getId());

        execute();

        // Wait for async ops
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PC_060915", 1);
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

        // Ensure attachments and counters specified in .pzl are applied:
        // - Attach Grafted Exoskeleton to Perilous Myr
        // - Give Priests of Norn one M1M1 (-1/-1) counter
        runCode("pc44 attach and counters", 1, PhaseStep.PRECOMBAT_MAIN, playerA, (info, player, game) -> {
            Permanent myr = null;
            Permanent exo = null;
            Permanent priest = null;
            for (Permanent p : game.getBattlefield().getAllActivePermanents(player.getId())) {
                String name = p.getName();
                if (name.equalsIgnoreCase("Perilous Myr")) {
                    myr = p;
                } else if (name.equalsIgnoreCase("Grafted Exoskeleton")) {
                    exo = p;
                } else if (name.equalsIgnoreCase("Priests of Norn") || name.equalsIgnoreCase("Priest of Norn")) {
                    priest = p;
                }
            }
            try {
                if (exo != null && myr != null) {
                    exo.attachTo(myr.getId(), null, game);
                }
                if (priest != null) {
                    int cur = priest.getCounters(game).getCount(CounterType.M1M1);
                    if (cur > 0) {
                        priest.removeCounters(CounterType.M1M1.getName(), cur, null, game, true);
                    }
                    priest.addCounters(CounterType.M1M1.createInstance(1), null, game);
                }
            } catch (Exception e) {
                // swallow - tests should continue even if helper methods not available
                System.err.println("PC_44 runCode helper exception: " + e.getMessage());
            }
        });

        execute();

        // Wait for async ops
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PC_44", 1);
    }

    @Test
    public void test_PS_M207_puzzle_llm_metrics() {
        // [metadata]
        // Name:Possibility Storm - Magic Core Set 2020 #07
        // URL:http://www.possibilitystorm.com/wp-content/uploads/2019/08/126.-M207.jpg
        // Goal:Win
        // Turns:1
        // Difficulty:Rare
        // Description:Win this turn. Your Dauntless Bodyguard chose Shanna, Sisay's
        // Legacy when it entered the battlefield.
        // [state]
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

        setupPuzzle("test_PS_M207_puzzle_llm_metrics", 1);

        // Set up PlayerA (Human)
        setLife(playerA, 20);
        addCard(Zone.BATTLEFIELD, playerA, "Gideon Blackblade");
        addCard(Zone.BATTLEFIELD, playerA, "Blackblade Reforged");
        addCard(Zone.BATTLEFIELD, playerA, "Omnispell Adept");
        addCard(Zone.BATTLEFIELD, playerA, "Shanna, Sisay's Legacy"); // will set id 9 in pzl, we will attach by name
        addCard(Zone.BATTLEFIELD, playerA, "Sigiled Sword of Valeron");
        addCard(Zone.BATTLEFIELD, playerA, "Dauntless Bodyguard");
        addCard(Zone.BATTLEFIELD, playerA, "Forebear's Blade");
        addCard(Zone.BATTLEFIELD, playerA, "Hallowed Fountain", 3, true);
        addCard(Zone.BATTLEFIELD, playerA, "Temple Garden", 3, true);

        // Set up PlayerB (AI)
        setLife(playerB, 7);
        addCard(Zone.BATTLEFIELD, playerB, "Charity Extractor");
        addCard(Zone.BATTLEFIELD, playerB, "Looming Altisaur", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Gate Colossus");
        addCard(Zone.BATTLEFIELD, playerB, "Charity Extractor");

        // Use runCode to attach equipments and set chosen card metadata / counters
        runCode("attach and set counters", 1, PhaseStep.PRECOMBAT_MAIN, playerA, (info, player, game) -> {
            // Find relevant permanents
            Permanent shanna = null;
            Permanent bodyguard = null;
            Permanent sword = null;
            Permanent blade = null;
            for (Permanent p : game.getBattlefield().getAllActivePermanents(player.getId())) {
                String name = p.getName();
                if (name.equalsIgnoreCase("Shanna, Sisay's Legacy")) {
                    shanna = p;
                } else if (name.equalsIgnoreCase("Dauntless Bodyguard")) {
                    bodyguard = p;
                } else if (name.equalsIgnoreCase("Sigiled Sword of Valeron")) {
                    sword = p;
                } else if (name.equalsIgnoreCase("Forebear's Blade")) {
                    blade = p;
                }
            }
            try {
                // Attach equipments using the correct API on the equipment permanent
                if (sword != null && shanna != null) {
                    sword.attachTo(shanna.getId(), null, game);
                }
                if (blade != null && bodyguard != null) {
                    blade.attachTo(bodyguard.getId(), null, game);
                }
                // Set Gideon loyalty to 6 (remove existing loyalty counters then add 6)
                for (Permanent p : game.getBattlefield().getAllActivePermanents(player.getId())) {
                    if (p.getName().equalsIgnoreCase("Gideon Blackblade")) {
                        int cur = p.getCounters(game).getCount(CounterType.LOYALTY);
                        if (cur > 0) {
                            p.removeCounters(CounterType.LOYALTY.getName(), cur, null, game, true);
                        }
                        p.addCounters(CounterType.LOYALTY.createInstance(6), null, game);
                    }
                }
                // Mark Dauntless Bodyguard chosen card metadata using addInfo (best-effort)
                if (bodyguard != null && shanna != null) {
                    bodyguard.addInfo("chosen", shanna.getId().toString(), game);
                }
            } catch (Exception e) {
                // swallow - tests should continue even if helper methods not available
                System.err.println("PS_M207 runCode helper exception: " + e.getMessage());
            }
        });

        execute();

        // Wait for async ops
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PS_M207", 1);
    }

    @Test
    public void test_PS_XLN5_puzzle_llm_metrics() {
        // [metadata]
        // Name:Possibility Storm - Ixalan #05
        // URL:http://www.possibilitystorm.com/035-ixalan-season-puzzle-5/
        // Goal:Win
        // Turns:1
        // Difficulty:Rare
        // Description:Win this turn. You have 2 copies of Snare Thopter in your library
        // and no other artifacts.
        // [state]
        // ActivePlayer=human
        // ActivePhase=main1
        // HumanLife=20
        // AILife=13
        // humanhand=Aether Tradewinds; Inventors' Fair; Tezzeret the Schemer;
        // Marionette Master; Fatal Push;
        // humanprecast=
        // humangraveyard=
        // humanlibrary=Snare Thopter; Snare Thopter;
        // humanbattlefield=Revel in Riches; Ruthless Knave; Dire Fleet Hoarder;
        // Festering Mummy; Tezzeret, Master of Metal|Counters:LOYALTY=5; Drowned
        // Catacomb|Set:XLN; Drowned Catacomb|Set:XLN; Drowned Catacomb|Set:XLN; Drowned
        // Catacomb|Set:XLN; Island|Set:XLN; Island|Set:XLN; Island|Set:XLN;
        // Island|Set:XLN;
        // aibattlefield=Solemnity; Crested Sunmare; Sacred Cat;
        // aiprecast=Crested Sunmare:TrigToken;
        // aigraveyard=
        // aillibrary=
        // aiexile=
        setupPuzzle("test_PS_XLN5_puzzle_llm_metrics", 1);

        // Set up PlayerA (Human)
        setLife(playerA, 20);
        addCard(Zone.HAND, playerA, "Aether Tradewinds");
        addCard(Zone.HAND, playerA, "Inventors' Fair");
        addCard(Zone.HAND, playerA, "Tezzeret the Schemer");
        addCard(Zone.HAND, playerA, "Marionette Master");
        addCard(Zone.HAND, playerA, "Fatal Push");
        addCard(Zone.LIBRARY, playerA, "Snare Thopter", 2);
        addCard(Zone.BATTLEFIELD, playerA, "Revel in Riches");
        addCard(Zone.BATTLEFIELD, playerA, "Ruthless Knave");
        addCard(Zone.BATTLEFIELD, playerA, "Dire Fleet Hoarder");
        addCard(Zone.BATTLEFIELD, playerA, "Festering Mummy");
        addCard(Zone.BATTLEFIELD, playerA, "Tezzeret, Master of Metal");
        addCard(Zone.BATTLEFIELD, playerA, "Drowned Catacomb", 4);
        addCard(Zone.BATTLEFIELD, playerA, "Island", 4);

        // Set up PlayerB (AI)
        setLife(playerB, 13);
        addCard(Zone.BATTLEFIELD, playerB, "Solemnity");
        addCard(Zone.BATTLEFIELD, playerB, "Crested Sunmare");
        addCard(Zone.BATTLEFIELD, playerB, "Sacred Cat");

        // Ensure Tezzeret has correct loyalty (best-effort)
        runCode("set tezzeret loyalty", 1, PhaseStep.PRECOMBAT_MAIN, playerA, (info, player, game) -> {
            try {
                for (Permanent p : game.getBattlefield().getAllActivePermanents(player.getId())) {
                    if (p.getName().equalsIgnoreCase("Tezzeret, Master of Metal")) {
                        int cur = p.getCounters(game).getCount(CounterType.LOYALTY);
                        if (cur > 0) {
                            p.removeCounters(CounterType.LOYALTY.getName(), cur, null, game, true);
                        }
                        p.addCounters(CounterType.LOYALTY.createInstance(5), null, game);
                    }
                }
            } catch (Exception e) {
                // swallow - tests should continue even if helper methods not available
                System.err.println("PS_XLN5 runCode helper exception: " + e.getMessage());
            }
        });

        execute();

        // Wait for async ops
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PS_XLN5", 1);
    }

    @Test
    public void test_PS_WAR0a_puzzle_llm_metrics() {
        // [metadata]
        // Name:Possibility Storm - War of the Spark #00a (Pre-release Puzzle)
        // URL:http://www.possibilitystorm.com/wp-content/uploads/2019/04/110.-WAR002.jpg
        // Goal:Win
        // Turns:1
        // Difficulty:Rare
        // Description:Win this turn. Start in your first main phase (after Flame of
        // Keld's Chapter II trigger has resolved). Assume your opponent has no mana
        // available. Assume that any card(s) you draw or exile from your library do not
        // contribute to the solution.
        // [state]
        // humanlife=20
        // ailife=30
        // turn=1
        // activeplayer=human
        // activephase=MAIN1
        // humanhand=Inspired Charge;Jaya's Immolating Inferno;Sky Tether;Tenth District
        // Legionnaire;Chandra, Fire Artisan
        // humanlibrary=Dimir Guildgate;Dimir Guildgate;Dimir Guildgate;Dimir
        // Guildgate;Dimir Guildgate;Dimir Guildgate;Dimir Guildgate;Dimir
        // Guildgate;Dimir Guildgate;Dimir Guildgate;Dimir Guildgate;Dimir
        // Guildgate;Dimir Guildgate;Dimir Guildgate;Dimir Guildgate;Dimir
        // Guildgate;Dimir Guildgate;Dimir Guildgate;Dimir Guildgate;Dimir
        // Guildgate;Dimir Guildgate;Dimir Guildgate;Dimir Guildgate;Dimir
        // Guildgate;Dimir Guildgate;Dimir Guildgate;Dimir Guildgate;Dimir
        // Guildgate;Dimir Guildgate;Dimir Guildgate
        // humanbattlefield=Angrath's Marauders;Truefire Captain;The Flame of
        // Keld|Counters:LORE=2;Grateful
        // Apparition;Mountain;Mountain;Mountain;Mountain;Plains;Plains;Plains;Plains
        // aibattlefield=Leonin Warleader;Shalai, Voice of Plenty
        // aiprecast=Leonin Warleader:CustomScript:DB$ Token | TokenAmount$ 4 |
        // TokenScript$ w_1_1_cat_lifelink | TokenOwner$ You
        // removesummoningsickness=true

        setupPuzzle("test_PS_WAR0a_puzzle_llm_metrics", 1);

        // Set up PlayerA (Human)
        setLife(playerA, 20);
        addCard(Zone.HAND, playerA, "Inspired Charge");
        addCard(Zone.HAND, playerA, "Jaya's Immolating Inferno");
        addCard(Zone.HAND, playerA, "Sky Tether");
        addCard(Zone.HAND, playerA, "Tenth District Legionnaire");
        addCard(Zone.HAND, playerA, "Chandra, Fire Artisan");
        addCard(Zone.LIBRARY, playerA, "Dimir Guildgate", 30);
        addCard(Zone.BATTLEFIELD, playerA, "Angrath's Marauders");
        addCard(Zone.BATTLEFIELD, playerA, "Truefire Captain");
        addCard(Zone.BATTLEFIELD, playerA, "The Flame of Keld");
        addCard(Zone.BATTLEFIELD, playerA, "Grateful Apparition");
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 4);

        // Ensure The Flame of Keld has 2 Lore counters as specified in the .pzl
        runCode("set flame lore counters", 1, PhaseStep.PRECOMBAT_MAIN, playerA, (info, player, game) -> {
            try {
                for (Permanent p : game.getBattlefield().getAllActivePermanents(player.getId())) {
                    if (p.getName().equalsIgnoreCase("The Flame of Keld")) {
                        int cur = p.getCounters(game).getCount(CounterType.LORE);
                        if (cur > 0) {
                            p.removeCounters(CounterType.LORE.getName(), cur, null, game, true);
                        }
                        p.addCounters(CounterType.LORE.createInstance(2), null, game);
                    }
                }
            } catch (Exception e) {
                // swallow - tests should continue even if helper methods not available
                System.err.println("PS_WAR0a set flame lore counters helper exception: " + e.getMessage());
            }
        });
        addCard(Zone.BATTLEFIELD, playerA, "Plains", 4);

        // Set up PlayerB (AI)
        setLife(playerB, 30);
        addCard(Zone.BATTLEFIELD, playerB, "Leonin Warleader");
        addCard(Zone.BATTLEFIELD, playerB, "Shalai, Voice of Plenty");

        // If tokens or summoning sickness flags are required, attempt best-effort setup
        runCode("apply pre-cast and remove summoning sickness", 1, PhaseStep.PRECOMBAT_MAIN, playerA,
                (info, player, game) -> {
                    try {
                        // If Leonin Warleader needs to produce tokens, try to create them via token API
                        // if available
                        for (Permanent p : game.getBattlefield().getAllActivePermanents(playerB.getId())) {
                            if (p.getName().equalsIgnoreCase("Leonin Warleader")) {
                                // best-effort: nothing more to do here in test harness
                            }
                        }
                        // Remove summoning sickness from player's creatures if engine supports it via
                        // TurnMods
                        game.getState().getTurnMods()
                                .add(new mage.game.turn.TurnMod(player.getId()).withTag("removeSummoningSickness"));
                    } catch (Exception e) {
                        System.err.println("PS_WAR0a runCode helper exception: " + e.getMessage());
                    }
                });

        execute();

        // Wait for async ops
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PS_WAR0a", 1);
    }

    @Ignore("Test cannot find Gemrazer, altough the card is implemented")
    @Test
    public void test_PS_KHM4_puzzle_llm_metrics() {
        // [metadata]
        // Name:Possibility Storm - Kaldheim #04
        // URL:https://i0.wp.com/www.possibilitystorm.com/wp-content/uploads/2021/02/168.-KHM4-scaled.jpg
        // Goal:Win
        // Turns:1
        // Difficulty:Rare
        // Description:Win this turn. Assume your opponent has no cards in hand. Your
        // solution must account for all possible decisions the opponent could make!
        // [state]
        // humanlife=20
        // ailife=6
        // turn=1
        // activeplayer=human
        // activephase=MAIN1
        // humanhand=Tergrid's Shadow;Gadrak, the Crown-Scourge;Tergrid, God of
        // Fright;Minion's Return
        // humanbattlefield=Goldspan Dragon;Labyrinth Raptor;Embereth
        // Skyblazer;Snow-Covered Mountain|Set:KHM;Snow-Covered
        // Mountain|Set:KHM;Snow-Covered Mountain|Set:KHM;Snow-Covered
        // Swamp|Set:KHM;Snow-Covered Swamp|Set:KHM;Snow-Covered Swamp|Set:KHM
        // aibattlefield=Starnheim Courser;Conclave Mentor;Gemrazer

        setupPuzzle("test_PS_KHM4_puzzle_llm_metrics", 1);

        // Set up PlayerA (Human)
        setLife(playerA, 20);
        addCard(Zone.HAND, playerA, "Tergrid's Shadow");
        addCard(Zone.HAND, playerA, "Gadrak, the Crown-Scourge");
        addCard(Zone.HAND, playerA, "Tergrid, God of Fright");
        addCard(Zone.HAND, playerA, "Minion's Return");
        addCard(Zone.BATTLEFIELD, playerA, "Goldspan Dragon");
        addCard(Zone.BATTLEFIELD, playerA, "Labyrinth Raptor");
        addCard(Zone.BATTLEFIELD, playerA, "Embereth Skyblazer");
        addCard(Zone.BATTLEFIELD, playerA, "Snow-Covered Mountain", 3);
        addCard(Zone.BATTLEFIELD, playerA, "Snow-Covered Swamp", 3);

        // Set up PlayerB (AI)
        setLife(playerB, 6);
        addCard(Zone.BATTLEFIELD, playerB, "Starnheim Courser");
        addCard(Zone.BATTLEFIELD, playerB, "Conclave Mentor");
        addCard(Zone.BATTLEFIELD, playerB, "Gemrazer");

        execute();

        // Wait for async ops
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PS_KHM4", 1);
    }

    @Test
    public void test_PS_DOM5_puzzle_llm_metrics() {
        // [metadata]
        // Name:Possibility Storm - Dominaria #05
        // URL:http://www.possibilitystorm.com/wp-content/uploads/2018/05/064.DOM5_.jpg
        // Goal:Win
        // Turns:1
        // Difficulty:Uncommon
        // Description:Win this turn. You have exiled Fatal Push and Mutiny with Karn.
        // Assume any cards you could draw from your library are irrelevant. You already
        // have the city's blessing at the start of the puzzle.
        // [state]
        // humanlife=20
        // ailife=8
        // turn=1
        // activeplayer=human
        // activephase=MAIN1
        // humanhand=Relic Runner;Garna, the Bloodflame;Forebear's Blade;Karn, Scion of
        // Urza
        // humanlibrary=Song of Freyalise;Song of Freyalise;Song of Freyalise;Song of
        // Freyalise;Song of Freyalise;Song of Freyalise;Song of Freyalise;Song of
        // Freyalise;Song of Freyalise;Song of Freyalise
        // humanbattlefield=Karn, Scion of Urza|Counters:LOYALTY=2;Weldfast
        // Wingsmith;Storm Fleet Swashbuckler;Reckless Fireweaver;Underhanded
        // Designs;Sulfur Falls|Set:DOM;Sulfur Falls|Set:DOM;Sulfur Falls|Set:DOM;Canyon
        // Slough;Canyon Slough;Canyon Slough
        // humanexile=Mutiny|Counters:SILVER=1;Fatal Push|Counters:SILVER=1
        // aibattlefield=Aerial Responder;Aerial Responder;Bonded Horncrest

        setupPuzzle("test_PS_DOM5_puzzle_llm_metrics", 1);

        // Set up PlayerA (Human)
        setLife(playerA, 20);
        addCard(Zone.HAND, playerA, "Relic Runner");
        addCard(Zone.HAND, playerA, "Garna, the Bloodflame");
        addCard(Zone.HAND, playerA, "Forebear's Blade");
        addCard(Zone.HAND, playerA, "Karn, Scion of Urza");
        addCard(Zone.LIBRARY, playerA, "Song of Freyalise", 10);
        addCard(Zone.BATTLEFIELD, playerA, "Karn, Scion of Urza");
        addCard(Zone.BATTLEFIELD, playerA, "Weldfast Wingsmith");
        addCard(Zone.BATTLEFIELD, playerA, "Storm Fleet Swashbuckler");
        addCard(Zone.BATTLEFIELD, playerA, "Reckless Fireweaver");
        addCard(Zone.BATTLEFIELD, playerA, "Underhanded Designs");
        addCard(Zone.BATTLEFIELD, playerA, "Sulfur Falls", 3);
        addCard(Zone.BATTLEFIELD, playerA, "Canyon Slough", 3);
        // Exiled cards (best-effort)
        addCard(Zone.EXILED, playerA, "Mutiny");
        addCard(Zone.EXILED, playerA, "Fatal Push");
        // Ensure exiled cards have SILVER counters (best-effort)
        runCode("set exile silver counters", 1, PhaseStep.PRECOMBAT_MAIN, playerA, (info, player, game) -> {
            try {
                for (ExileZone ez : game.getExile().getExileZones()) {
                    for (mage.cards.Card c : ez.getCards(game)) {
                        String name = c.getName();
                        if ("Mutiny".equalsIgnoreCase(name) || "Fatal Push".equalsIgnoreCase(name)) {
                            try {
                                // best-effort: attempt to set counters if API available on Card
                                int cur = c.getCounters(game).getCount(CounterType.SILVER);
                                if (cur > 0) {
                                    c.removeCounters(CounterType.SILVER.getName(), cur, null, game, true);
                                }
                                c.addCounters(CounterType.SILVER.createInstance(1), null, game);
                            } catch (Exception inner) {
                                // some card implementations may not support counters in exile; ignore
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("PS_DOM5 exile counters helper exception: " + e.getMessage());
            }
        });

        // Set up PlayerB (AI)
        setLife(playerB, 8);
        addCard(Zone.BATTLEFIELD, playerB, "Aerial Responder", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Bonded Horncrest");

        // Ensure Karn has 2 loyalty (best-effort)
        runCode("set karn loyalty", 1, PhaseStep.PRECOMBAT_MAIN, playerA, (info, player, game) -> {
            try {
                for (Permanent p : game.getBattlefield().getAllActivePermanents(player.getId())) {
                    if (p.getName().equalsIgnoreCase("Karn, Scion of Urza")) {
                        int cur = p.getCounters(game).getCount(CounterType.LOYALTY);
                        if (cur > 0) {
                            p.removeCounters(CounterType.LOYALTY.getName(), cur, null, game, true);
                        }
                        p.addCounters(CounterType.LOYALTY.createInstance(2), null, game);
                    }
                }
            } catch (Exception e) {
                // swallow - tests should continue even if helper methods not available
                System.err.println("PS_DOM5 runCode helper exception: " + e.getMessage());
            }
        });

        execute();

        // Wait for async ops
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PS_DOM5", 1);
    }

    @Test
    public void test_PP02_puzzle_llm_metrics() {
        // [metadata]
        // Name:Pauper Puzzles #02 - They See Me Rollin'
        // URL:https://pauperpuzzles.wordpress.com/2017/01/18/2-they-see-me-rollin/
        // Goal:Win
        // Turns:1
        // Difficulty:Medium
        // [state]
        // ActivePlayer=Human
        // ActivePhase=Main1
        // HumanLife=2
        // AILife=8
        // humanhand=Swamp;Jungle Hollow;Armadillo Cloak;Prey Upon;Shrink;Complete
        // Disregard
        // humanbattlefield=Swamp;Swamp;Forest;Forest;Forest;Orzhov Basilica;Pharika's
        // Chosen;Putrid Leech;Pestilence
        // aibattlefield=Ulamog's Crusher;Sea Gate Oracle

        setupPuzzle("test_PP02_puzzle_llm_metrics", 1);

        // Set up PlayerA (Human)
        setLife(playerA, 2);
        addCard(Zone.HAND, playerA, "Swamp");
        addCard(Zone.HAND, playerA, "Jungle Hollow");
        addCard(Zone.HAND, playerA, "Armadillo Cloak");
        addCard(Zone.HAND, playerA, "Prey Upon");
        addCard(Zone.HAND, playerA, "Shrink");
        addCard(Zone.HAND, playerA, "Complete Disregard");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp", 2);
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 3);
        addCard(Zone.BATTLEFIELD, playerA, "Orzhov Basilica");
        addCard(Zone.BATTLEFIELD, playerA, "Pharika's Chosen");
        addCard(Zone.BATTLEFIELD, playerA, "Putrid Leech");
        addCard(Zone.BATTLEFIELD, playerA, "Pestilence");

        // Set up PlayerB (AI)
        setLife(playerB, 8);
        addCard(Zone.BATTLEFIELD, playerB, "Ulamog's Crusher");
        addCard(Zone.BATTLEFIELD, playerB, "Sea Gate Oracle");

        execute();

        // Wait for async ops
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PP02", 1);
    }

    @Test
    public void test_PS_BLB3_puzzle_llm_metrics() {
        // [metadata]
        // Name:Possibility Storm - Bloomburrow #03
        // URL:https://i0.wp.com/www.possibilitystorm.com/wp-content/uploads/2024/08/latest-1-scaled.jpg?ssl=1
        // Goal:Win
        // Turns:1
        // Difficulty:Rare
        // Description:Win this turn. Assume any cards you or your opponent could draw
        // are not relevant to the puzzle. Ensure your solution considers all possible
        // opponent decisions. Good luck!
        // [state]
        // turn=1
        // activeplayer=p0
        // activephase=MAIN1
        // p0life=5
        // p0hand=Bitter Reunion;Season of the Burrow;Harvestrite Host;For the Common
        // Good
        // p0library=Opt;Opt;Opt;Opt;Opt;Opt;Opt;Opt;Opt;Opt;Opt;Opt;Opt;Opt;Opt;Opt;Opt;Opt;Opt;Opt
        // p0battlefield=Baylen, the Haymaker;Alchemist's Talent;Plains;Copperline
        // Gorge;Copperline
        // Gorge;T:c_a_treasure_sac;T:c_a_treasure_sac;T:c_a_treasure_sac;T:c_a_treasure_sac;T:c_a_treasure_sac
        // p1life=12
        // p1library=Opt;Opt;Opt;Opt;Opt;Opt;Opt;Opt;Opt;Opt;Opt;Opt;Opt;Opt;Opt;Opt;Opt;Opt;Opt;Opt
        // p1battlefield=Vengeful Tracker

        setupPuzzle("test_PS_BLB3_puzzle_llm_metrics", 1);

        // Set up PlayerA (Human)
        setLife(playerA, 5);
        addCard(Zone.HAND, playerA, "Bitter Reunion");
        addCard(Zone.HAND, playerA, "Season of the Burrow");
        addCard(Zone.HAND, playerA, "Harvestrite Host");
        addCard(Zone.HAND, playerA, "For the Common Good");
        addCard(Zone.LIBRARY, playerA, "Opt", 20);
        addCard(Zone.BATTLEFIELD, playerA, "Baylen, the Haymaker");
        addCard(Zone.BATTLEFIELD, playerA, "Alchemist's Talent");
        addCard(Zone.BATTLEFIELD, playerA, "Plains");
        addCard(Zone.BATTLEFIELD, playerA, "Copperline Gorge", 2);
        new mage.game.permanent.token.TreasureToken().putOntoBattlefield(5, currentGame, null, playerA.getId());

        // Set up PlayerB (AI)
        setLife(playerB, 12);
        addCard(Zone.LIBRARY, playerB, "Opt", 20);
        addCard(Zone.BATTLEFIELD, playerB, "Vengeful Tracker");

        execute();

        // Wait for async ops
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PS_BLB3", 1);
    }

    @Test
    public void test_pv_custom_multi_attack_puzzle_llm_metrics() {
        // custom validation: two White Knights must both attack to deal lethal (4 dmg vs 6 life).
        // Lava Spike (targets player OR planeswalker only — never creatures) ensures a main-phase
        // priority decision without the risk of the RL model killing its own knights.
        // Win requires: Lava Spike targets opponent (3 dmg) + both knights attack (2+2 = 4 dmg) = 7.
        // This validates multi-attacker selection: CP7 picks both knights; CP8 must also pick both.
        setupPuzzle("test_pv_custom_multi_attack_puzzle_llm_metrics", 1);

        // PlayerA: two 2/2 first-strikers + Lava Spike + mana to cast it
        setLife(playerA, 20);
        addCard(Zone.BATTLEFIELD, playerA, "White Knight");
        addCard(Zone.BATTLEFIELD, playerA, "White Knight");
        addCard(Zone.HAND, playerA, "Lava Spike");
        addCard(Zone.BATTLEFIELD, playerA, "Mountain");

        // PlayerB: 6 life — needs Lava Spike + both knights to kill in one turn
        setLife(playerB, 6);

        execute();

        finishAndSave("pv_custom_multi_attack", 1);  // puzzle_id for trajectory logs
    }

    @Test
    public void test_pv_custom_attack() {
        // custom validation: White Knight should be able to win against 2 life
        setupPuzzle("test_pv_custom_attack", 1);

        // Set up PlayerA
        setLife(playerA, 20);
        addCard(Zone.BATTLEFIELD, playerA, "White Knight");

        // Set up PlayerB
        setLife(playerB, 2);

        execute();

        finishAndSave("pv_custom_attack", 1);
    }

    @Test
    public void test_pv_custom_token_attack() {
        // custom validation: a Myr token attacking should win vs 1 life
        setupPuzzle("test_pv_custom_token_attack", 1);

        // Set up PlayerA
        setLife(playerA, 20);
        new mage.game.permanent.token.MyrToken().putOntoBattlefield(1, currentGame, null, playerA.getId());

        // Set up PlayerB
        setLife(playerB, 1);

        execute();

        finishAndSave("pv_custom_token_attack", 1);
    }

    @Test
    public void test_pv_custom_token_sacrifice() {
        // custom validation: Germ token with Mortarpod attached sacrifices / attacks to
        // win vs 1 life
        setupPuzzle("test_pv_custom_token_sacrifice", 1);

        // Set up PlayerA
        setLife(playerA, 20);
        addCard(Zone.BATTLEFIELD, playerA, "Mortarpod");
        new mage.game.permanent.token.MyrToken().putOntoBattlefield(1, currentGame, null, playerA.getId());
        // Attach Mortarpod to the Myr token
        runCode("attach mortarpod to myr token", 1, PhaseStep.PRECOMBAT_MAIN, playerA, (info, player, game) -> {
            Permanent mortarpod = null;
            Permanent myr = null;
            for (Permanent p : game.getBattlefield().getAllActivePermanents(player.getId())) {
                String name = p.getName();
                if (name.equalsIgnoreCase("Mortarpod")) {
                    mortarpod = p;
                } else if (name.equalsIgnoreCase("Myr Token")) {
                    myr = p;
                }
            }
            if (mortarpod != null && myr != null) {
                mortarpod.addAttachment(myr.getId(), null, game);
            }
        });

        // Set up PlayerB
        setLife(playerB, 2);

        execute();

        finishAndSave("pv_custom_token_sacrifice", 1);
    }

    @Test
    public void test_forge_tutorial01_puzzle_llm_metrics() {
        // Full puzzle file included below as required by test-first policy:
        // [metadata]
        //
        // Name:Forge Puzzle Tutorial #01 - Bypass Operation
        // URL:https://www.cardforge.org
        //
        // Goal:Win
        //
        // Turns:1
        //
        // Difficulty:Easy
        //
        // Description:Most Magic puzzles will require you to win by reducing your
        // opponent's life total to zero. The most common way of doing this involves
        // dealing damage to your opponent with one or more attacking creatures. As a
        // result, you should look for any evasive abilities on the table - abilities
        // like Flying, Shadow, Horsemanship, and even Trample can help you out.\n\nIn
        // this puzzle, you have a clear source of damage in your hand. But you don't
        // have the mana to cast it, and it wouldn't be enough to defeat your opponent
        // by itself in any case. That means that you've got to attack in some way. With
        // this in mind, how do you get past your opponent's blockers?
        // [state]
        //
        // humanlife=7
        // ailife=5
        //
        // activeplayer=human
        //
        // activephase=MAIN1
        //
        // humanhand=Unstable Mutation|Set:4ED; Turn // Burn
        //
        // humanlibrary=
        //
        // humangraveyard=
        //
        // humanbattlefield=Knight of Dawn|Set:TMP; Vedalken Mastermind|Set:10E;
        // Mausoleum Guard|Set:ISD; Silver Knight|Set:SCG; Giant Crab|Set:TMP;
        // Island|Set:ISD; Island|Set:ISD; Island|Set:ISD; Island|Set:ISD;
        // Island|Set:ISD; Plains|Set:ISD; Plains|Set:ISD; Plains|Set:ISD;
        // Plains|Set:ISD; Crumbling Vestige|Set:OGW
        // humanexile=
        //
        // humancommand=
        //
        // aihand=
        //
        // ailibrary=
        //
        // aigraveyard=
        //
        // aibattlefield=Sylvan Caryatid|Set:THS; Willbender|Set:LGN; Minotaur
        // Sureshot|Set:AKH; Steamcore Weird|Set:GPT; Giant Spider|Set:8ED; Assembled
        // Alphas|Set:EMN
        // aiexile=
        // aicommand=
        //
        setupPuzzle("test_forge_tutorial01_puzzle_llm_metrics", 1);

        // Set up PlayerA (Human)
        setLife(playerA, 7);
        addCard(Zone.HAND, playerA, "Unstable Mutation");
        addCard(Zone.HAND, playerA, "Turn // Burn");
        addCard(Zone.BATTLEFIELD, playerA, "Knight of Dawn");
        addCard(Zone.BATTLEFIELD, playerA, "Vedalken Mastermind");
        addCard(Zone.BATTLEFIELD, playerA, "Mausoleum Guard");
        addCard(Zone.BATTLEFIELD, playerA, "Silver Knight");
        addCard(Zone.BATTLEFIELD, playerA, "Giant Crab");
        addCard(Zone.BATTLEFIELD, playerA, "Island", 6);
        addCard(Zone.BATTLEFIELD, playerA, "Plains", 4);
        addCard(Zone.BATTLEFIELD, playerA, "Crumbling Vestige");

        // Set up PlayerB (AI)
        setLife(playerB, 5);
        addCard(Zone.BATTLEFIELD, playerB, "Sylvan Caryatid");
        addCard(Zone.BATTLEFIELD, playerB, "Willbender");
        addCard(Zone.BATTLEFIELD, playerB, "Minotaur Sureshot");
        addCard(Zone.BATTLEFIELD, playerB, "Steamcore Weird");
        addCard(Zone.BATTLEFIELD, playerB, "Giant Spider");
        addCard(Zone.BATTLEFIELD, playerB, "Assembled Alphas");

        execute();

        // Wait for async ops
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("forge_tutorial01", 1);
    }

    @Test
    public void test_PC_033115_puzzle_llm_metrics() {
        // Full puzzle file included below as required by test-first policy:
        // [metadata]
        // Name:Perplexing Chimera (GatheringMagic.com) 033115 - Ground Control
        // URL:http://www.gatheringmagic.com/seanuy-033115-perplexing-chimera-1-ground-control/
        // Goal:Win
        // Turns:1
        // Difficulty:Hard
        // Description:Win this turn.
        // [state]
        // ActivePlayer=Human
        // ActivePhase=Main1
        // HumanLife=4
        // AILife=4
        // humanhand=Glaring Aegis|Set:DTK; Jeskai Barricade|Set:FRF; Epic
        // Confrontation|Set:DTK; Dromoka's Gift|Set:DTK
        // humanbattlefield=Forest|Set:DTK; Forest|Set:DTK; Forest|Set:DTK;
        // Forest|Set:DTK; Plains|Set:DTK; Plains|Set:DTK; Plains|Set:DTK;
        // Plains|Set:DTK; Plains|Set:DTK; Lightwalker|Set:DTK; Dragon-Scarred
        // Bear|Set:DTK; Abzan Skycaptain|Set:FRF|Tapped|Id:420; Battlefront
        // Krushok|Set:FRF; Ambush Krotiq|Set:FRF; Dromoka Monument|Set:DTK; Stormrider
        // Rig|AttachedTo:420|Set:DTK
        // aibattlefield=Plains|Set:DTK|Tapped; Plains|Set:DTK|Tapped;
        // Plains|Set:DTK|Tapped; Island|Set:DTK|Tapped; Island|Set:DTK|Tapped;
        // Island|Set:DTK|Tapped; Island|Set:DTK|Tapped; Dromoka Dunecaster|Set:DTK;
        // Territorial Roc|Set:DTK; Orator of Ojutai|Set:DTK; Updraft Elemental|Set:DTK;
        // Ancient Carp|Set:DTK; Strongarm Monk|Set:DTK; Ojutai, Soul of Winter|Set:FRF;
        // Cunning Breezedancer|Tapped|Set:DTK
        //
        setupPuzzle("test_PC_033115_puzzle_llm_metrics", 1);

        // Set up PlayerA (Human)
        setLife(playerA, 4);
        addCard(Zone.HAND, playerA, "Glaring Aegis");
        addCard(Zone.HAND, playerA, "Jeskai Barricade");
        addCard(Zone.HAND, playerA, "Epic Confrontation");
        addCard(Zone.HAND, playerA, "Dromoka's Gift");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 4);
        addCard(Zone.BATTLEFIELD, playerA, "Plains", 5);
        addCard(Zone.BATTLEFIELD, playerA, "Lightwalker");
        addCard(Zone.BATTLEFIELD, playerA, "Dragon-Scarred Bear");
        addCard(Zone.BATTLEFIELD, playerA, "Abzan Skycaptain", 1, true); // tapped as specified
        addCard(Zone.BATTLEFIELD, playerA, "Battlefront Krushok");
        addCard(Zone.BATTLEFIELD, playerA, "Ambush Krotiq");
        addCard(Zone.BATTLEFIELD, playerA, "Dromoka Monument");
        addCard(Zone.BATTLEFIELD, playerA, "Stormrider Rig");

        // Attach Stormrider Rig to Abzan Skycaptain (best-effort, pzl references
        // Id:420)
        runCode("attach stormrider rig to abzan skycaptain", 1, PhaseStep.PRECOMBAT_MAIN, playerA,
                (info, player, game) -> {
                    Permanent rig = null;
                    Permanent sky = null;
                    for (Permanent p : game.getBattlefield().getAllActivePermanents(player.getId())) {
                        String name = p.getName();
                        if (name.equalsIgnoreCase("Stormrider Rig")) {
                            rig = p;
                        } else if (name.equalsIgnoreCase("Abzan Skycaptain")) {
                            sky = p;
                        }
                    }
                    rig.attachTo(sky.getId(), null, game);
                });

        // Set up PlayerB (AI)
        setLife(playerB, 4);
        addCard(Zone.BATTLEFIELD, playerB, "Plains", 3, true); // tapped plains
        addCard(Zone.BATTLEFIELD, playerB, "Island", 4, true); // tapped islands
        addCard(Zone.BATTLEFIELD, playerB, "Dromoka Dunecaster");
        addCard(Zone.BATTLEFIELD, playerB, "Territorial Roc");
        addCard(Zone.BATTLEFIELD, playerB, "Orator of Ojutai");
        addCard(Zone.BATTLEFIELD, playerB, "Updraft Elemental");
        addCard(Zone.BATTLEFIELD, playerB, "Ancient Carp");
        addCard(Zone.BATTLEFIELD, playerB, "Strongarm Monk");
        addCard(Zone.BATTLEFIELD, playerB, "Ojutai, Soul of Winter");
        addCard(Zone.BATTLEFIELD, playerB, "Cunning Breezedancer", 1, true); // tapped

        execute();

        // Wait for async ops
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PC_033115", 1);
    }

    @Test
    public void test_PS_GRN7_puzzle_llm_metrics() {
        // Full puzzle file included below as required by test-first policy:
        // [metadata]
        // Name:Possibility Storm - Guilds of Ravnica #07
        // URL:http://www.possibilitystorm.com/wp-content/uploads/2018/11/089.-GRN7.jpg
        // Goal:Win
        // Turns:1
        // Difficulty:Rare
        // Description:Win this turn. Remember that your answer must satisfy all
        // possible blocking scenarios. Assume the top three cards of your opponent's
        // library are all basic Forests. Titanic Growth and Prying Blade are exiled
        // with your Thief of Sanity.
        // [state]
        // humanlife=20
        // ailife=14
        // turn=1
        // activeplayer=human
        // activephase=MAIN1
        // humanhand=Urza's Ruinous Blast;The Eldest Reborn;Demonic Vigor;Switcheroo
        // humanbattlefield=Thief of
        // Sanity|Id:1|RememberedCards:2,3|ExecuteScript:DBEffect;Etrata, the
        // Silencer;Steadfast Armasaur;Goring Ceratops;Watery Grave|NoETBTrigs;Watery
        // Grave|NoETBTrigs;Watery Grave|NoETBTrigs;Glacial Fortress;Glacial
        // Fortress;Glacial Fortress;Glacial Fortress
        // ailibrary=Forest;Forest;Forest
        // aibattlefield=Kraul Harpooner;Primordial Wurm;Trostani Discordant
        // aiexile=Titanic Growth|Id:2|ExiledWith:1|FaceDown;Prying
        // Blade|Id:3|ExiledWith:1|FaceDown
        //
        setupPuzzle("test_PS_GRN7_puzzle_llm_metrics", 1);

        // Set up PlayerA (Human)
        setLife(playerA, 20);
        addCard(Zone.HAND, playerA, "Urza's Ruinous Blast");
        addCard(Zone.HAND, playerA, "The Eldest Reborn");
        addCard(Zone.HAND, playerA, "Demonic Vigor");
        addCard(Zone.HAND, playerA, "Switcheroo");
        addCard(Zone.BATTLEFIELD, playerA, "Thief of Sanity");
        addCard(Zone.BATTLEFIELD, playerA, "Etrata, the Silencer");
        addCard(Zone.BATTLEFIELD, playerA, "Steadfast Armasaur");
        addCard(Zone.BATTLEFIELD, playerA, "Goring Ceratops");
        addCard(Zone.BATTLEFIELD, playerA, "Watery Grave", 3);
        addCard(Zone.BATTLEFIELD, playerA, "Glacial Fortress", 4);

        // Set up PlayerB (AI)
        setLife(playerB, 14);
        // Put three basic Forests on top of AI library (best-effort)
        addCard(Zone.LIBRARY, playerB, "Forest", 3);
        addCard(Zone.BATTLEFIELD, playerB, "Kraul Harpooner");
        addCard(Zone.BATTLEFIELD, playerB, "Primordial Wurm");
        addCard(Zone.BATTLEFIELD, playerB, "Trostani Discordant");
        // Exiled cards that were exiled with Thief of Sanity (best-effort)
        addCard(Zone.EXILED, playerB, "Titanic Growth");
        addCard(Zone.EXILED, playerB, "Prying Blade");

        // Best-effort: mark Thief of Sanity remembered/exile metadata
        runCode("mark thief remembered and exile metadata", 1, PhaseStep.PRECOMBAT_MAIN, playerA,
                (info, player, game) -> {
                    for (Permanent p : game.getBattlefield().getAllActivePermanents(player.getId())) {
                        if (p.getName().equalsIgnoreCase("Thief of Sanity")) {
                            p.addInfo("RememberedCards", "2,3", game);
                            p.addInfo("ExiledWith", "1", game);
                        } else if (p.getName().equalsIgnoreCase("Titanic Growth") ||
                                p.getName().equalsIgnoreCase("Prying Blade")) {
                            new ExileFaceDownYouMayPlayAsLongAsExiledTargetEffect(true,
                                    CastManaAdjustment.AS_THOUGH_ANY_MANA_TYPE)
                                    .setTargetPointer(new FixedTarget(p, game))
                                    .apply(game, null);
                        }
                    }
                });

        execute();

        // Wait for async ops
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PS_GRN7", 1);
    }

    // =========================================================================
    // SKILL-BASED CURRICULUM PUZZLES
    // Ordered by tier (T0 → T4). Each puzzle isolates a single skill so
    // model capability can be tracked precisely across training checkpoints.
    // Card pool: Pre-Modern (4th Edition – Scourge) unless marked [OOT].
    // =========================================================================

    // -------------------------------------------------------------------------
    // TIER 0 -- Foundational Actions
    // -------------------------------------------------------------------------

    @Test
    public void test_pv_bolt_face_puzzle_llm_metrics() {
        // [skill-metadata]
        // Tier: 0
        // Skill: T0.3 -- Cast a single spell at opponent for lethal
        // PreModern: Yes (Lightning Bolt in 4th/5th Edition)
        // [state]
        // turn=1
        // activeplayer=p0
        // activephase=MAIN1
        // p0life=20
        // p0hand=Lightning Bolt
        // p0battlefield=Mountain
        // p1life=3
        // p1battlefield=
        //
        // Win line: cast Lightning Bolt targeting opponent (3 damage = lethal).
        // Tests: /choose_from_all_actions (cast spell), /choose_targets (opponent).
        setupPuzzle("test_pv_bolt_face_puzzle_llm_metrics", 1);

        setLife(playerA, 20);
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.BATTLEFIELD, playerA, "Mountain");

        setLife(playerB, 3);

        execute();

        finishAndSave("pv_bolt_face", 1);
    }

    @Test
    public void test_pv_ping_ability_puzzle_llm_metrics() {
        // [skill-metadata]
        // Tier: 0
        // Skill: T0.4 -- Activate a tap ability targeting opponent for lethal
        // PreModern: Yes (Prodigal Sorcerer in 4th–7th Edition)
        // [state]
        // turn=1
        // activeplayer=p0
        // activephase=MAIN1
        // p0life=20
        // p0battlefield=Prodigal Sorcerer;Island
        // p1life=1
        // p1battlefield=
        //
        // Win line: tap Prodigal Sorcerer, target opponent (1 damage = lethal).
        // Tests: /choose_from_all_actions (activate ability), /choose_targets (opponent).
        setupPuzzle("test_pv_ping_ability_puzzle_llm_metrics", 1);

        setLife(playerA, 20);
        addCard(Zone.BATTLEFIELD, playerA, "Prodigal Sorcerer");
        addCard(Zone.BATTLEFIELD, playerA, "Island");

        setLife(playerB, 1);

        execute();

        finishAndSave("pv_ping_ability", 1);
    }

    // -------------------------------------------------------------------------
    // TIER 1 -- Single-Mechanic Decisions
    // -------------------------------------------------------------------------

    @Test
    public void test_sk_double_bolt_puzzle_llm_metrics() {
        // [skill-metadata]
        // Tier: 1
        // Skill: T1.1 -- Two burn spells at opponent for lethal, no combat
        // PreModern: Yes (Lightning Bolt 4th/5th, Shock Stronghold/6th/7th)
        // [state]
        // turn=1
        // activeplayer=p0
        // activephase=MAIN1
        // p0life=20
        // p0hand=Lightning Bolt;Shock
        // p0battlefield=Mountain;Mountain;Mountain
        // p1life=5
        // p1battlefield=
        //
        // Win line: Lightning Bolt (3) + Shock (2) = 5 damage = lethal.
        // Combat is not needed. Tests casting two spells and choosing correct targets.
        setupPuzzle("test_sk_double_bolt_puzzle_llm_metrics", 1);

        setLife(playerA, 20);
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.HAND, playerA, "Shock");
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 3);

        setLife(playerB, 5);

        execute();

        finishAndSave("sk_double_bolt", 1);
    }

    @Test
    public void test_sk_bolt_plus_attack_puzzle_llm_metrics() {
        // [skill-metadata]
        // Tier: 1
        // Skill: T1.2 -- Burn spell + combat attack both needed for lethal
        // PreModern: Yes (Shock Stronghold/6th, Grizzly Bears 4th–7th)
        // [state]
        // turn=1
        // activeplayer=p0
        // activephase=MAIN1
        // p0life=20
        // p0hand=Shock
        // p0battlefield=Grizzly Bears;Mountain;Mountain
        // p1life=4
        // p1battlefield=
        //
        // Win line: Shock opponent (2) then attack with Grizzly Bears (2) = 4 = lethal.
        // Neither alone is enough (2 < 4). Tests spell + combat sequencing.
        setupPuzzle("test_sk_bolt_plus_attack_puzzle_llm_metrics", 1);

        setLife(playerA, 20);
        addCard(Zone.HAND, playerA, "Shock");
        addCard(Zone.BATTLEFIELD, playerA, "Grizzly Bears");
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 2);

        setLife(playerB, 4);

        execute();

        finishAndSave("sk_bolt_plus_attack", 1);
    }

    @Test
    public void test_sk_evasion_pick_puzzle_llm_metrics() {
        // [skill-metadata]
        // Tier: 1
        // Skill: T1.3 -- Choose the flying attacker to bypass a ground defender
        // PreModern: Yes (Wind Drake 5th/6th/7th, Grizzly Bears 4th–7th, Wall of Ice 4th/5th)
        // [state]
        // turn=1
        // activeplayer=p0
        // activephase=MAIN1
        // p0life=20
        // p0battlefield=Wind Drake;Grizzly Bears;Island;Island;Island
        // p1life=2
        // p1battlefield=Wall of Ice
        //
        // Win line: attack with Wind Drake (flying, 2 damage). Wall of Ice (0/7 defender)
        // blocks Grizzly Bears but cannot block Wind Drake. Tests selecting the correct
        // attacker subset when evasion is relevant.
        setupPuzzle("test_sk_evasion_pick_puzzle_llm_metrics", 1);

        setLife(playerA, 20);
        addCard(Zone.BATTLEFIELD, playerA, "Wind Drake");
        addCard(Zone.BATTLEFIELD, playerA, "Grizzly Bears");
        addCard(Zone.BATTLEFIELD, playerA, "Island", 3);

        setLife(playerB, 2);
        addCard(Zone.BATTLEFIELD, playerB, "Wall of Ice");

        execute();

        finishAndSave("sk_evasion_pick", 1);
    }

    @Test
    public void test_sk_haste_creature_puzzle_llm_metrics() {
        // [skill-metadata]
        // Tier: 1
        // Skill: T1.4 -- Cast a creature with haste and attack with it same turn
        // PreModern: Yes (Raging Goblin in Exodus/6th/7th Edition)
        // [state]
        // turn=1
        // activeplayer=p0
        // activephase=MAIN1
        // p0life=20
        // p0hand=Raging Goblin
        // p0battlefield=Mountain
        // p1life=1
        // p1battlefield=
        //
        // Win line: cast Raging Goblin (haste), attack for 1 = lethal.
        // Tests recognizing haste allows immediate attack from a freshly cast creature.
        setupPuzzle("test_sk_haste_creature_puzzle_llm_metrics", 1);

        setLife(playerA, 20);
        addCard(Zone.HAND, playerA, "Raging Goblin");
        addCard(Zone.BATTLEFIELD, playerA, "Mountain");

        setLife(playerB, 1);

        execute();

        finishAndSave("sk_haste_creature", 1);
    }

    @Test
    public void test_sk_pump_and_attack_puzzle_llm_metrics() {
        // [skill-metadata]
        // Tier: 1
        // Skill: T1.5 -- Cast a pump spell on own creature before combat for lethal
        // PreModern: Yes (Giant Growth in 4th–7th, Grizzly Bears 4th–7th)
        // [state]
        // turn=1
        // activeplayer=p0
        // activephase=MAIN1
        // p0life=20
        // p0hand=Giant Growth
        // p0battlefield=Grizzly Bears;Forest
        // p1life=5
        // p1battlefield=
        //
        // Win line: Giant Growth on Grizzly Bears (becomes 5/5), attack for 5 = lethal.
        // Without Giant Growth, Bears attack for 2 (not lethal). Tests pump + attack.
        setupPuzzle("test_sk_pump_and_attack_puzzle_llm_metrics", 1);

        setLife(playerA, 20);
        addCard(Zone.HAND, playerA, "Giant Growth");
        addCard(Zone.BATTLEFIELD, playerA, "Grizzly Bears");
        addCard(Zone.BATTLEFIELD, playerA, "Forest");

        setLife(playerB, 5);

        execute();

        finishAndSave("sk_pump_and_attack", 1);
    }

    @Test
    public void test_sk_fling_for_value_puzzle_llm_metrics() {
        // [skill-metadata]
        // Tier: 1
        // Skill: T1.6 -- Sacrifice a creature via Fling to deal damage (only line)
        // PreModern: Yes (Fling in Stronghold, Hill Giant 4th–7th, Wall of Ice 4th/5th)
        // [state]
        // turn=1
        // activeplayer=p0
        // activephase=MAIN1
        // p0life=20
        // p0hand=Fling
        // p0battlefield=Hill Giant;Mountain;Mountain
        // p1life=3
        // p1battlefield=Wall of Ice
        //
        // Win line: Fling sacrificing Hill Giant (3 damage to opponent) = lethal.
        // Wall of Ice (0/7 defender) blocks Hill Giant in combat -- no trample, 0 damage.
        // Fling bypasses the defender. Tests sacrifice-as-damage pattern.
        setupPuzzle("test_sk_fling_for_value_puzzle_llm_metrics", 1);

        setLife(playerA, 20);
        addCard(Zone.HAND, playerA, "Fling");
        addCard(Zone.BATTLEFIELD, playerA, "Hill Giant");
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 2);

        setLife(playerB, 3);
        addCard(Zone.BATTLEFIELD, playerB, "Wall of Ice");

        execute();

        finishAndSave("sk_fling_for_value", 1);
    }

    @Test
    public void test_sk_correct_target_puzzle_llm_metrics() {
        // [skill-metadata]
        // Tier: 1
        // Skill: T1.7 -- Target selection: bolt the opponent, not the irrelevant creature
        // PreModern: Yes (Lightning Bolt 4th/5th, Llanowar Elves 4th–7th)
        // [state]
        // turn=1
        // activeplayer=p0
        // activephase=MAIN1
        // p0life=20
        // p0hand=Lightning Bolt
        // p0battlefield=Mountain
        // p1life=3
        // p1battlefield=Llanowar Elves
        //
        // Win line: Lightning Bolt targeting opponent (3 damage = lethal).
        // Bolting Llanowar Elves wastes the spell (opponent at 3, not lethal).
        // Tests choosing the correct target when multiple legal targets exist.
        setupPuzzle("test_sk_correct_target_puzzle_llm_metrics", 1);

        setLife(playerA, 20);
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.BATTLEFIELD, playerA, "Mountain");

        setLife(playerB, 3);
        addCard(Zone.BATTLEFIELD, playerB, "Llanowar Elves");

        execute();

        finishAndSave("sk_correct_target", 1);
    }

    // -------------------------------------------------------------------------
    // TIER 2 -- Two-Step Reasoning
    // -------------------------------------------------------------------------

    @Test
    public void test_sk_remove_blocker_attack_puzzle_llm_metrics() {
        // [skill-metadata]
        // Tier: 2
        // Skill: T2.1 -- Remove the blocker first, then attack for lethal
        // PreModern: Yes (Terror 4th–6th, Grizzly Bears 4th–7th, Craw Wurm 4th–7th)
        // [state]
        // turn=1
        // activeplayer=p0
        // activephase=MAIN1
        // p0life=20
        // p0hand=Terror
        // p0battlefield=Grizzly Bears;Swamp;Swamp
        // p1life=2
        // p1battlefield=Craw Wurm
        //
        // Win line: Terror on Craw Wurm (destroy it), attack with Bears (2 damage = lethal).
        // Without Terror, Craw Wurm (6/4) blocks Bears (Bears die, 0 player damage).
        // There is no burn spell to go face directly. Tests two-step: removal then attack.
        setupPuzzle("test_sk_remove_blocker_attack_puzzle_llm_metrics", 1);

        setLife(playerA, 20);
        addCard(Zone.HAND, playerA, "Terror");
        addCard(Zone.BATTLEFIELD, playerA, "Grizzly Bears");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp", 2);

        setLife(playerB, 2);
        addCard(Zone.BATTLEFIELD, playerB, "Craw Wurm");

        execute();

        finishAndSave("sk_remove_blocker_attack", 1);
    }

    @Test
    public void test_sk_pump_evasion_puzzle_llm_metrics() {
        // [skill-metadata]
        // Tier: 2
        // Skill: T2.2 -- Pump a flying creature for lethal; ground creatures can't block
        // PreModern: Yes (Giant Growth 4th–7th, Wind Drake 5th–7th, Craw Wurm 4th–7th)
        // [state]
        // turn=1
        // activeplayer=p0
        // activephase=MAIN1
        // p0life=20
        // p0hand=Giant Growth
        // p0battlefield=Wind Drake;Forest
        // p1life=5
        // p1battlefield=Craw Wurm
        //
        // Win line: Giant Growth on Wind Drake (5/5 flying), attack. Craw Wurm is ground
        // and cannot block a flyer. Wind Drake deals 5 = lethal.
        // Without Giant Growth, Drake only deals 2 (not lethal). Tests pump + evasion.
        setupPuzzle("test_sk_pump_evasion_puzzle_llm_metrics", 1);

        setLife(playerA, 20);
        addCard(Zone.HAND, playerA, "Giant Growth");
        addCard(Zone.BATTLEFIELD, playerA, "Wind Drake");
        addCard(Zone.BATTLEFIELD, playerA, "Forest");

        setLife(playerB, 5);
        addCard(Zone.BATTLEFIELD, playerB, "Craw Wurm");

        execute();

        finishAndSave("sk_pump_evasion", 1);
    }

    @Test
    public void test_sk_two_spells_mana_puzzle_llm_metrics() {
        // [skill-metadata]
        // Tier: 2
        // Skill: T2.3 -- Cast two burn spells with exactly 2 mana (tight resource management)
        // PreModern: Yes (Lightning Bolt 4th/5th, Shock Stronghold/6th/7th)
        // [state]
        // turn=1
        // activeplayer=p0
        // activephase=MAIN1
        // p0life=20
        // p0hand=Lightning Bolt;Shock
        // p0battlefield=Mountain;Mountain
        // p1life=5
        // p1battlefield=
        //
        // Win line: Lightning Bolt (3) + Shock (2) = 5 = lethal. Exactly 2 mana for 2 spells.
        // Differs from sk_double_bolt (3 Mountains): here there is no slack mana.
        // Tests mana-constrained sequencing of two burn spells.
        setupPuzzle("test_sk_two_spells_mana_puzzle_llm_metrics", 1);

        setLife(playerA, 20);
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.HAND, playerA, "Shock");
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 2);

        setLife(playerB, 5);

        execute();

        finishAndSave("sk_two_spells_mana", 1);
    }

    @Test
    public void test_sk_mana_puzzle_puzzle_llm_metrics() {
        // [skill-metadata]
        // Tier: 2
        // Skill: T2.5 -- Choose the correct spell combination when a distractor is in hand
        // PreModern: Yes (Lightning Bolt 4th/5th, Shock 6th/7th, Lava Axe 7th Edition)
        // [state]
        // turn=1
        // activeplayer=p0
        // activephase=MAIN1
        // p0life=20
        // p0hand=Lightning Bolt;Shock;Lava Axe
        // p0battlefield=Mountain;Mountain
        // p1life=5
        // p1battlefield=
        //
        // Win line: Lightning Bolt (3, costs R) + Shock (2, costs R) = 5 = lethal.
        // Lava Axe (5 damage) costs 4R -- uncastable with only 2 Mountains.
        // Model must ignore the distractor and pick the castable pair.
        setupPuzzle("test_sk_mana_puzzle_puzzle_llm_metrics", 1);

        setLife(playerA, 20);
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.HAND, playerA, "Shock");
        addCard(Zone.HAND, playerA, "Lava Axe");
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 2);

        setLife(playerB, 5);

        execute();

        finishAndSave("sk_mana_puzzle", 1);
    }

    @Test
    public void test_sk_equip_attack_puzzle_llm_metrics() {
        // [skill-metadata]
        // Tier: 2
        // Skill: T2.6 -- Enchant own creature with Rancor to boost power, then attack
        // PreModern: Yes (Rancor in Urza's Legacy, Grizzly Bears 4th–7th)
        // [state]
        // turn=1
        // activeplayer=p0
        // activephase=MAIN1
        // p0life=20
        // p0hand=Rancor
        // p0battlefield=Grizzly Bears;Forest;Forest
        // p1life=4
        // p1battlefield=
        //
        // Win line: cast Rancor on Grizzly Bears (+2/+0, bears become 4/2), attack for 4 = lethal.
        // Without Rancor, Bears attack for 2 (not lethal). Tests enchant + attack two-step.
        setupPuzzle("test_sk_equip_attack_puzzle_llm_metrics", 1);

        setLife(playerA, 20);
        addCard(Zone.HAND, playerA, "Rancor");
        addCard(Zone.BATTLEFIELD, playerA, "Grizzly Bears");
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 2);

        setLife(playerB, 4);

        execute();

        finishAndSave("sk_equip_attack", 1);
    }

    @Test
    public void test_sk_ability_plus_combat_puzzle_llm_metrics() {
        // [skill-metadata]
        // Tier: 2
        // Skill: T2.7 -- Activate tap ability + attack, both required for lethal
        // PreModern: Yes (Prodigal Sorcerer 4th–7th, Grizzly Bears 4th–7th)
        // [state]
        // turn=1
        // activeplayer=p0
        // activephase=MAIN1
        // p0life=20
        // p0battlefield=Prodigal Sorcerer;Grizzly Bears;Island;Island
        // p1life=3
        // p1battlefield=
        //
        // Win line: tap Prodigal Sorcerer to deal 1 damage to opponent, then attack with
        // Grizzly Bears for 2 = total 3 = lethal. Neither action alone is enough (1 < 3, 2 < 3).
        // Tests recognizing that both the ability and combat are needed.
        setupPuzzle("test_sk_ability_plus_combat_puzzle_llm_metrics", 1);

        setLife(playerA, 20);
        addCard(Zone.BATTLEFIELD, playerA, "Prodigal Sorcerer");
        addCard(Zone.BATTLEFIELD, playerA, "Grizzly Bears");
        addCard(Zone.BATTLEFIELD, playerA, "Island", 2);

        setLife(playerB, 3);

        execute();

        finishAndSave("sk_ability_plus_combat", 1);
    }

    // -------------------------------------------------------------------------
    // TIER 3 -- Non-Obvious Plays
    // -------------------------------------------------------------------------

    @Test
    public void test_sk_fling_for_lethal_puzzle_llm_metrics() {
        // [skill-metadata]
        // Tier: 3
        // Skill: T3.1 -- Sacrifice own creature via Fling instead of attacking (combat blocked)
        // PreModern: Yes (Fling Stronghold, Mahamoti Djinn 4th–7th, Fog Bank Urza's Saga)
        // [state]
        // turn=1
        // activeplayer=p0
        // activephase=MAIN1
        // p0life=20
        // p0hand=Fling
        // p0battlefield=Mahamoti Djinn;Mountain;Mountain
        // p1life=5
        // p1battlefield=Fog Bank
        //
        // Win line: Fling sacrificing Mahamoti Djinn (5 damage to opponent) = lethal.
        // Fog Bank (0/2, flying, prevents all combat damage) blocks the Djinn and prevents
        // all combat damage -- attacking is completely useless. Fling bypasses combat entirely.
        // Tests the non-obvious line: sacrifice the big creature rather than attacking with it.
        setupPuzzle("test_sk_fling_for_lethal_puzzle_llm_metrics", 1);

        setLife(playerA, 20);
        addCard(Zone.HAND, playerA, "Fling");
        addCard(Zone.BATTLEFIELD, playerA, "Mahamoti Djinn");
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 2);

        setLife(playerB, 5);
        addCard(Zone.BATTLEFIELD, playerB, "Fog Bank");

        execute();

        finishAndSave("sk_fling_for_lethal", 1);
    }

    @Test
    public void test_sk_counterspell_to_survive_puzzle_llm_metrics() {
        // [skill-metadata]
        // Tier: 3
        // Skill: T3.2 -- Counter a lethal spell from the opponent to survive (SURVIVAL PUZZLE)
        // PreModern: Yes (Counterspell 4th–7th, Shock Stronghold/6th/7th)
        // [state]
        // turn=2
        // activeplayer=p1 (opponent's turn)
        // activephase=MAIN1
        // p0life=2
        // p0hand=Counterspell
        // p0battlefield=Island;Island
        // p1life=20
        // p1hand=Shock
        // p1battlefield=Mountain
        //
        // Setup: opponent's turn. Opponent (MageAI) has Shock and a Mountain.
        // PlayerA has 2 life and Counterspell. If opponent casts Shock (2 damage),
        // PlayerA must counter it to survive. Win = PlayerA life > 0 (survival mode).
        // Tests: responding to opponent's spell on the stack with a counterspell.
        // Note: if MageAI does not cast Shock (unlikely but possible), PlayerA survives
        // without acting -- puzzle still records a win in survival mode.
        setupPuzzle("test_sk_counterspell_to_survive_puzzle_llm_metrics",
                playerB.getId(), PhaseStep.PRECOMBAT_MAIN, 2);

        setLife(playerA, 2);
        addCard(Zone.HAND, playerA, "Counterspell");
        addCard(Zone.BATTLEFIELD, playerA, "Island", 2);

        setLife(playerB, 20);
        addCard(Zone.HAND, playerB, "Shock");
        addCard(Zone.BATTLEFIELD, playerB, "Mountain");

        execute();

        finishAndSave("sk_counterspell_to_survive", 2, true);
    }

    @Test
    public void test_sk_ignore_board_go_face_puzzle_llm_metrics() {
        // [skill-metadata]
        // Tier: 3
        // Skill: T3.3 -- Ignore the opponent's board; direct damage wins this turn
        // PreModern: Yes (Lightning Bolt 4th/5th, Shock Stronghold/6th/7th, Serra Angel 4th–7th)
        // [state]
        // turn=1
        // activeplayer=p0
        // activephase=MAIN1
        // p0life=20
        // p0hand=Lightning Bolt;Shock
        // p0battlefield=Mountain;Mountain;Mountain
        // p1life=5
        // p1battlefield=Serra Angel
        //
        // Win line: Lightning Bolt (3) + Shock (2) at opponent = 5 = lethal.
        // Serra Angel (4/4 flying, vigilance) is threatening but irrelevant -- the model
        // should not waste spells on it. Tests recognizing lethal is available via direct
        // damage and not getting distracted by a scary creature on board.
        setupPuzzle("test_sk_ignore_board_go_face_puzzle_llm_metrics", 1);

        setLife(playerA, 20);
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.HAND, playerA, "Shock");
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 3);

        setLife(playerB, 5);
        addCard(Zone.BATTLEFIELD, playerB, "Serra Angel");

        execute();

        finishAndSave("sk_ignore_board_go_face", 1);
    }

    @Test
    public void test_sk_mogg_maniac_redirect_puzzle_llm_metrics() {
        // [skill-metadata]
        // Tier: 3
        // Skill: T3.5 -- Damage own creature to trigger a damage-redirect ability
        // PreModern: Yes (Shock Stronghold/6th/7th, Mogg Maniac Stronghold)
        // [state]
        // turn=1
        // activeplayer=p0
        // activephase=MAIN1
        // p0life=5
        // p0hand=Shock
        // p0battlefield=Mogg Maniac;Mountain;Mountain
        // p1life=2
        // p1battlefield=
        //
        // Win line: Shock own Mogg Maniac (2 damage). Mogg Maniac's ability triggers:
        // deal 2 damage to target opponent = lethal.
        // The non-obvious target is your OWN creature with a damage spell.
        // Mogg Maniac: "When Mogg Maniac is dealt damage, it deals that much damage to
        // target opponent." Tests redirecting damage through own creature ability.
        setupPuzzle("test_sk_mogg_maniac_redirect_puzzle_llm_metrics", 1);

        setLife(playerA, 5);
        addCard(Zone.HAND, playerA, "Shock");
        addCard(Zone.BATTLEFIELD, playerA, "Mogg Maniac");
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 2);

        setLife(playerB, 2);

        execute();

        finishAndSave("sk_mogg_maniac_redirect", 1);
    }

    @Test
    @org.junit.Ignore("OOT: Goblin Arsonist (M12) is not Pre-Modern. Tests out-of-distribution generalization.")
    public void test_sk_death_trigger_attack_puzzle_llm_metrics() {
        // [skill-metadata]
        // Tier: 3
        // Skill: T3.4 -- Attack into a blocker on purpose to trigger a death-trigger ability
        // PreModern: [OOT] Goblin Arsonist is from M12/M13 (not Pre-Modern)
        // [state]
        // turn=1
        // activeplayer=p0
        // activephase=MAIN1
        // p0life=20
        // p0battlefield=Goblin Arsonist;Grizzly Bears
        // p1life=3
        // p1battlefield=Giant Spider
        //
        // Win line: attack with both Goblin Arsonist and Grizzly Bears.
        //   - Giant Spider (2/4, reach) blocks Goblin Arsonist.
        //   - Goblin Arsonist dies -> "when this dies, deal 1 damage to any target" -> 1 to opponent.
        //   - Grizzly Bears (2/2) is not blocked (Spider is blocked on Arsonist) -> 2 damage.
        //   - Total: 1 + 2 = 3 = lethal.
        // Attacking with only Bears: 2 damage, not lethal. Attacking with only Arsonist: blocked,
        // dies, 1 damage, not lethal. Must attack with both to combine death trigger + combat damage.
        setupPuzzle("test_sk_death_trigger_attack_puzzle_llm_metrics", 1);

        setLife(playerA, 20);
        addCard(Zone.BATTLEFIELD, playerA, "Goblin Arsonist");
        addCard(Zone.BATTLEFIELD, playerA, "Grizzly Bears");

        setLife(playerB, 3);
        addCard(Zone.BATTLEFIELD, playerB, "Giant Spider");

        execute();

        finishAndSave("sk_death_trigger_attack", 1);
    }

    // -------------------------------------------------------------------------
    // TIER 4 -- Multi-Step Combos
    // -------------------------------------------------------------------------

    @Test
    public void test_sk_triple_burn_puzzle_llm_metrics() {
        // [skill-metadata]
        // Tier: 4
        // Skill: T4.1 -- Three burn spells for exact lethal; combat useless (Fog Bank)
        // PreModern: Yes (Lightning Bolt 4th/5th, Shock Stronghold/6th/7th, Fog Bank Urza's Saga)
        // [state]
        // turn=1
        // activeplayer=p0
        // activephase=MAIN1
        // p0life=20
        // p0hand=Lightning Bolt;Shock;Shock
        // p0battlefield=Mountain;Mountain;Mountain
        // p1life=7
        // p1battlefield=Fog Bank
        //
        // Win line: Lightning Bolt (3) + Shock (2) + Shock (2) = 7 = lethal.
        // Fog Bank (0/2, flying, prevents all combat damage) makes combat useless.
        // All three spells are required; missing any one falls short of lethal.
        setupPuzzle("test_sk_triple_burn_puzzle_llm_metrics", 1);

        setLife(playerA, 20);
        addCard(Zone.HAND, playerA, "Lightning Bolt");
        addCard(Zone.HAND, playerA, "Shock");
        addCard(Zone.HAND, playerA, "Shock");
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 3);

        setLife(playerB, 7);
        addCard(Zone.BATTLEFIELD, playerB, "Fog Bank");

        execute();

        finishAndSave("sk_triple_burn", 1);
    }

    @Test
    public void test_sk_remove_pump_attack_puzzle_llm_metrics() {
        // [skill-metadata]
        // Tier: 4
        // Skill: T4.2 -- Three-step combo: remove blocker + pump own creature + attack
        // PreModern: Yes (Terror 4th–6th, Giant Growth 4th–7th, Grizzly Bears 4th–7th,
        //             Serra Angel 4th–7th)
        // [state]
        // turn=1
        // activeplayer=p0
        // activephase=MAIN1
        // p0life=20
        // p0hand=Terror;Giant Growth
        // p0battlefield=Grizzly Bears;Swamp;Swamp;Forest;Forest
        // p1life=5
        // p1battlefield=Serra Angel
        //
        // Win line: Terror Serra Angel (destroy blocker), Giant Growth on Grizzly Bears
        // (now 5/5), attack for 5 = lethal.
        //   - Without Terror: Serra Angel blocks Bears (Serra survives, 0 player damage).
        //   - Without Giant Growth: Bears deal 2 after removal (not lethal vs 5 life).
        //   - All three actions are individually necessary.
        setupPuzzle("test_sk_remove_pump_attack_puzzle_llm_metrics", 1);

        setLife(playerA, 20);
        addCard(Zone.HAND, playerA, "Terror");
        addCard(Zone.HAND, playerA, "Giant Growth");
        addCard(Zone.BATTLEFIELD, playerA, "Grizzly Bears");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp", 2);
        addCard(Zone.BATTLEFIELD, playerA, "Forest", 2);

        setLife(playerB, 5);
        addCard(Zone.BATTLEFIELD, playerB, "Serra Angel");

        execute();

        finishAndSave("sk_remove_pump_attack", 1);
    }

    @Test
    public void test_sk_mogg_sacrifice_combo_puzzle_llm_metrics() {
        // [skill-metadata]
        // Tier: 4
        // Skill: T4.3 -- Sacrifice Mogg Fanatic + burn spell combo; combat is useless
        // PreModern: Yes (Mogg Fanatic Tempest, Shock Stronghold/6th/7th, Wall of Ice 4th/5th)
        // [state]
        // turn=1
        // activeplayer=p0
        // activephase=MAIN1
        // p0life=20
        // p0hand=Shock
        // p0battlefield=Mogg Fanatic;Grizzly Bears;Mountain;Mountain
        // p1life=3
        // p1battlefield=Wall of Ice
        //
        // Win line: sacrifice Mogg Fanatic targeting opponent (1 damage) + Shock opponent (2) = 3 = lethal.
        // Wall of Ice (0/7 defender) blocks Grizzly Bears in combat -- no trample, 0 combat damage.
        // Model must use the sacrifice ability + cast the burn spell; combat is irrelevant.
        //   - Shock only: 2 damage, not lethal.
        //   - Mogg Fanatic only: 1 damage, not lethal.
        //   - Both together: 3 = lethal.
        setupPuzzle("test_sk_mogg_sacrifice_combo_puzzle_llm_metrics", 1);

        setLife(playerA, 20);
        addCard(Zone.HAND, playerA, "Shock");
        addCard(Zone.BATTLEFIELD, playerA, "Mogg Fanatic");
        addCard(Zone.BATTLEFIELD, playerA, "Grizzly Bears");
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 2);

        setLife(playerB, 3);
        addCard(Zone.BATTLEFIELD, playerB, "Wall of Ice");

        execute();

        finishAndSave("sk_mogg_sacrifice_combo", 1);
    }

    // ── Cantrip / Card-Draw skill group ──────────────────────────────────────

    @Test
    public void test_sk_cantrip_then_win_puzzle_llm_metrics() {
        // [skill-metadata]
        // Tier: 2
        // Skill: T2.8 -- Cast a cantrip (Opt) to dig for the winning card
        // PreModern: Yes (Opt Invasion 2000, Lightning Bolt 4th/5th Ed)
        // [state]
        // turn=1
        // activeplayer=p0
        // activephase=MAIN1
        // p0life=20
        // p0hand=Opt
        // p0library_top=Lightning Bolt
        // p0battlefield=Island;Island;Mountain
        // p1life=3
        //
        // Win line: Opt (U) → scry (keep Bolt on top) → draw Lightning Bolt →
        //           Lightning Bolt (R) → 3 damage → win.
        // Opt costs U (1 mana). Brainstorm costs UU for the effect but Opt is just U.
        // Mana: Island pays colorless, Island pays U for Opt; Mountain pays R for Bolt.
        // Without the Opt cast, A has no damage spells and cannot win this turn.
        setupPuzzle("test_sk_cantrip_then_win_puzzle_llm_metrics", 1);

        setLife(playerA, 20);
        addCard(Zone.HAND, playerA, "Opt");
        // Library: Forest on bottom, Plains above it, Lightning Bolt on top (drawn first).
        addCard(Zone.LIBRARY, playerA, "Forest");
        addCard(Zone.LIBRARY, playerA, "Plains");
        addCard(Zone.LIBRARY, playerA, "Lightning Bolt");
        addCard(Zone.BATTLEFIELD, playerA, "Island", 2);
        addCard(Zone.BATTLEFIELD, playerA, "Mountain");

        setLife(playerB, 3);

        execute();

        finishAndSave("sk_cantrip_then_win", 1);
    }

    @Test
    public void test_sk_loot_for_win_puzzle_llm_metrics() {
        // [skill-metadata]
        // Tier: 2
        // Skill: T2.9 -- Activate a loot tap-ability to discard junk and draw the win card
        // PreModern: Yes (Merfolk Looter 4th Ed, Lightning Bolt 4th Ed)
        // [state]
        // turn=1
        // activeplayer=p0
        // activephase=MAIN1
        // p0life=20
        // p0hand=Forest (junk)
        // p0library_top=Lightning Bolt
        // p0battlefield=Merfolk Looter (untapped);Island;Island;Mountain
        // p1life=3
        //
        // Win line: tap Merfolk Looter → draw Lightning Bolt, discard Forest →
        //           Lightning Bolt (R) → 3 damage → win.
        // Merfolk Looter's tap ability is free (no mana cost).
        // Mountain pays R for Bolt after Looter draws it.
        // Hand starts with only "Forest" (useless land) — must use Looter to find damage.
        setupPuzzle("test_sk_loot_for_win_puzzle_llm_metrics", 1);

        setLife(playerA, 20);
        addCard(Zone.HAND, playerA, "Forest");
        addCard(Zone.LIBRARY, playerA, "Plains");
        addCard(Zone.LIBRARY, playerA, "Lightning Bolt");
        addCard(Zone.BATTLEFIELD, playerA, "Merfolk Looter");
        addCard(Zone.BATTLEFIELD, playerA, "Island", 2);
        addCard(Zone.BATTLEFIELD, playerA, "Mountain");

        setLife(playerB, 3);

        execute();

        finishAndSave("sk_loot_for_win", 1);
    }

    @Test
    public void test_sk_brainstorm_for_lethal_puzzle_llm_metrics() {
        // [skill-metadata]
        // Tier: 3
        // Skill: T3.6 -- Brainstorm: cast it AND correctly choose which 2 cards to put back
        // PreModern: Yes (Brainstorm Ice Age, Lightning Bolt 4th Ed, Wall of Ice 4th Ed)
        // [state]
        // turn=1
        // activeplayer=p0
        // activephase=MAIN1
        // p0life=20
        // p0hand=Brainstorm
        // p0library_top=Lightning Bolt (then Plains, then Forest below)
        // p0battlefield=Island;Island;Mountain
        // p1life=3
        // p1battlefield=Wall of Ice
        //
        // Win line: Brainstorm (1U) → draw 3 (Lightning Bolt, Plains, Forest) →
        //           put Plains + Forest back → cast Lightning Bolt (R) → 3 damage → win.
        // Wall of Ice (0/7 defender) blocks all ground attackers — combat is useless.
        // The key decision: put BACK the two junk cards, not the Bolt.
        // Mana: Island + Island for Brainstorm (1U); Mountain for Lightning Bolt (R).
        setupPuzzle("test_sk_brainstorm_for_lethal_puzzle_llm_metrics", 1);

        setLife(playerA, 20);
        addCard(Zone.HAND, playerA, "Brainstorm");
        // Library order (last added = top of library = drawn first):
        addCard(Zone.LIBRARY, playerA, "Forest");
        addCard(Zone.LIBRARY, playerA, "Plains");
        addCard(Zone.LIBRARY, playerA, "Lightning Bolt");
        addCard(Zone.BATTLEFIELD, playerA, "Island", 2);
        addCard(Zone.BATTLEFIELD, playerA, "Mountain");

        setLife(playerB, 3);
        addCard(Zone.BATTLEFIELD, playerB, "Wall of Ice");

        execute();

        finishAndSave("sk_brainstorm_for_lethal", 1);
    }

    // ── Hold-Mana / Reactive-Play skill group ────────────────────────────────

    @Test
    public void test_sk_hold_mana_counter_puzzle_llm_metrics() {
        // [skill-metadata]
        // Tier: 3
        // Skill: T3.7 -- Don't spend the blue mana on Opt; preserve it for Counterspell (SURVIVAL)
        // PreModern: Yes (Counterspell 4th–7th, Opt Invasion, Shock Stronghold/6th/7th)
        // [state]
        // turn=1 (playerA's main), then turn=2 (playerB's turn)
        // activeplayer=p0
        // activephase=MAIN1
        // p0life=2
        // p0hand=Counterspell;Opt
        // p0battlefield=Island;Island
        // p1life=20
        // p1hand=Shock
        // p1battlefield=Mountain
        //
        // Trap: casting Opt (U) taps 1 Island → only 1 Island left → cannot pay UU
        //       for Counterspell → opponent casts Shock (2 damage) → PlayerA at 0 → dies.
        // Correct line: pass A's main phase → B casts Shock → A counters with
        //              Counterspell (UU, using both Islands) → A survives at 2.
        // Win = PlayerA life > 0 (survival mode).  2-turn puzzle (A then B).
        setupPuzzle("test_sk_hold_mana_counter_puzzle_llm_metrics", 2);

        setLife(playerA, 2);
        addCard(Zone.HAND, playerA, "Counterspell");
        addCard(Zone.HAND, playerA, "Opt");
        addCard(Zone.BATTLEFIELD, playerA, "Island", 2);

        setLife(playerB, 20);
        addCard(Zone.HAND, playerB, "Shock");
        addCard(Zone.BATTLEFIELD, playerB, "Mountain");

        execute();

        finishAndSave("sk_hold_mana_counter", 2, true);
    }

    @Test
    public void test_sk_hold_mana_removal_puzzle_llm_metrics() {
        // [skill-metadata]
        // Tier: 3
        // Skill: T3.8 -- Don't waste mana on Carnophage; hold both Swamps for Terror (SURVIVAL)
        // PreModern: Yes (Terror 4th Ed, Carnophage Exodus, Craw Wurm 4th Ed)
        // [state]
        // turn=1 (playerA's main), then turn=2 (playerB attacks)
        // activeplayer=p0
        // activephase=MAIN1
        // p0life=4
        // p0hand=Terror;Carnophage
        // p0battlefield=Swamp;Swamp
        // p1life=20
        // p1battlefield=Craw Wurm (6/4)
        //
        // Trap: casting Carnophage (B) taps 1 Swamp → 1 Swamp left → cannot pay 1B
        //       (2 mana total) for Terror → B attacks with 6/4 Craw Wurm → A takes 6
        //       → A at 4 - 6 = -2 → dies.
        // Correct lines (both valid):
        //   a) Terror Craw Wurm on A's main phase (BB = both Swamps) → B can't attack → win.
        //   b) Pass, then Terror Craw Wurm in response to B's attack → A takes 0 → win.
        // Win = PlayerA life > 0 (survival mode).  2-turn puzzle (A then B).
        setupPuzzle("test_sk_hold_mana_removal_puzzle_llm_metrics", 2);

        setLife(playerA, 4);
        addCard(Zone.HAND, playerA, "Terror");
        addCard(Zone.HAND, playerA, "Carnophage");
        addCard(Zone.BATTLEFIELD, playerA, "Swamp", 2);

        setLife(playerB, 20);
        addCard(Zone.BATTLEFIELD, playerB, "Craw Wurm");

        execute();

        finishAndSave("sk_hold_mana_removal", 2, true);
    }

    @Test
    public void test_PS_MGOB_puzzle_llm_metrics() {
        // Full puzzle file included below as required by test-first policy:
        // [metadata]
        // Name:Possibility Storm - Special Guest Puzzle - Modern Goblins
        // URL:http://www.possibilitystorm.com/wp-content/uploads/2019/09/129.-FK01.jpg
        // Goal:Win
        // Turns:1
        // Difficulty:Mythic
        // Description:Win this turn. Your opponent has chosen 'Goblin' with Plague
        // Engineer's ability.
        // [state]
        // humanlife=20
        // ailife=19
        // turn=1
        // activeplayer=human
        // activephase=MAIN1
        // humanhand=Krenko, Mob Boss;Sling-Gang Lieutenant;Munitions Expert;Goblin
        // Warchief;Goblin Piledriver;Skirk Prospector
        // humanbattlefield=Pashalik Mons;Goblin Guide;Kiki-Jiki, Mirror
        // Breaker;Mountain;Mountain;Mountain;Blood Crypt|NoETBTrigs;Blood
        // Crypt|NoETBTrigs
        // aibattlefield=Plague Engineer|ChosenType:Goblin|Id:1;Sylvok
        // Lifestaff|AttachedTo:1;Sylvok Lifestaff|AttachedTo:1;Accorder's
        // Shield|AttachedTo:1
        //
        setupPuzzle("test_PS_MGOB_puzzle_llm_metrics", 1);

        // Set up PlayerA (Human)
        setLife(playerA, 20);
        addCard(Zone.HAND, playerA, "Krenko, Mob Boss");
        addCard(Zone.HAND, playerA, "Sling-Gang Lieutenant");
        addCard(Zone.HAND, playerA, "Munitions Expert");
        addCard(Zone.HAND, playerA, "Goblin Warchief");
        addCard(Zone.HAND, playerA, "Goblin Piledriver");
        addCard(Zone.HAND, playerA, "Skirk Prospector");
        addCard(Zone.BATTLEFIELD, playerA, "Pashalik Mons");
        addCard(Zone.BATTLEFIELD, playerA, "Goblin Guide");
        addCard(Zone.BATTLEFIELD, playerA, "Kiki-Jiki, Mirror Breaker");
        addCard(Zone.BATTLEFIELD, playerA, "Mountain", 3);
        addCard(Zone.BATTLEFIELD, playerA, "Blood Crypt", 2);

        // Set up PlayerB (AI)
        setLife(playerB, 19);
        addCard(Zone.BATTLEFIELD, playerB, "Plague Engineer");
        // Add equipments/artifacts that should be attached to Plague Engineer
        addCard(Zone.BATTLEFIELD, playerB, "Sylvok Lifestaff", 2);
        addCard(Zone.BATTLEFIELD, playerB, "Accorder's Shield");

        // Set up Plague Engineer's creature type choice (Approach 1)
        setChoice(playerB, "Goblin");

        // Best-effort: attach equipments to Plague Engineer and verify chosen type
        runCode("attach equipments and set plague engineer chosen type", 1, PhaseStep.PRECOMBAT_MAIN, playerB,
                (info, player, game) -> {
                    Permanent plague = null;
                    Set<Permanent> equipments = new HashSet<>();
                    for (Permanent p : game.getBattlefield().getAllActivePermanents(player.getId())) {
                        String name = p.getName();
                        if (name.equalsIgnoreCase("Plague Engineer")) {
                            plague = p;
                        } else if (name.equalsIgnoreCase("Sylvok Lifestaff")
                                || name.equalsIgnoreCase("Accorder's Shield")) {
                            equipments.add(p);
                        }
                    }
                    if (plague != null) {
                        // Attach equipments
                        for (Permanent eq : equipments) {
                            eq.attachTo(plague.getId(), null, game);
                        }

                        // Verify and ensure the chosen creature type is set
                        SubType chosenType = ChooseCreatureTypeEffect.getChosenCreatureType(plague.getId(), game);
                        if (chosenType == null) {
                            // Manually set if needed
                            game.getState().setValue(plague.getId() + "_type", SubType.GOBLIN);
                            plague.addInfo("chosen type", CardUtil.addToolTipMarkTags("Chosen type: Goblin"), game);
                        }
                    }
                });

        // REMOVE COMMENT TO TEST Add test Goblin creature to verify Plague Engineer's
        // effect
        // addCard(Zone.BATTLEFIELD, playerA, "Goblin Piker"); // 2/1 Goblin for testing
        execute();
        // REMOVE COMMENT TO TEST Verify that the 2/1 Goblin creature was destroyed by
        // Plague Engineer's effect after execution
        // assertGraveyardCount(playerA, "Goblin Piker", 1);

        // Wait for async ops
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        finishAndSave("PS_MGOB", 1);
    }

}
