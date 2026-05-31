# Important Docs: Build, Simulation, Upstream Rebase

This is the canonical shortlist of markdown references in this fork for build/simulation/upstream sync workflow.

## Build (canonical)

1. `GETTING_STARTED.md` — fork setup, full build, desktop-only build, quick run commands.
2. `CONTRIBUTING.md` — contributor baseline tooling and standard Maven build command.
3. `docs/Development/Snapshots-and-Releases.md` — release pipeline and branch/PR/release handoff.

## Simulation / CLI (canonical)

1. `docs/CLI.md` — full CLI mode reference (`sim`, `replay`, `gui`, `parse`) and options.
2. `GETTING_STARTED.md` — practical CLI examples for simulation, replay, and parse.
3. `README.md` — high-level CLI overview and quick-start examples.

## Upstream rebase to commit/release (canonical)

1. `GETTING_STARTED.md` — upstream remote setup and sync path:
   - `git remote add upstream https://github.com/forge-ai/forge.git`
   - `git fetch upstream`
   - `git rebase upstream/master`
2. `docs/Development/Snapshots-and-Releases.md` — branch/PR/release flow after syncing.

## Context-only (not canonical process docs)

- `FORGE_EXE_BUILD_GUIDE.md`
- `docs/SIMULATION_AND_LOG_ANALYSIS_GUIDE.md`
- `FORK_VS_UPSTREAM_COMPARISON.md`
- `FORK_CHANGES_SUMMARY.md`
