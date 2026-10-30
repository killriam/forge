package forge.ai.simulation;

import java.util.ArrayList;
import java.util.List;

import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import forge.game.Game;
import forge.game.card.Card;
import forge.game.card.CounterEnumType;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

/**
 * Checks the Star Trek Commander (TRC/TRK) card scripts for the Federation Fleet, Klingon Fury and We Are the Borg decks:
 * every script loads and builds its abilities, and the main mechanics resolve as printed.
 */
public class StarTrekCommanderCardsTest extends SimulationTest {

    private static final List<String> TASK_CARDS = java.util.Arrays.asList(
            "Alexander, Ship's Fool",
            "Ambassador K'Ehleyr",
            "Ambulatory Reactor",
            "Assimilate the Wounded",
            "Assimilation Assessor",
            "B'Elanna Torres, Scrapper",
            "Battle of Wolf 359",
            "Beverly Crusher, Ship's Doctor",
            "Blood-Sport Champion",
            "Borg Incursion",
            "Borg Scout Sphere",
            "Captain Janeway, Endgame",
            "Chancellor Gorkon",
            "Chief Miles O'Brien",
            "Class-4 Tactical Cube",
            "Cmdr. Troi, Outgoing Empath",
            "Co-Option Adjutant",
            "Collective Override",
            "Command Subadjunct",
            "Commander William Riker",
            "Conquest Mission",
            "Curzon Dax, Bound by Blood",
            "Data, Exploring Humanity",
            "Deliver from Chaos",
            "Dr. Pulaski, Cynical Clinician",
            "Emergency Regeneration",
            "Ensign Harry Kim, Operations",
            "Extraction Integrant",
            "Ezri Dax, Newly Joined",
            "Ferocious Colonist",
            "Gagh Slinger",
            "Galvanizing Tenor",
            "Geordi La Forge, Innovator",
            "Gift of Perfection",
            "Glory to the Empire!",
            "Gnarling Targ",
            "Grilka, Widowed Warrior",
            "Guinan, Ancient Advisor",
            "Hugh, First of Many",
            "I.K.S. Negh'Var, Imperial Flagship",
            "Imperial Hullbreachers",
            "Indelicate Healer",
            "Jean-Luc Picard, Luminary",
            "Julian Bashir, Frontier Doctor",
            "Jurati, Cordial Queen",
            "K't'inga-Class Battle Cruiser",
            "K'Vort-Class Raider",
            "Kang, Suspicious Captain",
            "Kes, Evolving Telepath",
            "Kor, Cause of Vengeance",
            "Korok, Liberated Rage",
            "Kurn, Son of Mogh",
            "Locutus of Borg",
            "Martok, Hero of the Empire",
            "Maturation Chamber",
            "Merciful Mandate",
            "On the Shoulders of Giants",
            "One, Borg Evolved",
            "Optimization Annex",
            "Own the Day",
            "Qo'noS Quartermaster",
            "Queen's Diamond",
            "Reclamation Specialist",
            "Reconnaissance Partition",
            "Reg Barclay, Fidgety Fantasist",
            "Right of Vengeance",
            "Rite of Ascension",
            "Ro Laren, Double Agent",
            "Rom and Leeta, Unlikely Pair",
            "Seven of Nine, Tertiary Adjunct",
            "Shore Leave Planet",
            "Sins of the Father",
            "Stratagem Emulator",
            "Supplementation Node",
            "Temporal Tribulation",
            "The Beacon of Kahless",
            "The Best of Both Worlds",
            "The Promise of Kahless",
            "The Sword of Kahless",
            "Tom Paris, Risk-Taker",
            "Transwarp Hub",
            "Tuvok, Chief Tactical Officer",
            "Type-03 Borg Vessel",
            "U.S.S. Defiant, Tough Little Ship",
            "U.S.S. Enterprise-C, Time-Tossed",
            "U.S.S. Enterprise-E, Sovereign",
            "U.S.S. Prometheus, Prototype",
            "U.S.S. Stargazer, Here and There",
            "Unification",
            "Vic Fontaine, One Night Only",
            "Voq, Torchbearer",
            "Võx, Child of the Collective",
            "Worf, Son of Mogh");

