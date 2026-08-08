# Task 1 Report

## Changes

- `MachineDefinitions` now exposes `beginRegistryPhase()`, `freezeRegistryPhase()`, and `isRegistryPhaseOpen()`. Registration rejects writes after freeze with an explicit registry-phase error while preserving duplicate-ID rejection during the open phase.
- `MMCR` now opens the startup phase before built-in and startup integration registration, bootstraps definitions, freezes the phase, and only then touches mod block/item/block-entity registries.
- `MachineBuilderJS` remains the public startup registration API and now inherits the explicit phase boundary from `MachineDefinitions`.
- `DynamicContentReloadService` was verified to validate existing startup definitions and update dynamic snapshots without calling `MachineDefinitions.register()`.
- `MachineDefinitionBootstrapTest` covers startup registration, freeze rejection, reload registration-count stability, and existing/unknown machine reload behavior. Setup and teardown explicitly reset and reopen the phase.
- `MachineBuilderJSTest` covers startup registration during the open phase and rejection after freeze, with explicit phase setup/teardown.

## Tests

Command:

```text
./gradlew test --no-daemon --tests cn.howxu.mmcr.MachineDefinitionBootstrapTest --tests cn.howxu.mmcr.compat.kubejs.MachineBuilderJSTest
```

Actual result:

```text
BUILD SUCCESSFUL in 11s
17 actionable tasks: 3 executed, 14 up-to-date
```

The selected test classes completed successfully. The Java compile emitted only pre-existing deprecation/unchecked warnings from unrelated sources.

## Commit

`32b3105` (`refactor: freeze machine registrations before registry load`)

## Concerns

- The worktree already contained an unrelated modification to `TODO.md`; it was intentionally not changed or staged.
- No separate test was added for production `MMCR` construction because constructing the NeoForge mod entry point requires the full mod event environment; the ordering is implemented directly and the targeted bootstrap tests cover the registration boundary.
