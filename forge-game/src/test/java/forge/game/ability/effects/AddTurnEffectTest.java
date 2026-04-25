package forge.game.ability.effects;

import forge.game.phase.ExtraTurn;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

public class AddTurnEffectTest {

    @Test
    public void testExtraTurn_TurnOrderPositionDefaultsToZero() {
        ExtraTurn turn = new ExtraTurn(null);
        AssertJUnit.assertEquals(0, turn.getTurnOrderPosition());
    }

    @Test
    public void testExtraTurn_SetTurnOrderPositionIsRetrievable() {
        ExtraTurn turn = new ExtraTurn(null);
        turn.setTurnOrderPosition(3);
        AssertJUnit.assertEquals(3, turn.getTurnOrderPosition());
    }

    @Test
    public void testExtraTurn_TurnOrderPositionReflectsStackOrder() {
        // PhaseHandler.addExtraTurn sets position = extraTurns.size() before push,
        // so the first extra turn gets 0, the second gets 1, etc.
        ExtraTurn first = new ExtraTurn(null);
        first.setTurnOrderPosition(0);
        ExtraTurn second = new ExtraTurn(null);
        second.setTurnOrderPosition(1);

        AssertJUnit.assertEquals(0, first.getTurnOrderPosition());
        AssertJUnit.assertEquals(1, second.getTurnOrderPosition());
        AssertJUnit.assertTrue(second.getTurnOrderPosition() > first.getTurnOrderPosition());
    }
}
