# Forge AI Features - Detaillierte Analyse

## Inhaltsverzeichnis
1. [Übersicht](#übersicht)
2. [AI-Architektur](#ai-architektur)
3. [AI-Profile und Persönlichkeiten](#ai-profile-und-persönlichkeiten)
4. [AI-Entscheidungssysteme](#ai-entscheidungssysteme)
5. [Kartenspezifische AI-Logik](#kartenspezifische-ai-logik)
6. [Simulation und Spielzustandsbewertung](#simulation-und-spielzustandsbewertung)
7. [Kampf-KI](#kampf-ki)
8. [Erweiterungspunkte für Deck-spezifische Playbooks](#erweiterungspunkte-für-deck-spezifische-playbooks)
9. [Zusammenfassung](#zusammenfassung)

---

## Übersicht

Die Forge AI ist eine **heuristik-basierte KI**, die keine maschinellen Lernalgorithmen verwendet. Sie basiert auf:
- Regeln und Bewertungsfunktionen
- Persönlichkeitsprofile (.ai Dateien)
- Kartenspezifische Logik (SpecialCardAi, SpecialAiLogic)
- API-basierte Ability-Handler (forge-ai/ability/*.java)
- Optionale Simulation für bessere Entscheidungen

### Stärken und Schwächen der AI (laut Dokumentation)
- **Gut**: Aggro- und Midrange-Decks
- **Mittelmäßig**: Control-Decks
- **Schlecht**: Combo-Decks

---

## AI-Architektur

### Hauptkomponenten

Die AI-Implementierung befindet sich im Modul **`forge-ai`** unter:
```
forge-ai/src/main/java/forge/ai/
```

#### Kernklassen

| Klasse | Datei | Beschreibung |
|--------|-------|--------------|
| `AiController` | `AiController.java` | Hauptsteuerungsklasse für AI-Entscheidungen (2385 Zeilen) |
| `PlayerControllerAi` | `PlayerControllerAi.java` | Schnittstelle zwischen Spiellogik und AI |
| `LobbyPlayerAi` | `LobbyPlayerAi.java` | Konfiguration des AI-Spielers |
| `ComputerUtil` | `ComputerUtil.java` | Utility-Funktionen für AI-Entscheidungen (3186 Zeilen) |
| `ComputerUtilAbility` | `ComputerUtilAbility.java` | Verwaltung verfügbarer Spielzüge |
| `ComputerUtilCard` | `ComputerUtilCard.java` | Kartenbewertung |
| `ComputerUtilMana` | `ComputerUtilMana.java` | Mana-Management |
| `ComputerUtilCombat` | `ComputerUtilCombat.java` | Kampfberechnungen |
| `ComputerUtilCost` | `ComputerUtilCost.java` | Kostenberechnung |

### Klassendiagramm (vereinfacht)

```
┌─────────────────────┐
│   LobbyPlayerAi     │
│  - aiProfile        │
│  - useSimulation    │
└──────────┬──────────┘
           │ creates
           ▼
┌─────────────────────┐      ┌──────────────────┐
│ PlayerControllerAi  │─────▶│   AiController   │
└─────────────────────┘      │  - memory        │
                             │  - simPicker     │
                             │  - predictedCombat│
                             └────────┬─────────┘
                                      │ uses
                    ┌─────────────────┼─────────────────┐
                    ▼                 ▼                 ▼
           ┌────────────┐   ┌──────────────────┐ ┌────────────────┐
           │ComputerUtil│   │SpellAbilityPicker│ │AiAttackController│
           └────────────┘   │  (Simulation)    │ └────────────────┘
                            └──────────────────┘
```

---

## AI-Profile und Persönlichkeiten

### Profilsystem

Die AI verwendet konfigurierbare Persönlichkeitsprofile, die in `.ai`-Dateien gespeichert sind:

**Speicherort:** `forge-gui/res/ai/`

#### Verfügbare Profile

| Profil | Datei | Beschreibung |
|--------|-------|--------------|
| Default | `Default.ai` | Standard-Profil mit ausgewogenen Einstellungen |
| Cautious | `Cautious.ai` | Vorsichtiges Spielverhalten |
| Reckless | `Reckless.ai` | Aggressives, risikoreiches Verhalten |
| Experimental | `Experimental.ai` | Experimentelle Einstellungen mit erweiterten Features |

#### Profilauswahl im Code

```java
// In LobbyPlayerAi.java
public void setAiProfile(String profileName) {
    aiProfile = profileName;
}

// Zufällige Profilrotation pro Spiel
if (rotateProfileEachGame) {
    setAiProfile(AiProfileUtil.getRandomProfile());
}
```

### AI-Eigenschaften (AiProps.java)

Die `AiProps`-Enum definiert **166+ konfigurierbare Parameter**. Die wichtigsten Kategorien:

#### 1. Angriffs-Verhalten
```
PLAY_AGGRO=false                              # Globaler Aggro-Modus
CHANCE_TO_ATTACK_INTO_TRADE=0                 # Wahrscheinlichkeit für Trade-Angriffe
ATTACK_INTO_TRADE_WHEN_TAPPED_OUT=false       # Angreifen wenn getapped
RANDOMLY_ATKTRADE_ONLY_ON_LOWER_LIFE_PRESSURE=true
CHANCE_TO_ATKTRADE_WHEN_OPP_HAS_MANA=30
TRY_TO_AVOID_ATTACKING_INTO_CERTAIN_BLOCK=true
```

#### 2. Block-Verhalten
```
ENABLE_RANDOM_FAVORABLE_TRADES_ON_BLOCK=true
RANDOMLY_TRADE_EVEN_WHEN_HAVE_LESS_CREATS=false
MAX_DIFF_IN_CREATURE_COUNT_TO_TRADE=1
MIN_CHANCE_TO_RANDOMLY_TRADE_ON_BLOCK=30
MAX_CHANCE_TO_RANDOMLY_TRADE_ON_BLOCK=70
```

#### 3. Planeswalker-Schutz
```
CHANCE_TO_TRADE_TO_SAVE_PLANESWALKER=70
THRESHOLD_TOKEN_CHUMP_TO_SAVE_PLANESWALKER=135
THRESHOLD_NONTOKEN_CHUMP_TO_SAVE_PLANESWALKER=110
CHUMP_TO_SAVE_PLANESWALKER_ONLY_ON_LETHAL=true
```

#### 4. Counter-Spell-Verhalten
```
MIN_SPELL_CMC_TO_COUNTER=0
CHANCE_TO_COUNTER_CMC_1=50
CHANCE_TO_COUNTER_CMC_2=75
CHANCE_TO_COUNTER_CMC_3=100
ALWAYS_COUNTER_OTHER_COUNTERSPELLS=true
ALWAYS_COUNTER_REMOVAL_SPELLS=true
```

#### 5. Ressourcen-Management
```
HOLD_LAND_DROP_FOR_MAIN2_IF_UNUSED=0
PREDICT_SPELLS_FOR_MAIN2=true
RESERVE_MANA_FOR_MAIN2_CHANCE=0
```

#### 6. Lebensbedrohung
```
AI_IN_DANGER_THRESHOLD=4                      # Unter diesem Leben wird defensiv gespielt
AI_IN_DANGER_MAX_THRESHOLD=4                  # Random-Bereich für Unvorhersehbarkeit
```

### Laden von Profilen

```java
// In AiProfileUtil.java
public static void loadAllProfiles(String aiProfileDir) {
    AI_PROFILE_DIR = aiProfileDir;
    loadedProfiles.clear();
    List<String> availableProfiles = getAvailableProfiles();
    for (String profile : availableProfiles) {
        loadedProfiles.put(profile, loadProfile(profile));
    }
}

public static String getProperty(final Player p, final AiProps propName) {
    String prop = AiProfileUtil.getAIProp(p.getLobbyPlayer(), propName);
    if (prop == null || prop.isEmpty()) {
        return propName.getDefault();
    }
    return prop;
}
```

---

## AI-Entscheidungssysteme

### Hauptentscheidungslogik (AiController)

Der `AiController` ist das Herzstück der AI-Entscheidungen:

```java
public class AiController {
    private final Player player;
    private final Game game;
    private final AiCardMemory memory;          // Speichert gesehene Karten
    private Combat predictedCombat;             // Vorhersage des nächsten Kampfes
    private boolean useSimulation;              // Simulations-Modus aktiv?
    private SpellAbilityPicker simPicker;       // Simulations-basierte Auswahl
    private int lastAttackAggression;           // Letzte Angriffs-Aggressivität
    
    // Hauptmethode für Spielzug-Auswahl
    public SpellAbility getSpellAbilityToPlay() {
        // 1. Verfügbare Karten sammeln
        CardCollection cards = ComputerUtilAbility.getAvailableCards(game, player);
        
        // 2. Spell Abilities extrahieren
        List<SpellAbility> all = ComputerUtilAbility.getSpellAbilities(cards, player);
        
        // 3. Nach Priorität sortieren
        all.sort(ComputerUtilAbility.saEvaluator);
        
        // 4. Beste spielbare Aktion finden
        for (SpellAbility sa : all) {
            if (canPlaySa(sa) == AiPlayDecision.WillPlay) {
                return sa;
            }
        }
        return null;
    }
}
```

### AiPlayDecision Enum

Die AI verwendet ein Enum für Entscheidungsgründe:

```java
public enum AiPlayDecision {
    WillPlay,           // AI will die Karte spielen
    CantPlayAi,         // AI kann nicht entscheiden
    CantPlaySa,         // SpellAbility kann nicht gespielt werden
    CostNotAcceptable,  // Kosten zu hoch
    WaitForEndOfTurn,   // Warten auf End-of-Turn
    MissingNeededCards, // Fehlende Karten
    MissingPhaseRestrictions, // Falsche Phase
    // ... weitere
}
```

### AILogic System

Karten können `AILogic`-Parameter haben, die spezifisches Verhalten steuern:

```java
// In SpellAbilityAi.java
protected boolean checkAiLogic(final Player ai, final SpellAbility sa, final String aiLogic) {
    if ("Never".equals(aiLogic)) {
        return false;  // Niemals spielen
    }
    if ("Once".equals(aiLogic)) {
        return !sa.getHostCard().getAbilityActivatedThisTurn()
                  .getActivators(sa).contains(ai);
    }
    return true;
}

// Beispiel in Kartendefinition:
// AILogic$ PriorityDamage
// AILogic$ Removal
// AILogic$ Never
```

### AI Memory System

```java
public class AiCardMemory {
    public enum MemorySet {
        OWNED_CARDS,           // Eigene Karten in Zonen
        REVEALED_CARDS,        // Aufgedeckte gegnerische Karten
        MANDATORY_ATTACKERS,   // Kreaturen die angreifen müssen
        TRICK_CARDS,           // Erkannte Combat-Tricks
        // ... weitere
    }
    
    // Speichern einer Karte im Memory
    public void rememberCard(Card card, MemorySet memory);
    
    // Abfragen ob Karte bekannt
    public boolean hasRememberedCard(Card card, MemorySet memory);
}
```

---

## Kartenspezifische AI-Logik

### SpecialCardAi.java

Diese Klasse enthält **kartenspezifische AI-Logik** für komplexe Karten:

```java
public class SpecialCardAi {
    
    // Arena und Magus of the Arena
    public static class Arena {
        public static AiAbilityDecision consider(final Player ai, final SpellAbility sa) {
            // Spezielle Logik für Arena-Karten
            if (!game.getPhaseHandler().is(PhaseType.END_OF_TURN)) {
                return new AiAbilityDecision(0, AiPlayDecision.WaitForEndOfTurn);
            }
            // ... Kampf-Evaluierung
        }
    }
    
    // Black Lotus und Lotus Bloom
    public static class BlackLotus {
        public static boolean consider(final Player ai, final SpellAbility sa, 
                                       final ManaCostBeingPaid cost) {
            // Spezielle Logik: Nicht auf billige Spells verschwenden
            int minCMC = isLowCMCDeck ? 3 : 4;
            return paidCMC >= minCMC;
        }
    }
    
    // Brain in a Jar, Chrome Mox, Dark Depths, Demonic Consultation...
    // Viele weitere kartenspezifische Klassen
}
```

**Vorhandene kartenspezifische Klassen (Auszug):**
- Arena
- BlackLotus
- BrainInAJar
- ChromeMox
- DarkDepths
- DemonicConsultation
- Donate
- Food Chain
- Force of Will
- Gifts Ungiven
- Laboratory Maniac
- Lion's Eye Diamond
- Necropotence
- Thassa's Oracle
- Yawgmoth's Will
- Und viele mehr...

### SpecialAiLogic.java

Enthält **gemeinsam genutzte AI-Logik** für ähnliche Kartentypen:

```java
public class SpecialAiLogic {
    
    // Logik für Pongify/Crib Swap/Angelic Ascension
    public static boolean doPongifyLogic(final Player ai, final SpellAbility sa) {
        // Transformations-Removal Logik
        Card choice = selectBestTargetForUpgrade(ai, sa);
        if (choice != null) {
            sa.getTargets().add(choice);
            return true;
        }
        return false;
    }
    
    // Counterspell-Verzweigungslogik (z.B. für Archmage's Charm)
    public static boolean doBranchCounterspellLogic(final Player aiPlayer, 
                                                     final SpellAbility sa) {
        // Entscheide zwischen verschiedenen Modi
    }
    
    // Riot-Keyword Logik
    public static boolean preferHasteForRiot(SpellAbility sa, Player ai) {
        // Entscheide ob Haste oder +1/+1 Counter
    }
}
```

---

## Simulation und Spielzustandsbewertung

### Simulation-Modus

Die AI kann optional eine **Simulation** verwenden für bessere Entscheidungen:

```java
// Aktivierung in LobbyPlayerAi
public LobbyPlayerAi(String name, Set<AIOption> options) {
    super(name);
    if (options != null && options.contains(AIOption.USE_SIMULATION)) {
        this.useSimulation = true;
    }
}
```

### SpellAbilityPicker (Simulations-basierte Auswahl)

```java
public class SpellAbilityPicker {
    private Game game;
    private Player player;
    private Score bestScore;
    private Plan plan;
    
    public SpellAbility chooseSpellAbilityToPlay(SimulationController controller) {
        // 1. Aktuellen Spielstand bewerten
        Score origGameScore = new GameStateEvaluator()
            .getScoreForGameState(game, player);
        
        // 2. Kandidaten sammeln
        List<SpellAbility> candidateSAs = getCandidateSpellsAndAbilities();
        
        // 3. Plan erstellen durch Simulation
        createNewPlan(origGameScore, candidateSAs);
        
        // 4. Geplante Aktion zurückgeben
        return getPlannedSpellAbility(origGameScore, candidateSAs);
    }
}
```

### GameStateEvaluator (Spielzustandsbewertung)

Die Bewertung des Spielzustands ist entscheidend für gute Entscheidungen:

```java
public class GameStateEvaluator {
    private SimulationCreatureEvaluator eval = new SimulationCreatureEvaluator();
    
    public Score getScoreForGameState(Game game, Player aiPlayer) {
        if (game.isGameOver()) {
            return getScoreForGameOver(game, aiPlayer);
        }
        
        // Simuliere anstehenden Kampf
        CombatSimResult result = simulateUpcomingCombatThisTurn(game, aiPlayer);
        if (result != null) {
            return getScoreForGameStateImpl(result.gameCopy, aiPlayerCopy);
        }
        return getScoreForGameStateImpl(game, aiPlayer);
    }
    
    // Bewertungsfaktoren:
    // - Lebenspunkte
    // - Kreaturen auf dem Feld
    // - Karten in Hand
    // - Mana-Verfügbarkeit
    // - Planeswalker-Loyalität
    // - Board-Presence
}
```

### CreatureEvaluator (Kreaturenbewertung)

```java
public class CreatureEvaluator implements Function<Card, Integer> {
    
    public int evaluateCreature(final Card c) {
        int value = 80;  // Basiswert
        
        // Token-Abzug
        if (!c.isToken()) {
            value += 20;
        }
        
        // Power/Toughness
        value += power * 15;
        value += toughness * 10;
        
        // Evasion
        if (c.hasKeyword(Keyword.FLYING)) {
            value += power * 10;
        }
        if (c.hasKeyword(Keyword.MENACE)) {
            value += power * 4;
        }
        
        // Combat-Keywords
        if (c.hasKeyword(Keyword.DOUBLE_STRIKE)) {
            value += 10 + (power * 15);
        }
        if (c.hasKeyword(Keyword.DEATHTOUCH)) {
            value += 25;
        }
        if (c.hasKeyword(Keyword.LIFELINK)) {
            value += power * 10;
        }
        
        // ... viele weitere Faktoren
        return value;
    }
}
```

---

## Kampf-KI

### AiAttackController

Steuert Angriffsentscheidungen (1775 Zeilen):

```java
public class AiAttackController {
    private List<Card> attackers;    // Mögliche Angreifer
    private List<Card> blockers;     // Gegnerische Blocker
    private int aiAggression = 0;    // Aggressivitätslevel
    
    public AiAttackController(final Player ai) {
        this.ai = ai;
        defendingOpponent = choosePreferredDefenderPlayer(ai, true);
        refreshCombatants(defendingOpponent);
    }
    
    public void declareAttackers(Combat combat) {
        // 1. Aggressivität berechnen
        calculateAggression();
        
        // 2. Angreifer auswählen basierend auf:
        //    - Aggressivitätslevel
        //    - Gegnerische Blocker
        //    - Lebensbedrohung
        //    - Mana-Verfügbarkeit für Tricks
        
        // 3. Must-Attack Kreaturen hinzufügen
        
        // 4. Optional: Profitable Angriffe hinzufügen
    }
}
```

### AiBlockController

Steuert Blockentscheidungen (1379 Zeilen):

```java
public class AiBlockController {
    private List<Card> attackers;           // Alle Angreifer
    private List<Card> attackersLeft;       // Ungeblockte Angreifer
    private List<Card> blockedButUnkilled;  // Geblockt aber nicht tot
    private List<Card> blockersLeft;        // Verfügbare Blocker
    private boolean lifeInDanger = false;
    
    // Findet mögliche Blocker für einen Angreifer
    private static List<Card> getPossibleBlockers(Combat combat, Card attacker, 
                                                   List<Card> blockersLeft) {
        List<Card> blockers = new ArrayList<>();
        for (Card blocker : blockersLeft) {
            if (CombatUtil.canBlock(attacker, blocker, combat)) {
                blockers.add(blocker);
            }
        }
        return blockers;
    }
    
    // Hauptblocking-Logik
    public void assignBlockers(Combat combat) {
        // 1. Lebensbedrohliche Angriffe identifizieren
        // 2. Chump-Blocker für Lethal zuweisen
        // 3. Profitable Trades identifizieren
        // 4. Restliche Blocker zuweisen
    }
}
```

---

## Erweiterungspunkte für Deck-spezifische Playbooks

### 1. **Neues AI-Profil erstellen**

**Ort:** `forge-gui/res/ai/`

Erstellen Sie eine neue `.ai`-Datei, z.B. `ComboControl.ai`:

```ini
# Combo-Control Deck Profile
PLAY_AGGRO=false
CHANCE_TO_ATTACK_INTO_TRADE=0

# Ressourcen sparen
HOLD_LAND_DROP_FOR_MAIN2_IF_UNUSED=100
RESERVE_MANA_FOR_MAIN2_CHANCE=80

# Counterspells priorisieren
MIN_SPELL_CMC_TO_COUNTER=1
CHANCE_TO_COUNTER_CMC_1=80
CHANCE_TO_COUNTER_CMC_2=100
ALWAYS_COUNTER_OTHER_COUNTERSPELLS=true

# Konservatives Blocken
ENABLE_RANDOM_FAVORABLE_TRADES_ON_BLOCK=false
```

### 2. **Kartenspezifische Logik hinzufügen**

**Ort:** `forge-ai/src/main/java/forge/ai/SpecialCardAi.java`

```java
// Neue Inner Class für spezielle Karte
public static class MyComboCard {
    public static boolean consider(final Player ai, final SpellAbility sa) {
        // 1. Prüfe Spielzustand
        if (!isComboReady(ai)) {
            return false;
        }
        
        // 2. Prüfe ob alle Combo-Pieces vorhanden
        if (!hasAllComboPieces(ai)) {
            return false;
        }
        
        // 3. Führe Combo aus
        return executeCombo(ai, sa);
    }
    
    private static boolean isComboReady(Player ai) {
        // Implementierung
    }
}
```

### 3. **AILogic Parameter in Kartendefinitionen**

**Ort:** Karten-Scripting-Dateien

```
# In der Kartendefinition (.txt)
SVar:AILogic:ComboKey
SVar:AIPriority:100
```

### 4. **Ability-spezifische AI erweitern**

**Ort:** `forge-ai/src/main/java/forge/ai/ability/`

Jeder Ability-Typ hat eine eigene AI-Klasse:

| Datei | Ability-Typ |
|-------|-------------|
| `CounterAi.java` | Counterspells |
| `DrawAi.java` | Karten ziehen |
| `DamageDealAi.java` | Schaden zufügen |
| `ChangeZoneAi.java` | Karten verschieben |
| `PumpAi.java` | Kreaturen verstärken |
| `TokenAi.java` | Tokens erstellen |
| ... | (147+ Dateien) |

Beispiel für Erweiterung:

```java
// In DrawAi.java
@Override
protected AiAbilityDecision canPlayAI(Player ai, SpellAbility sa) {
    // Füge Deck-spezifische Logik hinzu
    if (ai.getController() instanceof PlayerControllerAi) {
        String deckType = getDeckType(ai);
        if ("Storm".equals(deckType)) {
            return handleStormDrawLogic(ai, sa);
        }
    }
    return super.canPlayAI(ai, sa);
}
```

### 5. **Deck-Statistiken nutzen**

**Ort:** `forge-ai/src/main/java/forge/ai/AiDeckStatistics.java`

```java
public class AiDeckStatistics {
    public float averageCMC = 0;
    public int maxCost = 0;
    public int[] maxPips = null;  // WUBRGC
    public int numLands = 0;
    
    // Analyse des Decks
    public static AiDeckStatistics fromDeck(Deck deck, Player player) {
        // Berechne Deck-Metriken
    }
}
```

### 6. **Neue AiProps hinzufügen**

**Ort:** `forge-ai/src/main/java/forge/ai/AiProps.java`

```java
public enum AiProps {
    // ... existierende Props ...
    
    // Neue Deck-spezifische Props
    COMBO_DECK_HOLD_PIECES("true"),
    COMBO_DECK_MIN_PIECES_TO_GO_OFF("3"),
    STORM_COUNT_THRESHOLD("5"),
    CONTROL_DECK_HOLD_COUNTERS("true"),
    // ...
}
```

### 7. **Simulation erweitern**

**Ort:** `forge-ai/src/main/java/forge/ai/simulation/`

| Datei | Beschreibung |
|-------|--------------|
| `GameSimulator.java` | Spielsimulation |
| `GameStateEvaluator.java` | Zustandsbewertung |
| `Plan.java` | Mehrschritt-Planung |
| `SpellAbilityPicker.java` | Auswahl der besten Aktion |

```java
// Erweiterte Bewertung für Deck-Typen
public class CustomGameStateEvaluator extends GameStateEvaluator {
    @Override
    protected Score evaluateDeckSpecificFactors(Game game, Player ai) {
        String deckType = detectDeckType(ai);
        switch (deckType) {
            case "Storm":
                return evaluateStormPosition(game, ai);
            case "Control":
                return evaluateControlPosition(game, ai);
            default:
                return super.evaluateDeckSpecificFactors(game, ai);
        }
    }
}
```

---

## Detaillierte Implementierung: saEvaluator

### Überblick

Der `saEvaluator` ist ein Comparator in `ComputerUtilAbility.java`, der SpellAbilities nach Priorität sortiert. Er bestimmt die Reihenfolge, in der die AI Aktionen evaluiert.

**Datei:** `forge-ai/src/main/java/forge/ai/ComputerUtilAbility.java` (Zeilen 244-464)

### Hauptstruktur

```java
public final static saComparator saEvaluator = new saComparator();

public final static class saComparator implements Comparator<SpellAbility> {
    @Override
    public int compare(final SpellAbility a, final SpellAbility b) {
        return compareEvaluator(a, b, false);
    }
    
    public int compareEvaluator(final SpellAbility a, final SpellAbility b, 
                                 boolean safeToEvaluateCreatures) {
        // Sortiert von höchsten Kosten zu niedrigsten
        int a1 = a.getPayCosts().getTotalMana().getCMC();
        int b1 = b.getPayCosts().getTotalMana().getCMC();
        
        // ... viele Prioritätsanpassungen ...
        
        return b1 - a1;  // Höhere Priorität zuerst
    }
}
```

### Prioritätsregeln (in Reihenfolge)

#### 1. AIActivateLast Parameter
```java
// Fähigkeiten mit AIActivateLast werden nach hinten sortiert
if (a.hasParam("AIActivateLast") && !b.hasParam("AIActivateLast")) {
    return 1;  // a kommt nach b
}
```

#### 2. Planar Dice Roll mit niedriger Priorität
```java
// Planar Die mit LowPriority$ True werden deprioritisiert
if (ApiType.RollPlanarDice == a.getApi()) {
    if (c.getSVar("AIRollPlanarDieParams").matches(".*lowpriority\\$\\s*true.*")) {
        return 1;  // Nach hinten sortieren
    }
}
```

#### 3. Energy-basierte Pump-Spells
```java
// Pump-Spells mit reinen Energy-Kosten werden deprioritisiert
// (Energy ist knapp, kann für Electrostatic Pummeler etc. nützlich sein)
if (a.getApi() == ApiType.Pump && a.getPayCosts().getCostEnergy() != null) {
    if (a.getPayCosts().hasOnlySpecificCostType(CostPayEnergy.class)) {
        a2 = a.getPayCosts().getCostEnergy().convertAmount();
    }
}
```

#### 4. 0-Mana-Kosten Spells zuerst (außer Mana-Abilities)
```java
// 0-Mana Spells zuerst (könnte ein Mox sein)
if (a1 == 0 && b1 > 0 && ApiType.Mana != a.getApi()) {
    return -1;  // a kommt vor b
}
```

#### 5. FreeSpellAI SVar
```java
// Karten mit FreeSpellAI SVar werden priorisiert
if (a.getHostCard().hasSVar("FreeSpellAI")) {
    return -1;
}
```

#### 6. Spectacle-Kosten bevorzugen
```java
// Günstigere Spectacle-Kosten werden bevorzugt
if (a.isSpectacle() && !b.isSpectacle() && a1 < b1) {
    return 1;
}
```

### getSpellAbilityPriority() - Detaillierte Prioritätsberechnung

Diese Methode berechnet zusätzliche Prioritätspunkte:

| Bedingung | Prioritätsänderung | Erklärung |
|-----------|-------------------|-----------|
| Kreatur-Spell | +1 | Kreaturen vor Nicht-Kreaturen |
| `AIPriorityModifier` SVar | +/- Wert | Benutzerdefinierte Anpassung |
| `EndOfTurnLeavePlay` | +1 | Vor Ablauf nutzen |
| `isCardRemAIDeck` | -10 | Karten die AI nicht spielen sollte |
| Equipment ohne Kreaturen | -9 | Erst Kreaturen, dann Equipment |
| Attach nach Combat | -1 | Nicht in Main 2 equippen |
| Surge (nicht gesurged) | -9 | Nicht-Surge Versionen ineffizient |
| Aus Friedhof spielbar | +50 | Snap-Cast Spells priorisieren |
| Storm-Keyword | -X | Profil-abhängig, Storm-Count aufbauen |
| Magecraft-Trigger | +1 | Profitiert von Spell-Cast |
| Kostenreduktion-Statisch | +1 | Ermöglicht mehr Plays |
| Surge/Prowl bezahlt | +9 | Alternative Kosten nutzen |
| Planeswalker Ultimate | +9 | Ultimates priorisieren |
| DestroyAll API | +4 | Boardwipes früh prüfen |
| Mana API | -9 | Mana-Abilities für Bezahlung aufheben |
| ManaRitual AILogic | +9 | Mana-Rituale vor anderen Spells |

### Kreatur-Evaluierung im Comparator

```java
if (safeToEvaluateCreatures) {
    // Kreaturenbewertung wird skaliert basierend auf bisheriger Priorität
    a1 += Math.round(ComputerUtilCard.evaluateCreature(a) / (10.5f + Math.abs(a1)));
    b1 += Math.round(ComputerUtilCard.evaluateCreature(b) / (10.5f + Math.abs(b1)));
}
```

### Kreaturen-Sortierung

```java
public static List<SpellAbility> sortCreatureSpells(final List<SpellAbility> all) {
    // Versucht Power Creep zu glätten, CMC weniger wichtig
    final List<SpellAbility> creatures = filterListByApi(all, ApiType.PermanentCreature);
    if (creatures.size() <= 1) return all;
    
    // Sortiert nach Kreaturenbewertung statt nur CMC
    creatures.sort(ComputerUtilCard.EvaluateCreatureSpellComparator);
    // ... ersetzt Kreaturen in der Original-Liste
}
```

---

## Detaillierte Implementierung: canPlayAI()

### Überblick

`canPlayAI()` (eigentlich `canPlay()` und `canPlayWithoutRestrict()`) ist die Hauptmethode in `SpellAbilityAi.java`, die entscheidet ob die AI eine Fähigkeit spielen möchte.

**Datei:** `forge-ai/src/main/java/forge/ai/SpellAbilityAi.java` (484 Zeilen)

### Entscheidungsablauf

```
┌─────────────────────────────────────────────────────────────┐
│                    canPlayWithSubs()                         │
│  Prüft Haupt-SA und alle Sub-Abilities                      │
└─────────────────────┬───────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────────┐
│                      canPlay()                               │
│  1. Prüft Restrictions (canPlay der Karte)                  │
│  2. Ruft canPlayWithoutRestrict() auf                       │
└─────────────────────┬───────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────────┐
│               canPlayWithoutRestrict()                       │
│  1. Prüft AILogic Parameter                                 │
│  2. Prüft Phasen-Restriktionen                              │
│  3. Prüft Runaway-Aktivierungen                             │
│  4. Ruft checkApiLogic() auf                                │
│  5. Prüft Kosten                                            │
│  6. Prüft Bedingungen                                       │
└─────────────────────┴───────────────────────────────────────┘
```

### Schritt 1: canPlayWithSubs()

```java
public final AiAbilityDecision canPlayWithSubs(Player aiPlayer, SpellAbility sa) {
    // Prüft die Haupt-Ability
    AiAbilityDecision decision = canPlay(aiPlayer, sa);
    
    // Wenn nicht spielwillig und nicht "PlayForSub" Logik → Abbruch
    if (!decision.willingToPlay() && !"PlayForSub".equals(sa.getParam("AILogic"))) {
        return decision;
    }
    
    // Prüft alle Sub-Abilities rekursiv
    final AbilitySub subAb = sa.getSubAbility();
    if (subAb == null) {
        return decision;
    }
    return chkDrawbackWithSubs(aiPlayer, subAb);
}
```

### Schritt 2: canPlay()

```java
protected AiAbilityDecision canPlay(Player ai, SpellAbility sa) {
    // Basisprüfung: Kann die Karte überhaupt gespielt werden?
    if (sa.getRestrictions() != null && 
        !sa.getRestrictions().canPlay(sa.getHostCard(), sa)) {
        return new AiAbilityDecision(0, AiPlayDecision.CantPlaySa);
    }
    return canPlayWithoutRestrict(ai, sa);
}
```

### Schritt 3: canPlayWithoutRestrict() - Hauptlogik

```java
protected AiAbilityDecision canPlayWithoutRestrict(Player ai, SpellAbility sa) {
    final Card source = sa.getHostCard();

    // ═══════════════════════════════════════════════════════════════
    // PHASE 1: AILogic Parameter prüfen
    // ═══════════════════════════════════════════════════════════════
    if (sa.hasParam("AILogic")) {
        final String logic = sa.getParam("AILogic");
        
        // Spezialfall: AlwaysOnDiscard bei Handlimit-Überschreitung
        final boolean alwaysOnDiscard = "AlwaysOnDiscard".equals(logic) 
            && ai.getGame().getPhaseHandler().is(PhaseType.END_OF_TURN, ai)
            && !ai.isUnlimitedHandSize() 
            && ai.getCardsIn(ZoneType.Hand).size() > ai.getMaxHandSize();
        
        // AILogic-Check (z.B. "Never", "Once", etc.)
        if (!checkAiLogic(ai, sa, logic)) {
            return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
        }
        
        // Phasen-Check mit AILogic
        if (!alwaysOnDiscard && !checkPhaseRestrictions(ai, sa, ph, logic)) {
            return new AiAbilityDecision(0, AiPlayDecision.MissingPhaseRestrictions);
        }
    } else {
        // ═══════════════════════════════════════════════════════════════
        // PHASE 2: Standard Phasen-Restriktionen (ohne AILogic)
        // ═══════════════════════════════════════════════════════════════
        if (!checkPhaseRestrictions(ai, sa, ph)) {
            return new AiAbilityDecision(0, AiPlayDecision.MissingPhaseRestrictions);
        }
        
        // Verhindert endlose Aktivierungsschleifen
        if (ComputerUtil.preventRunAwayActivations(sa)) {
            return new AiAbilityDecision(0, AiPlayDecision.StopRunawayActivations);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // PHASE 3: API-spezifische Logik
    // ═══════════════════════════════════════════════════════════════
    AiAbilityDecision decision = checkApiLogic(ai, sa);
    if (!decision.willingToPlay()) {
        return decision;
    }

    // ═══════════════════════════════════════════════════════════════
    // PHASE 4: Kostenprüfung
    // ═══════════════════════════════════════════════════════════════
    final Cost cost = sa.getPayCosts();
    if (cost != null && !willPayCosts(ai, sa, cost, source)) {
        return new AiAbilityDecision(0, AiPlayDecision.CostNotAcceptable);
    }

    // ═══════════════════════════════════════════════════════════════
    // PHASE 5: Bedingungsprüfung (Conditions)
    // ═══════════════════════════════════════════════════════════════
    if (!checkConditions(ai, sa)) {
        SpellAbility sub = sa.getSubAbility();
        if (sub == null || !checkConditions(ai, sub)) {
            return new AiAbilityDecision(0, AiPlayDecision.NeedsToPlayCriteriaNotMet);
        }
    }
    
    return decision;  // WillPlay!
}
```

### checkAiLogic() - AILogic Parameter

```java
protected boolean checkAiLogic(Player ai, SpellAbility sa, String aiLogic) {
    // "Never" → Niemals spielen
    if ("Never".equals(aiLogic)) {
        return false;
    }
    
    // "Once" → Nur einmal pro Zug spielen
    if (!"Once".equals(aiLogic)) {
        return !sa.getHostCard().getAbilityActivatedThisTurn()
                  .getActivators(sa).contains(ai);
    }
    
    return true;
}
```

### Bekannte AILogic-Werte

| AILogic | Verhalten |
|---------|-----------|
| `Never` | Niemals spielen |
| `Once` | Nur einmal pro Zug |
| `Always` | Immer spielen (bei Triggern) |
| `AtOppEOT` | Nur am gegnerischen End-of-Turn |
| `AlwaysOnDiscard` | Immer wenn Handlimit überschritten |
| `PlayForSub` | Für Sub-Abilities spielen |
| `ManaRitual` | Mana-Ritual Priorität |
| `Removal` | Removal-Logik |
| `PriorityDamage` | Schaden-Priorität |
| ... | Viele weitere spezifische Logiken |

### checkPhaseRestrictions() - Phasen-Prüfung

```java
protected boolean checkPhaseRestrictions(Player ai, SpellAbility sa, PhaseHandler ph) {
    // Standard: Keine Einschränkung
    return true;
}

protected boolean checkPhaseRestrictions(Player ai, SpellAbility sa, 
                                          PhaseHandler ph, String logic) {
    // Spezielle Logik für "AtOppEOT"
    if (logic.equals("AtOppEOT")) {
        return ph.getNextTurn() == ai && ph.is(PhaseType.END_OF_TURN);
    }
    return checkPhaseRestrictions(ai, sa, ph);
}
```

### checkApiLogic() - API-spezifische Entscheidung

```java
protected AiAbilityDecision checkApiLogic(Player ai, SpellAbility sa) {
    // Standard-Implementierung (wird von Subklassen überschrieben!)
    if (sa.getActivationsThisTurn() == 0 || MyRandom.getRandom().nextFloat() < .8f) {
        // 80% Chance die Ability zu spielen
        return new AiAbilityDecision(100, AiPlayDecision.WillPlay);
    }
    return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
}
```

**Wichtig:** Jede API (Draw, Damage, Counter, etc.) hat eine eigene Subklasse mit überschriebenem `checkApiLogic()`!

### willPayCosts() - Kostenakzeptanz

```java
protected boolean willPayCosts(Player payer, SpellAbility sa, Cost cost, Card source) {
    // Lebenskosten prüfen (max 4 Leben standardmäßig)
    if (!ComputerUtilCost.checkLifeCost(payer, cost, source, 4, sa)) {
        return false;
    }
    
    // Abwurf-Kosten prüfen
    if (!ComputerUtilCost.checkDiscardCost(payer, cost, source, sa)) {
        return false;
    }
    
    // Opfer-Kosten prüfen
    if (!ComputerUtilCost.checkSacrificeCost(payer, cost, source, sa)) {
        return false;
    }
    
    // Counter-Entfernungs-Kosten prüfen
    if (!ComputerUtilCost.checkRemoveCounterCost(cost, source, sa)) {
        return false;
    }
    
    return true;
}
```

### checkConditions() - Bedingungsprüfung

```java
protected boolean checkConditions(Player ai, SpellAbility sa) {
    // Kopiert Bedingungen für AI-spezifische Prüfung
    SpellAbilityCondition con = (SpellAbilityCondition) sa.getConditions().copy();

    // Mana-Spent Bedingung prüfen
    if (!con.getManaSpent().isEmpty()) {
        ManaCostBeingPaid paid = new ManaCostBeingPaid(
            new ManaCost(new ManaCostParser(con.getManaSpent())));
        if (ComputerUtilMana.canPayManaCost(paid, sa, ai, sa.isTrigger())) {
            con.setManaSpent("");  // Bedingung erfüllt
        }
    }

    return con.areMet(sa);
}
```

### Trigger-Behandlung: doTriggerNoCost()

```java
protected AiAbilityDecision doTriggerNoCost(Player aiPlayer, SpellAbility sa, 
                                             boolean mandatory) {
    // Versucht normale Logik
    AiAbilityDecision decision = canPlayWithoutRestrict(aiPlayer, sa);
    if (decision.willingToPlay() && (!mandatory || sa.isTargetNumberValid())) {
        return decision;
    }

    // Nicht mandatory → Abbruch
    if (!mandatory) {
        return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
    }

    // Mandatory mit Targeting → Versuche irgendjemanden zu zielen
    if (sa.usesTargeting()) {
        List<Player> players = Lists.newArrayList();
        players.addAll(aiPlayer.getOpponents());  // Gegner zuerst
        players.addAll(aiPlayer.getAllies());     // Dann Verbündete
        players.add(aiPlayer);                    // Dann sich selbst

        for (Player p : players) {
            if (sa.canTarget(p)) {
                sa.resetTargets();
                sa.getTargets().add(p);
                return new AiAbilityDecision(100, AiPlayDecision.WillPlay);
            }
        }
        return new AiAbilityDecision(0, AiPlayDecision.TargetingFailed);
    }
    
    return new AiAbilityDecision(100, AiPlayDecision.WillPlay);
}
```

### Sub-Ability Prüfung: chkDrawback()

```java
public AiAbilityDecision chkDrawback(Player aiPlayer, SpellAbility sa) {
    // Sub-Ability mit Targeting aber ohne Kandidaten → Fehler
    if (sa.usesTargeting()) {
        if (!sa.getTargetRestrictions().hasCandidates(sa)) {
            return new AiAbilityDecision(0, AiPlayDecision.TargetingFailed);
        }
        // Warnung: sollte überschrieben werden!
        System.err.println("Warning: default implementation of chkAIDrawback...");
        return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
    }
    return new AiAbilityDecision(100, AiPlayDecision.WillPlay);
}
```

### API-spezifische Subklassen (Beispiele)

Jede Ability-API hat eine eigene AI-Klasse in `forge-ai/ability/`:

| Datei | Überschreibt | Beschreibung |
|-------|--------------|--------------|
| `CounterAi.java` | `checkApiLogic()` | Wann countern? |
| `DrawAi.java` | `checkApiLogic()` | Wann Karten ziehen? |
| `DamageDealAi.java` | `checkApiLogic()` | Wann Schaden zufügen? |
| `DestroyAi.java` | `checkApiLogic()` | Wann zerstören? |
| `PumpAi.java` | `checkApiLogic()` | Wann pumpen? |
| `TokenAi.java` | `checkApiLogic()` | Wann Tokens erstellen? |

Diese Klassen enthalten die tatsächliche "Intelligenz" für jede Ability-Art.

---

## AI-Entscheidungs-Logging

### Übersicht

Das AI-Decision-Logging-System protokolliert die Entscheidungen der AI im Game-Log für Debugging und Analyse. Es hilft zu verstehen, warum die AI bestimmte Karten oder Fähigkeiten spielt und warum sie bestimmte Ziele wählt.

### Aktivierung

Das Logging ist standardmäßig aktiviert. Im Game-Log erscheinen Einträge mit dem Typ `AI_DECISION`.

**⚠️ WICHTIG: Log-Filter-Problem**

Die AI-Decision-Logs werden standardmäßig **NICHT angezeigt**, obwohl der Logger aktiv ist! Das liegt am Log-Filter-System:

- Die `GameLogEntryType`-Enum verwendet `compareTo()` für die Filterung
- `AI_DECISION` ist der **letzte Eintrag** (Position 19) in der Enum
- Der Standard-Filter ist `DAMAGE` (Position 8)
- Nur Log-Einträge mit Position ≤ Filter-Position werden angezeigt

**Lösung: Log-Level auf `AI_DECISION` setzen:**

1. **In der UI:** Settings → Game Log Verbosity → `AI_DECISION`
2. **Oder in `userdata/forge.preferences`:** 
   ```
   DEV_LOG_ENTRY_TYPE=AI_DECISION
   ```

### Log-Speicherorte

| Typ | Speicherort | Enthält AI-Decisions? |
|-----|-------------|----------------------|
| In-Game Log Panel | UI während des Spiels | Ja (wenn Filter richtig gesetzt) |
| Replay Logs | `userdata/replaylogs/*.json` | Nein (nur Spielaktionen) |
| **Keine separate Datei** | - | - |

**Hinweis:** Die AI-Decision-Logs werden nur im In-Game-Log angezeigt, nicht in separate Dateien geschrieben.

### Implementierung

**Hauptklasse:** `forge-ai/src/main/java/forge/ai/AiDecisionLogger.java`

```java
public class AiDecisionLogger {
    // Logging ein-/ausschalten
    public static void setEnabled(boolean value);
    
    // Loggt warum die AI eine Spell/Ability spielt
    public static void logDecision(Player ai, SpellAbility sa, AiPlayDecision decision);
    
    // Loggt mit Top-3 Alternativen die die AI auch hätte wählen können
    public static void logDecisionWithAlternatives(Player ai, SpellAbility sa, 
                                                    AiPlayDecision decision, 
                                                    List<SpellAbility> alternatives);
    
    // Loggt warum die AI bestimmte Ziele gewählt hat
    public static void logTargeting(Player ai, SpellAbility sa, String targetReason);
    
    // Loggt warum die AI etwas NICHT spielt (nur interessante Gründe)
    public static void logSkipDecision(Player ai, SpellAbility sa, AiPlayDecision decision);
    
    // Loggt Kampfentscheidungen (Angriff/Block)
    public static void logCombatDecision(Player ai, String decision);
}
```

### Log-Format

#### Spell/Ability-Entscheidungen mit Alternativen
```
[AI] PlayerName decides to play: CardName | Reason: Best available play | Type: PermanentCreature | Targets: Cards[TargetCard (ControllerName)]
[AI] PlayerName other options considered: AlternativeCard1 (DamageDeal), AlternativeCard2 (Counter), AlternativeCard3 (Draw)
```

#### Kampfentscheidungen
```
[AI] PlayerName combat: Attacking with 3 creature(s): Creature1 -> Opponent, Creature2 -> Planeswalker | Aggression level: 5
```

### AiPlayDecision Beschreibungen

| Decision | Log-Beschreibung |
|----------|------------------|
| WillPlay | Best available play |
| MandatoryPlay | Mandatory (forced to play) |
| ImpactCombat | Will impact combat favorably |
| Removal | Removing a threat |
| Tempo | Tempo advantage |
| CardAdvantage | Card advantage |
| WaitForMain2 | Waiting for Main Phase 2 |
| TargetingFailed | No valid targets |
| CostNotAcceptable | Cost too high |
| LifeInDanger | Life in danger, being defensive |

### Verwendung im Code

Die Logging-Aufrufe sind an folgenden Stellen integriert:

1. **AiController.chooseSpellAbilityToPlayFromList()** - Wenn die AI entscheidet, eine Spell/Ability zu spielen
2. **AiController.chooseCounterSpell()** - Wenn die AI einen Counterspell wählt
3. **AiAttackController.declareAttackers()** - Wenn die AI ihre Angreifer deklariert

### GameLogEntryType

Ein neuer Log-Typ wurde hinzugefügt:

```java
// In GameLogEntryType.java
AI_DECISION("AI Decision")
```

### Beispiel-Output im Game-Log

```
[AI] Computer decides to play: Lightning Bolt | Reason: Best available play | Type: DamageDeal | Targets: Cards[Grizzly Bears (Human)]
[AI] Computer other options considered: Giant Growth (Pump), Counterspell (Counter), Divination (Draw)
[AI] Computer decides to play: Counterspell | Reason: Best available play | Type: Counter | Targets: Spells[Wrath of God]
[AI] Computer combat: Attacking with 2 creature(s): Serra Angel -> Human, Shivan Dragon -> Jace, the Mind Sculptor | Aggression level: 5
```

---

## Zusammenfassung

### Architektur-Übersicht

```
┌─────────────────────────────────────────────────────────────────┐
│                        FORGE AI SYSTEM                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────────┐     ┌──────────────────┐                     │
│  │ AI Profiles  │────▶│  AiProfileUtil   │                     │
│  │ (.ai files)  │     │  AiProps (Enum)  │                     │
│  └──────────────┘     └────────┬─────────┘                     │
│                                │                               │
│                                ▼                               │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                    AiController                          │   │
│  │  ┌─────────────┐  ┌────────────────┐  ┌──────────────┐ │   │
│  │  │ CardMemory  │  │ PredictedCombat│  │ SimPicker    │ │   │
│  │  └─────────────┘  └────────────────┘  └──────────────┘ │   │
│  └───────────────────────────┬─────────────────────────────┘   │
│                              │                                 │
│         ┌────────────────────┼────────────────────┐            │
│         ▼                    ▼                    ▼            │
│  ┌──────────────┐   ┌────────────────┐   ┌────────────────┐   │
│  │ ComputerUtil │   │ AbilityAi (147)│   │ SpecialCardAi  │   │
│  │ (Utilities)  │   │ (per API Type) │   │ (Named Cards)  │   │
│  └──────────────┘   └────────────────┘   └────────────────┘   │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                    Combat System                         │   │
│  │  ┌──────────────────┐    ┌───────────────────┐          │   │
│  │  │AiAttackController│    │AiBlockController  │          │   │
│  │  └──────────────────┘    └───────────────────┘          │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                  Simulation (Optional)                   │   │
│  │  ┌────────────┐  ┌──────────────────┐  ┌────────────┐  │   │
│  │  │GameCopier  │  │GameStateEvaluator│  │   Plan     │  │   │
│  │  └────────────┘  └──────────────────┘  └────────────┘  │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Erweiterungspunkte für Deck-Playbooks

| Priorität | Erweiterungspunkt | Aufwand | Flexibilität |
|-----------|-------------------|---------|--------------|
| 1 | Neue `.ai` Profile | Niedrig | Mittel |
| 2 | AILogic in Karten | Niedrig | Niedrig |
| 3 | SpecialCardAi | Mittel | Hoch |
| 4 | AiProps erweitern | Mittel | Hoch |
| 5 | Ability-AI anpassen | Hoch | Sehr Hoch |
| 6 | Simulation erweitern | Sehr Hoch | Sehr Hoch |

### Empfohlener Ansatz für Deck-Playbooks

1. **Phase 1**: Neue AI-Profile für verschiedene Archetypen (Aggro, Control, Combo, Midrange)
2. **Phase 2**: AiProps für deck-spezifische Verhaltensweisen erweitern
3. **Phase 3**: SpecialCardAi für Key-Karten der Archetypen
4. **Phase 4**: Deck-Erkennung implementieren (basierend auf AiDeckStatistics)
5. **Phase 5**: GameStateEvaluator für archetype-spezifische Bewertung erweitern

### Wichtige Dateien für Implementierung

| Kategorie | Dateipfad |
|-----------|-----------|
| Profile | `forge-gui/res/ai/*.ai` |
| Hauptlogik | `forge-ai/src/main/java/forge/ai/AiController.java` |
| Eigenschaften | `forge-ai/src/main/java/forge/ai/AiProps.java` |
| Kartenlogik | `forge-ai/src/main/java/forge/ai/SpecialCardAi.java` |
| Abilities | `forge-ai/src/main/java/forge/ai/ability/*.java` |
| Kampf | `forge-ai/src/main/java/forge/ai/AiAttackController.java` |
| Bewertung | `forge-ai/src/main/java/forge/ai/CreatureEvaluator.java` |
| Simulation | `forge-ai/src/main/java/forge/ai/simulation/*.java` |

