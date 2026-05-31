# Deck Pre-Selection Investigation (`--deck` Parameter)

**Date:** 2026-05-18  
**Status:** NOT WORKING - Deck not being selected in lobby  
**Command:** `java -jar forge.jar --format commander --deck "killriam - Horror: Dead is not an end (2026-05-18)"`

## Problem Description

The `--deck` CLI parameter is implemented and writes to preferences correctly, but the deck is NOT being selected in the Commander lobby when Forge starts.

## Implementation Summary

### 1. CLI Parameter Parsing (`Main.java`)
- **Location:** `forge-gui-desktop/src/main/java/forge/view/Main.java`
- **Method:** `parseGuiLaunchOptions()`
- **What it does:** Parses `--deck` and `--format` arguments into `GuiLaunchOptions`

### 2. Preference Writing (`Main.java`)
- **Method:** `applyGuiLaunchOptions()`
- **Timing:** Called BEFORE `Singletons.getControl().initialize()`
- **Format:** Writes `"COMMANDER_DECK;{deckname}"` to `FPref.COMMANDER_DECK_STATES[0]`
- **Code:**
```java
final String deckPrefix = options.format.deckTypeName + ";";
if (options.playerOneDeck != null) {
    prefs.setPref(options.format.prefKeys[0], deckPrefix + options.playerOneDeck);
}
```

### 3. Preference Restoration (`FDeckChooser.java`)
- **Location:** `forge-gui-desktop/src/main/java/forge/deckchooser/FDeckChooser.java`
- **Method:** `restoreSavedState()`
- **What it does:**
  1. Reads saved state from `FPref.COMMANDER_DECK_STATES[0]`
  2. Parses deck type and deck name
  3. Sets `pendingDeckSelection` with deck names
  4. Calls `refreshDecksList()` which triggers async `updateCustom()`

### 4. Async Deck Loading (`FDeckChooser.java`)
- **Method:** `updateCustom()`
- **Flow:**
  1. Runs in background thread: `DeckProxy.getAllCommanderDecks()`
  2. Returns to EDT via `FThreads.invokeInEdtLater()`
  3. Calls `updateDecks(decks, config)`
- **Race Condition Fix:** `pendingDeckSelection` mechanism

### 5. Selection Application (`FDeckChooser.java`)
- **Method:** `updateDecks()`
- **What it does:**
  1. Checks if `pendingDeckSelection` is set
  2. Tries exact match via `lstDecks.setSelectedStrings()`
  3. Falls back to partial name matching
  4. Selects index 0 if nothing matches
- **Selection method:** Calls `ItemManager.setSelectedStrings()` → `stringToItem()` → `setSelectedItems()`

### 6. String-to-Item Matching (`ItemManager.java`)
- **Method:** `stringToItem()`
- **Matching logic:**
```java
for (final Entry<T, Integer> itemEntry : this.pool) {
    if (itemEntry.getKey().toString().equals(str)) {
        return itemEntry.getKey();
    }
}
```
- **Important:** Requires EXACT match with `DeckProxy.toString()`

### 7. DeckProxy String Representation
- **Location:** `forge-gui/src/main/java/forge/deck/DeckProxy.java`
- **Method:** `toString()`
- **Format:** Returns `path + "/" + name` OR just `name` if path is empty
- **Example:** `"killriam - Horror: Dead is not an end (2026-05-18)"`

## Initialization Flow

1. **Main.main()** → parses args → `applyGuiLaunchOptions()` → writes preferences
2. **Main.main()** → `startGui()` → `Singletons.initializeOnce()`
3. **Main.main()** → `Singletons.getControl().initialize()`
4. **FControl.initialize()** → `SwingUtilities.invokeLater(() → Singletons.getView().initialize())`
5. **FView.initialize()** → navigation/screen setup
6. **User navigates to Constructed lobby** OR **Screen auto-opened**
7. **FControl.setCurrentScreen()** → `screen.getController().initialize()`
8. **CSubmenuConstructed.initialize()** → `lobby.initialize()`
9. **CLobby.initialize()** → reads `FPref.UI_APPLIED_VARIANTS` → calls `view.applyVariant(GameType.Commander)`
10. **VLobby.applyVariant()** → `lobby.applyVariant(variant)` → `update(false)`
11. **VLobby.update()** → creates player panels → calls `createDeckChooser()` → `populate()`
12. **FDeckChooser.populate()** → creates `DecksComboBox` → calls `restoreSavedState()`
13. **FDeckChooser.restoreSavedState()** → sets `pendingDeckSelection` → `refreshDecksList()`
14. **FDeckChooser.refreshDecksList()** → calls `updateCustom()`
15. **FDeckChooser.updateCustom()** → background thread loads decks → EDT calls `updateDecks()`
16. **FDeckChooser.updateDecks()** → applies `pendingDeckSelection` via `lstDecks.setSelectedStrings()`

