# Deck Metadata: CardsUnderTest & NewCardsSinceLastRevision

## Overview

Forge `.dck` deck files support informational metadata tags in the `[metadata]` header block.

The **`CardsUnderTest=`** and **`NewCardsSinceLastRevision=`** keys allow external deck-building tools (such as MaMo Deckbuilder and Evaluation Workbench) to tag cards that are currently being evaluated or were newly added in the current deck revision.

---

## Key Principles & Design Rules

1. **Strict Section Isolation:**
   - These keys exist **only** within the `[metadata]` section.
   - They **never** alter, reformat, or annotate the card lines in `[Commander]`, `[Main]`, `[Sideboard]`, or any other card pool.
   - The card pools remain strictly parseable and compliant with standard Forge rules.

2. **List Delimiter:**
   - Card names are serialized as a semicolon-separated list: `CardName1; CardName2; CardName3` (matching the convention used by `KeyCards=`).
   - Semicolon delimiters ensure card names containing commas (e.g. `Atraxa, Praetors' Voice` or `Grist, the Hunger Tide`) are parsed unambiguously.

3. **Round-Trip Fidelity:**
   - When a deck file is loaded by `DeckSerializer.fromFile()` or `DeckSerializer.fromSections()`, these metadata fields are populated on the `Deck` model.
   - When exported or saved via `DeckSerializer.writeDeck()`, the metadata is preserved in the header.

---

## File Format Specification

### Example `.dck` File

```ini
[metadata]
Name=killriam - Atraxa Superfriends (2026-09-08)
CardsUnderTest=Cyclonic Rift; Rhystic Study; Sol Ring
NewCardsSinceLastRevision=Arcane Signet; Mana Drain

[Commander]
1 Atraxa, Praetors' Voice

[Main]
1 Arcane Signet
1 Cyclonic Rift
1 Mana Drain
1 Rhystic Study
1 Sol Ring

[Sideboard]
```

### Metadata Fields Reference

| Metadata Key | Value Format | Description | Example |
|---|---|---|---|
| `CardsUnderTest` | `Name1; Name2; ...` | Semicolon-separated list of card names marked as "under test" (e.g. via MaMo Quick Card Test). | `CardsUnderTest=Cyclonic Rift; Rhystic Study; Sol Ring` |
| `NewCardsSinceLastRevision` | `Name1; Name2; ...` | Semicolon-separated list of card names that were newly added in this revision compared to the previous revision. | `NewCardsSinceLastRevision=Arcane Signet; Mana Drain` |

---

## Architecture & Code Map

### 1. `forge-core` Engine (`forge.deck.io`)

* **`DeckFileHeader.java`:**
  * Declares constants:
    ```java
    public static final String CARDS_UNDER_TEST = "CardsUnderTest";
    public static final String NEW_CARDS_SINCE_LAST_REVISION = "NewCardsSinceLastRevision";
    ```
  * Parses semicolon-delimited values into `List<String> cardsUnderTest` and `List<String> newCardsSinceLastRevision`.
  * Exposes getters `getCardsUnderTest()` and `getNewCardsSinceLastRevision()`.

* **`Deck.java`:**
  * Holds in-memory collections for cards under test and new cards.
  * API Methods:
    * `List<String> getCardsUnderTest()`
    * `void addCardUnderTest(String cardName)`
    * `void removeCardUnderTest(String cardName)`
    * `boolean isCardUnderTest(String cardName)`
    * `List<String> getNewCardsSinceLastRevision()`
    * `void addNewCardSinceLastRevision(String cardName)`
    * `void removeNewCardSinceLastRevision(String cardName)`
    * `boolean isNewCardSinceLastRevision(String cardName)`

* **`DeckSerializer.java`:**
  * `serializeDeck(Deck d)`: Writes `CardsUnderTest=` and `NewCardsSinceLastRevision=` to the `[metadata]` section if present.
  * `fromSections(...)`: Deserializes both fields from the header into the constructed `Deck` instance.

### 2. Backend Export Integration (`new-backend`)

* **Endpoint:** `GET /api/deck/export/:deckId/forge` (`getPublicDeckForgeExport` in `CRUDDeckController.ts`)
  * Queries `MaMo.deck_cards_under_test` to retrieve marked Oracle IDs for the deck.
  * Diffs against the previous revision in `MaMo.deckcardlist` for new cards.
  * Emits sorted, semicolon-separated names on `CardsUnderTest=` and `NewCardsSinceLastRevision=`.

---

## Unit Testing

* **`forge-core`:**
  * `DeckSerializerScenarioTest.java`:
    * `testCardsUnderTest_roundTripsThroughFile()`
    * `testNewCardsSinceLastRevision_roundTripsThroughFile()`
    * `testDeckFileHeader_parsesCardsUnderTestAndNewCardsDirectly()`
* **`new-backend`:**
  * `getPublicDeckForgeExport.cardsUnderTest.test.ts` (4 unit tests)
  * `getPublicDeckForgeExport.newCards.test.ts` (5 unit tests)
