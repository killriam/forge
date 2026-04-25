package forge.game.log.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Metadata object for a scenario replay file (spec v1.7.0+).
 * Present only when the top-level {@code mode} field is {@code "scenario"}.
 *
 * <p>A scenario is a focused, hand-crafted board state used to check an
 * interaction, clarify a rule, or verify a combo outcome. It is a read-only
 * teaching document and cannot be launched as a live match.</p>
 *
 * <p>Valid scenario types are declared as {@code TYPE_*} constants on this class.</p>
 */
public class Scenario {

    /** Verify how two or more cards interact (e.g. replacement effect ordering). */
    public static final String TYPE_INTERACTION_CHECK   = "interaction_check";

    /** Clarify a specific rule in context (e.g. trample + deathtouch, state-based actions). */
    public static final String TYPE_RULES_CLARIFICATION = "rules_clarification";

    /** Demonstrate the final board state after a combo resolves. */
    public static final String TYPE_COMBO_OUTCOME       = "combo_outcome";

    private String type;              // required — one of the TYPE_* constants above
    private String title;             // required — short human-readable title
    private String description;       // full narrative description of the board state
    private String question;          // the specific question being answered
    private String answer;            // the authoritative answer
    private List<String> rulingReferences;   // CR citations, e.g. ["CR 702.2b", "CR 702.19d"]
    private List<String> tags;               // free-form keyword tags for search/filtering

    public Scenario() {
        this.rulingReferences = new ArrayList<>();
        this.tags = new ArrayList<>();
    }

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
}
