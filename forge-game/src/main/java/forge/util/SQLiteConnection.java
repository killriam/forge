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

