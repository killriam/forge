# Forge - Custom Modifications Documentation

This document describes all custom modifications made to the Forge codebase for implementing **Game Analytics and Deck Testing Features**. Use this guide to apply the same changes to a newer version of Forge.

---

## Overview

The modifications add the following functionality:
1. **SQLite Database for Game Analytics** - Stores game results, deck stats, and starting hand statistics
2. **Game Analysis Classes** - Track game outcomes, mana statistics, and starting hands
3. **Deck Statistics Tracking** - Win rates, life delta, and turn count per deck
4. **Mana Curve Analysis** - Track available mana per turn
5. **Simulation Enhancements** - Extended simulation mode with deck testing against sparring decks
6. **Deck Fitting Infrastructure** (partial) - Framework for deck optimization

---

## File Changes Summary

### NEW FILES (8 files)

| File Path | Purpose |
|-----------|---------|
| `forge-core/src/main/java/forge/deck/CardForFitting.java` | Card fitting data class |
| `forge-core/src/main/java/forge/deck/DeckIdea.java` | Deck idea/template class |
| `forge-core/src/main/java/forge/deck/FittingSection.java` | Deck section fitting class |
| `forge-game/src/main/java/forge/game/GameAnalysis.java` | Game analysis results class |
| `forge-game/src/main/java/forge/game/player/DeckStats.java` | Deck statistics tracking |
| `forge-game/src/main/java/forge/game/player/DeckWins.java` | Deck wins tracking (stub) |
| `forge-game/src/main/java/forge/game/startingHandStats.java` | Starting hand statistics |
| `forge-game/src/main/java/forge/util/SQLiteConnection.java` | SQLite database operations |

### MODIFIED FILES (7 files)

| File Path | Type of Change |
|-----------|---------------|
| `pom.xml` | Added SQLite dependency |
| `forge-game/pom.xml` | Added sentry-logback dependency |
| `forge-game/src/main/java/forge/game/player/Player.java` | Added analytics fields and mana tracking methods |
| `forge-game/src/main/java/forge/game/phase/PhaseHandler.java` | Added end-of-turn mana statistics |
| `forge-game/src/main/java/forge/game/player/RegisteredPlayer.java` | No significant changes |
| `forge-gui-desktop/src/main/java/forge/view/SimulateMatch.java` | Extended simulation with analytics |
| `forge-gui/src/main/java/forge/deck/DeckImportController.java` | Added `createDeckOutof` method |

---

## Detailed Changes

### 1. Root `pom.xml`

**Location:** `pom.xml`

**Add to `<dependencyManagement><dependencies>` section:**

```xml
<dependency>
    <groupId>org.xerial</groupId>
    <artifactId>sqlite-jdbc</artifactId>
    <version>3.46.1</version>
</dependency>
```

---

### 2. `forge-game/pom.xml`

**Location:** `forge-game/pom.xml`

**Add to `<dependencies>` section:**

```xml
<dependency>
    <groupId>io.sentry</groupId>
    <artifactId>sentry-logback</artifactId>
    <version>7.14.0</version>
</dependency>
```

---

### 3. NEW FILE: `CardForFitting.java`

**Location:** `forge-core/src/main/java/forge/deck/CardForFitting.java`

```java
package forge.deck;

public class CardForFitting {
    String cardName;
    int quantity;
    int fittingScore;

    public CardForFitting(String cardName, int quantity) {
        this.cardName = cardName;
        this.quantity = quantity;
        this.fittingScore = 0;
    }
}
```

---

### 4. NEW FILE: `DeckIdea.java`

**Location:** `forge-core/src/main/java/forge/deck/DeckIdea.java`

```java
package forge.deck;

import java.util.ArrayList;

public class DeckIdea {
    private String DeckIdeaName;
    private int RevisionsNr;

    private ArrayList<CardForFitting> Main;
    private ArrayList<CardForFitting> Sideboard;
    private ArrayList<CardForFitting> Maybeboard;
}
```

---

### 5. NEW FILE: `FittingSection.java`

