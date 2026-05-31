package forge.game.log.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Metadata object for a scenario replay file (spec v1.7.0+).
 * Present only when the top-level {@code mode} field is {@code "scenario"}.
 *
 * <p>A scenario is a focused, hand-crafted board state used to check an
 * interaction, clarify a rule, or verify a combo outcome. It can optionally
 * define a live-playable starting configuration via the {@code players} map.</p>
 *
 * <p>Valid scenario types are declared as {@code TYPE_*} constants on this class.</p>
 */
public class Scenario {

    /** Verify how two or more cards interact (e.g. replacement effect ordering). */
    public static final String TYPE_INTERACTION_CHECK   = "interaction_check";

    /** Clarify a specific rule in context (e.g. trample + deathttouch, state-based actions). */
    public static final String TYPE_RULES_CLARIFICATION = "rules_clarification";

    /** Demonstrate the final board state after a combo resolves. */
    public static final String TYPE_COMBO_OUTCOME       = "combo_outcome";

    /**
     * Define and test a specific opening hand + first N draw cards.
     * Players block must have {@code starting_hand} and {@code first_draws} per player.
     */
    public static final String TYPE_OPENING_HAND_TEST   = "opening_hand_test";

    private String type;              // required — one of the TYPE_* constants above
    private String title;             // required — short human-readable title
    private String description;       // full narrative description of the board state
    private String question;          // the specific question being answered
    private String answer;            // the authoritative answer
    private List<String> rulingReferences;   // CR citations, e.g. ["CR 702.2b", "CR 702.19d"]
    private List<String> tags;               // free-form keyword tags for search/filtering

    /**
     * Per-player setup configuration.
     * Key = player id ("P1", "P2", …).
     * Used for {@link #TYPE_OPENING_HAND_TEST} and any scenario that needs
     * a live-playable starting state.
     */
    private Map<String, PlayerSetup> players;

    public Scenario() {
        this.rulingReferences = new ArrayList<>();
        this.tags = new ArrayList<>();
        this.players = new LinkedHashMap<>();
    }

    // -------------------------------------------------------------------------
    // Inner class: PlayerSetup
    // -------------------------------------------------------------------------

    /**
     * Per-player starting configuration for a scenario.
     *
     * <ul>
     *   <li>{@code starting_hand} – exact cards in the opening hand (bypasses mulligan)</li>
     *   <li>{@code first_draws}   – ordered list of cards on top of the library (drawn first)</li>
     *   <li>{@code commanders}    – commander cards in the command zone (name only)</li>
     *   <li>{@code battlefield}   – cards to place on the battlefield before the game starts</li>
     *   <li>{@code starting_life} – life total override (default 20)</li>
     * </ul>
     */
    public static class PlayerSetup {
        private List<String> startingHand;
        private List<String> firstDraws;
        private List<String> commanders;
        private List<String> battlefield;
        private int startingLife = 20;

        public PlayerSetup() {
            this.startingHand = new ArrayList<>();
            this.firstDraws = new ArrayList<>();
            this.commanders = new ArrayList<>();
            this.battlefield = new ArrayList<>();
        }

        public List<String> getStartingHand() { return startingHand; }
        public void setStartingHand(List<String> startingHand) { this.startingHand = startingHand; }

        public List<String> getFirstDraws() { return firstDraws; }
        public void setFirstDraws(List<String> firstDraws) { this.firstDraws = firstDraws; }

        public List<String> getCommanders() { return commanders; }
        public void setCommanders(List<String> commanders) { this.commanders = commanders; }

        public List<String> getBattlefield() { return battlefield; }
        public void setBattlefield(List<String> battlefield) { this.battlefield = battlefield; }

        public int getStartingLife() { return startingLife; }
        public void setStartingLife(int startingLife) { this.startingLife = startingLife; }

        /** Returns true when this player setup has at least a starting hand or draws defined. */
        public boolean hasStartingConfig() {
            return !startingHand.isEmpty() || !firstDraws.isEmpty();
        }
    }

    // -------------------------------------------------------------------------
    // Getters / Setters
    // -------------------------------------------------------------------------

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }

    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }

    public List<String> getRulingReferences() { return rulingReferences; }
    public void setRulingReferences(List<String> rulingReferences) { this.rulingReferences = rulingReferences; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }

    public Map<String, PlayerSetup> getPlayers() { return players; }
    public void setPlayers(Map<String, PlayerSetup> players) { this.players = players; }

    /** Convenience: get or create PlayerSetup for a player id (e.g. "P1"). */
    public PlayerSetup getOrCreatePlayerSetup(String playerId) {
        return players.computeIfAbsent(playerId, k -> new PlayerSetup());
    }

    /**
     * Returns true when any player in this scenario has a starting hand or first draws defined.
     */
    public boolean hasPlayerSetup() {
        return players.values().stream().anyMatch(PlayerSetup::hasStartingConfig);
    }
}
