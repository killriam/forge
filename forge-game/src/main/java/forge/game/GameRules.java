package forge.game;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GameRules {
    private final GameType gameType;
    private boolean manaBurn;
    private boolean orderCombatants;
    private int poisonCountersToLose = 10; // is commonly 10, but turns into 15 for 2HG
    private int gamesPerMatch = 3;
    private int gamesToWinMatch = 2;
    private boolean playForAnte = false;
    private boolean matchAnteRarity = false;
    private boolean anteIncludeBasicLands = false;
    private boolean AISideboardingEnabled = false;
    private boolean sideboardForAI = false;
    private boolean allowCheatShuffle = false;
    private final Set<GameType> appliedVariants = EnumSet.noneOf(GameType.class);
    private int simTimeout = 120;

    // it's a preference, not rule... but I could hardly find a better place for it
    private boolean useGrayText;

    // whether to warn about cards AI can't play well
    private boolean warnAboutAICards = true;

    public GameRules(final GameType type) {
        this.gameType = type;
    }

    public GameType getGameType() {
        return gameType;
    }

    public boolean hasManaBurn() {
        return manaBurn;
    }
    public void setManaBurn(final boolean manaBurn) {
        this.manaBurn = manaBurn;
    }

    public boolean hasOrderCombatants() {
        return orderCombatants;
    }
    public void setOrderCombatants(final boolean ordered) {
        this.orderCombatants = ordered;
    }

    public int getPoisonCountersToLose() {
        return poisonCountersToLose;
    }
    public void setPoisonCountersToLose(final int amount) {
        this.poisonCountersToLose = amount;
    }

    public int getGamesPerMatch() {
        return gamesPerMatch;
    }
    public void setGamesPerMatch(final int gamesPerMatch) {
        this.gamesPerMatch = gamesPerMatch;
        this.gamesToWinMatch = gamesPerMatch / 2 + 1;
    }

    public boolean useAnte() {
        return playForAnte;
    }
    public void setPlayForAnte(final boolean useAnte) {
        this.playForAnte = useAnte;
    }

    public boolean getMatchAnteRarity() {
        return matchAnteRarity;
    }
    public void setMatchAnteRarity(final boolean matchRarity) {
        matchAnteRarity = matchRarity;
    }

    public boolean getAnteIncludeBasicLands() {
        return anteIncludeBasicLands;
    }
    public void setAnteIncludeBasicLands(final boolean includeBasicLands) {
        anteIncludeBasicLands = includeBasicLands;
    }

    public boolean getSideboardForAI() {
        return sideboardForAI;
    }
    public void setSideboardForAI(final boolean sideboard) {
        sideboardForAI = sideboard;
    }

    public boolean getAISideboardingEnabled() {
        return AISideboardingEnabled;
    }
    public void setAISideboardingEnabled(final boolean aiSideboarding) {
        AISideboardingEnabled = aiSideboarding;
    }

    public boolean isAllowCheatShuffle() {
        return allowCheatShuffle;
    }
    public void setAllowCheatShuffle(boolean allowCheatShuffle) {
        this.allowCheatShuffle = allowCheatShuffle;
    }

    public int getGamesToWinMatch() {
        return gamesToWinMatch;
    }

    public void setAppliedVariants(final Set<GameType> appliedVariants) {
        if (appliedVariants != null && !appliedVariants.isEmpty())
            this.appliedVariants.addAll(appliedVariants);
    }

    public void addAppliedVariant(final GameType variant) {
        this.appliedVariants.add(variant);
    }

    public boolean hasAppliedVariant(final GameType variant) {
        return appliedVariants.contains(variant);
    }

    public boolean hasCommander() {
        return appliedVariants.contains(GameType.Commander)
                || appliedVariants.contains(GameType.Oathbreaker)
                || appliedVariants.contains(GameType.TinyLeaders)
                || appliedVariants.contains(GameType.Brawl);
    }

    public boolean useGrayText() {
        return useGrayText;
    }
    public void setUseGrayText(final boolean useGrayText) {
        this.useGrayText = useGrayText;
    }

    public boolean warnAboutAICards() {
        return warnAboutAICards;
    }
    public void setWarnAboutAICards(final boolean warnAboutAICards) {
        this.warnAboutAICards = warnAboutAICards;
    }

    public int getSimTimeout() {
        return this.simTimeout;
    }

    public void setSimTimeout(final int duration) {
        this.simTimeout = duration;
    }

    private String replayLogPath = null;

    public String getReplayLogPath() {
        return replayLogPath;
    }

    public void setReplayLogPath(final String path) {
        this.replayLogPath = path;
    }

    /**
     * When true: this game was started by the headless AI simulator (CLI "sim" command).
     * The replay JSON file will be saved with a "sim_" filename prefix instead of "replay_"
     * so it can be distinguished from human-played games in the Game Recap list.
     */
    private boolean simulationMode = false;

    public boolean isSimulationMode() {
        return simulationMode;
    }

    public void setSimulationMode(final boolean simulationMode) {
        this.simulationMode = simulationMode;
    }

    /** When true: skip game-log saving and achievement checks at end of game. */
    private boolean scenarioMode = false;

    public boolean isScenarioMode() {
        return scenarioMode;
    }

    public void setScenarioMode(final boolean scenarioMode) {
        this.scenarioMode = scenarioMode;
    }

    /** When true: auto-save a replay JSON file at end of every game. */
    private boolean autoSaveReplay = true;

    public boolean isAutoSaveReplay() {
        return autoSaveReplay;
    }

    public void setAutoSaveReplay(final boolean autoSaveReplay) {
        this.autoSaveReplay = autoSaveReplay;
    }

    /**
     * When true: this game is a Replay — forces library order to match original game.
     * The player replays from turn 1 with the same draw sequence to test alternate decisions.
     */
    private boolean replayMode = false;

    public boolean isReplayMode() {
        return replayMode;
    }

    public void setReplayMode(final boolean replayMode) {
        this.replayMode = replayMode;
    }

    /**
     * When true: this is a Shuffle Replay — the human player plays with a normally shuffled deck
     * rather than the predetermined draw sequence from the original replay.
     */
    private boolean shuffleReplay = false;

    public boolean isShuffleReplay() {
        return shuffleReplay;
    }

    public void setShuffleReplay(final boolean shuffleReplay) {
        this.shuffleReplay = shuffleReplay;
    }

    /**
     * Forced library order for Replay Mode: maps player lobby-name to ordered list of card names.
     * Index 0 = top of library (next draw).
     */
    private Map<String, List<String>> forcedLibraryOrder = null;

    public Map<String, List<String>> getForcedLibraryOrder() {
        return forcedLibraryOrder;
    }

    public void setForcedLibraryOrder(final Map<String, List<String>> forcedLibraryOrder) {
        this.forcedLibraryOrder = forcedLibraryOrder;
    }

    /**
     * Controls whether the forced library order is restored after any shuffle in Replay Mode.
     * "always" = re-apply after every shuffle (default); "never" = only enforce initial order.
     */
    private String shuffleRestore = "always";

    public String getShuffleRestore() {
        return shuffleRestore;
    }

    public void setShuffleRestore(final String shuffleRestore) {
        this.shuffleRestore = shuffleRestore;
    }

    // -------------------------------------------------------------------------
    // Replay Mode — branch & comparison data
    // -------------------------------------------------------------------------

    /** Path to the source replay JSON file being replayed. */
    private String originalReplayFile = null;

    public String getOriginalReplayFile() { return originalReplayFile; }
    public void setOriginalReplayFile(final String path) { this.originalReplayFile = path; }

    /** Turn number from which the replay branches (1 = full replay from start). */
    private int replayBranchTurn = 1;

    public int getReplayBranchTurn() { return replayBranchTurn; }
    public void setReplayBranchTurn(final int turn) { this.replayBranchTurn = turn; }

    /** Pre-computed per-turn summary of the original game for post-replay comparison. */
    private OriginalGameSummary originalGameSummary = null;

    public OriginalGameSummary getOriginalGameSummary() { return originalGameSummary; }
    public void setOriginalGameSummary(final OriginalGameSummary summary) { this.originalGameSummary = summary; }

    /** 0-based index of the player who goes first in a replay (from original game_start). */
    private int replayStartingPlayerIndex = 0;

    public int getReplayStartingPlayerIndex() { return replayStartingPlayerIndex; }
    public void setReplayStartingPlayerIndex(final int idx) { this.replayStartingPlayerIndex = idx; }

    // -------------------------------------------------------------------------
    // Scenario: defined starting hand + first draws
    // -------------------------------------------------------------------------

    /**
     * Per-player starting hand cards for scenario mode (type {@code opening_hand_test}).
     * Key = "P1", "P2", … — value = ordered card names placed at the front of the library
     * so they are drawn as the opening hand.
     * {@code null} means no scenario hand override.
     */
    private Map<String, List<String>> scenarioStartingHands = null;

    public Map<String, List<String>> getScenarioStartingHands() { return scenarioStartingHands; }
    public void setScenarioStartingHands(final Map<String, List<String>> hands) {
        this.scenarioStartingHands = hands;
    }

    /**
     * Per-player first-N draw cards for scenario mode.
     * Key = "P1", "P2", … — value = ordered card names placed directly after the starting hand
     * in the library so they are drawn in turns 1..N.
     * {@code null} means no override.
     */
    private Map<String, List<String>> scenarioFirstDraws = null;

    public Map<String, List<String>> getScenarioFirstDraws() { return scenarioFirstDraws; }
    public void setScenarioFirstDraws(final Map<String, List<String>> draws) {
        this.scenarioFirstDraws = draws;
    }

    /**
     * When {@code true}: AI players skip the mulligan (keep hand automatically).
     * The human player is unaffected and may still mulligan freely.
     * Used for {@code opening_hand_test} scenarios where the AI hand is predefined.
     */
    private boolean scenarioSkipMulligan = false;

    public boolean isScenarioSkipMulligan() { return scenarioSkipMulligan; }
    public void setScenarioSkipMulligan(final boolean skip) { this.scenarioSkipMulligan = skip; }

    // -------------------------------------------------------------------------
    // Replay Mode — forced play sequence
    // -------------------------------------------------------------------------

    /**
     * Forced play sequence for Replay AI mode: maps player lobby-name to an ordered list
     * of card names to cast/activate. Populated from CAST/ACTIVATE events in the replay JSON.
     *
     * <p>The AI checks this queue at each decision point (before normal heuristics).
     * Soft enforcement: if the next card is not castable, the AI falls back to normal logic
     * and keeps the entry in the queue for retrying next priority window.
     *
     * {@code null} means no forced sequence (normal AI behaviour).
     */
    private Map<String, List<String>> forcedPlaySequence = null;

    public Map<String, List<String>> getForcedPlaySequence() {
        return forcedPlaySequence;
    }

    public void setForcedPlaySequence(final Map<String, List<String>> seq) {
        this.forcedPlaySequence = seq;
    }

    /**
     * Recorded sacrifice-cost target for each {@link #forcedPlaySequence} entry, index-aligned
     * 1:1 per lobby name with {@link #forcedPlaySequence}'s list for that name ({@code null}
     * where that entry recorded no sacrifice choice). {@code AiController} pops both lists
     * together so they never drift apart, then uses the popped sacrifice name (if any) to force
     * its own sacrifice-cost decision instead of falling back to its usual heuristic.
     *
     * <p>{@code null} means no recorded sacrifice choices (normal AI sacrifice heuristic for
     * every scripted entry) — distinct from an empty map, which would mean "recorded, but no
     * entry needed one".
     */
    private Map<String, List<String>> forcedPlaySequenceSacrifice = null;

    public Map<String, List<String>> getForcedPlaySequenceSacrifice() {
        return forcedPlaySequenceSacrifice;
    }

    public void setForcedPlaySequenceSacrifice(final Map<String, List<String>> seq) {
        this.forcedPlaySequenceSacrifice = seq;
    }

    /**
     * Pops {@code cardName} off the head of {@code lobbyName}'s forced-play-sequence queue if it
     * matches, so a human-facing "what's next" hint (see {@code CPrompt}) stays in sync with what
     * the player has actually done. AI seats never need this call: {@code AiController} already
     * pops its own queue entry before executing the matching play, so by the time an engine event
     * fires for that play the head is already the next entry. No-op if there's no forced sequence
     * for this seat, or if its queue is empty.
     *
     * <p><b>Callers must gate this on the player not being AI-controlled.</b> Calling it
     * unconditionally would double-pop a repeated card name (e.g. two scripted "Swamp" entries in
     * a row) for an AI seat: the AI's own pre-play pop removes the first, the engine plays it,
     * and this call — seeing the same name still at the head — would incorrectly pop the second
     * scripted entry too, silently skipping a step of the AI's own script.</p>
     */
    public void popForcedPlayIfMatches(final String lobbyName, final String cardName) {
        if (forcedPlaySequence == null || lobbyName == null || cardName == null) return;
        final List<String> seq = forcedPlaySequence.get(lobbyName);
        if (seq == null || seq.isEmpty()) return;
        if (cardName.equals(seq.get(0))) {
            seq.remove(0);
            // Keep forcedPlaySequenceSacrifice index-aligned with forcedPlaySequence even for a
            // human seat's queue (which never reads the sacrifice list back) - a hint-only pop
            // here must not leave the two lists out of sync for good.
            if (forcedPlaySequenceSacrifice != null) {
                final List<String> sacSeq = forcedPlaySequenceSacrifice.get(lobbyName);
                if (sacSeq != null && !sacSeq.isEmpty()) sacSeq.remove(0);
            }
        }
    }
}