**Location:** `forge-core/src/main/java/forge/deck/FittingSection.java`

```java
package forge.deck;

import java.util.ArrayList;

public class FittingSection {
    private ArrayList<CardForFitting> cardsInSection;

    public FittingSection() {
        cardsInSection = new ArrayList<>();
    }

    public boolean readCards(final Iterable<String> lines) {
        CardPool.fromCardList(lines);
        return true;
    }
}
```

---

### 6. NEW FILE: `GameAnalysis.java`

**Location:** `forge-game/src/main/java/forge/game/GameAnalysis.java`

```java
package forge.game;

import forge.game.card.CardCollectionView;

public class GameAnalysis {

    private String winningPlayer;
    private int lastTurnNumber;
    private int lifeDelta;
    private CardCollectionView cardsinStartingHand;
    private final int manascoreOfStartingHand;
    private Manastats ManaStatsPlayer1;
    private Manastats ManaStatsPlayer2;

    // Constructor
    public GameAnalysis(String winningPlayer, int lastTurnNumber, int lifeDelta, 
                       CardCollectionView cardsinStartingHand, int manascoreOfStartingHand) {
        this.winningPlayer = winningPlayer;
        this.lastTurnNumber = lastTurnNumber;
        this.lifeDelta = lifeDelta;
        this.cardsinStartingHand = cardsinStartingHand;
        this.manascoreOfStartingHand = manascoreOfStartingHand;
    }

    // Getters
    public String getWinningPlayer() { return winningPlayer; }
    public int getLastTurnNumber() { return lastTurnNumber; }
    public int getLifeDelta() { return lifeDelta; }
    public CardCollectionView getCardsinStartingHand() { return cardsinStartingHand; }
    public int getManascoreOfStartingHand() { return manascoreOfStartingHand; }
    public Manastats getManaStatsPlayer1() { return ManaStatsPlayer1; }
    public Manastats getManaStatsPlayer2() { return ManaStatsPlayer2; }

    // Setters
    public void setWinningPlayer(String winningPlayer) { this.winningPlayer = winningPlayer; }
    public void setLastTurnNumber(int lastTurnNumber) { this.lastTurnNumber = lastTurnNumber; }
    public void setLifeDelta(int lifeDelta) { this.lifeDelta = lifeDelta; }
    public void setCardsinStartingHand(CardCollectionView cardsinStartingHand) { 
        this.cardsinStartingHand = cardsinStartingHand; 
    }
    public void setManaStatsPlayer1(Manastats manaStatsPlayer1) { ManaStatsPlayer1 = manaStatsPlayer1; }
    public void setManaStatsPlayer2(Manastats manaStatsPlayer2) { ManaStatsPlayer2 = manaStatsPlayer2; }

    @Override
    public String toString() {
        return "GameAnalysis{winningPlayer=" + winningPlayer +
                ", lastTurnNumber=" + lastTurnNumber +
                ", lifeDelta=" + lifeDelta + "}";
    }

    public static class Manastats {
        private final String playerName;
        private final int manaScoreStartHand;
        private final String[] availablemana;
        public String PlayerName;
        public int ManaScoreStartHand;
        public String[] availablemanaPerRound;

        public Manastats(String playerName, int manaScoreStartHand, String[] availablemana) {
            this.playerName = playerName;
            this.manaScoreStartHand = manaScoreStartHand;
            this.availablemana = availablemana;
        }
    }
}
```

---

### 7. NEW FILE: `DeckStats.java`

**Location:** `forge-game/src/main/java/forge/game/player/DeckStats.java`

