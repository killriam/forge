# Learning Mode: User Guide

This is a player-facing guide to Forge's **Learning Mode** sidebar group and the related
"attach a scenario to a real match" feature. It covers what each screen does, how to get to
it, and what happens when you use it — no code, no JSON schemas.

For the JSON authoring reference (how to *write* a scenario file), see
`docs/SCENARIO_STARTING_HAND_FORMAT.md`. For implementation notes, see
`docs/LEARNING_MODE_OVERVIEW.md`.

---

## Overview

On the home screen's left sidebar, under **Learning Mode**, there are three entries:

| Sidebar entry | What it's for |
|---|---|
| **Game Recap** | Revisit a game you already played — replay it from scratch, or step through it turn by turn |
| **Investigate Scenarios** | Play curated, scripted teaching puzzles with a guaranteed opening hand |
| **Game Learning Viewer** | (See note below — normally reached *through* Game Recap, not by clicking this directly) |

There's also a fourth capability that isn't a sidebar entry at all: attaching a scenario's
guaranteed draw order directly to a deck, so it's available as an option in a **real**
Constructed or Commander match. That's covered in its own section near the end.

> **Known quirk:** clicking "Game Learning Viewer" directly in the sidebar currently opens an
> empty screen. To actually see a game's turn-by-turn viewer, go through **Game Recap → View**
> instead (see below). This is a known limitation, not something you're doing wrong.

---

## Game Recap — replay a game you've already played

**Screen title:** "Game Recap: Recent Games"

### Where the games come from

