# Variant Selection Analysis - Blocking Deck Pre-Selection

**Date:** 2026-05-19  
**Issue:** Automatic Commander selection in CLobby may block deck name pre-selection  
**Status:** Testing hypothesis

## Problem Statement

User reported that upstream Forge does NOT auto-select Commander variant on startup, while the fork DOES. This automatic selection may be interfering with CLI deck pre-selection.

## Key Differences: Fork vs Upstream

### Upstream `CLobby.initialize()` (Lines 60-76)
```java
public void initialize() {
    final ForgePreferences prefs = FModel.getPreferences();
    // Checkbox event handling
    view.getCbSingletons().addActionListener(arg0 -> {
        prefs.setPref(FPref.DECKGEN_SINGLETONS, String.valueOf(view.getCbSingletons().isSelected()));
        prefs.save();
    });

    view.getCbArtifacts().addActionListener(arg0 -> {
        prefs.setPref(FPref.DECKGEN_ARTIFACTS, String.valueOf(view.getCbArtifacts().isSelected()));
        prefs.save();
    });

    // Pre-select checkboxes
    view.getCbSingletons().setSelected(prefs.getPrefBoolean(FPref.DECKGEN_SINGLETONS));
    view.getCbArtifacts().setSelected(prefs.getPrefBoolean(FPref.DECKGEN_ARTIFACTS));
    
    // NO variant selection!
}
```

### Fork `CLobby.initialize()` (Lines 62-89)
```java
public void initialize() {
    final ForgePreferences prefs = FModel.getPreferences();
    // ... same checkbox handling ...

    // Pre-select checkboxes
    view.getCbSingletons().setSelected(prefs.getPrefBoolean(FPref.DECKGEN_SINGLETONS));
    view.getCbArtifacts().setSelected(prefs.getPrefBoolean(FPref.DECKGEN_ARTIFACTS));

    // FORK-ONLY: Automatic variant selection
    Set<GameType> savedVariants = prefs.getGameType(FPref.UI_APPLIED_VARIANTS);
    if (!savedVariants.isEmpty()) {
        for (GameType variant : savedVariants) {
            view.applyVariant(variant);  // ← Triggers deck chooser creation!
        }
    } else {
        // Default to Commander if no variants are saved
        view.applyVariant(GameType.Commander);  // ← Always applies Commander as fallback
    }
}
```

## Execution Flow Analysis

### Startup Sequence
1. **Main.main()** parses CLI args
2. **Main.startGui(options)**:
   - `Singletons.initializeOnce(true)` - initializes FModel & preferences
   - `applyGuiLaunchOptions(options)` - writes deck names & variant to in-memory prefs
   - `Singletons.getControl().initialize()` - starts GUI

3. **GUI Initialization** (async):
   - Screens are created
   - User navigates to Constructed lobby OR lobby opens by default
   - `CLobby.initialize()` is called

4. **Fork-Specific:** `CLobby.initialize()` calls `view.applyVariant(GameType.Commander)`
5. **VLobby.applyVariant()** calls `lobby.applyVariant()` then `update(false)`
6. **VLobby.update(false)** creates player panels:
   ```java
   if (isNewPanel || fullUpdate) {
       final FDeckChooser deckChooser = createDeckChooser(lobby.getGameType(), i, isSlotAI);
       deckChooser.populate();  // ← Reads preferences here!
       panel.setDeckChooser(deckChooser);
   }
   ```
7. **FDeckChooser.populate()** calls `restoreSavedState()` → reads deck name preferences

## Hypothesis: Race Condition

### Scenario A: Normal Flow (Should Work)
```
1. applyGuiLaunchOptions() writes deck names to prefs (in-memory)
2. applyGuiLaunchOptions() writes variant to FPref.UI_APPLIED_VARIANTS
3. Singletons.getControl().initialize() starts
4. CLobby.initialize() reads FPref.UI_APPLIED_VARIANTS
5. CLobby.initialize() calls applyVariant(Commander)
6. update() creates deck choosers
7. populate() → restoreSavedState() reads deck names
8. ✅ Deck names should be found
```