```java
package forge.game.player;

public class DeckStats {

    private String name;
    private int id;
    private int wincount;
    private int lifescore;
    private int turnCount;

    public DeckStats(String name) {
        this.name = name;
        wincount = 0;
        lifescore = 0;
        turnCount = 0;
    }

    public int getTurnCount() { return turnCount; }
    public int getWinCount() { return wincount; }
    public int getLifescore() { return lifescore; }
    public int getId() { return id; }
    public String getName() { return name; }

    public void setId(int id) { this.id = id; }
    public void setName(String name) { this.name = name; }

    public void incrementWinCount() { this.wincount++; }
    public void addToLifeScore(int score) { this.lifescore += score; }
    public void addToturncount(int turncount) { this.turnCount += turncount; }

    public void addVictoryStats(int lastTurnNumber, int lifeDelta) {
        incrementWinCount();
        addToturncount(lastTurnNumber);
        addToLifeScore(lifeDelta);
    }

    @Override
    public String toString() {
        if (wincount == 0) {
            return "Name" + name + " Count: 0, Score: N/A, Turncount: N/A";
        } else {
            return "Name" + name + " Count: " + wincount + ", Score: " + lifescore / wincount + 
                   ", Turncount: " + turnCount / wincount;
        }
    }
}
```

---

### 8. NEW FILE: `DeckWins.java`

**Location:** `forge-game/src/main/java/forge/game/player/DeckWins.java`

```java
package forge.game.player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Main class to manage the players
public class DeckWins {
    private String deckName;
}
```

---

### 9. NEW FILE: `startingHandStats.java`

**Location:** `forge-game/src/main/java/forge/game/startingHandStats.java`

```java
package forge.game;

import forge.game.card.Card;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class startingHandStats {

    private Map<String, Integer> startingHandCount;

    public startingHandStats() {
        startingHandCount = new HashMap<>();
    }

    public void addCard(String card) {
        if (startingHandCount.containsKey(card)) {
            startingHandCount.put(card, startingHandCount.get(card) + 1);
        } else {
            startingHandCount.put(card, 1);
        }
    }

    public int getCardCount(Card card) {
        return startingHandCount.getOrDefault(card, 0);
    }

    public void displayPlayerCounts() {
        List<Map.Entry<String, Integer>> entryList = getCardByCount();
        for (Map.Entry<String, Integer> entry : entryList) {
            System.out.println("Card: " + entry.getKey() + ", Count: " + entry.getValue());
        }
    }

    public List<Map.Entry<String, Integer>> getCardByCount() {
        List<Map.Entry<String, Integer>> entryList = new ArrayList<>(startingHandCount.entrySet());
        entryList.sort((entry1, entry2) -> entry2.getValue().compareTo(entry1.getValue()));
        return entryList;
    }
}
```

---

### 10. NEW FILE: `SQLiteConnection.java`

**Location:** `forge-game/src/main/java/forge/util/SQLiteConnection.java`

