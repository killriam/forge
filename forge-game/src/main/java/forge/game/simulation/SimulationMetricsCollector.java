package forge.game.simulation;

import forge.game.Game;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Collects simulation metrics during game execution.
 * Called at key points (turn end, game end) to track statistics.
 */
public class SimulationMetricsCollector {
    private final Game game;
    private final long gameStartTime;
    private final Map<String, PlayerMetrics> playerMetrics;

    // Timeline tracking (optional)
    private boolean trackTimeline;
    private final List<Integer> turnNumbers;
    private final Map<String, List<Integer>> lifeTimeline;
    private final Map<String, List<Integer>> creatureTimeline;

    public SimulationMetricsCollector(Game game, boolean trackTimeline) {
        this.game = game;
        this.gameStartTime = System.currentTimeMillis();
        this.playerMetrics = new HashMap<>();
        this.trackTimeline = trackTimeline;

        if (trackTimeline) {
            this.turnNumbers = new ArrayList<>();
            this.lifeTimeline = new HashMap<>();
            this.creatureTimeline = new HashMap<>();
        } else {
            this.turnNumbers = null;
            this.lifeTimeline = null;
            this.creatureTimeline = null;
        }

        // Initialize metrics for each player
        for (Player player : game.getPlayers()) {
            String playerId = getPlayerId(player);
            playerMetrics.put(playerId, new PlayerMetrics(player));

            if (trackTimeline) {
                lifeTimeline.put(playerId, new ArrayList<>());
                creatureTimeline.put(playerId, new ArrayList<>());
            }
        }
    }

    /**
     * Called at the end of each turn to update metrics.
     */
    public void onTurnEnd(int turnNumber) {
        if (trackTimeline) {
            turnNumbers.add(turnNumber);
        }

        for (Player player : game.getPlayers()) {
            String playerId = getPlayerId(player);
            PlayerMetrics metrics = playerMetrics.get(playerId);

            if (metrics == null) {
                continue;
            }

            // Update peak values
            int availableMana = calculateAvailableMana(player);
            metrics.updatePeakMana(availableMana);

            int creaturesOnBoard = countCreatures(player);
            metrics.updatePeakCreatures(creaturesOnBoard);

            // Track timeline
            if (trackTimeline) {
                lifeTimeline.get(playerId).add(player.getLife());
                creatureTimeline.get(playerId).add(creaturesOnBoard);
            }

            // Track missed land drops (after turn 1)
            if (turnNumber > 1) {
                int landsInHand = countLandsInHand(player);
                if (metrics.getLandsPlayedThisTurn() == 0 && landsInHand > 0) {
                    metrics.incrementMissedDrops();
                }
            }

            // Reset per-turn counters
            metrics.resetTurnCounters();
        }
    }

