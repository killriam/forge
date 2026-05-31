package forge.game.mulligan;

import forge.game.player.Player;

/**
 * A trivial mulligan implementation for scenario mode: the player (AI) immediately
 * keeps their hand without any prompt, mulligan draw, or scry.
 *
 * Used when {@code GameRules.isScenarioSkipMulligan()} is true and the player is an AI.
 * The hand was deterministically set by {@link forge.game.log.ScenarioLibrarySetup}
 * and must not be changed.
 */
public class ScenarioKeepMulligan extends AbstractMulligan {

    public ScenarioKeepMulligan(Player player) {
        super(player, false);
        // Mark as kept immediately — runPlayerMulligans() will skip this player entirely.
        kept = true;
    }

    @Override
    public boolean canMulligan() {
        return false;
    }

    @Override
    public int handSizeAfterNextMulligan() {
        return 0;
    }

    @Override
    public void afterMulligan() {
        // No log entry — the AI silently keeps its scenario hand.
    }
}

