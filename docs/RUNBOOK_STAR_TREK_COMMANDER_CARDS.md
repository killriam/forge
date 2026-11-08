# Runbook: implement the Star Trek Commander cards in Forge, end to end

Written for: the Forge team (people or agents) who will execute `docs/TASK_STAR_TREK_COMMANDER_CARDS.md`.
Read first: `docs/AGENT_TUTORIAL_ADDING_MISSING_CARDS.md` (script and edition-file rules). This runbook is the execution plan around it: setup, the loop per batch, verification, release.

Work list: `docs/TASK_STAR_TREK_COMMANDER_CARDS.md` (93 cards from Federation Fleet, Klingon Fury, We Are the Borg; Landing Party is out of scope).

## 0. Setup (once)

1. Work in `C:\SWProjects\Forge` on branch `replay-Features`. `git status` first: this checkout has unrelated uncommitted files (for example `analyze_commander_stats.py`, `commander_simulation_report.json`). Never `git add -A`.
2. Make sure Forge builds and starts from this checkout before you change anything, so later failures are yours and not pre-existing. Build guide: `FORGE_EXE_BUILD_GUIDE.md` (`mvn clean package -pl forge-gui-desktop -am -DskipTests`), start with `start-forge.ps1`.
3. Create a test deck file per source deck that lists the cards you are scripting (a `.dck`, format in `convert_decklist_to_dck.py` and `my_decks/`). Keep them outside the repo or untracked.

## 1. Order the work

Split the 93 cards into batches of about 10, grouped by mechanic so you reuse the same reference scripts:

- Borg artifact/adapt cards (Ambulatory Reactor, Maturation Chamber, ...)
- Klingon cards (Alexander, Chancellor Gorkon, Kang, ...)
- Federation cards (Picard, Luminary, Ezri Dax, ...)
- Spacecraft/Station cards (`K:Station:N`; reference `dawnsire_sunstar_dreadnought.txt`)

Do the simplest cards of each group first. They give you a working pattern for the harder ones.

## 2. Loop per batch

For each batch:

1. **Read the card block** in the task file. The oracle text is from Scryfall; do not retype it from memory.
2. **Find reference scripts**: grep `cardsfolder` for an existing card with the same effect and copy its parameter spelling (see tutorial section 2). Do not invent parameters.
3. **Write the script** in `forge-gui/res/cardsfolder/upcoming/<snake_case_name>.txt`. File name: lowercase, spaces to underscores, drop punctuation. Include `Oracle:` exactly as given.
4. **Edition lines**: all 93 cards already have lines in `editions/Star Trek Commander.txt` or `Star Trek.txt`. Do not edit those files unless a card is really missing, and then use a byte-safe edit (the files contain non-UTF-8 bytes).
5. **Verify** (section 3).
6. **Commit** only your files: `git add forge-gui/res/cardsfolder/upcoming/<files>` then `git commit -m "feat(cards): add <names> (Star Trek Commander)"`. End the message with the attribution line your instructions require. Do not push.

## 3. Verify each batch

The script parser is lenient in some places and strict in others, so a script that "looks right" can silently drop the card. Check all of these:

1. Start Forge from the checkout. Watch the log for parse errors mentioning your card files.
2. Import your test deck. No "Removed from your Deck" popup. If the popup lists a card, its script failed to load or its name does not match the edition line exactly.
3. Open each card in the deck editor and compare the shown text with the task file.
4. Play a test game (or use a scenario/puzzle file) and trigger each ability once: triggers, activated abilities, static effects, tokens. Note anything that does not work.
5. Cards with a mechanic Forge lacks: do not ship a wrong approximation. Skip the card, record it in your report with the missing mechanic.

## 4. Special cases to expect

- **Station / Spacecraft**: `K:Station:N`, thresholds as `counters_GEN_CHARGE` (see the two reference scripts in the tutorial). Spacecraft become creatures only at the printed threshold.
- **Tokens**: look in `forge-gui/res/tokenscripts/` before adding one. Officer, Borg and Tribble style tokens may or may not exist. A new token script goes in the same commit and is named in the commit message.
- **Partner/alternate commanders and legendary rules**: scripts do not need anything special for being a commander. Do not add commander-specific logic.
- **Name punctuation**: `Võx, Child of the Collective` has a non-ASCII letter. Copy it from the task file. If the script is not found, check how `Name:` handles it in other scripts with accents.
- **Planets** (type line `Land — Planet`, for example Shore Leave Planet): check an existing Planet script before writing; they are new and may need a type entry.

## 5. After all batches: ship

Cards reach players only through a Forge release. The Connector's auto-update replaces `res/` wholesale, so never copy files into an installed Forge by hand.

1. Make sure everything is committed and pushed to `killriam/forge` `replay-Features`. Pushing is a separate decision: ask the owner before you push.
2. Release: the owner triggers `build-release.yml` (the project's release skill does this: `gh workflow run build-release.yml --repo killriam/forge --ref replay-Features`). It builds the jar and publishes the `replay-features-latest` release (~230 MB jar plus the `res/` bundle).
3. Verify the release asset resolves and downloads (`gh api repos/killriam/forge/releases/tags/replay-features-latest`).
4. In the MaMo Connector, let it auto-update, then import a deck from each of the three precons and confirm no popup.
5. Customer-visible? "Star Trek Commander cards now playable in Forge" is a change a player notices: add an entry to the root `CHANGELOG.md` (workspace `MaMo-Base`), per the repo's changelog rule. The owner flips the tested checkbox, not you.

## 6. Report back

At the end send: cards implemented (with commit hashes), cards skipped and the missing mechanic for each, tokens added, anything you could not verify, and the release run ID if you triggered one. Do not state a card works unless you saw it work in a game.

## Done checklist

- [ ] Forge built and started before any change
- [ ] 93 cards handled: scripted, or skipped with a reason
- [ ] Every batch verified (parse log, import, text, game test)
- [ ] Commits contain only intended files, none pushed without approval
- [ ] Release built and asset verified (if the owner asked for it)
- [ ] Changelog entry added
- [ ] Report sent
