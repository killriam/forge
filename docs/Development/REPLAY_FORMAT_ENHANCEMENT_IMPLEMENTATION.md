# Forge Replay Format - Implementierungsplan für Limitierungen

## Übersicht

Dieses Dokument beschreibt den Implementierungsplan zur Behebung der identifizierten Limitierungen im Forge MTG Replay Format.

---

## 1. Missing Object-to-Card Mapping (🔴 Kritisch)

### Problem
`initial_state.objects` ist immer leer (`{}`), obwohl die Datenstruktur `GameState.ObjectState` bereits existiert.

### Ursache im Code

**Datei:** `ReplayNotationExporter.java`, Methode `captureInitialState()` (Zeile 70-99)

```java
private void captureInitialState() {
    GameState state = new GameState();
    // ... Zone-Initialisierung
    // ❌ PROBLEM: objects wird nie befüllt!
    replayLog.setInitialState(state);
}
```

**Datei:** `ReplayJsonSerializer.java`, Methode `appendGameState()` (Zeile 115-123)

```java
private static void appendGameState(StringBuilder json, GameState state) {
    // ...
    json.append("    \"objects\": {}\n");  // ❌ Hardcoded leer!
}
```

### Lösung

#### Schritt 1: `ReplayNotationExporter.captureInitialState()` erweitern

```java
private void captureInitialState() {
    GameState state = new GameState();
    state.setTurn(0);
    state.setPhase("PREGAME");
    state.setStep("PREGAME");

    // Initialize player states
    for (Player player : game.getPlayers()) {
        GameState.PlayerState playerState = new GameState.PlayerState();
        playerState.setLife(player.getLife());
        playerState.setMaxHandSize(7);
        state.getPlayers().put(getPlayerId(player), playerState);
    }

    // Initialize zones (existing code)
    // ...

    // ✅ NEU: Befülle initial_state.objects mit allen Karten
    captureAllGameObjects(state);

    replayLog.setInitialState(state);
}

/**
 * Capture all game objects (cards) and map them to object IDs.
 * This enables complete card identification throughout the game.
 */
private void captureAllGameObjects(GameState state) {
    for (Player player : game.getPlayers()) {
        String playerId = getPlayerId(player);
        
        // Process all zones
        captureZoneObjects(state, player, ZoneType.Library, playerId + ":library");
        captureZoneObjects(state, player, ZoneType.Hand, playerId + ":hand");
        captureZoneObjects(state, player, ZoneType.Graveyard, playerId + ":graveyard");
        captureZoneObjects(state, player, ZoneType.Command, playerId + ":command");
        captureZoneObjects(state, player, ZoneType.Sideboard, playerId + ":sideboard");
    }
    
    // Process shared zones
    captureSharedZoneObjects(state, ZoneType.Battlefield, "battlefield");
    captureSharedZoneObjects(state, ZoneType.Exile, "exile");
    captureSharedZoneObjects(state, ZoneType.Stack, "stack");
}

private void captureZoneObjects(GameState state, Player player, ZoneType zoneType, String zoneName) {
    PlayerZone zone = player.getZone(zoneType);
    if (zone == null) return;
    
    int position = 0;
    for (Card card : zone) {
        String cardId = getCardId(card);  // Assigns stable ID
        
        GameState.ObjectState objState = new GameState.ObjectState();
        objState.setCardRef(getActualCardName(card));
        objState.setOwner(getPlayerId(player));
        objState.setController(getPlayerId(card.getController()));
        objState.setZone(zoneName);
        objState.setTapped(card.isTapped());
        objState.setFaceDown(card.isFaceDown());
        objState.setFlipped(card.isFlipped());
        
        // Position in library (für Shuffle-Seed-Verification)
        if (zoneType == ZoneType.Library) {
            objState.getNotes().put("position", position++);
        }
        
        state.getObjects().put(cardId, objState);
    }
}

private void captureSharedZoneObjects(GameState state, ZoneType zoneType, String zoneName) {
    for (Card card : game.getCardsIn(zoneType)) {
        String cardId = getCardId(card);
        
        GameState.ObjectState objState = new GameState.ObjectState();
        objState.setCardRef(getActualCardName(card));
        objState.setOwner(getPlayerId(card.getOwner()));
        objState.setController(getPlayerId(card.getController()));
        objState.setZone(zoneName);
        objState.setTapped(card.isTapped());
        objState.setFaceDown(card.isFaceDown());
        objState.setFlipped(card.isFlipped());
        
        // Counters
        for (Map.Entry<CounterType, Integer> counter : card.getCounters().entrySet()) {
            objState.getCounters().put(counter.getKey().getName(), counter.getValue());
        }
        
        // Attached to
        if (card.getAttachedTo() != null) {
            objState.setAttachedTo(getCardId(card.getAttachedTo()));
        }
        
        state.getObjects().put(cardId, objState);
    }
}
```