Every real game you finish through the GUI is automatically saved as a small JSON file in
`%AppData%\Forge\games\gamelogs\`. Nothing needs to be turned on — this happens by itself
whenever a normal Constructed/Commander/etc. game ends. Games played through Investigate
Scenarios (see below) don't show up here; they're recorded too, but kept separate.

### The list

Game Recap shows your past games, newest first, one line each:

```
2026-04-03 14:22 | Atraxa Praetors, Elves Tribal | 12 turns | Win
```

A **"Show last (days, 0=all)"** dropdown at the top lets you filter to just the last day, few
days, or everything. A game already shows `[Replayed]` once you've used its Start button.

Click a game to see more detail below the list: the date, game type, turn count, duration, and
each player's deck name, card count, life total, and whether they won.

### The two buttons

- **Start** — launches this game again as a **brand-new, fully live, playable match**. This is
  not a movie playback: you actually play from Turn 1 onward, with the same decks, the same
  opening hand and draw order as the original game, but you're free to make different decisions
  at every step. You'll be seated as the first human player; every other seat is played by AI
  using the same deck the original player had.
- **View** — opens the **Game Learning Viewer** (below) so you can study the game turn by turn
  without playing it.

### CLI shortcut

Power users can jump straight into replaying a specific file without clicking through the menu:

```
java -jar <forge-jar> replay <path-to-replay.json>
```

This boots Forge normally and auto-starts that replay once the home screen is ready — equivalent
to picking the file in Game Recap and clicking Start.

---

## Game Learning Viewer — step through a game turn by turn

**Reached via:** Game Recap → select a game → **View**. (Not the sidebar entry — see the note
at the top of this guide.)

This opens as its own tab (next to Home and Deck Editor), separate from the sidebar panel. It's
a **read-only study tool**: you're not playing, you're reviewing what happened.

### What you see

- **Left:** a scrollable list of turn cards, one per turn, each showing small life-total dots
  per player (red = low life, yellow = mid, green = healthy; a white ring marks whose turn it
  was). A ⚠ marks a turn where the game detected a costly mistake ("blunder"); a 🔖 marks a
  turn with an author-added note. Three extra entries bookend the list: an overview, the game's
  starting setup, and the final result.
- **Right (for whichever turn is selected):** a board view (opponent on top, you on the bottom)
  with life totals, hand/library/graveyard/exile counts, and the actual cards on the
  battlefield; a short evaluation summary; and tabs for that turn's **Events** log,
  **Statistics**, and a **Game Report**.
- **Bottom:** **◄ Prev Turn**, **Next Turn ►**, and **▶ Replay from here**.

One thing worth knowing: a turn's snapshot shows the board **at the start of that turn**,
before that turn's own plays happen — something played mid-turn shows up starting on the
*next* turn's snapshot, not the one where it was played.

### Replay from here

This branches a **new, live, playable game** starting from whatever turn you're looking at,
with the same decks. You'll be asked to confirm, with an option (checked by default) to
**"Enforce Cards drawn order"** — keeping the same draw order as the original game from that
point forward, so you're testing "what if I'd played differently" rather than "what if I'd also
drawn different cards." Mulligan and the coin toss are skipped since you're starting mid-game.

---

## Investigate Scenarios — scripted teaching puzzles

**Screen title:** "Investigate Scenarios: Teaching Scenarios"

This is a separate, curated list of hand-built teaching scenarios — not your own game history.
Each one guarantees a specific opening hand (and sometimes a specific board state, commander,
or life total) so you can study a particular situation without relying on luck.

### Where scenario files live

Same folder as replays — `%AppData%\Forge\games\gamelogs\` — but only files that are
specifically marked as scenarios show up in this list (ordinary replays stay in Game Recap
instead, never both). If someone hands you a scenario `.json` file, drop it in that folder and
it'll appear here.

Selecting one shows its title, description, a study question, an answer/explanation if
provided, and any reference tags.

### The two buttons

- **Start** — launches the scenario as a standalone puzzle: you get the exact scripted opening
  hand (and draw order after it), against an AI opponent. If the scenario also scripts a
  specific play-by-play line for the AI to follow, the AI will attempt to follow it — trying its
  scripted next play at every opportunity, and moving on (with a note in the game log, not a
  freeze) if that particular play never becomes possible during the turn it was meant to happen.
  Some scenarios also make the AI skip its mulligan decision, so it always keeps its scripted
  hand.
- **Demo Play (record actions)** — this one's for people **writing** scenarios, not playing
  them. It gives you the guaranteed opening hand but *doesn't* apply any pre-scripted AI
  line — you play it out yourself, for real, to discover a good line. Once the game ends, Forge
  automatically writes out everything you did (as a ready-to-paste block) to a companion file
  next to the recording, and shows you its location. If you're just here to study a scenario,
  use Start instead — Demo Play is a scenario-authoring aid.

---

## Attach a scenario to a real Constructed/Commander match

This is different from Investigate Scenarios: instead of a standalone puzzle with no deck of
your own, you can bring a scenario's guaranteed draw order into an **actual match**, using your
own real deck, from the normal Constructed/Commander lobby.

### Making a deck eligible

Open your deck's `.dck` file and add a `Scenario=` line to its `[metadata]` block, naming one
or more scenario files (by their `id`, or by filename if the scenario has no `id`):

```ini
[metadata]
Name=Horror: Dead is not an end
Scenario=perfect_game_horror,scenario_horror_t3_test
```

(This is a different key from `EvalScenario=`, which is for an unrelated feature — don't mix
them up.)

### Using it in the lobby

Once a deck with a `Scenario=` key is selected for a seat in the Constructed or Commander
lobby, a new **"Scenario:"** dropdown appears at the bottom of that seat's deck settings (below
the deck/commander/vanguard selectors). It starts on "None." If none of the deck's listed
scenarios can actually be found, the dropdown simply doesn't appear for that seat.

Picking a scenario there is per-seat — set it independently for your seat, an AI opponent's
seat, or both, using different scenarios if you like. If some of the scenario's specified cards
aren't actually in the deck you picked, the entry is still selectable but marked
**"(missing cards)"** as a heads-up; those specific cards just won't be there to draw.

### What happens when the match starts

- **Every seat with a scenario attached** (human or AI) draws its exact scripted opening hand
  and follow-up draws, guaranteed — pulled from that seat's own real, shuffled deck, same as
  Investigate Scenarios.
- **If the scenario also has a scripted play-by-play line:**
  - On an **AI seat**, the AI actually tries to follow it.
  - On **your own (human) seat**, nothing is forced or blocked — you play however you want. But
    the prompt area will show a small hint (💡 "Scripted line suggests: …") naming what the
    script intended as your next move, purely as a suggestion.
- If any seat in the match has this kind of scenario attached, AI opponents in that match skip
  their mulligan decision entirely (this applies match-wide, not just to the seat that
  requested it).

This is the "Perfect Game" / best-opening-hand idea applied to a real match with real decks,
rather than an isolated puzzle: you (or your opponent) get a specific opening hand to test
against, and everything else about the match plays out normally.

### CLI equivalent

For quick one-off testing without touching a deck file, the CLI simulator supports the same
idea directly:

```
java -jar <forge-jar> sim -d <deck1> <deck2> -n 1 -f Commander -scenario <path-to-scenario.json>
```

(Note the flag is `-scenario`, not `-s` — `-s` is already used for the random-seed value.)

---

## Quick reference

| I want to... | Go to... |
|---|---|
| Replay a game I already finished, live, from scratch | **Game Recap** → select game → **Start** |
| Study a finished game turn by turn without playing it | **Game Recap** → select game → **View** |
| Branch a live game from partway through a past game | Game Learning Viewer → **Replay from here** |
| Play a hand-crafted teaching puzzle | **Investigate Scenarios** → select scenario → **Start** |
| Record my own play to help write a scenario's script | **Investigate Scenarios** → select scenario → **Demo Play** |
| Give my own deck a guaranteed opening hand in a real match | Add `Scenario=` to the deck's `.dck` file, then pick it from the **Scenario:** dropdown in the lobby |