### Scenario B: Async Interference (Potential Problem)
```
1. applyGuiLaunchOptions() writes variant to FPref.UI_APPLIED_VARIANTS
2. Singletons.getControl().initialize() starts (async initialization)
3. CLobby.initialize() is called BEFORE applyGuiLaunchOptions() completes?
4. restoreSavedState() reads empty deck name prefs?
5. ❌ Deck names NOT found
```

### Scenario C: Double Application (Potential Problem)
```
1. applyGuiLaunchOptions() writes variant Commander to FPref.UI_APPLIED_VARIANTS
2. CLobby.initialize() reads saved variant = Commander
3. CLobby.initialize() calls applyVariant(Commander) → creates deck choosers
4. User manually clicks Commander checkbox in UI
5. applyVariant(Commander) called AGAIN → recreates deck choosers
6. New deck choosers lose the pending selection
7. ❌ Deck names lost
```

## Testing Hypothesis

### Change Made
Modified `CLobby.initialize()` to:
- Still apply saved variants from CLI (when `FPref.UI_APPLIED_VARIANTS` is non-empty)
- **NOT** auto-select Commander as fallback when preferences are empty
- Add debug logging to trace exact execution

### Code Change
```java
Set<GameType> savedVariants = prefs.getGameType(FPref.UI_APPLIED_VARIANTS);
if (!savedVariants.isEmpty()) {
    System.err.println("[VARIANT-SELECT] CLobby.initialize: Applying saved variants: " + savedVariants);
    for (GameType variant : savedVariants) {
        view.applyVariant(variant);
    }
} else {
    System.err.println("[VARIANT-SELECT] CLobby.initialize: No saved variants, NOT auto-selecting Commander (testing)");
    // COMMENTED OUT FOR TESTING:
    // view.applyVariant(GameType.Commander);
}
```

## Expected Test Results

### With `--format commander --deck "deckname"` (Variant Set):
- ✅ Should read Commander from preferences
- ✅ Should apply Commander variant
- ✅ Should create deck choosers
- ✅ Should restore deck name from preferences
- **Expected:** Deck pre-selection WORKS

### Without `--format` (No Variant Set):
- ✅ Should find NO saved variants
- ❌ Should NOT apply Commander variant automatically
- ❌ Lobby starts with NO variant selected
- User must manually click Commander
- **Expected:** Matches upstream behavior, but deck pre-selection may fail if no variant is applied

## Alternative Solutions

### Option 1: Apply Variant in Main.applyGuiLaunchOptions()
Instead of relying on CLobby to apply the variant, do it directly in Main:
```java
// In applyGuiLaunchOptions()
if (!variants.isEmpty()) {
    // Force synchronous application before GUI starts
    // This ensures deck choosers are created AFTER preferences are written
    SwingUtilities.invokeAndWait(() -> {
        // Find lobby view and apply variant
    });
}
```

### Option 2: Delay Deck Chooser Creation
Don't create deck choosers in `CLobby.initialize()`, wait for explicit user action or CLI trigger.

### Option 3: Explicit Deck Loading After Initialization
Add a method to force deck loading after lobby is fully initialized:
```java
// In Main, after Singletons.getControl().initialize()
SwingUtilities.invokeLater(() -> {
    // Find lobby and force deck restore
    lobbyView.updateDeckPanel();
});
```

## Files Modified
- `forge-gui-desktop/src/main/java/forge/screens/home/CLobby.java` - Disabled auto-Commander selection

## Next Steps
1. Build and test with change
2. Verify deck pre-selection works when variant is explicitly set via `--format`
3. Compare behavior to upstream (no variant auto-selection)
4. Decide on permanent fix based on test results

## Related Documents
- DECK_PRESELECT_INVESTIGATION.md - Original deck pre-selection analysis
- FORK_VS_UPSTREAM_COMPARISON.md - All fork vs upstream differences