```java
package forge.util;

import forge.game.player.DeckStats;

import java.io.File;
import java.sql.*;

public class SQLiteConnection {

    public static Connection connect() {
        Connection conn = null;
        try {
            String url = getsqliteFileName();
            conn = DriverManager.getConnection(url);
            System.out.println("Connection to SQLite has been established.");
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return conn;
    }

    public static String getsqliteFileName() {
        String dbFilename = "MTGGameAnalysis.db";
        String appFolderPath = getAppdataDir();
        String dbFilePath = appFolderPath + File.separator + dbFilename;
        return "jdbc:sqlite:" + dbFilePath;
    }

    public static String getAppdataDir() {
        String appDataPath = System.getenv("APPDATA");
        if (appDataPath == null) {
            System.out.println("AppData directory not found. Using current directory.");
            appDataPath = ".";
        }

        String appFolderPath = appDataPath + File.separator + "Forge";
        File appFolder = new File(appFolderPath);
        if (!appFolder.exists()) {
            if (appFolder.mkdir()) {
                System.out.println("Created application directory: " + appFolderPath);
            } else {
                System.out.println("Failed to create application directory.");
            }
        }
        return appFolderPath;
    }

    public static void createNewTable() {
        String sqlCreateSetOfGamesTable = "CREATE TABLE IF NOT EXISTS setOfGames (\n"
                + " id integer PRIMARY KEY,\n"
                + " name text NOT NULL,\n"
                + " date text NOT NULL,\n"
                + " gamesPlayed integer NOT NULL,\n"
                + " timeUsed integer NOT NULL\n"
                + ");";

        String sqlCreateDeckstatsTable = "CREATE TABLE IF NOT EXISTS deckStats (\n"
                + " id integer PRIMARY KEY,\n"
                + " setOfGamesID integer NOT NULL,\n"
                + " deck_name text NOT NULL,\n"
                + " wins integer,\n"
                + " lifedeltaScore integer,\n"
                + " turnCountScore integer,\n"
                + " FOREIGN KEY (setOfGamesID) REFERENCES setOfGames(id)\n"
                + ");";

        String sqlCreateCardstatsTable = "CREATE TABLE IF NOT EXISTS CardinStartingHandAppearenceStats (\n"
                + " id integer PRIMARY KEY,\n"
                + " deckStatsID integer NOT NULL,\n"
                + " card_name text NOT NULL,\n"
                + " cardoccurence integer,\n"
                + " cardscore integer,\n"
                + " FOREIGN KEY (deckStatsID) REFERENCES deckStats(id),\n"
                + " UNIQUE(deckStatsID, card_name)\n"
                + ");";

        String sqlCreateHandstatsTable = "CREATE TABLE IF NOT EXISTS CombinedHand (\n"
                + " id integer PRIMARY KEY,\n"
                + " deckStatsID integer NOT NULL,\n"
                + " CardinHand_combined text NOT NULL,\n"
                + " cardoccurence integer,\n"
                + " cardscore integer,\n"
                + " FOREIGN KEY (deckStatsID) REFERENCES deckStats(id),\n"
                + " UNIQUE(deckStatsID, CardinHand_combined)\n"
                + ");";

        try (Connection conn = DriverManager.getConnection(getsqliteFileName());
             Statement stmt = conn.createStatement()) {
            stmt.execute(sqlCreateSetOfGamesTable);
            System.out.println("Table setOfGames has been created.");
            stmt.execute(sqlCreateDeckstatsTable);
            System.out.println("Table Deckstats has been created.");
            stmt.execute(sqlCreateCardstatsTable);
            System.out.println("Table Cardstats has been created.");
            stmt.execute(sqlCreateHandstatsTable);
            System.out.println("Table Handstats has been created.");
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    private static void updateDeckStats(Connection conn, int deckstatsId, String deckName, 
                                        int wins, int lifedeltaScore, int turnCountScore) {
        String sql = "UPDATE deckStats SET wins=?, lifedeltaScore=?, turnCountScore=? where id=?";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(4, deckstatsId);
            pstmt.setInt(1, wins);
            pstmt.setInt(2, lifedeltaScore);
            pstmt.setInt(3, turnCountScore);
            pstmt.executeUpdate();
            System.out.println("Deck stats have been inserted.");
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    public static int insertDeckStats(int setOfGamesID, String deckName) {
        try (Connection conn = DriverManager.getConnection(getsqliteFileName())) {
            String sql = "INSERT INTO deckStats(setOfGamesID, deck_name) VALUES(?, ?)";

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, setOfGamesID);
                pstmt.setString(2, deckName);
                pstmt.executeUpdate();

                try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        return generatedKeys.getInt(1);
                    } else {
                        throw new SQLException("Inserting deckStats failed, no ID obtained.");
                    }
                }
            } catch (SQLException e) {
                System.out.println(e.getMessage());
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return 0;
    }

    public static void insertorUpdateCardOccurence(int deckStatsID, String cardName, int cardScore) {
        try (Connection conn = DriverManager.getConnection(getsqliteFileName())) {
            conn.setAutoCommit(false);

            String sqlUpsert = "INSERT INTO CardinStartingHandAppearenceStats " +
                    "(deckStatsID, card_name, cardoccurence, cardscore) " +
                    "VALUES (?, ?, ?, ?) " +
                    "ON CONFLICT(deckStatsID,card_name) DO UPDATE SET " +
                    "cardoccurence = cardoccurence + 1, " +
                    "cardscore = cardscore + ?";

            try (PreparedStatement pstmt = conn.prepareStatement(sqlUpsert)) {
                pstmt.setInt(1, deckStatsID);
                pstmt.setString(2, cardName);
                pstmt.setInt(3, 1);
                pstmt.setInt(4, cardScore);
                pstmt.setInt(5, cardScore);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                System.out.println(e.getMessage());
                if (conn != null) {
                    try {
                        conn.rollback();
                    } catch (SQLException ex) {
                        System.out.println(ex.getMessage());
                    }
                }
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    public static void insertorUpdateCardInHandsOccurence(int setOfGamesID, String CardinHand_combined, 
                                                          int cardScore) {
        try (Connection conn = DriverManager.getConnection(getsqliteFileName())) {
            conn.setAutoCommit(false);

            String sqlUpsert = "INSERT INTO CombinedHand " +
                    "(deckStatsID, CardinHand_combined, cardoccurence, cardscore) " +
                    "VALUES (?, ?, ?, ?) " +
                    "ON CONFLICT(deckStatsID,CardinHand_combined) DO UPDATE SET " +
                    "cardoccurence = cardoccurence + 1, " +
                    "cardscore = cardscore + ?;";

            try (PreparedStatement pstmt = conn.prepareStatement(sqlUpsert)) {
                pstmt.setInt(1, setOfGamesID);
                pstmt.setString(2, CardinHand_combined);
                pstmt.setInt(3, 1);
                pstmt.setInt(4, cardScore);
                pstmt.setInt(5, cardScore);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                System.out.println(e.getMessage());
                if (conn != null) {
                    try {
                        conn.rollback();
                    } catch (SQLException ex) {
                        System.out.println(ex.getMessage());
                    }
                }
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    public static int insertSetOfGames(String name, String date) throws SQLException {
        try (Connection conn = DriverManager.getConnection(getsqliteFileName())) {
            String sql = "INSERT INTO setOfGames(name, date, gamesPlayed, timeUsed) VALUES(?, ?, ?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                pstmt.setString(1, name);
                pstmt.setString(2, date);
                pstmt.setInt(3, -1);
                pstmt.setInt(4, -1);
                pstmt.executeUpdate();

                try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        return generatedKeys.getInt(1);
                    } else {
                        throw new SQLException("Inserting setOfGames failed, no ID obtained.");
                    }
                }
            }
        }
    }

    private static void updateSetOfGames(Connection conn, int setOfGamesID, int gamesPlayed, 
                                         int timeUsed) throws SQLException {
        String sqlUpdate = "UPDATE setOfGames SET gamesPlayed = ?, timeUsed = ? WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sqlUpdate)) {
            pstmt.setInt(1, gamesPlayed);
            pstmt.setInt(2, timeUsed);
            pstmt.setInt(3, setOfGamesID);
            int rowsAffected = pstmt.executeUpdate();
            System.out.println("Rows updated: " + rowsAffected);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void insertGameAnalysis(int setOfGamesID, int gamesPlayed, int timeUsed) {
        try (Connection conn = DriverManager.getConnection(getsqliteFileName())) {
            conn.setAutoCommit(false);
            try {
                updateSetOfGames(conn, setOfGamesID, gamesPlayed, timeUsed);
                conn.commit();
            } catch (SQLException e) {
                System.out.println(e.getMessage());
                if (conn != null) {
                    try {
                        conn.rollback();
                    } catch (SQLException ex) {
                        System.out.println(ex.getMessage());
                    }
                }
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    public static void test() throws SQLException {
        int id = SQLiteConnection.insertSetOfGames("Testing", "2000-33-99");
    }

    public static void updateDeckStatsBy(DeckStats dw) throws SQLException {
        try (Connection conn = DriverManager.getConnection(getsqliteFileName())) {
            updateDeckStats(conn, dw.getId(), dw.getName(), dw.getWinCount(), 
                           dw.getLifescore(), dw.getTurnCount());
        }
    }
}
```

