package forge.deck;

public class CardForFitting {
    String cardName;
    int quantity;
    int fittingScore;

    public CardForFitting(String cardName, int quantity) {
        this.cardName = cardName;
        this.quantity = quantity;
        this.fittingScore = 0;
    }
}

