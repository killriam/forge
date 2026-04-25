package forge.game;

import forge.game.card.Card;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class startingHandStats {

    private Map<String, Integer> startingHandCount;

    public startingHandStats() {
        startingHandCount = new HashMap<>();
    }

    public void addCard(String card) {
        if (startingHandCount.containsKey(card)) {
            startingHandCount.put(card, startingHandCount.get(card) + 1);
        } else {
            startingHandCount.put(card, 1);
        }
    }

    public int getCardCount(Card card) {
        return startingHandCount.getOrDefault(card, 0);
    }

    public void displayPlayerCounts() {
        List<Map.Entry<String, Integer>> entryList = getCardByCount();
        for (Map.Entry<String, Integer> entry : entryList) {
            System.out.println("Card: " + entry.getKey() + ", Count: " + entry.getValue());
        }
    }

    public List<Map.Entry<String, Integer>> getCardByCount() {
        List<Map.Entry<String, Integer>> entryList = new ArrayList<>(startingHandCount.entrySet());
        entryList.sort((entry1, entry2) -> entry2.getValue().compareTo(entry1.getValue()));
        return entryList;
    }
}