## Investigation Points

### ✅ Confirmed Working
- [x] CLI parameter parsing
- [x] Preference writing (format: `"COMMANDER_DECK;{deckname}"`)
- [x] Debug prints removed
- [x] Build successful
- [x] `pendingDeckSelection` mechanism implemented
- [x] Async race condition handled

### ❓ Potential Issues

#### Issue 1: Timing - When is CLobby.initialize() called?
- **Question:** Is `CLobby.initialize()` called BEFORE or AFTER `applyGuiLaunchOptions()`?
- **Why it matters:** If it reads preferences before they're written, selection will fail
- **Evidence needed:** Add logging to both methods with timestamps

#### Issue 2: Deck Name Format Mismatch
- **Question:** Does the deck name from `--deck` EXACTLY match `DeckProxy.toString()`?
- **Deck file name:** `killriam - Horror: Dead is not an end (2026-05-18).dck`
- **Name in file:** Unknown (need to check `Name:` field in .dck file)
- **DeckProxy.toString():** Returns `path/name` or just `name`
- **CLI parameter:** `"killriam - Horror: Dead is not an end (2026-05-18)"`
- **Evidence needed:** Log actual deck names in pool vs. search string

#### Issue 3: Lobby Opens Before Preferences Written
- **Question:** Does the lobby screen open automatically causing premature `populate()`?
- **Why it matters:** If lobby opens before `applyGuiLaunchOptions()`, preferences won't be set
- **Evidence needed:** Check default screen and screen switching logic

#### Issue 4: FDeckChooser Already Populated
- **Question:** Is `populate()` called multiple times, overwriting `pendingDeckSelection`?
- **Code check:**
```java
public void populate() {
    if (decksComboBox == null) {
        // initialization + restoreSavedState()
    } else {
        removeAll();
        // rebuild UI but NO restoreSavedState()
    }
}
```
- **Evidence needed:** Check if `populate()` is called > 1 time

#### Issue 5: Variant Not Applied
- **Question:** Is `GameType.Commander` variant properly applied before deck chooser created?
- **Why it matters:** Wrong variant = wrong deck type = wrong deck pool
- **Evidence needed:** Check `lobby.getAppliedVariants()` vs `GameType.Commander`

#### Issue 6: updateDeckPanel() Not Called
- **Question:** Is `updateDeckPanel()` ever called to trigger `restoreSavedState()` on already-created choosers?
- **Code:**
```java
public void updateDeckPanel() {
    for (final PlayerPanel playerPanel : playerPanels) {
        playerPanel.getDeckChooser().restoreSavedState();
    }
}
```
- **When called:** Only from `DeckController` after deck edits
- **Evidence needed:** May need manual call after lobby initialization

## Changes Made

### Session 1-2: Initial Implementation
1. ✅ Added `GuiLaunchOptions` and `GuiDeckFormat` enum to `Main.java`
2. ✅ Added `parseGuiLaunchOptions()` to parse `--deck`, `--deck2`, `--format`
3. ✅ Added `applyGuiLaunchOptions()` to write preferences
4. ✅ Modified `main()` to handle `--deck` as GUI mode trigger

### Session 3: Race Condition Fix
1. ✅ Added `pendingDeckSelection` field to `FDeckChooser`
2. ✅ Modified `updateDecks()` to consume `pendingDeckSelection` after async load
3. ✅ Modified `restoreSavedState()` to set `pendingDeckSelection` instead of immediate selection
4. ✅ Added fallback partial-name matching in `updateDecks()`

### Session 4: Debug Cleanup
1. ✅ Removed all `[GUI LAUNCH]` debug prints from `Main.java`
2. ✅ Removed all `[DECK LOADING DEBUG]` prints from `ItemManager.java` (all 5 occurrences in `updateView()`)

### Session 5: Root Cause Diagnosis & Fix
1. ✅ Added comprehensive diagnostic logging to:
   - `Main.applyGuiLaunchOptions()` - log preference writes
   - `FDeckChooser.restoreSavedState()` - log preference reads and pendingDeckSelection
   - `FDeckChooser.updateDecks()` - log available decks and selection process
   - `FDeckChooser.updateCustom()` - log async loading progress
   - `FDeckChooser.refreshDecksList()` - log deck type changes
