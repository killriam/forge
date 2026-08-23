package forge.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import forge.ai.guidance.AiGuidanceProfile;
import forge.deck.Deck;
import forge.deck.DeckRulesConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads {@link DeckRulesConfig} from an external Commander Decklist Notation JSON file
 * (referenced via the {@code DecklistSpec$} AiHint) and attaches it to the {@link Deck}.
 *
 * This class lives in forge-ai because it needs Gson (available here) to parse the JSON.
 * The resulting {@link DeckRulesConfig} model lives in forge-core.
 *
 * Usage:
 * <pre>
 *     DeckRulesLoader.loadIfNeeded(deck);
 *     // or
 *     DeckRulesLoader.loadIfNeeded(deck, baseDir);
 * </pre>
 */
public final class DeckRulesLoader {

    private static final Logger LOG = LoggerFactory.getLogger(DeckRulesLoader.class);

    private DeckRulesLoader() { }

    /**
     * If the deck has a DecklistSpec path and its DeckRulesConfig has not been loaded yet,
     * parse the JSON and attach the config to the deck.
     *
     * @param deck    the deck to enrich
     * @param baseDir base directory for resolving relative paths (e.g., deck file directory)
     */
    public static void loadIfNeeded(Deck deck, File baseDir) {
        if (deck == null) return;

        String specPath = deck.getDecklistSpecPath();
        if (specPath == null || specPath.isEmpty()) return;

        // Already loaded via JSON? Check if config is non-empty
        DeckRulesConfig existing = deck.getDeckRulesConfig();
        if (existing != null && !existing.isEmpty()) {
            // Only skip if we already have a JSON-loaded config (not just inline hints)
            return;
        }

        File jsonFile = resolveSpecPath(specPath, baseDir);
        if (jsonFile == null || !jsonFile.exists()) {
            LOG.debug("Decklist spec file not found: {} (resolved from '{}')", jsonFile, specPath);
            return;
        }

        try {
            DeckRulesConfig config = loadFromFile(jsonFile);
            if (config != null) {
                deck.setDeckRulesConfig(config);
                LOG.info("Loaded deck rules from {} — mulligan={}, combos={}, dontCombos={}",
                        jsonFile.getName(),
                        config.hasMulligan(),
                        config.getCombos().size(),
                        config.getDontCombos().size());
            }
        } catch (Exception e) {
            LOG.warn("Failed to load deck rules from {}: {}", jsonFile, e.getMessage());
        }
    }

    /** Convenience overload — uses no base directory (absolute path only). */
    public static void loadIfNeeded(Deck deck) {
        loadIfNeeded(deck, null);
    }

    /**
     * Parses the {@code deck_rules.ai_guidance} block of the same decklist-spec JSON file
     * {@link #loadIfNeeded} already resolves via {@link Deck#getDecklistSpecPath()}, and returns
     * it as an {@link AiGuidanceProfile}.
     *
     * <p>Deliberately <b>not</b> attached to {@code Deck}/{@code DeckRulesConfig} the way
     * {@link DeckRulesConfig} itself is — {@code AiGuidanceProfile} is Gson-shaped and
     * forge-core's {@code Deck} must stay Gson-free (see this class's own javadoc). Callers
     * (currently only {@code AiController.initGuidanceProfile()}) hold the returned profile
     * themselves, exactly as {@code AiController} already holds its own {@code ComboTracker}
     * rather than storing it on {@code Deck}. Re-parses the file on every call, same as
     * {@link #loadIfNeeded} does for {@code DeckRulesConfig} — called once per game setup, not
     * per turn, so this is not a hot path. Returns {@code null} if there is no spec file, no
     * {@code deck_rules.ai_guidance} block, or the file fails to parse.
     */
    public static AiGuidanceProfile loadAiGuidanceIfNeeded(Deck deck, File baseDir) {
        if (deck == null) return null;

        String specPath = deck.getDecklistSpecPath();
        if (specPath == null || specPath.isEmpty()) return null;

        File jsonFile = resolveSpecPath(specPath, baseDir);
        if (jsonFile == null || !jsonFile.exists()) return null;

        try {
            return loadAiGuidanceFromFile(jsonFile);
        } catch (Exception e) {
            LOG.warn("Failed to load ai_guidance from {}: {}", jsonFile, e.getMessage());
            return null;
        }
    }