    /**
     * Export final statistics at game end.
     */
    public SimulationStats exportStats() {
        long gameEndTime = System.currentTimeMillis();
        long durationMs = gameEndTime - gameStartTime;

        SimulationStats stats = new SimulationStats();

        // Meta
        SimulationStats.MetaData meta = stats.getMeta();
        meta.setTimestamp(new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").format(new java.util.Date()));
        meta.setGameType(game.getRules().getGameType().toString());

        // Set deck names from players
        List<Player> players = game.getPlayers();
        if (players.size() >= 1) {
            meta.setDeck1Name(players.get(0).getRegisteredPlayer().getDeck().getName());
        }
        if (players.size() >= 2) {
            meta.setDeck2Name(players.get(1).getRegisteredPlayer().getDeck().getName());
        }

        // Outcome
        SimulationStats.GameOutcomeData outcome = stats.getOutcome();
        outcome.setTotalTurns(game.getPhaseHandler().getTurn());
        outcome.setDurationMs(durationMs);

        if (game.getOutcome() != null) {
            // Find the winning Player instance by matching RegisteredPlayer
            Player winner = null;
            for (Player p : game.getPlayers()) {
                if (game.getOutcome().isWinner(p.getRegisteredPlayer())) {
                    winner = p;
                    break;
                }
            }
            outcome.setWinner(winner != null ? getPlayerId(winner) : null);
            outcome.setWinCondition(determineWinCondition(game));
            outcome.setGameEndedReason(game.getOutcome().isWinner(game.getPlayers().get(0).getRegisteredPlayer()) ? "PLAYER_LOST_GAME" : "DRAW");
        }

        // Player stats
        for (Map.Entry<String, PlayerMetrics> entry : playerMetrics.entrySet()) {
            String playerId = entry.getKey();
            PlayerMetrics metrics = entry.getValue();
            Player player = metrics.getPlayer();

            PlayerStats playerStats = new PlayerStats();
            playerStats.setDeckName(player.getRegisteredPlayer().getDeck().getName());
            playerStats.setFinalLife(player.getLife());
            playerStats.setLifeDelta(player.getLife() - player.getStartingLife());

            // Cards
            playerStats.getCards().setDrawn(metrics.getCardsDrawn());
            playerStats.getCards().setMulligans(metrics.getMulligans());
            playerStats.getCards().setStartingHandSize(metrics.getStartingHandSize());

            // Spells
            playerStats.getSpells().setTotalCast(metrics.getSpellsCast());
            playerStats.getSpells().setCreatures(metrics.getCreaturesCast());
            playerStats.getSpells().setNoncreatures(metrics.getSpellsCast() - metrics.getCreaturesCast());
            playerStats.getSpells().setAvgCmc(metrics.getAverageCmc());

            // Mana
            playerStats.getMana().setLandsPlayed(metrics.getLandsPlayed());
            playerStats.getMana().setMissedDrops(metrics.getMissedDrops());
            playerStats.getMana().setPeakAvailable(metrics.getPeakMana());
            playerStats.getMana().setTotalProduced(metrics.getTotalManaProduced());
            playerStats.getMana().setTotalSpent(metrics.getTotalManaSpent());

            // Combat
            playerStats.getCombat().setDamageDealt(metrics.getDamageDealt());
            playerStats.getCombat().setDamageTaken(metrics.getDamageTaken());
            playerStats.getCombat().setAttacksDeclared(metrics.getAttacksDeclared());
            playerStats.getCombat().setBlocksDeclared(metrics.getBlocksDeclared());

            // Board
            playerStats.getBoard().setFinalCreatures(countCreatures(player));
            playerStats.getBoard().setFinalLands(countLands(player));
            playerStats.getBoard().setFinalOther(countOtherPermanents(player));
            playerStats.getBoard().setPeakCreatures(metrics.getPeakCreatures());

            // Tempo
            playerStats.getTempo().setAbilitiesActivated(metrics.getAbilitiesActivated());
            playerStats.getTempo().setCountersPlaced(metrics.getCountersPlaced());
            playerStats.getTempo().setTurnsWithAction(metrics.getTurnsWithAction());

            stats.getPlayers().put(playerId, playerStats);
        }

        // Timeline (if tracked)
        if (trackTimeline && !turnNumbers.isEmpty()) {
            SimulationStats.TimelineData timeline = new SimulationStats.TimelineData();
            timeline.setTurnCount(turnNumbers.stream().mapToInt(Integer::intValue).toArray());

            if (lifeTimeline.containsKey("P1")) {
                timeline.setP1Life(lifeTimeline.get("P1").stream().mapToInt(Integer::intValue).toArray());
            }
            if (lifeTimeline.containsKey("P2")) {
                timeline.setP2Life(lifeTimeline.get("P2").stream().mapToInt(Integer::intValue).toArray());
            }
            if (creatureTimeline.containsKey("P1")) {
                timeline.setP1Creatures(creatureTimeline.get("P1").stream().mapToInt(Integer::intValue).toArray());
            }
            if (creatureTimeline.containsKey("P2")) {
                timeline.setP2Creatures(creatureTimeline.get("P2").stream().mapToInt(Integer::intValue).toArray());
            }

            stats.setTimeline(timeline);
        }

        return stats;
    }

    // Event tracking methods (called from game events)

    public void onCardDrawn(Player player) {
        PlayerMetrics metrics = playerMetrics.get(getPlayerId(player));
        if (metrics != null) {
            metrics.incrementCardsDrawn();
        }
    }

    public void onSpellCast(Player player, Card spell) {
        PlayerMetrics metrics = playerMetrics.get(getPlayerId(player));
        if (metrics != null) {
            metrics.incrementSpellsCast();
            if (spell.isCreature()) {
                metrics.incrementCreaturesCast();
            }
            metrics.addCmcCast(spell.getCMC());
        }
    }

    public void onLandPlayed(Player player) {
        PlayerMetrics metrics = playerMetrics.get(getPlayerId(player));
        if (metrics != null) {
            metrics.incrementLandsPlayed();
            metrics.incrementLandsPlayedThisTurn();
        }
    }

    public void onDamageDealt(Player player, int damage) {
        PlayerMetrics metrics = playerMetrics.get(getPlayerId(player));
        if (metrics != null) {
            metrics.addDamageDealt(damage);
        }
    }

    public void onDamageTaken(Player player, int damage) {
        PlayerMetrics metrics = playerMetrics.get(getPlayerId(player));
        if (metrics != null) {
            metrics.addDamageTaken(damage);
        }
    }

    // Helper methods

    private String getPlayerId(Player player) {
        return "P" + (player.getGame().getPlayers().indexOf(player) + 1);
    }

    private int calculateAvailableMana(Player player) {
        // Simple estimation: count untapped lands
        int mana = 0;
        for (Card land : player.getCardsIn(ZoneType.Battlefield)) {
            if (land.isLand() && !land.isTapped()) {
                mana++; // Simplified: 1 mana per untapped land
            }
        }
        return mana;
    }

    private int countCreatures(Player player) {
        int count = 0;
        for (Card card : player.getCardsIn(ZoneType.Battlefield)) {
            if (card.isCreature()) {
                count++;
            }
        }
        return count;
    }

    private int countLands(Player player) {
        int count = 0;
        for (Card card : player.getCardsIn(ZoneType.Battlefield)) {
            if (card.isLand()) {
                count++;
            }
        }
        return count;
    }

    private int countOtherPermanents(Player player) {
        int total = player.getCardsIn(ZoneType.Battlefield).size();
        return total - countCreatures(player) - countLands(player);
    }

    private int countLandsInHand(Player player) {
        int count = 0;
        for (Card card : player.getCardsIn(ZoneType.Hand)) {
            if (card.isLand()) {
                count++;
            }
        }
        return count;
    }

    private String determineWinCondition(Game game) {
        // Simple heuristic
        if (game.getOutcome() == null) {
            return "draw";
        }
        // Find a losing player (one who has not won)
        Player loser = null;
        for (Player p : game.getPlayers()) {
            if (!game.getOutcome().isWinner(p.getRegisteredPlayer())) {
                loser = p;
                break;
            }
        }
        if (loser != null && loser.getLife() <= 0) {
            return "damage";
        }
        return "other";
    }

    /**
     * Internal class to track per-player metrics during game.
     */
    private static class PlayerMetrics {
        private final Player player;
        private int cardsDrawn = 0;
        private int mulligans = 0;
        private int startingHandSize = 7;
        private int spellsCast = 0;
        private int creaturesCast = 0;
        private int totalCmc = 0;
        private int landsPlayed = 0;
        private int landsPlayedThisTurn = 0;
        private int missedDrops = 0;
        private int peakMana = 0;
        private int totalManaProduced = 0;
        private int totalManaSpent = 0;
        private int damageDealt = 0;
        private int damageTaken = 0;
        private int attacksDeclared = 0;
        private int blocksDeclared = 0;
        private int peakCreatures = 0;
        private int abilitiesActivated = 0;
        private int countersPlaced = 0;
        private int turnsWithAction = 0;

        public PlayerMetrics(Player player) {
            this.player = player;
        }

        public Player getPlayer() { return player; }
        public int getCardsDrawn() { return cardsDrawn; }
        public void incrementCardsDrawn() { cardsDrawn++; }
        public int getMulligans() { return mulligans; }
        public void setMulligans(int mulligans) { this.mulligans = mulligans; }
        public int getStartingHandSize() { return startingHandSize; }
        public void setStartingHandSize(int startingHandSize) { this.startingHandSize = startingHandSize; }
        public int getSpellsCast() { return spellsCast; }
        public void incrementSpellsCast() { spellsCast++; }
        public int getCreaturesCast() { return creaturesCast; }
        public void incrementCreaturesCast() { creaturesCast++; }
        public void addCmcCast(int cmc) { totalCmc += cmc; }
        public double getAverageCmc() { return spellsCast > 0 ? (double)totalCmc / spellsCast : 0.0; }
        public int getLandsPlayed() { return landsPlayed; }
        public void incrementLandsPlayed() { landsPlayed++; }
        public int getLandsPlayedThisTurn() { return landsPlayedThisTurn; }
        public void incrementLandsPlayedThisTurn() { landsPlayedThisTurn++; }
        public void resetTurnCounters() { landsPlayedThisTurn = 0; }
        public int getMissedDrops() { return missedDrops; }
        public void incrementMissedDrops() { missedDrops++; }
        public int getPeakMana() { return peakMana; }
        public void updatePeakMana(int mana) { if (mana > peakMana) peakMana = mana; }
        public int getTotalManaProduced() { return totalManaProduced; }
        public void addManaProduced(int mana) { totalManaProduced += mana; }
        public int getTotalManaSpent() { return totalManaSpent; }
        public void addManaSpent(int mana) { totalManaSpent += mana; }
        public int getDamageDealt() { return damageDealt; }
        public void addDamageDealt(int damage) { damageDealt += damage; }
        public int getDamageTaken() { return damageTaken; }
        public void addDamageTaken(int damage) { damageTaken += damage; }
        public int getAttacksDeclared() { return attacksDeclared; }
        public void incrementAttacksDeclared() { attacksDeclared++; }
        public int getBlocksDeclared() { return blocksDeclared; }
        public void incrementBlocksDeclared() { blocksDeclared++; }
        public int getPeakCreatures() { return peakCreatures; }
        public void updatePeakCreatures(int creatures) { if (creatures > peakCreatures) peakCreatures = creatures; }
        public int getAbilitiesActivated() { return abilitiesActivated; }
        public void incrementAbilitiesActivated() { abilitiesActivated++; }
        public int getCountersPlaced() { return countersPlaced; }
        public void addCountersPlaced(int count) { countersPlaced += count; }
        public int getTurnsWithAction() { return turnsWithAction; }
        public void incrementTurnsWithAction() { turnsWithAction++; }
    }
}

