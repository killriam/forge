package forge;

import forge.view.Main;
import org.testng.annotations.Test;

import static org.testng.AssertJUnit.assertEquals;

/** The deck options a GUI launch accepts, one per lobby seat (MaMo's playtest against a whole Legacy Game table). */
public class MainCliSeatsTest {

    @Test
    public void deckOptionsMapToSeats() {
        assertEquals(0, Main.cliSeatIndex("--deck"));
        assertEquals(0, Main.cliSeatIndex("--deck1"));
        assertEquals(1, Main.cliSeatIndex("--deck2"));
        assertEquals(2, Main.cliSeatIndex("--deck3"));
        assertEquals(7, Main.cliSeatIndex("--deck8"));
    }

    @Test
    public void otherOptionsAreNoSeat() {
        assertEquals(-1, Main.cliSeatIndex("--deck9"));
        assertEquals(-1, Main.cliSeatIndex("--deck0"));
        assertEquals(-1, Main.cliSeatIndex("--deck10"));
        assertEquals(-1, Main.cliSeatIndex("--deckX"));
        assertEquals(-1, Main.cliSeatIndex("--format"));
        assertEquals(-1, Main.cliSeatIndex(null));
    }
}
