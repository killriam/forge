package forge.deck;

import java.util.ArrayList;

public class FittingSection {
    private ArrayList<CardForFitting> cardsInSection;

    public FittingSection() {
        cardsInSection = new ArrayList<>();
    }

    public boolean readCards(final Iterable<String> lines) {
        CardPool.fromCardList(lines);
        return true;
    }
}