---

### 11. MODIFY: `Player.java`

**Location:** `forge-game/src/main/java/forge/game/player/Player.java`

**Add new imports at the top:**
```java
import forge.game.ability.ApiType;
import java.util.stream.Collectors;
```

**Add new fields in the class (around line 178, after existing fields):**
```java
//analytic purpose
private CardCollectionView cardsInStartingHand = new CardCollection();
private static final int manacurveDataturnCount = 8;
private String[] manacurveData = new String[manacurveDataturnCount];
```

**Add new methods at the end of the class (before closing brace):**
```java
//for statistics
public void setCardsInStartingHand(CardCollectionView cardsIn) {
    cardsInStartingHand = cardsIn;
}

public CardCollectionView getCardsInStartingHand() {
    return cardsInStartingHand;
}

public int countManaLandAndRampsInStartingHand() {
    return countManaLandRampsIn(cardsInStartingHand);
}

public int countManaLandRampsIn(CardCollectionView cards) {
    CardCollection cardsSelection = getCardswithManaAbilities(cards);
    return cardsSelection.size();
}

public static CardCollection getCardswithManaAbilities(CardCollectionView cards) {
    CardCollection cardsSelection = new CardCollection();
    for (Card c : cards) {
        if (c.isLand() && !c.getManaAbilities().isEmpty()) {
            cardsSelection.add(c);
        } else if (c.isPermanent() && c.getCMC() <= 2 && !c.getManaAbilities().isEmpty()) {
            cardsSelection.add(c);
        }
    }
    return cardsSelection;
}

public static String listManaCreatableIn(CardCollectionView cards) {
    StringBuilder combinedManas = new StringBuilder();
    for (Card c : cards) {
        if ((c.isLand() && !c.getManaAbilities().isEmpty()) || 
            (c.isPermanent() && c.getCMC() <= 3 && !c.getManaAbilities().isEmpty())) {
            int countmanaAbilities = 0;
            for (final SpellAbility mana : c.getManaAbilities()) {
                if (mana.getApi() == ApiType.ManaReflected) {
                    String collect = CardUtil.getReflectableManaColors(mana).stream()
                            .collect(Collectors.joining(""));
                    if (countmanaAbilities >= 1) {
                        combinedManas.append("|" + collect);
                    } else {
                        combinedManas.append(collect);
                    }
                } else {
                    if (mana.getManaPart().mana(mana) != null) {
                        String mana1 = mana.getManaPart().mana(mana);
                        if (countmanaAbilities >= 1) {
                            combinedManas.append("|" + mana1);
                        } else {
                            combinedManas.append(mana1);
                        }
                    }
                }
                countmanaAbilities++;
            }
        }
    }
    return combinedManas.toString();
}

public String[] getManacurveData() {
    return manacurveData;
}

public void setManacurveData(String manacurveData, int turn) {
    if (turn < manacurveDataturnCount) {
        this.manacurveData[turn] = manacurveData;
    }
}
```

