# Task: Create Forge card scripts for Star Trek Commander (TRC) decks

Audience: the Forge agent working in `C:\SWProjects\Forge` (branch `replay-Features`).
First read `docs/AGENT_TUTORIAL_ADDING_MISSING_CARDS.md` — it explains the workflow, script idioms and the edition-file byte-safety trap. This file is the work list.

## Scope

Decks covered: **Federation Fleet**, **Klingon Fury**, **We Are the Borg** (Wizards decklist page, `magic.wizards.com/en/news/announcements/star-trek-commander-decklists`).
**Out of scope for now: the Landing Party deck.** Do not write scripts for it yet (its cards are also missing from MaMo's database).

Already done, do not redo: Captain Jean-Luc Picard, Deep Space 9 (commit f9298a03cdc).

I checked every Star Trek-specific card of the three decks against `forge-gui/res/cardsfolder` (all folders, incl. `upcoming/`) by `Name:` line. The 93 cards below have **no script**. Generic staples (Sol Ring, Swords to Plowshares, basics, ...) were not checked and are assumed present.

## Work to do

1. For each card below: create `forge-gui/res/cardsfolder/upcoming/<snake_case_name>.txt`. Oracle text below comes from Scryfall; copy it exactly into `Oracle:`.
2. Edition lines: all of these cards already have lines in `editions/Star Trek Commander.txt` (TRC) or `editions/Star Trek.txt` (TRK), so normally **no edition change is needed**. Only touch an edition file if a card turns out to be missing there, and then follow the byte-safe rule in the tutorial.
3. A card's script must match the Scryfall name exactly; the edition line is matched by name.
4. Work in batches (suggest ~10 cards), verify each batch by loading Forge and importing a deck containing them, then commit with `feat(cards): add <names> (Star Trek Commander)`. Stage only your files. Do not push.
5. Tokens: check `forge-gui/res/tokenscripts/` before inventing one (e.g. `r_1_1_officer` exists). If a needed token script is missing, add it and list it in the commit.
6. If a card's mechanic has no Forge support, do not fake it: write the closest correct script, add a `# TODO` note in your report, and skip the card rather than shipping wrong behavior.

## Cards (93)

### Alexander, Ship's Fool
- Cost: {3}{R}{W} · Type: Legendary Creature — Klingon Human 3/5
- Set/number: TRC #81 · rare · @Javier Charro · in edition
- Oracle:
  > Vigilance, haste
  > {T}: Each player reveals the top card of their library, then draws a card. Alexander deals X damage to a random opponent, where X is the greatest mana value among cards revealed this way.

### Ambassador K'Ehleyr
- Cost: {2}{W} · Type: Legendary Creature — Klingon Human Advisor 2/2
- Set/number: TRC #9 · rare · @Lius Lasahido · in edition
- Oracle:
  > Vigilance
  > Parley — At the beginning of combat on your turn, each player reveals the top card of their library, then draws a card. For each nonland card revealed this way, put a +1/+1 counter on Ambassador K'Ehleyr.

### Ambulatory Reactor
- Cost: {3}{U} · Type: Artifact Creature — Borg 2/5
- Set/number: TRC #35 · rare · @David Auden Nash · in edition
- Oracle:
  > Artifact creatures you control have "{T}: Add {C}. Spend this mana only to cast an artifact spell or activate an ability of an artifact source."

### Assimilate the Wounded
- Cost: {2}{B} · Type: Instant
- Set/number: TRC #55 · rare · @Bryan Sola · in edition
- Oracle:
  > For each opponent, assimilate up to one target creature card from that player's graveyard that was put there from the battlefield this turn. (Put it onto the battlefield under your control with a +1/+1 counter. It's a Borg artifact creature and loses all other creature types.)