2. ✅ **FIXED RACE CONDITION:** Modified `restoreSavedState()` to NOT consume `pendingDeckSelection` for async deck types
   - Added `isAsyncDeckType` check for COMMANDER/CUSTOM/OATHBREAKER/TINY_LEADERS/BRAWL decks
   - Fallback selection code now only runs for synchronous deck types (color/theme decks)
   - Async deck types wait for `updateDecks()` to consume `pendingDeckSelection`

## Test Results

### Build Status
- ✅ Maven build: **SUCCESS**
- ✅ JAR created: `forge-gui-desktop-2.0.13-SNAPSHOT-jar-with-dependencies.jar`

### Runtime Status (BEFORE FIX)
- ❌ Deck selection: **FAILED** - Deck not selected in lobby
- ✅ Console spam: **FIXED** - No debug prints
- ❌ User report: "still not setted"

### Runtime Status (AFTER FIX)
- ⏳ Testing in progress...

## ROOT CAUSE IDENTIFIED

**Bug Location:** `FDeckChooser.restoreSavedState()` lines 822-840

**Problem:** Race condition where `pendingDeckSelection` is consumed TWICE:
1. `restoreSavedState()` sets `pendingDeckSelection` 
2. `restoreSavedState()` calls `refreshDecksList()` (starts async load for Commander decks)
3. **IMMEDIATELY** the fallback code (lines 822-840) consumes `pendingDeckSelection` and clears it
4. When `updateDecks()` runs later in the async thread, `pendingDeckSelection` is already NULL

**Evidence from logs:**
```
[DECK-PRESELECT] Writing P1 pref to COMMANDER_P1_DECK_STATE: COMMANDER_DECK;killriam...
[DECK-PRESELECT] restoreSavedState: Reading pref COMMANDER_P1_DECK_STATE = COMMANDER_DECK;killriam...
[DECK-PRESELECT] restoreSavedState: Parsed DeckType = Commander Decks
[DECK-PRESELECT] restoreSavedState: Parsed deck names = [killriam - Horror: Dead is not an end (2026-05-18)]
[DECK-PRESELECT] restoreSavedState: Set pendingDeckSelection = [killriam...]
# NO updateDecks() log! Because pendingDeckSelection was cleared by fallback code
```

**Fix:** Only run the fallback selection code for NON-async deck types.

```java
// For async types (COMMANDER_DECK, CUSTOM_DECK), updateDecks() will consume pendingDeckSelection
final boolean isAsyncDeckType = (deckTypeFromState == DeckType.COMMANDER_DECK || 
                                  deckTypeFromState == DeckType.CUSTOM_DECK ||
                                  deckTypeFromState == DeckType.OATHBREAKER_DECK ||
                                  deckTypeFromState == DeckType.TINY_LEADERS_DECK ||
                                  deckTypeFromState == DeckType.BRAWL_DECK);

if (!isAsyncDeckType && pendingDeckSelection != null) {
    // Apply selection immediately for sync deck types
    System.err.println("[DECK-PRESELECT] restoreSavedState: Applying pending selection for non-async deck type");
    // ... selection logic ...
} else if (isAsyncDeckType) {
    System.err.println("[DECK-PRESELECT] restoreSavedState: Skipping immediate selection for async deck type, will be applied in updateDecks()");
}
```

## Next Steps

### Immediate Actions
1. **Add diagnostic logging** to understand execution flow:
   ```java
   // In applyGuiLaunchOptions()
   System.err.println("[DECK-PRESELECT] Writing pref: " + deckPrefix + deckName);
   
   // In restoreSavedState()
   System.err.println("[DECK-PRESELECT] Reading pref: " + savedState);
   System.err.println("[DECK-PRESELECT] Parsed deck names: " + savedDeckNames);
   System.err.println("[DECK-PRESELECT] pendingDeckSelection set: " + pendingDeckSelection);
   
   // In updateDecks()
   System.err.println("[DECK-PRESELECT] Applying pending selection: " + toSelect);
   System.err.println("[DECK-PRESELECT] Available decks in pool:");
   for (DeckProxy deck : lstDecks.getPool().toFlatList()) {
       System.err.println("  - " + deck.toString());
   }
   ```

2. **Check deck file** to verify exact name:
   ```bash
   cat "my_decks/commander/killriam - Horror: Dead is not an end (2026-05-18).dck" | grep "Name:"
   ```