---

### 12. MODIFY: `PhaseHandler.java`

**Location:** `forge-game/src/main/java/forge/game/phase/PhaseHandler.java`

**In the `onPhaseBegin()` method, inside `case END_OF_TURN:` block, add after `game.getEndOfTurn().executeAt();`:**

```java
// for analytics add Land statistics
// count lands
CardCollectionView permantens = playerTurn.getCardsIn(ZoneType.Battlefield);
int amountManacard = playerTurn.countManaLandRampsIn(permantens);
String producalbeMana = playerTurn.listManaCreatableIn(permantens);

playerTurn.setManacurveData(producalbeMana, playerTurn.getTurn());

CardCollection cardswithManaAbilities = playerTurn.getCardswithManaAbilities(permantens);

System.out.println("Player " + playerTurn.getName() + " in Turn " + playerTurn.getTurn() + 
    " can produce with card " + cardswithManaAbilities + "(" + amountManacard + ") mana: " + producalbeMana);
// add them to turn index list
```

---

### 13. MODIFY: `SimulateMatch.java`

**Location:** `forge-gui-desktop/src/main/java/forge/view/SimulateMatch.java`

**Add new imports:**
```java
import java.io.*;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import forge.deck.DeckRecognizer;
import forge.game.*;
import forge.game.card.Card;
import forge.game.card.CardCollectionView;
import forge.game.player.DeckStats;
import forge.util.SQLiteConnection;

import static forge.deck.DeckImportController.createDeckOutof;
import static forge.localinstance.properties.ForgeConstants.DECK_COMMANDER_DIR;
```

