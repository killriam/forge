package forge.game.event;

import forge.game.card.Card;
import forge.game.card.CardView;
import forge.game.player.Player;
import forge.game.player.PlayerView;
import forge.util.Lang;
import forge.util.TextUtil;

public record GameEventPlayerLivesChanged(PlayerView player, int oldLives, int newLives, CardView source, String cause) implements GameEvent {

    public GameEventPlayerLivesChanged(Player player, int oldLives, int newLives, Card source, String cause) {
        this(PlayerView.get(player), oldLives, newLives, CardView.get(source), cause);
    }

    /** Convenience constructor for changes with no identifiable card source (e.g. life loss). */
    public GameEventPlayerLivesChanged(Player player, int oldLives, int newLives) {
        this(player, oldLives, newLives, (Card) null, null);
    }

    @Override
    public <T> T visit(IGameEventVisitor<T> visitor) {
        return visitor.visit(this);
    }

    @Override
    public String toString() {
        return TextUtil.concatWithSpace(Lang.getInstance().getPossesive(player.getName()),"lives changed:",  String.valueOf(oldLives),"->", String.valueOf(newLives));
    }
}