#### Schritt 2: `ReplayJsonSerializer.appendGameState()` korrigieren

```java
private static void appendGameState(StringBuilder json, GameState state) {
    json.append("{\n");
    json.append("    \"turn\": ").append(state.getTurn()).append(",\n");
    json.append("    \"phase\": ").append(quote(state.getPhase())).append(",\n");
    json.append("    \"step\": ").append(quote(state.getStep())).append(",\n");
    json.append("    \"priority\": ").append(quote(state.getPriority())).append(",\n");
    json.append("    \"active_player\": ").append(quote(state.getActivePlayer())).append(",\n");
    
    // Players
    json.append("    \"players\": ");
    appendPlayerStates(json, state.getPlayers());
    json.append(",\n");
    
    // Zones
    json.append("    \"zones\": ");
    appendZones(json, state.getZones());
    json.append(",\n");
    
    // ✅ NEU: Objects richtig serialisieren
    json.append("    \"objects\": ");
    appendObjects(json, state.getObjects());
    json.append("\n");
    
    json.append("  }");
}

private static void appendObjects(StringBuilder json, Map<String, GameState.ObjectState> objects) {
    json.append("{\n");
    boolean first = true;
    for (Map.Entry<String, GameState.ObjectState> entry : objects.entrySet()) {
        if (!first) json.append(",\n");
        json.append("      \"").append(escape(entry.getKey())).append("\": ");
        appendObjectState(json, entry.getValue());
        first = false;
    }
    json.append("\n    }");
}

private static void appendObjectState(StringBuilder json, GameState.ObjectState obj) {
    json.append("{");
    json.append("\"card_ref\": ").append(quote(obj.getCardRef())).append(", ");
    json.append("\"owner\": ").append(quote(obj.getOwner())).append(", ");
    json.append("\"controller\": ").append(quote(obj.getController())).append(", ");
    json.append("\"zone\": ").append(quote(obj.getZone()));
    
    if (obj.isTapped()) json.append(", \"tapped\": true");
    if (obj.isFaceDown()) json.append(", \"face_down\": true");
    if (obj.isFlipped()) json.append(", \"flipped\": true");
    
    if (!obj.getCounters().isEmpty()) {
        json.append(", \"counters\": ");
        appendMap(json, (Map<String, Object>)(Map)obj.getCounters());
    }
    
    if (obj.getAttachedTo() != null) {
        json.append(", \"attached_to\": ").append(quote(obj.getAttachedTo()));
    }
    
    if (!obj.getNotes().isEmpty()) {
        json.append(", \"notes\": ");
        appendMap(json, obj.getNotes());
    }
    
    json.append("}");
}
```

### Änderung am Model

**Datei:** `GameState.ObjectState` - Feld `cardRef` umbenennen/erweitern:

```java
public static class ObjectState {
    private String cardRef;     // Referenz auf card_index (Kartenname)
    private String cardName;    // ✅ NEU: Direkter Kartenname für Lesbarkeit
    // ... rest bleibt gleich
}
```

---

## 2. Empty L2 Learning Views (🟠 Hoch)

### Problem
`views_l2` ist immer leer (`[]`), obwohl `ReplayL2Generator` existiert.

### Ursache im Code

1. **`ReplayJsonSerializer.appendL2Units()`** (Zeile 155):
   ```java
   private static void appendL2Units(StringBuilder json, List<L2Unit> units) {
       json.append("[]");  // ❌ Hardcoded leer!
   }
   ```