**Replace the `simulate()` method and add supporting methods** (the entire file is extensively modified - see the full file in the codebase for complete implementation).

Key additions:
- `simulationSeries()` method for running analytics
- `readAndSplitFile()` method for reading deck files
- `displayDeckStats()` method for output
- `findDeckIn()` method for finding decks
- `initDeckstats()` method for database initialization
- `getFirstInsertOfGameset()` method for database insertion
- `InsertStartingHandStats()` method for recording starting hands
- Modified `simulateSingleMatch()` to return `GameAnalysis`

---

### 14. MODIFY: `DeckImportController.java`

**Location:** `forge-gui/src/main/java/forge/deck/DeckImportController.java`

**Make the `createDeckOutof` method public and static:**

Find the method and change its signature from:
```java
private Deck createDeckOutof(List<Token> tokens) {
```

To:
```java
public static Deck createDeckOutof(List<Token> tokens, boolean inlcludeBnRInDeck) {
```

---

## Database Schema

The SQLite database (`MTGGameAnalysis.db`) is created in `%APPDATA%/Forge/` with the following tables:

### Table: `setOfGames`
- `id` (INTEGER PRIMARY KEY)
- `name` (TEXT) - Name of the game set
- `date` (TEXT) - Date/time of the simulation
- `gamesPlayed` (INTEGER) - Total games played
- `timeUsed` (INTEGER) - Time in minutes

### Table: `deckStats`
- `id` (INTEGER PRIMARY KEY)
- `setOfGamesID` (INTEGER, FK -> setOfGames.id)
- `deck_name` (TEXT)
- `wins` (INTEGER)
- `lifedeltaScore` (INTEGER)
- `turnCountScore` (INTEGER)

### Table: `CardinStartingHandAppearenceStats`
- `id` (INTEGER PRIMARY KEY)
- `deckStatsID` (INTEGER, FK -> deckStats.id)
- `card_name` (TEXT)
- `cardoccurence` (INTEGER)
- `cardscore` (INTEGER)
- UNIQUE(deckStatsID, card_name)

### Table: `CombinedHand`
- `id` (INTEGER PRIMARY KEY)
- `deckStatsID` (INTEGER, FK -> deckStats.id)
- `CardinHand_combined` (TEXT)
- `cardoccurence` (INTEGER)
- `cardscore` (INTEGER)
- UNIQUE(deckStatsID, CardinHand_combined)

---

## Usage

### Running Simulations with Analytics

Use the `-xd` parameter for extended deck testing:

```
forge.exe sim -xd <mode> <deck_file_path> -n <num_games> -f commander
```

Example:
```
forge.exe sim -xd 0 "C:\decks\mydeckidea.txt" -n 1000 -f commander
```

The deck idea file should contain card names in standard decklist format.

---

## Notes

1. **SQLite JDBC Driver**: Ensure the SQLite JDBC driver is available at runtime
2. **Database Location**: Database is stored in `%APPDATA%/Forge/MTGGameAnalysis.db` on Windows
3. **Sparring Decks**: The code currently uses a hardcoded "DO Nothing DECK.dck" as sparring partner
4. **Score Calculation**: Score = lifeDelta * 1 + lastTurnNumber * 10

---

## Version Compatibility

These modifications were made on Forge version **1.6.65-SNAPSHOT**. When applying to a newer version:

1. Check for any changes to the `Player` class interface
2. Check for changes to `PhaseHandler` phase handling
3. Verify `SimulateMatch` simulation framework hasn't changed significantly
4. Check `DeckImportController` token handling

---

*Document generated: December 2024*