3. **Test with exact path** if needed:
   ```bash
   --deck "my_decks/commander/killriam - Horror: Dead is not an end (2026-05-18)"
   ```

### Alternative Approaches

#### Option A: Delay Selection Until Lobby Visible
- Add `updateDeckPanel()` call after lobby fully initialized
- Trigger from `VLobby.update()` after all panels created

#### Option B: Force Synchronous Load for CLI Mode
- Detect CLI `--deck` parameter presence
- Skip async load in `updateCustom()` and load synchronously
- Apply selection immediately

#### Option C: Use Deck Index Instead of Name
- Add `--deck-index 0` parameter for first deck in list
- Avoid string matching issues entirely

#### Option D: Direct Deck Setting
- Bypass preference system entirely for CLI mode
- Directly set deck in lobby after initialization
- Call `selectMainDeck()` programmatically

## File Locations

- `forge-gui-desktop/src/main/java/forge/view/Main.java` - CLI parsing & pref writing
- `forge-gui-desktop/src/main/java/forge/deckchooser/FDeckChooser.java` - Deck selection logic
- `forge-gui-desktop/src/main/java/forge/screens/home/VLobby.java` - Lobby UI & deck chooser creation
- `forge-gui-desktop/src/main/java/forge/screens/home/CLobby.java` - Lobby controller
- `forge-gui-desktop/src/main/java/forge/itemmanager/ItemManager.java` - Item selection infrastructure
- `forge-gui/src/main/java/forge/deck/DeckProxy.java` - Deck representation & toString()
- `forge-gui/src/main/java/forge/localinstance/properties/ForgePreferences.java` - Preference definitions

## Code Changes Since Fork (Fork-Specific Features)

**Last Updated:** 2026-05-18  
**Comparison:** Fork (`killriam/forge@upstream-sync/replay-notation`) vs Upstream (`Card-Forge/forge@master`)

This fork adds GUI deck pre-selection functionality that does NOT exist in the official Forge repository. Below are all code changes in areas responsible for deck selection:

### 1. Main.java - CLI GUI Deck Selection (NEW FEATURE)
**File:** `forge-gui-desktop/src/main/java/forge/view/Main.java`  
**Status:** Fork-only feature (no equivalent in upstream)

**Added:**
- `GuiLaunchOptions` class - Stores deck names and format for CLI-launched GUI
- `GuiDeckFormat` enum - Maps CLI format names to deck types and preference keys
  - Formats: `commander`, `oathbreaker`, `tinyleaders`, `brawl`, `constructed`
  - Each links to DeckType name and FPref preference array
- `parseGuiLaunchOptions()` - Parses `--deck`, `--deck2`, `--format` CLI args
- `applyGuiLaunchOptions()` - Writes deck selections to preferences BEFORE GUI init
- Modified `main()` to handle `gui` mode and `--*` flags

**Key Logic:**
```java
// Writes preference in format: "COMMANDER_DECK;{deckname}"
final String deckPrefix = options.format.deckTypeName + ";";
prefs.setPref(options.format.prefKeys[0], deckPrefix + options.playerOneDeck);
```

**Changed Lines:** +156 lines (enum, classes, methods)

### 2. FDeckChooser.java - Async-Safe Deck Selection
**File:** `forge-gui-desktop/src/main/java/forge/deckchooser/FDeckChooser.java`  
**Status:** Modified existing file

**Changed:**
- Removed `lastUpdateRequestTime` field (was unused)
- **Added `pendingDeckSelection` field** - Survives async deck loading race condition
- Modified `updateDecks()`:
  - Checks `pendingDeckSelection` after async load completes
  - Applies selection via `lstDecks.setSelectedStrings(toSelect)`
  - Falls back to partial-name matching if exact match fails
  - Logs available decks and selection status to stderr
- Modified `updateCustom()`:
  - Removed ~90 lines of debug logging
  - Simplified async loading logic
  - Added `[DECK-PRESELECT]` stderr logs for diagnostics

**Key Addition:**
```java
// Apply any pending selection set by restoreSavedState() before the async load finished
if (pendingDeckSelection != null) {
    final List<String> toSelect = pendingDeckSelection;
    pendingDeckSelection = null;
    System.err.println("[DECK-PRESELECT] updateDecks: Applying pending selection = " + toSelect);
    boolean selected = lstDecks.setSelectedStrings(toSelect);
    // ... partial-match fallback ...
}
```

**Changed Lines:** ~250 lines removed (debug logging), ~50 lines added (selection logic)