2. **`ReplayL2Generator.generateL2Units()`** wird nicht aufgerufen

3. **`ReplayL2Generator.createStateSnapshot()`** erstellt nur minimale Snapshots

### Lösung

#### Schritt 1: L2-Generierung aktivieren in `GameLogSaver`

```java
public static File saveGameLogReplayNotation(Game game, boolean includeL2) {
    ReplayNotationExporter exporter = new ReplayNotationExporter(game);
    
    // Set game outcome
    if (game.getOutcome() != null) {
        exporter.setGameOutcome(game.getOutcome());
    }
    
    // ✅ NEU: L2 generieren wenn gewünscht
    if (includeL2) {
        ReplayL2Generator l2gen = new ReplayL2Generator(exporter.getReplayLog());
        l2gen.generateL2Units();
    }
    
    return exporter.exportToFile(outputDir);
}
```

#### Schritt 2: `ReplayJsonSerializer.appendL2Units()` implementieren

```java
private static void appendL2Units(StringBuilder json, List<L2Unit> units) {
    json.append("[\n");
    for (int i = 0; i < units.size(); i++) {
        L2Unit unit = units.get(i);
        json.append("    {\n");
        json.append("      \"u\": ").append(unit.getU()).append(",\n");
        json.append("      \"t_start\": ").append(quote(unit.getTStart())).append(",\n");
        json.append("      \"t_end\": ").append(quote(unit.getTEnd())).append(",\n");
        json.append("      \"l1_range\": [").append(unit.getL1Range()[0]).append(", ").append(unit.getL1Range()[1]).append("],\n");
        
        json.append("      \"decision_events\": [");
        for (int j = 0; j < unit.getDecisionEvents().size(); j++) {
            if (j > 0) json.append(", ");
            json.append(unit.getDecisionEvents().get(j));
        }
        json.append("],\n");
        
        json.append("      \"before\": ");
        appendGameStateCompact(json, unit.getBefore());
        json.append(",\n");
        
        json.append("      \"stack\": ");
        appendStackItems(json, unit.getStack());
        json.append(",\n");
        
        json.append("      \"after\": ");
        appendGameStateCompact(json, unit.getAfter());
        json.append("\n");
        
        json.append("    }");
        if (i < units.size() - 1) json.append(",");
        json.append("\n");
    }
    json.append("  ]");
}
```

#### Schritt 3: `ReplayL2Generator` State-Tracking erweitern

Der Generator benötigt Zugriff auf den aktuellen GameState während des Spiels. Dafür muss ein State-Tracker implementiert werden:

```java
public class GameStateTracker {
    private final Map<Integer, GameState> stateSnapshots = new HashMap<>();
    
    /**
     * Capture current game state and associate with L1 event index.
     */
    public void captureState(Game game, int l1EventIndex) {
        GameState state = createFullSnapshot(game);
        stateSnapshots.put(l1EventIndex, state);
    }
    
    public GameState getStateAt(int l1EventIndex) {
        // Return exact or nearest snapshot
        return stateSnapshots.getOrDefault(l1EventIndex, findNearestState(l1EventIndex));
    }
    
    private GameState createFullSnapshot(Game game) {
        GameState state = new GameState();
        // Capture full game state...
        return state;
    }
}
```

---

## 3. Missing Winner Information (🟡 Mittel)

### Problem
`meta.winner` ist oft `null`, selbst wenn das Spiel beendet ist.

### Ursache im Code

**Datei:** `ReplayNotationExporter.java`, Methode `setGameOutcome()` (Zeile 109-131)

Der Winner wird nur gesetzt wenn `GameOutcome.getWinningPlayer()` nicht null ist. Problem: Diese Methode wird nicht immer aufgerufen.

### Lösung

#### Schritt 1: ReplayMeta erweitern

```java
public class ReplayMeta {
    // ... bestehende Felder
    private String winCondition;     // ✅ NEU
    private boolean conceded;        // ✅ NEU
    private String activePlayerAtEnd; // ✅ NEU
    
    // Getters/Setters...
}
```

