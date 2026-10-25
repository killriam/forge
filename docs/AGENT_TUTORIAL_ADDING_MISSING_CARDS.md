# Tutorial: Adding a Missing Card to Forge

Use this when a MaMo user imports a deck and Forge shows a **"Removed from your Deck"** popup.
That popup means Forge has no card with that name in its database. For unreleased sets the fix
is almost always a new card script plus an edition entry.

Worked example: *Captain Jean-Luc Picard* and *Deep Space 9* (Star Trek, TRK, commit f9298a03cdc).

## 0. Confirm the cause

Search `forge-gui/res` for the name before writing anything:

```
grep -rli "<card name>" forge-gui/res/cardsfolder forge-gui/res/editions
```

- Nothing found: the card is missing. Continue.
- Found in `editions/` but not in `cardsfolder/`: only the script is missing.
- Found in both: the name is probably spelled differently in the deck (split cards, commas, accents). Fix the spelling, not the data.

## 1. Get the real card data

Use Scryfall for exact oracle text, cost, types, P/T, rarity, artist and collector number:

```
curl -s "https://api.scryfall.com/cards/search?q=%21%22<Card+Name>%22"
```

Do not write oracle text from memory. Unreleased cards are not in your training data.

## 2. Find reference scripts

Never invent script syntax. Copy idioms from existing cards that do something similar:

| Need | Where to look |
|---|---|
| Spacecraft / Station | `cardsfolder/d/dawnsire_sunstar_dreadnought.txt`, `cardsfolder/a/atmospheric_greenhouse.txt` |
| Token creation | `grep "TokenScript\$"`, then check `res/tokenscripts/` for the token (e.g. `r_1_1_officer`) |
| Anthem | `S:Mode$ Continuous \| Affected$ ... \| AddPower$ ...` |
| Cast trigger | `T:Mode$ SpellCast \| ValidCard$ ... \| ValidActivatingPlayer$ You` |
| Tap-N-creatures cost | `Cost$ tapXType<N/Type/description>` |
| Exile top N, put permanents onto battlefield | `Dig` with `ChangeNum$ All`, `ChangeValid$`, `DestinationZone$`, `DestinationZone2$` |
| Search library onto battlefield with counters | `ChangeZone` with `WithCountersType$` / `WithCountersAmount$` |

Search with `grep -rh "<keyword>" forge-gui/res/cardsfolder | head`. Prefer parameters you saw in at least one existing script.

## 3. Write the card script

Put it in `forge-gui/res/cardsfolder/upcoming/<snake_case_name>.txt`. The `upcoming/` folder holds cards from sets that are not out yet; Forge loads it like the lettered folders.

Required lines, in this order: `Name`, `ManaCost`, `Types`, `PT` (creatures and vehicles), keywords/abilities, `Oracle`.

Rules that are easy to get wrong:
- `Oracle:` must be the exact Scryfall text, with `\n` between lines.
- Use `NICKNAME` in descriptions for the short name and `CARDNAME` for the full name.
- A trigger that fires on two events (for example "enters and at the beginning of your upkeep") needs two `T:` lines sharing one `Execute$`. Mark the second with `Secondary$ True`.
- A static ability gated on counters uses `IsPresent$ Card.Self+counters_GE9_CHARGE`.
- Do not add a `PT:` line to a Spacecraft that has no printed P/T.

## 4. Register the card in the edition file

Edit `forge-gui/res/editions/<Set Name>.txt`. Add a line under `[cards]` in collector-number order:

```
<number> <rarity letter> <Card Name> @<Artist>
```

Rarity letters: C, U, R, M. Keep the `[tokens]` section unchanged.

**Line endings are CRLF.** Edition files use `
`. Write your inserted line with `
`, or `git diff` will show mixed endings.

**If the set has no edition file yet**, create `forge-gui/res/editions/<Set Name>.txt` by copying the header of a neighbouring file (for example `Star Trek Commander.txt`):

```
[metadata]
Code=<code>
Date=<YYYY-MM-DD release date>
Name=<Set Name>
Type=<Expansion|Commander|...>
ScryfallCode=<code>

[cards]
<number> <rarity letter> <Card Name> @<Artist>
```

Take the code, date and type from Scryfall's set page, not from memory. Use CRLF line endings here too.

**The edition files contain non-UTF-8 bytes** (accented artist names). Do not open and re-save them as text. Insert lines with a byte-safe edit, for example Python with `open(p, 'rb')` and `bytes.replace`. Afterwards run `git diff` on the file. It should show only your added lines, with no other changes.

## 5. Verify

1. `git diff` shows only the intended additions.
2. Start Forge from this checkout (`start-forge.ps1`), import the original deck, and confirm no "Removed from your Deck" popup appears.
3. Open the card in the deck editor and compare the displayed text with Scryfall.
4. Put the card in a test game and trigger each ability once.
5. If Forge logs a script parse error, fix it before committing. A bad script can drop the card or break loading.

Do not report a card as working until you have done step 2 at the very least. Report anything you did not verify.

## 6. Commit and ship

- Run `git status` first. This repo usually has unrelated uncommitted changes. Stage only your files with `git add <paths>`, never `git add -A` or `git add .`.
- Commit message: `feat(cards): add <names> (<Set>)`.
- Do not push, tag or publish a release unless asked.
- When asked to release: this fork publishes pre-releases on `killriam/forge`, tagged `forge-2.0.16-replay.N` (take the next free N from `git tag --list "forge-2.0.16-replay.*"`). Build with `mvn -q -o clean package -pl forge-gui-desktop -am -Dmaven.test.skip=true`, then `gh release create <tag> <jar> -R killriam/forge --prerelease --target <full commit SHA>`. `gh` defaults to upstream, so always pass `-R killriam/forge`.
- Users do not get the card until a new Forge release is published. The Connector's auto-update replaces `res/` wholesale, so copying files into an installed Forge will be overwritten.

## Checklist

- [ ] Confirmed the card is missing from `cardsfolder/` and `editions/`
- [ ] Oracle text copied from Scryfall
- [ ] Script syntax copied from existing scripts
- [ ] Edition line added (CRLF, byte-safe), with a clean `git diff`
- [ ] Imported the deck in Forge and the popup is gone
- [ ] Tested each ability in a game
- [ ] Committed only the intended files (checked `git status` first)
- [ ] Did not push or release unless asked