### Assimilation Assessor
- Cost: {4}{B} · Type: Artifact Creature — Borg 5/5
- Set/number: TRC #56 · rare · @Miklós Ligeti · in edition
- Oracle:
  > When this creature enters, exile target opponent's graveyard.
  > Whenever this creature attacks, assimilate a creature card exiled with it. (Put it onto the battlefield under your control with a +1/+1 counter. It's a Borg artifact creature and loses all other creature types.)

### B'Elanna Torres, Scrapper
- Cost: {1}{U}{R} · Type: Legendary Creature — Klingon Human Artificer 2/2
- Set/number: TRC #82 · rare · @Ana Teresa Rivera · in edition
- Oracle:
  > Vigilance, trample
  > {2}, {T}: Surveil 2, then you may reveal the top card of your library. Draw a card if it's an artifact card. If you don't draw a card this way, put two +1/+1 counters on B'Elanna Torres.

### Battle of Wolf 359
- Cost: {3}{B}{B} · Type: Sorcery
- Set/number: TRC #57 · rare · @Henry Peters · in edition
- Oracle:
  > All nonartifact creatures get -X/-X until end of turn, where X is the number of artifact creatures you control.

### Beverly Crusher, Ship's Doctor
- Cost: {2}{W} · Type: Legendary Creature — Human Doctor 2/2
- Set/number: TRC #11 · rare · @Alex Negrea · in edition
- Oracle:
  > Seek out — Whenever you draw your second card each turn, put a +1/+1 counter on each of up to three target creatures.

### Blood-Sport Champion
- Cost: {1}{B} · Type: Creature — Klingon Warrior 2/2
- Set/number: TRC #58 · rare · @Johann Bodin · in edition
- Oracle:
  > Kicker {2} (You may pay an additional {2} as you cast this spell.)
  > Deathtouch
  > When this creature enters, if it was kicked, other creatures you control gain deathtouch until end of turn.

### Borg Incursion
- Cost: {2}{W} · Type: Instant
- Set/number: TRC #12 · rare · @Pierre Loyvet · in edition
- Oracle:
  > Create two 1/1 colorless Borg artifact creature tokens, then artifact creatures you control gain protection from the color of your choice until end of turn. (They can't be blocked, targeted, dealt damage, enchanted, or equipped by anything of that color.)

### Borg Scout Sphere
- Cost: {2}{U} · Type: Artifact — Spacecraft 5/5
- Set/number: TRC #36 · rare · @Lordigan · in edition
- Oracle:
  > When this Spacecraft enters, draw a card.
  > Station (Tap another creature you control: Put charge counters equal to its power on this Spacecraft. Station only as a sorcery. It's an artifact creature at 6+.)
  > 6+ | Artifact creatures you control have flying.

### Captain Janeway, Endgame
- Cost: {1}{U}{R}{W} · Type: Legendary Creature — Human Officer 3/3
- Set/number: TRC #84 · mythic · @Jason Pastrana · in edition
- Oracle:
  > Whenever one or more Officers you control deal combat damage to a player, you draw a card and create a 1/1 red Officer creature token.
  > {2}, Tap an untapped creature you control: Captain Janeway can't be blocked this turn.

### Chancellor Gorkon
- Cost: {3}{W}{B} · Type: Legendary Creature — Klingon Noble 5/3
- Set/number: TRC #86 · rare · @Chris Rallis · in edition
- Oracle:
  > Vigilance
  > When Chancellor Gorkon dies, exile up to two target creatures.

### Chief Miles O'Brien
- Cost: {2}{U} · Type: Legendary Creature — Human Artificer 2/4
- Set/number: TRC #37 · rare · @Alex Negrea · in edition
- Oracle:
  > Seek out — Whenever you draw your second card each turn, double the number of each kind of counter on target artifact or creature you control.

### Class-4 Tactical Cube
- Cost: {4} · Type: Artifact — Spacecraft 4/4
- Set/number: TRC #110 · rare · @Jetfreak-7 · in edition
- Oracle:
  > When this Spacecraft enters, create two 1/1 colorless Borg artifact creature tokens.
  > Station (Tap another creature you control: Put charge counters equal to its power on this Spacecraft. Station only as a sorcery. It's an artifact creature at 7+.)
  > 7+ | Flying
  > Artifact creatures you control get +1/+1.

### Cmdr. Troi, Outgoing Empath
- Cost: {2}{U}{R} · Type: Legendary Creature — Betazoid Human Advisor 3/3
- Set/number: TRC #89 · rare · @Justyna Dura · in edition
- Oracle:
  > Vigilance
  > {T}: Target opponent whose turn it is may cast a spell from their hand without paying its mana cost. If they do, you copy that spell. You may choose new targets for the copy. If they don't, you draw a card.

### Co-Option Adjutant
- Cost: {3}{U} · Type: Artifact Creature — Borg 3/3
- Set/number: TRC #39 · rare · @Aaron J. Riley · in edition
- Oracle:
  > Artifact creatures you control have "Whenever this creature deals combat damage to a player, draw a card."

### Collective Override
- Cost: {2}{W}{U} · Type: Instant
- Set/number: TRC #90 · rare · @Pierre Loyvet · in edition
- Oracle:
  > Choose one. If you control a commander as you cast this spell, you may choose both instead.
  > • Create a 1/1 colorless Borg artifact creature token for each attacking creature.
  > • Draw a card for each attacking creature.

### Command Subadjunct
- Cost: {W} · Type: Artifact Creature — Borg 2/1
- Set/number: TRK #9 · uncommon · @Alex Negrea · in edition
- Oracle:
  > Artifact creatures you control have vigilance.

### Commander William Riker
- Cost: {1}{R}{W} · Type: Legendary Creature — Human Officer 3/3
- Set/number: TRC #91 · rare · @Jake Murray · in edition
- Oracle:
  > Vigilance, haste
  > {1}, {T}: You and target opponent each draw two cards.

### Conquest Mission
- Cost: {2}{W} · Type: Enchantment
- Set/number: TRK #10 · uncommon · @Mark Spears · in edition
- Oracle:
  > Whenever a nontoken artifact you control enters, put a quest counter on this enchantment. Create a 1/1 colorless Borg artifact creature token. If this enchantment has six or more quest counters on it, create two of those tokens instead.

### Curzon Dax, Bound by Blood
- Cost: {2}{R}{W} · Type: Legendary Creature — Trill Warrior 4/3
- Set/number: TRC #92 · rare · @Wonchun Choi · in edition
- Oracle:
  > Whenever another Klingon or Warrior you control dies, Curzon Dax deals 1 damage to each opponent.
  > When Curzon Dax dies, you may put a creature card with mana value 3 or less from your hand onto the battlefield.

### Data, Exploring Humanity
- Cost: {4}{U} · Type: Legendary Artifact Creature — Android Scientist 4/4
- Set/number: TRC #40 · rare · @Borja Pindado · in edition
- Oracle:
  > Seek out — Whenever you draw your second card each turn, discover 4. (Exile cards from the top of your library until you exile a nonland card with mana value 4 or less. Cast it without paying its mana cost or put it into your hand. Put the rest on the bottom in a random order.)

### Deliver from Chaos
- Cost: {3}{W}{B} · Type: Instant
- Set/number: TRC #93 · rare · @Bastien L. Deharme · in edition
- Oracle:
  > Exile target artifact or creature. Create X 1/1 colorless Borg artifact creature tokens, where X is that permanent's mana value.

### Dr. Pulaski, Cynical Clinician
- Cost: {1}{W} · Type: Legendary Creature — Human Doctor 2/2
- Set/number: TRC #13 · rare · @Justyna Dura · in edition
- Oracle:
  > Seek out — Whenever you draw your second card each turn, you gain 4 life.

### Emergency Regeneration
- Cost: {1}{W} · Type: Instant
- Set/number: TRC #14 · rare · @Grant Griffin · in edition
- Oracle:
  > Artifacts you control gain hexproof and indestructible until end of turn.

### Ensign Harry Kim, Operations
- Cost: {1}{W} · Type: Legendary Creature — Human Officer 1/2
- Set/number: TRC #15 · rare · @Lixin Yin · in edition
- Oracle:
  > Whenever another creature you control enters, if it has greater power than Ensign Kim, put a +1/+1 counter on Ensign Kim.
  > {2}, Remove two +1/+1 counters from among creatures you control: Draw a card.

### Extraction Integrant
- Cost: {1}{B} · Type: Artifact Creature — Borg 1/1
- Set/number: TRC #59 · rare · @Smirtouille · in edition
- Oracle:
  > Artifact creatures you control have menace and "Whenever a creature blocks this creature, that creature gets -1/-1 until end of turn."

### Ezri Dax, Newly Joined
- Cost: {1}{W} · Type: Legendary Creature — Trill Advisor 2/2
- Set/number: TRC #16 · rare · @Lius Lasahido · in edition
- Oracle:
  > Flash
  > You may have Ezri Dax enter as a copy of any creature card in your graveyard that was put there from the battlefield this turn, except her name is Ezri Dax, Newly Joined and she's a legendary Trill in addition to her other types.

### Ferocious Colonist
- Cost: {1}{R} · Type: Creature — Klingon Citizen 0/1
- Set/number: TRC #65 · rare · @Thanh Tuấn · in edition
- Oracle:
  > Double strike
  > Melee (Whenever this creature attacks, it gets +1/+1 until end of turn for each opponent you attacked this combat.)

### Gagh Slinger
- Cost: {2}{R} · Type: Creature — Klingon Warrior 3/2
- Set/number: TRC #66 · rare · @Pavel Kolomeyets · in edition
- Oracle:
  > Klingon spells and Warrior spells you cast cost {1} less to cast.
  > Whenever another Klingon or Warrior you control enters, this creature deals 1 damage to each opponent.

### Galvanizing Tenor
- Cost: {3}{R} · Type: Creature — Klingon Performer 4/3
- Set/number: TRC #67 · rare · @Wonchun Choi · in edition
- Oracle:
  > Menace
  > Battlefront — Whenever this creature attacks, goad up to X target creatures, where X is the number of opponents you attacked this turn. (Until your next turn, those creatures attack each combat if able and attack a player other than you if able.)

### Geordi La Forge, Innovator
- Cost: {2}{U} · Type: Legendary Creature — Human Artificer 2/3
- Set/number: TRC #42 · rare · @Ryan Valle · in edition
- Oracle:
  > Geordi La Forge can't be blocked if you control a Spacecraft.
  > Whenever Geordi La Forge deals combat damage to a player, draw a card.

### Gift of Perfection
- Cost: {4}{U} · Type: Sorcery
- Set/number: TRC #44 · rare · @Alexander Mokhov · in edition
- Oracle:
  > Each opponent reveals cards from the top of their library until they reveal a creature card. Assimilate one of the creature cards revealed this way. Then each opponent puts the other cards they revealed this way on the bottom of their library in a random order. (To assimilate a card, put it onto the battlefield under your control with a +1/+1 counter. It's a Borg artifact creature and loses all other creature types.)

### Glory to the Empire!
- Cost: {5}{R} · Type: Enchantment
- Set/number: TRC #68 · rare · @Andrey Kuzinskiy · in edition
- Oracle:
  > When this enchantment enters, create a number of 2/1 red Klingon Warrior creature tokens with haste equal to the number of opponents you have.
  > Creatures you control have melee. (Whenever a creature with melee attacks, it gets +1/+1 until end of turn for each opponent you attacked this combat.)

### Gnarling Targ
- Cost: {4}{R} · Type: Creature — Boar 4/5
- Set/number: TRC #69 · rare · @Borja Pindado · in edition
- Oracle:
  > Trample
  > Whenever you attack, Klingons and Warriors you control get +2/+0 and gain trample until end of turn.

### Grilka, Widowed Warrior
- Cost: {5}{R} · Type: Legendary Creature — Klingon Warrior 5/5
- Set/number: TRC #70 · rare · @Lucio Parrillo · in edition
- Oracle:
  > When Grilka enters, gain control of target creature an opponent controls until end of turn. Untap it. Until end of turn, it gains trample, haste, and "Whenever this creature deals combat damage to a player, create that many tapped Treasure tokens."

### Guinan, Ancient Advisor
- Cost: {2}{W} · Type: Legendary Creature — El-Aurian Advisor 1/4
- Set/number: TRC #17 · rare · @Nicky Barkla · in edition
- Oracle:
  > {1}, {T}: Put a +1/+1 counter on each creature you control that entered this turn. If you put two or more +1/+1 counters on creatures this way, draw a card.

### Hugh, First of Many
- Cost: {1}{W}{U}{B} · Type: Legendary Artifact Creature — Borg 3/4
- Set/number: TRC #94 · rare · @Carlos Dattoli · in edition
- Oracle:
  > Artifact spells you cast have replicate {2}{U}. (Whenever you cast an artifact spell, copy it for each time you paid its replicate cost. Each copy becomes a token.)

### I.K.S. Negh'Var, Imperial Flagship
- Cost: {3} · Type: Legendary Artifact — Spacecraft 6/6
- Set/number: TRC #113 · rare · @Jetfreak-7 · in edition
- Oracle:
  > Klingons and Warriors you control have vigilance.
  > Station (Tap another creature you control: Put charge counters equal to its power on this Spacecraft. Station only as a sorcery. It's an artifact creature at 10+.)
  > 10+ | Flying, vigilance

### Imperial Hullbreachers
- Cost: {2}{W} · Type: Creature — Klingon Warrior 2/2
- Set/number: TRC #18 · rare · @Joan Piqué Llorens · in edition
- Oracle:
  > Squad {2} (As an additional cost to cast this spell, you may pay {2} any number of times. When this creature enters, create that many tokens that are copies of it.)
  > Flying
  > Battle cry (Whenever this creature attacks, each other attacking creature gets +1/+0 until end of turn.)

### Indelicate Healer
- Cost: {2}{W} · Type: Creature — Klingon Warrior Doctor 3/3
- Set/number: TRC #19 · rare · @Monztre · in edition
- Oracle:
  > Whenever this creature or another Klingon or Warrior you control enters, creatures you control gain lifelink until end of turn.

### Jean-Luc Picard, Luminary
- Cost: {1}{U}{R}{W} · Type: Legendary Creature — Human Officer 2/4
- Set/number: TRC #3 · mythic · @Jason Pastrana · in edition
- Oracle:
  > Whenever another historic permanent you control with mana value 3 or greater enters, draw a card. (Artifacts, legendaries, and Sagas are historic.)
  > Seek out — Whenever you draw your second card each turn, put a +1/+1 counter on up to one other target creature and two charge counters on up to one target Spacecraft.

### Julian Bashir, Frontier Doctor
- Cost: {2}{W} · Type: Legendary Creature — Human Doctor 1/1
- Set/number: TRC #21 · rare · @Alexander Mokhov · in edition
- Oracle:
  > Seek out — Whenever you draw your second card each turn, put a +1/+1 counter on Julian Bashir.
  > Whenever Julian Bashir attacks, return target creature card with mana value X or less from your graveyard to the battlefield, where X is Julian Bashir's power.

### Jurati, Cordial Queen
- Cost: {1}{W}{U} · Type: Legendary Artifact Creature — Borg Scientist 3/3
- Set/number: TRC #95 · rare · @Martina Fačková · in edition
- Oracle:
  > Whenever one or more artifact creatures you control deal combat damage to a player, create a token that's a copy of target artifact token you control.

### K't'inga-Class Battle Cruiser
- Cost: {3} · Type: Artifact — Spacecraft 5/5
- Set/number: TRC #115 · rare · @Dofresh · in edition
- Oracle:
  > Whenever this Spacecraft enters or attacks, one or two target Klingons and/or Warriors you control gain flying until end of turn.
  > Station (Tap another creature you control: Put charge counters equal to its power on this Spacecraft. Station only as a sorcery. It's an artifact creature at 4+.)
  > 4+ | Flying

### K'Vort-Class Raider
- Cost: {4} · Type: Artifact — Spacecraft 5/3
- Set/number: TRC #116 · rare · @Jetfreak-7 · in edition
- Oracle:
  > During your turn, this Spacecraft and creatures you control have hexproof.
  > Station (Tap another creature you control: Put charge counters equal to its power on this Spacecraft. Station only as a sorcery. It's an artifact creature at 4+.)
  > 4+ | Flying, menace, haste

### Kang, Suspicious Captain
- Cost: {3}{R} · Type: Legendary Creature — Klingon Warrior Officer 4/4
- Set/number: TRC #71 · rare · @Javier Charro · in edition
- Oracle:
  > When Kang enters, choose target creature an opponent controls. Kang and that creature are goaded for as long as you control Kang. (They attack each combat if able and attack a player other than you if able.)
  > Whenever one or more goaded creatures attack, put a +1/+1 counter on target creature you control.

### Kes, Evolving Telepath
- Cost: {4}{U} · Type: Legendary Creature — Ocampa Advisor 2/6
- Set/number: TRC #45 · rare · @Ariel Orea · in edition
- Oracle:
  > At the beginning of your end step, put a time counter on Kes, then draw X cards, where X is the number of time counters on her. Then if she has nine or more time counters on her, sacrifice her.

### Kor, Cause of Vengeance
- Cost: {1}{R}{W}{B} · Type: Legendary Creature — Klingon Warrior Officer 4/4
- Set/number: TRC #96 · mythic · @Eric Wilkerson · in edition
- Oracle:
  > Whenever you attack, you may sacrifice another creature. If you do, draw a card, then Klingons and Warriors you control get +2/+2 until end of turn.

### Korok, Liberated Rage
- Cost: {3}{W} · Type: Legendary Artifact Creature — Klingon Borg 2/2
- Set/number: TRC #22 · rare · @Ariel Orea · in edition
- Oracle:
  > Battlefront — Whenever Korok attacks, creatures you control get +X/+X until end of turn, where X is the number of opponents you attacked this turn.

### Kurn, Son of Mogh
- Cost: {2}{B} · Type: Legendary Creature — Klingon Warrior Officer 2/3
- Set/number: TRC #60 · rare · @Kieran Yanner · in edition
- Oracle:
  > Deathtouch
  > Battlefront — Whenever Kurn attacks, you draw X cards and lose X life, where X is the number of opponents you attacked this turn.

### Locutus of Borg
- Cost: {1}{W}{U}{B} · Type: Legendary Artifact Creature — Borg 3/3
- Set/number: TRC #97 · mythic · @Anton Solovianchyk · in edition
- Oracle:
  > Lifelink
  > Artifact creatures you control have "Whenever this creature deals combat damage to a player, create a 1/1 colorless Borg artifact creature token."

### Martok, Hero of the Empire
- Cost: {2}{W} · Type: Legendary Creature — Klingon Warrior 2/2
- Set/number: TRC #23 · rare · @Pavel Kolomeyets · in edition
- Oracle:
  > Battlefront — Whenever you attack, put a +1/+1 counter on each of up to X target creatures you control, where X is the number of opponents you attacked this turn.

### Maturation Chamber
- Cost: {1}{W} · Type: Artifact
- Set/number: TRC #24 · rare · @Jonas De Ro · in edition
- Oracle:
  > Whenever you cast an artifact spell, put a charge counter on this artifact.
  > {4}, {T}: Create a 1/1 colorless Borg artifact creature token for each charge counter on this artifact. Activate only as a sorcery.

### Merciful Mandate
- Cost: {1}{W} · Type: Instant
- Set/number: TRC #25 · rare · @Aaron J. Riley · in edition
- Oracle:
  > Untap all creatures you control. If it's a combat phase, you may end the combat phase. (Remove all attackers and blockers from combat. Exile all spells and abilities from the stack, including this spell.)

### On the Shoulders of Giants
- Cost: {2}{U} · Type: Enchantment
- Set/number: TRC #47 · rare · @Nadia Hurianova · in edition
- Oracle:
  > Historic creatures you control have flying. (Artifacts, legendaries, and Sagas are historic.)

### One, Borg Evolved
- Cost: {4}{W}{U}{B} · Type: Legendary Artifact Creature — Borg Artificer 3/3
- Set/number: TRC #100 · rare · @Lixin Yin · in edition
- Oracle:
  > Artifact creatures you control have flying, double strike, vigilance, menace, lifelink, and ward {2}.

### Optimization Annex
- Cost: {1}{U} · Type: Artifact Creature — Borg 1/3
- Set/number: TRC #48 · rare · @Filipe Pagliuso · in edition
- Oracle:
  > If one or more charge counters would be put on an artifact you control, that many plus one charge counters are put on it instead.

### Own the Day
- Cost: {4}{R}{R} · Type: Sorcery
- Set/number: TRC #73 · rare · @Tomáš Honz · in edition
- Oracle:
  > Battlefront — This spell costs {1} less to cast for each opponent you attacked this turn.
  > Untap all creatures that attacked this turn. After this main phase, there is an additional combat phase followed by an additional main phase.

### Qo'noS Quartermaster
- Cost: {1}{R} · Type: Creature — Klingon Warrior 2/2
- Set/number: TRC #74 · rare · @Lordigan · in edition
- Oracle:
  > Menace
  > Whenever this creature attacks, add one mana of any color. Until end of turn, you don't lose this mana as steps and phases end.

### Queen's Diamond
- Cost: {4} · Type: Legendary Artifact — Spacecraft 5/5
- Set/number: TRC #118 · rare · @Raymond Bonilla · in edition
- Oracle:
  > {T}: Choose a color. Add one mana of that color for each charge counter on Queen's Diamond. Spend this mana only to cast your commander.
  > Station (Tap another creature you control: Put charge counters equal to its power on this Spacecraft. Station only as a sorcery. It's an artifact creature at 8+.)
  > 8+ | Flying, vigilance, haste

### Reclamation Specialist
- Cost: {2}{B} · Type: Artifact Creature — Borg 2/2
- Set/number: TRC #62 · rare · @Sean Vo · in edition
- Oracle:
  > {1}, Sacrifice an artifact creature: Draw a card.

### Reconnaissance Partition
- Cost: {4} · Type: Artifact Creature — Borg 3/3
- Set/number: TRC #119 · rare · @Camille Alquier · in edition
- Oracle:
  > This creature enters with two charge counters on it.
  > {T}, Remove a counter from this creature: Choose one —
  > • Search your library for a basic land card, put it onto the battlefield tapped, then shuffle.
  > • Draw a card.

### Reg Barclay, Fidgety Fantasist
- Cost: {2}{U}{R} · Type: Legendary Creature — Human Artificer 1/5
- Set/number: TRC #102 · rare · @Ryan Valle · in edition
- Oracle:
  > Seek out — Whenever you draw your second card each turn, create a token that's a copy of another target creature you control, except it isn't legendary, it's an Illusion artifact in addition to its other types, and it has haste. Exile that token at the beginning of the next end step.

### Right of Vengeance
- Cost: {3}{W} · Type: Instant
- Set/number: TRC #27 · rare · @Albert Cordero · in edition
- Oracle:
  > Until end of turn, whenever a creature deals combat damage to you, destroy that creature and create a 2/1 red Klingon Warrior creature token with haste.

### Rite of Ascension
- Cost: {2}{R}{W} · Type: Enchantment
- Set/number: TRC #103 · rare · @Lordigan · in edition
- Oracle:
  > Battlefront — Whenever you attack, for each opponent you attacked this turn, create a tapped and attacking 2/1 red Klingon Warrior creature token with haste.

### Ro Laren, Double Agent
- Cost: {2}{R} · Type: Legendary Creature — Bajoran Rogue Officer 3/2
- Set/number: TRC #75 · rare · @David Auden Nash · in edition
- Oracle:
  > Seek out — Whenever you draw your second card each turn, Ro Laren deals 2 damage to each opponent.
  > {1}, {T}, Discard a card: Draw a card.

### Rom and Leeta, Unlikely Pair
- Cost: {3}{R} · Type: Legendary Creature — Ferengi Bajoran 4/3
- Set/number: TRC #76 · rare · @Nikki Dawes · in edition
- Oracle:
  > Seek out — Whenever you draw your second card each turn, create two tapped Treasure tokens.

### Seven of Nine, Tertiary Adjunct
- Cost: {2}{U} · Type: Legendary Artifact Creature — Borg 1/4
- Set/number: TRC #50 · rare · @Bastien L. Deharme · in edition
- Oracle:
  > That Is Irrelevant — {T}: Draw a card, then discard a card. When you discard a nonland card this way, put two charge counters on up to one target artifact.

### Shore Leave Planet
- Cost: (none) · Type: Land — Planet
- Set/number: TRC #130 · rare · @Arthur Haas · in edition
- Oracle:
  > This land enters tapped.
  > {T}: Add {U}.
  > {4}{U}: This land becomes a copy of target creature you control until end of turn, except it isn't legendary and it's a Robot artifact in addition to its other types.

### Sins of the Father
- Cost: {3}{B}{R} · Type: Enchantment — Saga
- Set/number: TRC #104 · rare · @Peter Diamond · in edition
- Oracle:
  > (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
  > I — Each opponent sacrifices a nontoken creature of their choice.
  > II — For each opponent, you create a 2/1 red Klingon Warrior creature token with haste.
  > III — Exile target player's graveyard. Each opponent loses life equal to the number of creature cards exiled this way.

### Stratagem Emulator
- Cost: {2}{W} · Type: Artifact Creature — Borg 2/1
- Set/number: TRC #30 · rare · @Grant Griffin · in edition
- Oracle:
  > When this creature dies, create X 1/1 colorless Borg artifact creature tokens, where X is its power.

### Supplementation Node
- Cost: {2}{W} · Type: Artifact Creature — Borg 2/2
- Set/number: TRC #31 · rare · @Piotr Foksowicz · in edition
- Oracle:
  > Artifact creatures you control have "Whenever this creature deals combat damage to a player, put a +1/+1 counter on it."

### Temporal Tribulation
- Cost: {2}{U} · Type: Instant
- Set/number: TRC #51 · rare · @Nikki Dawes · in edition
- Oracle:
  > Exile two target permanents you control. Return those cards to the battlefield under their owner's control at the beginning of the next end step.
  > Flashback {5}{U} (You may cast this card from your graveyard for its flashback cost. Then exile it.)

### The Beacon of Kahless
- Cost: {2}{R} · Type: Legendary Artifact
- Set/number: TRC #64 · rare · @Colin Searle · in edition
- Oracle:
  > {T}: Add one mana of any color.
  > Battlefront — {T}: Choose a color. Add an amount of mana of that color equal to the number of opponents you attacked this turn.

### The Best of Both Worlds
- Cost: {4}{W}{U} · Type: Enchantment — Saga
- Set/number: TRC #83 · rare · @Marko Manev · in edition
- Oracle:
  > (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
  > I — Assimilate target nonartifact creature an opponent controls. (Gain control of it and put a +1/+1 counter on it. It's a Borg artifact creature and loses all other creature types.)
  > II — Artifact creatures you control get +2/+2 until end of turn.
  > III — Exile the Borg assimilated with this Saga, then return it to the battlefield under its owner's control.

### The Promise of Kahless
- Cost: {4}{B} · Type: Sorcery
- Set/number: TRC #61 · rare · @Zezhou Chen · in edition
- Oracle:
  > You may sacrifice a Klingon or Warrior. If you do, return all other Klingon and Warrior creature cards from your graveyard to the battlefield.

### The Sword of Kahless
- Cost: {4} · Type: Legendary Artifact — Equipment
- Set/number: TRC #123 · rare · @Enora Mercier · in edition
- Oracle:
  > Equipped creature gets +2/+2 and has "Whenever this creature attacks, look at the top five cards of your library. You may put a Klingon or Warrior creature card from among them onto the battlefield. Put the rest on the bottom of your library in a random order."
  > Equip {2}

### Tom Paris, Risk-Taker
- Cost: {4}{U}{R} · Type: Legendary Creature — Human Pilot 4/4
- Set/number: TRC #105 · rare · @Borja Pindado · in edition
- Oracle:
  > Seek out — Whenever you draw your second card each turn, creatures you control gain flying, double strike, vigilance, and haste until end of turn.

### Transwarp Hub
- Cost: {2}{U} · Type: Artifact — Spacecraft
- Set/number: TRC #52 · rare · @David Frasheski · in edition
- Oracle:
  > When this Spacecraft enters, mill five cards. You may put up to two artifact cards from among them into your hand.
  > Station (Tap another creature you control: Put charge counters equal to its power on this Spacecraft. Station only as a sorcery.)
  > 3+ | {T}: Target creature can't be blocked this turn.

### Tuvok, Chief Tactical Officer
- Cost: {2}{U} · Type: Legendary Creature — Vulcan Officer 2/3
- Set/number: TRC #53 · rare · @Flavio Greco Paglia · in edition
- Oracle:
  > Flash
  > When Tuvok enters during the declare attackers step, choose up to two target attacking creatures. For each of them, you may reselect which player or permanent that creature is attacking. (A creature can't attack its controller.)

### Type-03 Borg Vessel
- Cost: {3}{W} · Type: Artifact — Spacecraft 5/5
- Set/number: TRC #33 · rare · @Florent Lebrun · in edition
- Oracle:
  > Artifact spells you cast cost {1} less to cast.
  > Station (Tap another creature you control: Put charge counters equal to its power on this Spacecraft. Station only as a sorcery. It's an artifact creature at 7+.)
  > 7+ | Flying
  > Whenever you cast a spell, create a 1/1 colorless Borg artifact creature token.

### U.S.S. Defiant, Tough Little Ship
- Cost: {6} · Type: Legendary Artifact — Spacecraft 7/4
- Set/number: TRC #125 · rare · @John Di Giovanni · in edition
- Oracle:
  > When U.S.S. Defiant enters, destroy up to one target artifact or enchantment.
  > Station (Tap another creature you control: Put charge counters equal to its power on this Spacecraft. Station only as a sorcery. It's an artifact creature at 5+.)
  > 5+ | Flying, hexproof

### U.S.S. Enterprise-C, Time-Tossed
- Cost: {5} · Type: Legendary Artifact — Spacecraft 6/6
- Set/number: TRC #126 · rare · @Dofresh · in edition
- Oracle:
  > When U.S.S. Enterprise-C enters or dies, you may discard your hand. If you do, draw a card for each card discarded this way.
  > Station (Tap another creature you control: Put charge counters equal to its power on this Spacecraft. Station only as a sorcery. It's an artifact creature at 3+.)
  > 3+ | Flying

### U.S.S. Enterprise-E, Sovereign
- Cost: {3}{W}{W} · Type: Legendary Artifact — Spacecraft 7/7
- Set/number: TRC #34 · rare · @Jetfreak-7 · in edition
- Oracle:
  > When U.S.S. Enterprise-E enters, destroy all tapped creatures.
  > Station (Tap another creature you control: Put charge counters equal to its power on this Spacecraft. Station only as a sorcery. It's an artifact creature at 8+.)
  > 8+ | Flying, vigilance

### U.S.S. Prometheus, Prototype
- Cost: {5} · Type: Legendary Artifact — Spacecraft 6/6
- Set/number: TRC #128 · rare · @Jetfreak-7 · in edition
- Oracle:
  > Station (Tap another creature you control: Put charge counters equal to its power on this Spacecraft. Station only as a sorcery. It's an artifact creature at 4+.)
  > 4+ | Flying
  > 8+ | Multivector Assault Mode — Whenever U.S.S. Prometheus deals combat damage to an opponent, it deals that much damage to each other opponent.

### U.S.S. Stargazer, Here and There
- Cost: {3} · Type: Legendary Artifact — Spacecraft 4/3
- Set/number: TRC #129 · rare · @Alexander Forssberg · in edition
- Oracle:
  > Station (Tap another creature you control: Put charge counters equal to its power on this Spacecraft. Station only as a sorcery. It's an artifact creature at 6+.)
  > 6+ | Flying
  > Picard Maneuver — Whenever U.S.S. Stargazer attacks, create a tapped and attacking 4/3 colorless Spacecraft Illusion artifact creature token with flying. Exile that token at end of combat.

### Unification
- Cost: {3}{W}{U} · Type: Enchantment — Saga
- Set/number: TRC #107 · rare · @Uzuri · in edition
- Oracle:
  > (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
  > I — The owner of up to one target creature draws two cards, then shuffles that creature into their library.
  > II — You may put a Spacecraft card from your hand or graveyard onto the battlefield.
  > III — You and target opponent each draw two cards.

### Vic Fontaine, One Night Only
- Cost: {1}{R}{W} · Type: Legendary Artifact Creature — Illusion Performer 3/3
- Set/number: TRC #108 · rare · @Tony Foti · in edition
- Oracle:
  > Seek out — Whenever you draw your second card each turn, one or two target creatures each get +2/+0 and gain lifelink until end of turn.

### Voq, Torchbearer
- Cost: {3}{B} · Type: Legendary Creature — Klingon Spy 4/3
- Set/number: TRC #63 · rare · @Rémi Jacquot · in edition
- Oracle:
  > Menace
  > ChoH'a' — You may have Voq enter as a copy of a creature card in a graveyard, except he's a Klingon Spy in addition to his other types and has menace.

### Võx, Child of the Collective
- Cost: {4}{U} · Type: Legendary Artifact Creature — Borg 3/3
- Set/number: TRC #54 · rare · @Carissa Susilo · in edition
- Oracle:
  > When Võx enters and at the beginning of your first main phase, the next artifact creature spell and the next Spacecraft spell you cast this turn have undaunted. (They cost {1} less to cast for each opponent.)

### Worf, Son of Mogh
- Cost: {1}{R}{W} · Type: Legendary Creature — Klingon Warrior Officer 3/3
- Set/number: TRC #109 · rare · @Borja Pindado · in edition
- Oracle:
  > Menace
  > Other Officers you control get +1/+1.
  > Seek out — Whenever you draw your second card each turn, create a 1/1 red Officer creature token.