    /** Convenience overload — uses no base directory (absolute path only). */
    public static AiGuidanceProfile loadAiGuidanceIfNeeded(Deck deck) {
        return loadAiGuidanceIfNeeded(deck, null);
    }

    static AiGuidanceProfile loadAiGuidanceFromFile(File file) throws IOException {
        try (Reader reader = new FileReader(file)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonObject()) return null;
            JsonObject rootObj = root.getAsJsonObject();

            if (!rootObj.has("deck_rules") || !rootObj.get("deck_rules").isJsonObject()) return null;
            JsonObject deckRulesObj = rootObj.getAsJsonObject("deck_rules");

            if (!deckRulesObj.has("ai_guidance") || !deckRulesObj.get("ai_guidance").isJsonObject()) return null;

            AiGuidanceProfile profile = AiGuidanceProfile.parse(deckRulesObj.getAsJsonObject("ai_guidance"));
            LOG.info("Loaded ai_guidance from {} — {} card role binding(s), {} deployment constraint(s)",
                    file.getName(), profile.cardRoleCount(), profile.deploymentConstraintCount());
            return profile;
        }
    }

    // ---- Internal ----

    private static File resolveSpecPath(String specPath, File baseDir) {
        File f = new File(specPath);
        if (f.isAbsolute() && f.exists()) return f;

        // Try relative to baseDir
        if (baseDir != null) {
            f = new File(baseDir, specPath);
            if (f.exists()) return f;
        }

        // Try relative to working directory
        f = new File(specPath);
        if (f.exists()) return f;

        return null;
    }

    static DeckRulesConfig loadFromFile(File file) throws IOException {
        try (Reader reader = new FileReader(file)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonObject()) {
                LOG.warn("Decklist spec is not a JSON object: {}", file);
                return null;
            }
            JsonObject rootObj = root.getAsJsonObject();

            // Navigate to deck_rules
            if (!rootObj.has("deck_rules") || !rootObj.get("deck_rules").isJsonObject()) {
                LOG.debug("No deck_rules section in {}", file);
                return null;
            }
            JsonObject deckRulesObj = rootObj.getAsJsonObject("deck_rules");

            DeckRulesConfig config = new DeckRulesConfig();

            // Parse mulligan
            if (deckRulesObj.has("mulligan") && deckRulesObj.get("mulligan").isJsonObject()) {
                config.setMulligan(parseMulligan(deckRulesObj.getAsJsonObject("mulligan")));
            }

            // Parse combos
            if (deckRulesObj.has("combos") && deckRulesObj.get("combos").isJsonArray()) {
                config.setCombos(parseCombos(deckRulesObj.getAsJsonArray("combos")));
            }

            // Parse dont_combos
            if (deckRulesObj.has("dont_combos") && deckRulesObj.get("dont_combos").isJsonArray()) {
                config.setDontCombos(parseDontCombos(deckRulesObj.getAsJsonArray("dont_combos")));
            }

            return config;
        }
    }

    private static DeckRulesConfig.MulliganConfig parseMulligan(JsonObject obj) {
        DeckRulesConfig.MulliganConfig mc = new DeckRulesConfig.MulliganConfig();

        // card_values
        if (obj.has("card_values") && obj.get("card_values").isJsonObject()) {
            JsonObject cv = obj.getAsJsonObject("card_values");
            DeckRulesConfig.MulliganConfig.CardValues vals = new DeckRulesConfig.MulliganConfig.CardValues();
            if (cv.has("land")) vals.setLand(cv.get("land").getAsDouble());
            if (cv.has("cmc_0_to_2")) vals.setCmc0To2(cv.get("cmc_0_to_2").getAsDouble());
            if (cv.has("cmc_3")) vals.setCmc3(cv.get("cmc_3").getAsDouble());
            if (cv.has("other")) vals.setOther(cv.get("other").getAsDouble());
            mc.setCardValues(vals);
        }

        // card_overrides
        if (obj.has("card_overrides") && obj.get("card_overrides").isJsonArray()) {
            List<DeckRulesConfig.MulliganConfig.CardOverride> overrides = new ArrayList<>();
            for (JsonElement el : obj.getAsJsonArray("card_overrides")) {
                if (!el.isJsonObject()) continue;
                JsonObject co = el.getAsJsonObject();
                String name = co.has("name") ? co.get("name").getAsString() : null;
                double value = co.has("value") ? co.get("value").getAsDouble() : 0.0;
                String reason = co.has("reason") ? co.get("reason").getAsString() : null;
                if (name != null) {
                    overrides.add(new DeckRulesConfig.MulliganConfig.CardOverride(name, value, reason));
                }
            }
            mc.setCardOverrides(overrides);
        }

        // thresholds
        if (obj.has("thresholds") && obj.get("thresholds").isJsonArray()) {
            List<DeckRulesConfig.MulliganConfig.Threshold> thresholds = new ArrayList<>();
            for (JsonElement el : obj.getAsJsonArray("thresholds")) {
                if (!el.isJsonObject()) continue;
                JsonObject t = el.getAsJsonObject();
                int round = t.has("round") ? t.get("round").getAsInt() : 0;
                int handSize = t.has("hand_size") ? t.get("hand_size").getAsInt() : 7 - round;
                double minValue = t.has("min_value") ? t.get("min_value").getAsDouble() : 0.0;
                String desc = t.has("description") ? t.get("description").getAsString() : null;
                thresholds.add(new DeckRulesConfig.MulliganConfig.Threshold(round, handSize, minValue, desc));
            }
            mc.setThresholds(thresholds);
        }

        return mc;
    }

    private static List<DeckRulesConfig.ComboDeclaration> parseCombos(JsonArray arr) {
        List<DeckRulesConfig.ComboDeclaration> result = new ArrayList<>();
        for (JsonElement el : arr) {
            if (!el.isJsonObject()) continue;
            JsonObject obj = el.getAsJsonObject();

            DeckRulesConfig.ComboDeclaration combo = new DeckRulesConfig.ComboDeclaration();
            if (obj.has("id")) combo.setId(obj.get("id").getAsString());
            if (obj.has("name")) combo.setName(obj.get("name").getAsString());
            if (obj.has("result")) combo.setResult(obj.get("result").getAsString());

            if (obj.has("pieces") && obj.get("pieces").isJsonArray()) {
                List<String> pieces = new ArrayList<>();
                for (JsonElement p : obj.getAsJsonArray("pieces")) {
                    pieces.add(p.getAsString());
                }
                combo.setPieces(pieces);
            }

            if (obj.has("tags") && obj.get("tags").isJsonArray()) {
                List<String> tags = new ArrayList<>();
                for (JsonElement t : obj.getAsJsonArray("tags")) {
                    tags.add(t.getAsString());
                }
                combo.setTags(tags);
            }

            result.add(combo);
        }
        return result;
    }

    private static List<DeckRulesConfig.AntiSynergy> parseDontCombos(JsonArray arr) {
        List<DeckRulesConfig.AntiSynergy> result = new ArrayList<>();
        for (JsonElement el : arr) {
            if (!el.isJsonObject()) continue;
            JsonObject obj = el.getAsJsonObject();

            DeckRulesConfig.AntiSynergy as = new DeckRulesConfig.AntiSynergy();
            if (obj.has("id")) as.setId(obj.get("id").getAsString());
            if (obj.has("name")) as.setName(obj.get("name").getAsString());
            if (obj.has("reason")) as.setReason(obj.get("reason").getAsString());
            if (obj.has("severity")) {
                as.setSeverity(DeckRulesConfig.Severity.fromString(obj.get("severity").getAsString()));
            }

            if (obj.has("pieces") && obj.get("pieces").isJsonArray()) {
                List<String> pieces = new ArrayList<>();
                for (JsonElement p : obj.getAsJsonArray("pieces")) {
                    pieces.add(p.getAsString());
                }
                as.setPieces(pieces);
            }

            result.add(as);
        }
        return result;
    }
}