#### Schritt 2: `setGameOutcome()` erweitern

```java
public void setGameOutcome(GameOutcome outcome) {
    if (outcome == null) return;
    
    ReplayMeta meta = replayLog.getMeta();
    
    // Set winner
    RegisteredPlayer winningPlayer = outcome.getWinningPlayer();
    if (winningPlayer != null) {
        for (int i = 0; i < game.getPlayers().size(); i++) {
            Player p = game.getPlayers().get(i);
            if (p.getRegisteredPlayer() == winningPlayer) {
                meta.setWinner("P" + (i + 1));
                break;
            }
        }
    } else if (outcome.isDraw()) {
        meta.setWinner("draw");
    }
    
    // ✅ NEU: Win condition
    meta.setWinCondition(determineWinCondition(outcome));
    
    // ✅ NEU: Concession check
    meta.setConceded(outcome.isConceded());
    
    // Set turns
    meta.setTurns(outcome.getLastTurnNumber());
    
    // Set duration
    long durationMs = System.currentTimeMillis() - gameStartTime;
    meta.setDurationSeconds((int) (durationMs / 1000));
}

private String determineWinCondition(GameOutcome outcome) {
    // Analyze how the game ended
    for (Player p : game.getPlayers()) {
        if (p.getLife() <= 0) return "life_zero";
        if (p.getPoisonCounters() >= 10) return "poison";
        if (p.getZone(ZoneType.Library).isEmpty() && /* drew from empty */) return "decked";
        for (Player commander : game.getPlayers()) {
            if (p.getCommanderDamage(commander.getCommander()) >= 21) {
                return "commander_damage";
            }
        }
    }
    if (outcome.isConceded()) return "concession";
    return "alternate_win";
}
```

---

## 4. Card Names in Log Events (🟡 Mittel)

### Problem
Events enthalten nur Object-IDs ohne Kartennamen.

### Lösung

#### Event-Logging mit Kartennamen erweitern

```java
public void logZoneChange(Card card, ZoneType from, ZoneType to, String timeMarker, Player owner) {
    flushPendingPhase();

    Map<String, Object> data = new HashMap<>();
    data.put("obj", getCardId(card));
    data.put("card_name", getActualCardName(card));  // ✅ NEU
    data.put("from", formatZone(from, owner));
    data.put("to", formatZone(to, owner));
    data.put("pos", "top");
    data.put("visibility", isPublicZone(to) ? "public" : "private");

    addEvent(timeMarker, "SYS", "MOVE", data);
}
```

Gleiches Pattern für alle anderen `log*` Methoden.

---

## 5. Token Creation Tracking (🟡 Mittel)

### Problem
Tokens werden nicht im `card_index` erfasst.

### Lösung

#### Neue Methode für Token-Erstellung

```java
private int tokenIdCounter = 0;
private final Map<Card, String> tokenIdMap = new HashMap<>();

private String getTokenId(Card token) {
    return tokenIdMap.computeIfAbsent(token, t -> {
        tokenIdCounter++;
        String tokenId = "t" + tokenIdCounter;
        
        // Add token to card_index
        CardDefinition def = new CardDefinition();
        def.setName(t.getName());
        def.setCost("");  // Tokens have no mana cost
        def.setType(t.getType().toString());
        replayLog.getCardIndex().put(tokenId + ":" + t.getName(), def);
        
        return tokenId;
    });
}

public void logTokenCreation(Card token, Card source, Player controller, String timeMarker) {
    flushPendingPhase();
    
    Map<String, Object> data = new HashMap<>();
    data.put("token_id", getTokenId(token));
    data.put("name", token.getName());
    data.put("characteristics", formatTokenCharacteristics(token));
    data.put("controller", getPlayerId(controller));
    data.put("source", source != null ? getCardId(source) : "unknown");
    data.put("source_name", source != null ? getActualCardName(source) : "unknown");
    
    addEvent(timeMarker, "SYS", "CREATE_TOKEN", data);
}

private String formatTokenCharacteristics(Card token) {
    StringBuilder sb = new StringBuilder();
    if (token.getNetPower() >= 0 || token.getNetToughness() >= 0) {
        sb.append(token.getNetPower()).append("/").append(token.getNetToughness()).append(" ");
    }
    sb.append(token.getType().toString());
    return sb.toString();
}
```

