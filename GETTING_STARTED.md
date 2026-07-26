# Getting Started with Forge (Fork)

This is a custom fork of the **Forge** Magic: The Gathering rules engine. This guide walks you through cloning the fork and setting up your development environment.

## Repository Address

**Fork Repository:** `https://github.com/killriam/forge.git`

This fork includes custom features like:
- Game analytics (SQLite integration)
- MTG Replay Notation JSON logging
- AI decision logging
- Interactive game replay
- Game learning viewer with turn evaluation
- Scenario viewer

**Upstream Repository:** `https://github.com/Card-Forge/forge.git`

## Prerequisites

Before you begin, ensure you have the following installed:

- **Java 17+** ([download](https://www.oracle.com/java/technologies/downloads/#java17))
- **Maven 3.8.0+** ([download](https://maven.apache.org/download.cgi))
- **Git** ([download](https://git-scm.com/))

Verify your installation:
```bash
java -version
mvn -version
git --version
```

## Clone the Fork

### Option 1: Clone This Fork (Recommended)

```bash
git clone https://github.com/killriam/forge.git forge-fork
cd forge-fork
```

### Option 2: Clone with Upstream Tracking

If you want to track upstream changes and sync regularly:

```bash
git clone https://github.com/killriam/forge.git forge-fork
cd forge-fork
git remote add upstream https://github.com/Card-Forge/forge.git
git fetch upstream
```

## Build the Project

### Full Build (All Modules)

```bash
mvn -U -B clean -P windows-linux install
```

This command:
- `-U`: Updates snapshots
- `-B`: Batch mode (non-interactive)
- `-P windows-linux`: Applies the Windows/Linux profile
- `clean install`: Cleans and builds all modules

**First-time build may take 5-10 minutes.**

### Quick Build (Desktop Client Only)

If you only need the desktop client:

```bash
mvn clean package -pl forge-gui-desktop -am -Dmaven.test.skip=true
```

## Run Forge

### Desktop GUI

After a successful build:

```bash
java -jar forge-gui-desktop/target/forge-gui-desktop-*-SNAPSHOT-jar-with-dependencies.jar
```

Or run directly from Maven:
```bash
mvn exec:java -pl forge-gui-desktop
```

### Headless AI Simulation (CLI)

Test two decks against each other 10 times:

```bash
java -jar forge-gui-desktop/target/forge-gui-desktop-*-SNAPSHOT-jar-with-dependencies.jar sim -d deck1.dck deck2.dck -n 10 -q
```

Options:
- `-d`: Deck files (can specify multiple)
- `-n`: Number of games to simulate
- `-m`: Match format (best-of, e.g., `-m 1` for single game)
- `-f`: Format (e.g., `constructed`, `limited`)
- `-q`: Quiet mode (minimal output)
- `-c`: Timeout in seconds

### Interactive Game Replay

Replay a previously recorded game:

```bash
java -jar forge-gui-desktop/target/forge-gui-desktop-*-SNAPSHOT-jar-with-dependencies.jar replay path/to/replay_log.json
```

### Card Validation

Validate all card scripts:

```bash
java -jar forge-gui-desktop/target/forge-gui-desktop-*-SNAPSHOT-jar-with-dependencies.jar parse
```

## Project Structure

```
forge/
├── forge-core/              # Card definitions, rules primitives
├── forge-game/              # Game loop, phases, combat, stack
├── forge-ai/                # Heuristic AI opponent
├── forge-gui/               # Shared UI logic + card scripts in res/
├── forge-gui-desktop/       # Swing desktop client (Main.main entry point)
├── forge-gui-mobile/        # libGDX mobile UI
├── forge-lda/               # LDA topic modeling
├── adventure-editor/        # Adventure mode editor
├── mtg-replay-notation/     # Git submodule: replay format spec
└── docs/                    # Documentation
```

## Verify Your Setup

```bash
# Test desktop build
java -jar forge-gui-desktop/target/forge-gui-desktop-*-SNAPSHOT-jar-with-dependencies.jar

# Quick simulation to verify AI works
java -jar forge-gui-desktop/target/forge-gui-desktop-*-SNAPSHOT-jar-with-dependencies.jar sim -d docs/example-deck.dck -n 1 -q
```

The desktop GUI should launch, and simulations should complete without errors.

## Next Steps

1. **Read the Documentation**
   - Main README: `README.md`
   - CLI Documentation: `docs/CLI.md`
   - Card Scripting API: `docs/Card-scripting-API/`

2. **Explore the Fork Changes**
   - See `FORK_CHANGES_SUMMARY.md` for custom features
   - See `FORK_VS_UPSTREAM_COMPARISON.md` for differences

3. **Create a Development Branch**
   ```bash
   git checkout -b feature/my-feature
   ```

4. **Sync with Upstream** (Optional)
   ```bash
   git fetch upstream
   git merge upstream/master
   ```
   Use merge, not rebase — custom commits are already pushed to `origin/replay-Features`,
   and rebasing would rewrite that published history.

## Troubleshooting

### Build Fails with Java Version Error
Ensure you're using Java 17 or higher:
```bash
java -version
```

### Build Fails with Maven Error
Clear Maven cache and retry:
```bash
mvn clean install -U
```

### Checkstyle Violations
The build enforces code style checks. Common issues:
- Redundant imports
- Unused imports

Run validation only:
```bash
mvn validate
```

### Desktop Client Won't Start
Ensure you have the correct JAR:
```bash
ls -la forge-gui-desktop/target/forge-gui-desktop-*-SNAPSHOT-jar-with-dependencies.jar
```

## Resources

- **Official Forge Repository:** https://github.com/Card-Forge/forge
- **Magic: The Gathering Rules:** https://magic.wizards.com/en/rules
- **Card Scripting Documentation:** `docs/Card-scripting-API/`
- **Fork Analysis:** `AI_FEATURES_ANALYSIS.md`

## Support

For issues specific to this fork, check:
- `BUILD_STATUS_*.md` files for recent build history
- `DEBUG_LOGS_CLEANUP_SUMMARY.md` for debugging tips
- Issue tracker on GitHub

Happy brewing! 🧙‍♂️