    @Test
    public void testAllScriptsLoadAndBuildAbilities() {
        final Game game = initAndCreateGame();
        final Player p = game.getPlayers().get(1);
        final List<String> names = TASK_CARDS;
        AssertJUnit.assertEquals(93, names.size());
        final List<String> failures = new ArrayList<>();
        for (final String name : names) {
            try {
                final Card c = addCardToZone(name, p, ZoneType.Hand);
                AssertJUnit.assertEquals(name, c.getName());
                // building the spell abilities, triggers and statics parses every SVar they use
                c.getSpellAbilities();
                c.getTriggers();
                c.getStaticAbilities();
                c.getReplacementEffects();
            } catch (final Throwable t) {
                failures.add(name + ": " + t);
            }
        }
        AssertJUnit.assertTrue(failures.toString(), failures.isEmpty());
    }

    private static int count(final Game game, final Player p, final String name) {
        int n = 0;
        for (final Card c : p.getCardsIn(ZoneType.Battlefield)) {
            if (c.getName().equals(name)) {
                n++;
            }
        }
        return n;
    }

    private Game cast(final Game game, final Player p, final Card spell) {
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, p);
        game.getAction().checkStateEffects(true);
        final SpellAbility sa = spell.getSpellAbilities().get(0);
        sa.setActivatingPlayer(p);
        final GameSimulator sim = createSimulator(p);
        sim.simulateSpellAbility(sa);
        return sim.getSimulatedGameState();
    }

    @Test
    public void testBattleOfWolf359() {
        final Game game = initAndCreateGame();
        final Player opp = game.getPlayers().get(0);
        final Player p = game.getPlayers().get(1);
        addCards("Swamp", 5, p);
        addCards("Ornithopter", 2, p);
        addCard("Runeclaw Bear", p);
        addCard("Runeclaw Bear", opp);
        final Game sim = cast(game, p, addCardToZone("Battle of Wolf 359", p, ZoneType.Hand));
        AssertJUnit.assertEquals(0, count(sim, sim.getPlayer(p.getId()), "Runeclaw Bear"));
        AssertJUnit.assertEquals(0, count(sim, sim.getPlayer(opp.getId()), "Runeclaw Bear"));
        AssertJUnit.assertEquals(2, count(sim, sim.getPlayer(p.getId()), "Ornithopter"));
    }

    @Test
    public void testBorgIncursion() {
        final Game game = initAndCreateGame();
        final Player p = game.getPlayers().get(1);
        addCards("Plains", 3, p);
        final Game sim = cast(game, p, addCardToZone("Borg Incursion", p, ZoneType.Hand));
        AssertJUnit.assertEquals(2, count(sim, sim.getPlayer(p.getId()), "Borg Token"));
    }

    @Test
    public void testGloryToTheEmpire() {
        final Game game = initAndCreateGame();
        final Player p = game.getPlayers().get(1);
        addCards("Mountain", 6, p);
        final Game sim = cast(game, p, addCardToZone("Glory to the Empire!", p, ZoneType.Hand));
        final Player sp = sim.getPlayer(p.getId());
        AssertJUnit.assertEquals(1, count(sim, sp, "Klingon Warrior Token"));
        for (final Card c : sp.getCardsIn(ZoneType.Battlefield)) {
            if (c.getName().equals("Klingon Warrior Token")) {
                AssertJUnit.assertTrue(c.hasKeyword("Melee"));
                AssertJUnit.assertTrue(c.hasKeyword("Haste"));
            }
        }
    }

    @Test
    public void testConquestMissionMakesBorgTokens() {
        final Game game = initAndCreateGame();
        final Player p = game.getPlayers().get(1);
        addCard("Conquest Mission", p);
        final Game sim = cast(game, p, addCardToZone("Ornithopter", p, ZoneType.Hand));
        final Player sp = sim.getPlayer(p.getId());
        AssertJUnit.assertEquals(1, count(sim, sp, "Borg Token"));
        for (final Card c : sp.getCardsIn(ZoneType.Battlefield)) {
            if (c.getName().equals("Conquest Mission")) {
                AssertJUnit.assertEquals(c.getCounters().toString(), 1, c.getCounters(CounterEnumType.QUEST));
            }
        }
    }

    @Test
    public void testSecondDrawTriggersSeekOut() {
        final Game game = initAndCreateGame();
        final Player p = game.getPlayers().get(1);
        fillLibrary(p, 10);
        addCards("Island", 3, p);
        addCard("Dr. Pulaski, Cynical Clinician", p);
        addCard("Rom and Leeta, Unlikely Pair", p);
        final int life = p.getLife();
        final Game sim = cast(game, p, addCardToZone("Divination", p, ZoneType.Hand));
        final Player sp = sim.getPlayer(p.getId());
        AssertJUnit.assertEquals(life + 4, sp.getLife());
        AssertJUnit.assertEquals(2, count(sim, sp, "Treasure Token"));
    }

    @Test
    public void testStationedSpacecraftBecomesCreature() {
        final Game game = initAndCreateGame();
        final Player p = game.getPlayers().get(1);
        final Card sphere = addCard("Borg Scout Sphere", p);
        final Card borg = addToken("c_1_1_a_borg", p);
        game.getAction().checkStateEffects(true);
        AssertJUnit.assertFalse(sphere.isCreature());
        AssertJUnit.assertFalse(borg.hasKeyword("Flying"));
        sphere.setCounters(CounterEnumType.CHARGE, 6);
        game.getAction().checkStateEffects(true);
        AssertJUnit.assertTrue(sphere.isCreature());
        AssertJUnit.assertTrue(borg.hasKeyword("Flying"));
    }

    @Test
    public void testGiftOfPerfectionAssimilates() {
        final Game game = initAndCreateGame();
        final Player opp = game.getPlayers().get(0);
        final Player p = game.getPlayers().get(1);
        addCards("Island", 5, p);
        addCardToZone("Runeclaw Bear", opp, ZoneType.Library);
        final Game sim = cast(game, p, addCardToZone("Gift of Perfection", p, ZoneType.Hand));
        final Player sp = sim.getPlayer(p.getId());
        AssertJUnit.assertEquals(1, count(sim, sp, "Runeclaw Bear"));
        for (final Card c : sp.getCardsIn(ZoneType.Battlefield)) {
            if (c.getName().equals("Runeclaw Bear")) {
                AssertJUnit.assertTrue(c.getType().hasSubtype("Borg"));
                AssertJUnit.assertTrue(c.isArtifact());
                AssertJUnit.assertFalse(c.getType().hasSubtype("Bear"));
                AssertJUnit.assertEquals(1, c.getCounters(CounterEnumType.P1P1));
            }
        }
    }

    private Game activate(final Game game, final Player p, final Card source, final String prefix) {
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, p);
        game.getAction().checkStateEffects(true);
        source.setSickness(false);
        final SpellAbility sa = findSAWithPrefix(source, prefix);
        AssertJUnit.assertNotNull("no ability starting with " + prefix, sa);
        sa.setActivatingPlayer(p);
        final GameSimulator sim = createSimulator(p);
        sim.simulateSpellAbility(sa);
        return sim.getSimulatedGameState();
    }

    private static Card find(final Player p, final String name) {
        for (final Card c : p.getCardsIn(ZoneType.Battlefield)) {
            if (c.getName().equals(name)) {
                return c;
            }
        }
        return null;
    }

    @Test
    public void testAlexanderDamagesRandomOpponentByGreatestRevealed() {
        final Game game = initAndCreateGame();
        final Player opp = game.getPlayers().get(0);
        final Player p = game.getPlayers().get(1);
        fillLibrary(p, 5);
        fillLibrary(opp, 5);
        final Card alex = addCard("Alexander, Ship's Fool", p);
        final Game sim = activate(game, p, alex, "{T}: Each player");
        AssertJUnit.assertEquals(18, sim.getPlayer(opp.getId()).getLife());
    }

    @Test
    public void testCurzonDaxPingsWhenKlingonDies() {
        final Game game = initAndCreateGame();
        final Player opp = game.getPlayers().get(0);
        final Player p = game.getPlayers().get(1);
        addCards("Plains", 4, p);
        addCard("Curzon Dax, Bound by Blood", p);
        addCard("Blood-Sport Champion", p);
        final Game sim = cast(game, p, addCardToZone("Day of Judgment", p, ZoneType.Hand));
        // Curzon sees the Champion die along with him: 1 damage; his own death trigger has no creature to put in
        AssertJUnit.assertEquals(19, sim.getPlayer(opp.getId()).getLife());
    }

    @Test
    public void testEzriDaxResolvesWithOptionalCopyReplacement() {
        final Game game = initAndCreateGame();
        final Player p = game.getPlayers().get(1);
        addCards("Plains", 2, p);
        final Card bear = addCard("Runeclaw Bear", p);
        game.getAction().destroy(bear, null, false, null);
        final Game sim = cast(game, p, addCardToZone("Ezri Dax, Newly Joined", p, ZoneType.Hand));
        final Card ezri = find(sim.getPlayer(p.getId()), "Ezri Dax, Newly Joined");
        AssertJUnit.assertNotNull(ezri);
        AssertJUnit.assertTrue(ezri.getType().isLegendary());
        AssertJUnit.assertTrue(ezri.getType().hasSubtype("Trill"));
    }

    @Test
    public void testGrilkaStealsUntilEndOfTurn() {
        final Game game = initAndCreateGame();
        final Player opp = game.getPlayers().get(0);
        final Player p = game.getPlayers().get(1);
        addCards("Mountain", 6, p);
        addCard("Runeclaw Bear", opp);
        final Game sim = cast(game, p, addCardToZone("Grilka, Widowed Warrior", p, ZoneType.Hand));
        final Card bear = find(sim.getPlayer(p.getId()), "Runeclaw Bear");
        AssertJUnit.assertNotNull(bear);
        AssertJUnit.assertTrue(bear.hasKeyword("Haste"));
        AssertJUnit.assertTrue(bear.hasKeyword("Trample"));
        AssertJUnit.assertFalse(bear.isTapped());
    }

    @Test
    public void testGuinanPumpsNewCreaturesAndDraws() {
        final Game game = initAndCreateGame();
        final Player p = game.getPlayers().get(1);
        fillLibrary(p, 5);
        final Card guinan = addCard("Guinan, Ancient Advisor", p);
        addCard("Runeclaw Bear", p);
        addCard("Runeclaw Bear", p);
        addCards("Plains", 1, p);
        final int hand = p.getCardsIn(ZoneType.Hand).size();
        final Game sim = activate(game, p, guinan, "{1}, {T}: Put");
        final Player sp = sim.getPlayer(p.getId());
        int pumped = 0;
        for (final Card c : sp.getCardsIn(ZoneType.Battlefield)) {
            if (c.getName().equals("Runeclaw Bear") && c.getCounters(CounterEnumType.P1P1) == 1) {
                pumped++;
            }
        }
        AssertJUnit.assertEquals(2, pumped);
        AssertJUnit.assertEquals(hand + 1, sp.getCardsIn(ZoneType.Hand).size());
    }

    @Test
    public void testSinsOfTheFatherChapterOne() {
        final Game game = initAndCreateGame();
        final Player opp = game.getPlayers().get(0);
        final Player p = game.getPlayers().get(1);
        addCards("Swamp", 3, p);
        addCards("Mountain", 2, p);
        addCard("Runeclaw Bear", opp);
        final Game sim = cast(game, p, addCardToZone("Sins of the Father", p, ZoneType.Hand));
        AssertJUnit.assertEquals(0, count(sim, sim.getPlayer(opp.getId()), "Runeclaw Bear"));
    }

    @Test
    public void testBestOfBothWorldsChapterOneAssimilates() {
        final Game game = initAndCreateGame();
        final Player opp = game.getPlayers().get(0);
        final Player p = game.getPlayers().get(1);
        addCards("Plains", 3, p);
        addCards("Island", 3, p);
        addCard("Runeclaw Bear", opp);
        final Game sim = cast(game, p, addCardToZone("The Best of Both Worlds", p, ZoneType.Hand));
        final Card bear = find(sim.getPlayer(p.getId()), "Runeclaw Bear");
        AssertJUnit.assertNotNull(bear);
        AssertJUnit.assertTrue(bear.isArtifact());
        AssertJUnit.assertTrue(bear.getType().hasSubtype("Borg"));
        AssertJUnit.assertEquals(1, bear.getCounters(CounterEnumType.P1P1));
    }

    @Test
    public void testEnterpriseEDestroysTappedCreatures() {
        final Game game = initAndCreateGame();
        final Player opp = game.getPlayers().get(0);
        final Player p = game.getPlayers().get(1);
        addCards("Plains", 5, p);
        final Card tapped = addCard("Runeclaw Bear", opp);
        tapped.setTapped(true);
        addCard("Runeclaw Bear", opp);
        final Game sim = cast(game, p, addCardToZone("U.S.S. Enterprise-E, Sovereign", p, ZoneType.Hand));
        AssertJUnit.assertEquals(1, count(sim, sim.getPlayer(opp.getId()), "Runeclaw Bear"));
    }

    @Test
    public void testTranswarpHubMillsFiveAndTakesArtifacts() {
        final Game game = initAndCreateGame();
        final Player p = game.getPlayers().get(1);
        addCards("Island", 3, p);
        for (int i = 0; i < 4; i++) {
            addCardToZone("Ornithopter", p, ZoneType.Library);
        }
        addCardToZone("Runeclaw Bear", p, ZoneType.Library);
        final Game sim = cast(game, p, addCardToZone("Transwarp Hub", p, ZoneType.Hand));
        final Player sp = sim.getPlayer(p.getId());
        int ornis = 0;
        for (final Card c : sp.getCardsIn(ZoneType.Hand)) {
            if (c.getName().equals("Ornithopter")) {
                ornis++;
            }
        }
        AssertJUnit.assertEquals("hand=" + sp.getCardsIn(ZoneType.Hand) + " gy=" + sp.getCardsIn(ZoneType.Graveyard) + " lib=" + sp.getCardsIn(ZoneType.Library) + " bf=" + sp.getCardsIn(ZoneType.Battlefield), 2, ornis);
    }

    private static int chargeCounters(final Player p) {
        int n = 0;
        for (final Card c : p.getCardsIn(ZoneType.Battlefield)) {
            n += c.getCounters(CounterEnumType.CHARGE);
        }
        return n;
    }

    @Test
    public void testSevenOfNineReflexiveChargeCounters() {
        final Game game = initAndCreateGame();
        final Player p = game.getPlayers().get(1);
        for (int i = 0; i < 5; i++) {
            addCardToZone("Runeclaw Bear", p, ZoneType.Library);
        }
        final Card seven = addCard("Seven of Nine, Tertiary Adjunct", p);
        final Game sim = activate(game, p, seven, "That Is Irrelevant");
        final Player sp = sim.getPlayer(p.getId());
        AssertJUnit.assertEquals(1, sp.getCardsIn(ZoneType.Graveyard).size());
        AssertJUnit.assertEquals(2, chargeCounters(sp));
    }

    @Test
    public void testMaturationChamberAndOptimizationAnnex() {
        final Game game = initAndCreateGame();
        final Player p = game.getPlayers().get(1);
        addCard("Maturation Chamber", p);
        final Game plain = cast(game, p, addCardToZone("Ornithopter", p, ZoneType.Hand));
        AssertJUnit.assertEquals(1, chargeCounters(plain.getPlayer(p.getId())));

        final Game game2 = initAndCreateGame();
        final Player p2 = game2.getPlayers().get(1);
        addCard("Maturation Chamber", p2);
        addCard("Optimization Annex", p2);
        final Game boosted = cast(game2, p2, addCardToZone("Ornithopter", p2, ZoneType.Hand));
        AssertJUnit.assertEquals(2, chargeCounters(boosted.getPlayer(p2.getId())));
    }

    @Test
    public void testTemporalTribulationExilesTwoPermanents() {
        final Game game = initAndCreateGame();
        final Player p = game.getPlayers().get(1);
        addCards("Island", 3, p);
        final Card b1 = addCard("Runeclaw Bear", p);
        final Card b2 = addCard("Runeclaw Bear", p);
        final Card spell = addCardToZone("Temporal Tribulation", p, ZoneType.Hand);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, p);
        game.getAction().checkStateEffects(true);
        final SpellAbility sa = spell.getSpellAbilities().get(0);
        sa.setActivatingPlayer(p);
        sa.getTargets().add(b1);
        sa.getTargets().add(b2);
        final GameSimulator sim = createSimulator(p);
        sim.simulateSpellAbility(sa);
        AssertJUnit.assertEquals(2, sim.getSimulatedGameState().getPlayer(p.getId()).getCardsIn(ZoneType.Exile).size());
    }

    @Test
    public void testRegBarclayCopiesCreatureOnSecondDraw() {
        final Game game = initAndCreateGame();
        final Player p = game.getPlayers().get(1);
        fillLibrary(p, 6);
        addCards("Island", 3, p);
        addCard("Reg Barclay, Fidgety Fantasist", p);
        addCard("Runeclaw Bear", p);
        final Game sim = cast(game, p, addCardToZone("Divination", p, ZoneType.Hand));
        final Player sp = sim.getPlayer(p.getId());
        final List<Card> copies = new ArrayList<>();
        for (final Card c : sp.getCardsIn(ZoneType.Battlefield)) {
            if (c.getName().equals("Runeclaw Bear") && c.isToken()) {
                copies.add(c);
            }
        }
        AssertJUnit.assertEquals(1, copies.size());
        AssertJUnit.assertTrue(copies.get(0).isArtifact());
        AssertJUnit.assertTrue(copies.get(0).getType().hasSubtype("Illusion"));
        AssertJUnit.assertTrue(copies.get(0).hasKeyword("Haste"));
    }

    @Test
    public void testPromiseOfKahlessReturnsOtherKlingons() {
        final Game game = initAndCreateGame();
        final Player p = game.getPlayers().get(1);
        addCards("Swamp", 5, p);
        addCard("Blood-Sport Champion", p);
        final Card inGy = addCardToZone("Gagh Slinger", p, ZoneType.Graveyard);
        final Game sim = cast(game, p, addCardToZone("The Promise of Kahless", p, ZoneType.Hand));
        final Player sp = sim.getPlayer(p.getId());
        AssertJUnit.assertEquals(1, count(sim, sp, inGy.getName()));
        AssertJUnit.assertEquals(0, count(sim, sp, "Blood-Sport Champion"));
    }

    @Test
    public void testKangGoadsItselfAndTarget() {
        final Game game = initAndCreateGame();
        final Player opp = game.getPlayers().get(0);
        final Player p = game.getPlayers().get(1);
        addCards("Mountain", 4, p);
        addCard("Runeclaw Bear", opp);
        final Game sim = cast(game, p, addCardToZone("Kang, Suspicious Captain", p, ZoneType.Hand));
        AssertJUnit.assertTrue(find(sim.getPlayer(p.getId()), "Kang, Suspicious Captain").isGoaded());
        AssertJUnit.assertTrue(find(sim.getPlayer(opp.getId()), "Runeclaw Bear").isGoaded());
    }

    @Test
    public void testVoxCreatesUndauntedEffects() {
        final Game game = initAndCreateGame();
        final Player p = game.getPlayers().get(1);
        addCards("Island", 5, p);
        final Game sim = cast(game, p, addCardToZone("Võx, Child of the Collective", p, ZoneType.Hand));
        AssertJUnit.assertEquals(2, sim.getPlayer(p.getId()).getCardsIn(ZoneType.Command).size());
    }

    @Test
    public void testGrantedTriggersReachArtifactCreatures() {
        final Game game = initAndCreateGame();
        final Player p = game.getPlayers().get(1);
        final Card orni = addCard("Ornithopter", p);
        addCard("Co-Option Adjutant", p);
        addCard("Locutus of Borg", p);
        addCard("Supplementation Node", p);
        addCard("Extraction Integrant", p);
        game.getAction().checkStateEffects(true);
        final List<String> modes = new ArrayList<>();
        for (final forge.game.trigger.Trigger t : orni.getTriggers()) {
            modes.add(t.getMode().toString());
        }
        AssertJUnit.assertEquals(modes.toString(), 3, java.util.Collections.frequency(modes, "DamageDone"));
        AssertJUnit.assertEquals(modes.toString(), 1, java.util.Collections.frequency(modes, "AttackerBlockedByCreature"));
        AssertJUnit.assertTrue(orni.hasKeyword("Menace"));
    }

    @Test
    public void testTroiEitherCopiesSpellOrDraws() {
        final Game game = initAndCreateGame();
        final Player opp = game.getPlayers().get(0);
        final Player p = game.getPlayers().get(1);
        fillLibrary(p, 5);
        final Card troi = addCard("Cmdr. Troi, Outgoing Empath", p);
        addCardToZone("Runeclaw Bear", opp, ZoneType.Hand);
        final int hand = p.getCardsIn(ZoneType.Hand).size();
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, opp);
        troi.setSickness(false);
        final SpellAbility sa = findSAWithPrefix(troi, "{T}: Target opponent");
        AssertJUnit.assertNotNull(sa);
        sa.setActivatingPlayer(p);
        sa.getTargets().add(opp);
        final GameSimulator sim = createSimulator(p);
        sim.simulateSpellAbility(sa);
        final Game simGame = sim.getSimulatedGameState();
        final Player sp = simGame.getPlayer(p.getId());
        final Player so = simGame.getPlayer(opp.getId());
        final boolean copied = count(simGame, sp, "Runeclaw Bear") == 1 && count(simGame, so, "Runeclaw Bear") == 1;
        final boolean drew = sp.getCardsIn(ZoneType.Hand).size() == hand + 1;
        AssertJUnit.assertTrue("copied=" + copied + " drew=" + drew + " ownBf=" + sp.getCardsIn(ZoneType.Battlefield)
                + " oppBf=" + so.getCardsIn(ZoneType.Battlefield) + " oppHand=" + so.getCardsIn(ZoneType.Hand), copied || drew);
    }

    @Test
    public void testAssimilateTheWoundedTakesCreatureThatDiedThisTurn() {
        final Game game = initAndCreateGame();
        final Player opp = game.getPlayers().get(0);
        final Player p = game.getPlayers().get(1);
        addCards("Swamp", 3, p);
        final Card bear = addCard("Runeclaw Bear", opp);
        game.getAction().destroy(bear, null, false, null);
        final Card spell = addCardToZone("Assimilate the Wounded", p, ZoneType.Hand);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, p);
        game.getAction().checkStateEffects(true);
        final SpellAbility sa = spell.getSpellAbilities().get(0);
        sa.setActivatingPlayer(p);
        sa.getTargets().add(opp.getCardsIn(ZoneType.Graveyard).get(0));
        // resolved in the live game: the simulator's copy does not keep "put into the graveyard this turn"
        forge.game.ability.AbilityUtils.resolve(sa);
        final Card taken = find(p, "Runeclaw Bear");
        AssertJUnit.assertNotNull(p.getCardsIn(ZoneType.Graveyard) + " / " + opp.getCardsIn(ZoneType.Graveyard) + " / " + p.getCardsIn(ZoneType.Battlefield), taken);
        AssertJUnit.assertTrue(taken.getType().hasSubtype("Borg"));
        AssertJUnit.assertEquals(1, taken.getCounters(CounterEnumType.P1P1));
    }

    @Test
    public void testAssimilationAssessorExilesOpponentGraveyard() {
        final Game game = initAndCreateGame();
        final Player opp = game.getPlayers().get(0);
        final Player p = game.getPlayers().get(1);
        addCards("Swamp", 5, p);
        addCardToZone("Runeclaw Bear", opp, ZoneType.Graveyard);
        final Game sim = cast(game, p, addCardToZone("Assimilation Assessor", p, ZoneType.Hand));
        final Player so = sim.getPlayer(opp.getId());
        AssertJUnit.assertEquals(0, so.getCardsIn(ZoneType.Graveyard).size());
        AssertJUnit.assertEquals(1, so.getCardsIn(ZoneType.Exile).size());
    }

    @Test
    public void testBelannaTorresPutsCountersWithoutArtifact() {
        final Game game = initAndCreateGame();
        final Player p = game.getPlayers().get(1);
        addCards("Island", 2, p);
        for (int i = 0; i < 6; i++) {
            addCardToZone("Runeclaw Bear", p, ZoneType.Library);
        }
        final Card b = addCard("B'Elanna Torres, Scrapper", p);
        final Game sim = activate(game, p, b, "{2}, {T}: Surveil");
        AssertJUnit.assertEquals(2, find(sim.getPlayer(p.getId()), "B'Elanna Torres, Scrapper").getCounters(CounterEnumType.P1P1));
    }

    @Test
    public void testDeliverFromChaosMakesTokensEqualToManaValue() {
        final Game game = initAndCreateGame();
        final Player opp = game.getPlayers().get(0);
        final Player p = game.getPlayers().get(1);
        addCards("Plains", 3, p);
        addCards("Swamp", 2, p);
        final Card bear = addCard("Runeclaw Bear", opp);
        final Card spell = addCardToZone("Deliver from Chaos", p, ZoneType.Hand);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, p);
        game.getAction().checkStateEffects(true);
        final SpellAbility sa = spell.getSpellAbilities().get(0);
        sa.setActivatingPlayer(p);
        sa.getTargets().add(bear);
        final GameSimulator sim = createSimulator(p);
        sim.simulateSpellAbility(sa);
        final Game sg = sim.getSimulatedGameState();
        AssertJUnit.assertEquals(0, count(sg, sg.getPlayer(opp.getId()), "Runeclaw Bear"));
        AssertJUnit.assertEquals(2, count(sg, sg.getPlayer(p.getId()), "Borg Token"));
    }

    @Test
    public void testSeekOutCountersAndDoubling() {
        final Game game = initAndCreateGame();
        final Player p = game.getPlayers().get(1);
        fillLibrary(p, 6);
        addCards("Island", 3, p);
        addCard("Beverly Crusher, Ship's Doctor", p);
        final Card obrien = addCard("Chief Miles O'Brien", p);
        obrien.addCounter(CounterEnumType.CHARGE, 0, p, new forge.game.GameEntityCounterTable());
        final Game sim = cast(game, p, addCardToZone("Divination", p, ZoneType.Hand));
        int counters = 0;
        for (final Card c : sim.getPlayer(p.getId()).getCardsIn(ZoneType.Battlefield)) {
            counters += c.getCounters(CounterEnumType.P1P1);
        }
        // Beverly puts a +1/+1 counter on up to three target creatures, O'Brien then doubles one pile
        AssertJUnit.assertTrue("counters=" + counters, counters >= 2);
    }

    @Test
    public void testHarryKimGrowsWhenBiggerCreatureEnters() {
        final Game game = initAndCreateGame();
        final Player p = game.getPlayers().get(1);
        addCards("Forest", 2, p);
        addCard("Ensign Harry Kim, Operations", p);
        final Game sim = cast(game, p, addCardToZone("Runeclaw Bear", p, ZoneType.Hand));
        AssertJUnit.assertEquals(1, find(sim.getPlayer(p.getId()), "Ensign Harry Kim, Operations").getCounters(CounterEnumType.P1P1));
    }

    @Test
    public void testGaghSlingerReducesCostAndPings() {
        final Game game = initAndCreateGame();
        final Player opp = game.getPlayers().get(0);
        final Player p = game.getPlayers().get(1);
        addCards("Swamp", 1, p);
        addCard("Gagh Slinger", p);
        final Game sim = cast(game, p, addCardToZone("Blood-Sport Champion", p, ZoneType.Hand));
        AssertJUnit.assertEquals(1, count(sim, sim.getPlayer(p.getId()), "Blood-Sport Champion"));
        AssertJUnit.assertEquals(19, sim.getPlayer(opp.getId()).getLife());
    }

    @Test
    public void testMercifulMandateUntapsCreatures() {
        final Game game = initAndCreateGame();
        final Player p = game.getPlayers().get(1);
        addCards("Plains", 2, p);
        final Card bear = addCard("Runeclaw Bear", p);
        bear.setTapped(true);
        final Game sim = cast(game, p, addCardToZone("Merciful Mandate", p, ZoneType.Hand));
        AssertJUnit.assertFalse(find(sim.getPlayer(p.getId()), "Runeclaw Bear").isTapped());
    }
}