---

## Implementierungspriorität

| # | Enhancement | Aufwand | Dateien |
|---|-------------|---------|---------|
| 1 | `initial_state.objects` | Mittel | `ReplayNotationExporter.java`, `ReplayJsonSerializer.java` |
| 2 | Winner & Win Condition | Niedrig | `ReplayNotationExporter.java`, `ReplayMeta.java` |
| 3 | Card Names in Events | Niedrig | `ReplayNotationExporter.java` |
| 4 | L2 Serialisierung | Mittel | `ReplayJsonSerializer.java` |
| 5 | L2 State Tracking | Hoch | Neuer `GameStateTracker.java`, Integration |
| 6 | Token Tracking | Mittel | `ReplayNotationExporter.java` |

---

## Versionierung

```java
// In ReplayLog.java
private String version = "1.0.0";

// Nach Implementierung von #1:
private String version = "1.1.0";

// Nach Implementierung von #4:
private String version = "1.2.0";
```

---

## Betroffene Dateien

1. `forge-game/src/main/java/forge/game/log/ReplayNotationExporter.java`
2. `forge-game/src/main/java/forge/game/log/ReplayJsonSerializer.java`
3. `forge-game/src/main/java/forge/game/log/model/ReplayMeta.java`
4. `forge-game/src/main/java/forge/game/log/model/GameState.java`
5. `forge-game/src/main/java/forge/game/log/ReplayL2Generator.java`
6. `forge-gui/src/main/java/forge/game/GameLogSaver.java`

---

**Letzte Aktualisierung:** 2026-02-08

## Status: IMPLEMENTIERT ✅ (Version 1.3.0)

Die folgenden Enhancements wurden implementiert:

### Version 1.1.0
1. ✅ **initial_state.objects Mapping** - Alle Karten werden jetzt mit Object-IDs erfasst
2. ✅ **Card Names in Events** - MOVE, CAST, PUT_ON_STACK, DAMAGE Events enthalten jetzt card_name
3. ✅ **Winner & Win Condition** - Meta enthält jetzt win_condition und conceded Felder
4. ✅ **Deck Names** - PlayerMeta enthält jetzt deck_name
5. ✅ **Deck Hash** - SHA-256 basierter Hash für eindeutige Deck-Identifikation (Main + Commander)

### Version 1.2.0
6. ✅ **GAME_START Event** - Neuer Event-Typ für Spielstart mit game_type, first_player, players
7. ✅ **PLAY_LAND Event** - Neuer Event-Typ mit Spieler als Actor
8. ✅ **DRAW Event** - Neuer Event-Typ für Kartenzug
9. ✅ **DISCARD Event** - Neuer Event-Typ mit flexiblem Actor (Spieler oder SYS)
10. ✅ **MULLIGAN Event** - Neuer Event-Typ für Mulligan-Entscheidungen
11. ✅ **GameLogFormatter Integration** - Alle neuen Events werden korrekt generiert
12. ✅ **Actor Attribution** - Spieleraktionen werden mit korrektem Actor (P1/P2) geloggt

### Version 1.3.0
13. ✅ **game_start Section** - Neue Sektion im JSON mit:
    - `toss_winner` - Spieler der den Würfelwurf gewonnen hat
    - `play_draw_choice` - "play" oder "draw"
    - `starting_player` - Spieler der zuerst dran ist
    - `mulligans` - Array mit detaillierten Mulligan-Infos pro Spieler:
      - `player`, `starting_hand_size`, `mulligans_taken`, `final_hand_size`, `cards_to_bottom`
14. ✅ **GameStartInfo Model** - Neues Datenmodell für Pre-Game Entscheidungen
15. ✅ **Mulligan Tracking** - recordMulliganTaken() und recordKeepHand() Methoden

Noch offen:
- L2 Views Serialisierung (appendL2Units implementiert noch "[]")
- Token Creation Tracking
- Forced Discard Tracking (momentan wird angenommen, dass Spieler wählt)