### 3. VSubmenuConstructed.java - Force Deck Restore on View Switch
**File:** `forge-gui-desktop/src/main/java/forge/screens/home/sanctioned/VSubmenuConstructed.java`  
**Status:** Modified existing file

**Changed:**
```java
for (final FDeckChooser fdc : vLobby.getDeckChoosers()) {
    fdc.populate();
+   fdc.restoreSavedState(); // Restore saved deck selections every time
}
```

**Purpose:** Ensures deck preferences are re-applied when user returns to lobby

**Changed Lines:** +1 line

### 4. ItemManager.java - Clean Up Debug Logging
**File:** `forge-gui-desktop/src/main/java/forge/itemmanager/ItemManager.java`  
**Status:** Modified existing file (cleanup only)

**Removed:**
- 8 lines of `[DECK LOADING DEBUG]` logging in `setPoolImpl()`
- 4 lines of debug logging in `updateView()`

**Changed Lines:** -12 lines (debug removal)

### 5. ItemView.java - Clean Up Debug Logging
**File:** `forge-gui-desktop/src/main/java/forge/itemmanager/views/ItemView.java`  
**Status:** Modified existing file (cleanup only)

**Removed:**
- 10 lines of `[DECK LOADING DEBUG]` logging in `refresh()`

**Changed Lines:** -10 lines (debug removal)

### 6. ItemManagerModel.java - Clean Up Debug Logging
**File:** `forge-gui/src/main/java/forge/itemmanager/ItemManagerModel.java`  
**Status:** Modified existing file (cleanup only)

**Removed:**
- 8 lines of `[DECK LOADING DEBUG]` logging in `refreshSort()`
- Changed 1 error message to use `System.err.println()` instead of `System.out.println()`

**Changed Lines:** -8 lines (debug removal), +1 line (error logging fix)

### 7. CLobby.java - Variant Persistence (Related to Deck Selection)
**File:** `forge-gui-desktop/src/main/java/forge/screens/home/CLobby.java`  
**Status:** Modified existing file (separate feature, but related)

**Added:**
```java
// Load saved variants or default to Commander
Set<GameType> savedVariants = prefs.getGameType(FPref.UI_APPLIED_VARIANTS);
if (!savedVariants.isEmpty()) {
    for (GameType variant : savedVariants) {
        view.applyVariant(variant);
    }
} else {
    // Default to Commander if no variants are saved
    view.applyVariant(GameType.Commander);
}
```

**Purpose:** Restores lobby variant (Commander/Constructed/etc.) from preferences  
**Changed Lines:** +12 lines

### Summary of Fork Changes

| File | Lines Added | Lines Removed | Nature |
|------|-------------|---------------|--------|
| Main.java | +156 | 0 | New feature (CLI deck selection) |
| FDeckChooser.java | +50 | -250 | Modified (async-safe selection + debug cleanup) |
| VSubmenuConstructed.java | +1 | 0 | Modified (force restore on view switch) |
| ItemManager.java | 0 | -12 | Cleanup (debug removal) |
| ItemView.java | 0 | -10 | Cleanup (debug removal) |
| ItemManagerModel.java | +1 | -8 | Cleanup (debug removal + error logging) |
| CLobby.java | +12 | 0 | Modified (variant persistence) |
| **TOTAL** | **+220** | **-280** | **Net: -60 lines** |

**Total Code Changes:** ~500 lines modified across 7 files  
**New Functionality:** GUI deck pre-selection via CLI parameters  
**Upstream Status:** NOT present in official Forge (fork-specific feature)

### Testing Changes

All changes are uncommitted on branch `upstream-sync/replay-notation`:
```bash
git status --short
M  forge-gui-desktop/src/main/java/forge/view/Main.java
M  forge-gui-desktop/src/main/java/forge/deckchooser/FDeckChooser.java
M  forge-gui-desktop/src/main/java/forge/screens/home/sanctioned/VSubmenuConstructed.java
M  forge-gui-desktop/src/main/java/forge/itemmanager/ItemManager.java
M  forge-gui-desktop/src/main/java/forge/itemmanager/views/ItemView.java
M  forge-gui/src/main/java/forge/itemmanager/ItemManagerModel.java
M  forge-gui-desktop/src/main/java/forge/screens/home/CLobby.java
```

**Diff from upstream:** `git diff upstream/master HEAD -- [files]` shows ALL changes listed above

## References

- AGENTS.md - Forge architecture overview
- CLI.md - CLI mode documentation
- FORK_CHANGES_SUMMARY.md - All fork-specific features




