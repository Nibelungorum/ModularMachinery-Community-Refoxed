# Multiblock Detection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Port the first MMCE-style multiblock detection phase into the current NeoForge controller.

**Architecture:** Keep the current `Machine`, `BlockArray`, and recipe execution model. Add controller-side matched-structure state, split structure detection from recipe ticking, and scan registered machines when the controller has no formed structure.

**Tech Stack:** Java 21, Minecraft 26.1.2, NeoForge, Gradle, JUnit/GameTest.

## Global Constraints

- Keep changes minimal and local to the controller detection phase.
- Do not implement `DynamicPattern`, blueprints, component selector tags, structure preview, or async recipe threads.
- Do not change public machine DSL shape unless detection needs read-only access.
- Remove the client-side machine-state DEBUG log.

---

### Task 1: Remove Machine State Debug Log

**Files:**
- Modify: `src/main/java/cn/howxu/mmcr/MMCR.java`

**Interfaces:**
- Consumes: existing `PktMachineStatePayload` registration.
- Produces: no-op client payload handler without DEBUG spam.

- [ ] Replace `(payload, ctx) -> MMCR.LOG.debug("Received machine state: {}", payload)` with `(payload, ctx) -> {}`.
- [ ] Run `./gradlew compileJava --no-daemon`.

### Task 2: Add Registry Scan GameTest

**Files:**
- Modify: `src/gametest/java/cn/howxu/mmcr/ControllerTickGameTest.java`
- Modify: `src/gametest/java/cn/howxu/mmcr/GameTestRegistry.java`

**Interfaces:**
- Consumes: `MachineRegistry.register(Machine)`, `MachineControllerBlockEntity.serverTick()`.
- Produces: regression coverage that a controller can form by scanning registered machines without `setMachine()`.

- [ ] Add `scansRegisteredMachineWhenDefaultBindingIsEmpty` to `ControllerTickGameTest`.
- [ ] Register it in `GameTestRegistry.registerAll`.
- [ ] Run a compile/test command to verify the new test is detected and current behavior is red where possible.

### Task 3: Implement Controller Detection State

**Files:**
- Modify: `src/main/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntity.java`

**Interfaces:**
- Consumes: `StructureMatcher.matches(BlockArray, Level, BlockPos, Direction)`, `MachineRegistry.getAll()`.
- Produces: `getFoundMachine()`, `getFoundPattern()`, `checkStructure()`, `checkAllPatterns()`, `resetMachine()`, and `onStructureFormed(...)` behavior.

- [ ] Add `foundMachine`, `foundPattern`, and `controllerFacing` fields.
- [ ] Add package/public read-only getters for tests and future UI.
- [ ] Split `serverTick()` into structure check then recipe execution.
- [ ] Prefer validating already found structure before scanning all registered machines.
- [ ] Reset active recipe and progress when structure fails.
- [ ] Run relevant tests and `compileJava`.
