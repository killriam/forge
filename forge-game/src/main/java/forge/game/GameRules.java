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
}
